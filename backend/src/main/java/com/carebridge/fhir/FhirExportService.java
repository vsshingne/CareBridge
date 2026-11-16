package com.carebridge.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.StrictErrorHandler;
import ca.uhn.fhir.validation.FhirValidator;
import com.carebridge.care.CareService;
import com.carebridge.common.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A validated R4 export boundary, not a general-purpose FHIR server. */
@Service
public class FhirExportService {
  private final CareService care;
  private final JdbcTemplate db;
  private final ObjectMapper mapper;
  private final String base;
  private final FhirContext context = FhirContext.forR4();
  private final FhirValidator validator;

  public FhirExportService(
      CareService care,
      JdbcTemplate db,
      ObjectMapper mapper,
      @Value("${app.fhir-base-url:https://carebridge.example/fhir/r4}") String base) {
    this.care = care;
    this.db = db;
    this.mapper = mapper;
    this.base = base.replaceAll("/$", "");
    var support =
        new org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain(
            new ca.uhn.fhir.context.support.DefaultProfileValidationSupport(context),
            new org.hl7.fhir.common.hapi.validation.support.CommonCodeSystemsTerminologyService(
                context),
            new org.hl7.fhir.common.hapi.validation.support
                .InMemoryTerminologyServerValidationSupport(context));
    context.setValidationSupport(support);
    validator = context.newValidator();
    validator.registerValidatorModule(new FhirInstanceValidator(support));
  }

  private Map<String, Object> resource(String type, UUID id) {
    var r = new LinkedHashMap<String, Object>();
    r.put("resourceType", type);
    r.put("id", id.toString());
    r.put(
        "identifier",
        List.of(Map.of("system", base + "/identifier/" + type, "value", id.toString())));
    return r;
  }

  private Map<String, String> ref(String type, Object id) {
    return Map.of("reference", type + "/" + id);
  }

  private void add(List<Map<String, Object>> entries, Map<String, Object> resource) {
    entries.add(
        Map.of(
            "fullUrl",
            base + "/" + resource.get("resourceType") + "/" + resource.get("id"),
            "resource",
            resource));
  }

  private static UUID derived(String type, UUID id) {
    return UUID.nameUUIDFromBytes(
        (type + ":" + id).getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  @Transactional
  public String export(UUID id) {
    var plan =
        care.get(id); // Reuse the same resource authorization and read audit as the clinical API.
    var activities =
        db.queryForList(
            "select * from care_activities where plan_id=? order by created_at,id limit 501", id);
    var referrals =
        db.queryForList(
            "select * from referrals where plan_id=? order by created_at,id limit 501", id);
    if (activities.size() > 500 || referrals.size() > 500)
      throw ApiException.badRequest(
          "Plan exceeds the supported export size of 500 activities and 500 referrals");
    List<Map<String, Object>> entries = new ArrayList<>();
    var patient =
        db.queryForMap(
            "select p.*,u.full_name from patients p join users u on u.id=p.user_id where p.id=?",
            plan.patientId());
    var p = resource("Patient", plan.patientId());
    p.put("name", List.of(Map.of("text", patient.get("full_name"))));
    if (patient.get("date_of_birth") != null)
      p.put("birthDate", patient.get("date_of_birth").toString());
    add(entries, p);
    Set<UUID> providers = new LinkedHashSet<>();
    providers.add(plan.providerId());
    for (var r : referrals) providers.add((UUID) r.get("receiving_provider_id"));
    for (UUID provider : providers) {
      var practitioner = resource("Practitioner", provider);
      practitioner.put(
          "name",
          List.of(
              Map.of(
                  "text",
                  db.queryForObject(
                      "select u.full_name from doctors d join users u on u.id=d.user_id where"
                          + " d.id=?",
                      String.class,
                      provider))));
      add(entries, practitioner);
    }
    var cp = resource("CarePlan", id);
    cp.put("status", plan.status().name().toLowerCase(Locale.ROOT).replace("cancelled", "revoked"));
    cp.put("intent", "plan");
    cp.put("title", plan.title());
    cp.put("description", plan.goals());
    cp.put("subject", ref("Patient", plan.patientId()));
    cp.put("author", ref("Practitioner", plan.providerId()));
    List<Map<String, Object>> links = new ArrayList<>();
    for (var activity : activities) {
      UUID aid = (UUID) activity.get("id");
      String status = (String) activity.get("status");
      var task = resource("Task", aid);
      task.put(
          "status",
          switch (status) {
            case "NOT_STARTED" -> "requested";
            case "SCHEDULED" -> "ready";
            case "IN_PROGRESS" -> "in-progress";
            default -> status.toLowerCase(Locale.ROOT);
          });
      task.put("intent", "order");
      task.put("description", activity.get("title"));
      task.put("for", ref("Patient", plan.patientId()));
      task.put("owner", ref("Practitioner", plan.providerId()));
      task.put("basedOn", List.of(ref("CarePlan", id)));
      task.put(
          "restriction",
          Map.of(
              "period",
              Map.of("end", ((java.sql.Timestamp) activity.get("due_at")).toInstant().toString())));
      links.add(Map.of("reference", ref("Task", aid)));
      add(entries, task);
      if (activity.get("type").equals("INVESTIGATION")
          && status.equals("COMPLETED")
          && activity.get("outcome") != null) {
        var observation = resource("Observation", derived("Observation", aid));
        observation.put("status", "final");
        observation.put("code", Map.of("text", activity.get("title")));
        observation.put("subject", ref("Patient", plan.patientId()));
        observation.put("valueString", activity.get("outcome"));
        observation.put(
            "effectiveDateTime",
            ((java.sql.Timestamp) activity.get("updated_at")).toInstant().toString());
        observation.put("performer", List.of(ref("Practitioner", plan.providerId())));
        add(entries, observation);
      }
    }
    Set<UUID> appointmentIds = new LinkedHashSet<>();
    appointmentIds.add(
        db.queryForObject(
            "select source_appointment_id from care_plans where id=?", UUID.class, id));
    for (var referral : referrals) {
      UUID rid = (UUID) referral.get("id");
      String status = (String) referral.get("status");
      var request = resource("ServiceRequest", rid);
      request.put(
          "status",
          switch (status) {
            case "CREATED", "PENDING_ACCEPTANCE" -> "draft";
            case "COMPLETED" -> "completed";
            case "CANCELLED" -> "revoked";
            default -> "active";
          });
      request.put("intent", "order");
      request.put("priority", ((String) referral.get("priority")).toLowerCase(Locale.ROOT));
      request.put("code", Map.of("text", "Specialist consultation"));
      request.put("subject", ref("Patient", plan.patientId()));
      request.put("requester", ref("Practitioner", plan.providerId()));
      request.put("performer", List.of(ref("Practitioner", referral.get("receiving_provider_id"))));
      request.put("reasonCode", List.of(Map.of("text", referral.get("clinical_reason"))));
      request.put("basedOn", List.of(ref("CarePlan", id)));
      request.put(
          "occurrencePeriod",
          Map.of("end", ((java.sql.Timestamp) referral.get("due_at")).toInstant().toString()));
      links.add(Map.of("reference", ref("ServiceRequest", rid)));
      add(entries, request);
      if (referral.get("appointment_id") != null)
        appointmentIds.add((UUID) referral.get("appointment_id"));
    }
    if (!links.isEmpty()) cp.put("activity", links);
    add(entries, cp);
    for (UUID appointmentId : appointmentIds) {
      var a = db.queryForMap("select * from appointments where id=?", appointmentId);
      var appointment = resource("Appointment", appointmentId);
      String status = (String) a.get("status");
      appointment.put(
          "status",
          switch (status) {
            case "PENDING" -> "pending";
            case "CONFIRMED" -> "booked";
            case "COMPLETED" -> "fulfilled";
            default -> "cancelled";
          });
      appointment.put("start", ((java.sql.Timestamp) a.get("start_time")).toInstant().toString());
      appointment.put("end", ((java.sql.Timestamp) a.get("end_time")).toInstant().toString());
      appointment.put(
          "participant",
          List.of(
              Map.of("actor", ref("Patient", plan.patientId()), "status", "accepted"),
              Map.of(
                  "actor",
                  ref("Practitioner", a.get("doctor_id")),
                  "status",
                  status.equals("PENDING") ? "needs-action" : "accepted")));
      add(entries, appointment);
      if (status.equals("COMPLETED")) {
        var encounter = resource("Encounter", derived("Encounter", appointmentId));
        encounter.put("status", "finished");
        encounter.put(
            "class",
            Map.of("system", "http://terminology.hl7.org/CodeSystem/v3-ActCode", "code", "AMB"));
        encounter.put("subject", ref("Patient", plan.patientId()));
        encounter.put("appointment", List.of(ref("Appointment", appointmentId)));
        encounter.put(
            "period", Map.of("start", appointment.get("start"), "end", appointment.get("end")));
        add(entries, encounter);
      }
    }
    try {
      return validateAndEncode(
          mapper.writeValueAsString(
              Map.of("resourceType", "Bundle", "type", "collection", "entry", entries)));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("FHIR serialization failed", e);
    }
  }

  public String validateAndEncode(String json) {
    var parser = context.newJsonParser().setParserErrorHandler(new StrictErrorHandler());
    Bundle bundle = parser.parseResource(Bundle.class, json);
    var result = validator.validateWithResult(bundle);
    if (!result.isSuccessful())
      throw new IllegalStateException("Generated FHIR resource failed R4 validation");
    return context.newJsonParser().encodeResourceToString(bundle);
  }
}
