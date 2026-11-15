package com.carebridge.appointment;

import static com.carebridge.appointment.AppointmentDtos.*;

import com.carebridge.audit.AuditService;
import com.carebridge.auth.CurrentUser;
import com.carebridge.availability.*;
import com.carebridge.common.*;
import com.carebridge.doctor.*;
import com.carebridge.outbox.EventService;
import com.carebridge.patient.*;
import com.carebridge.user.*;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentService {
  private final AppointmentRepository appointments;
  private final DoctorRepository doctors;
  private final PatientRepository patients;
  private final UserRepository users;
  private final AvailabilityRepository availability;
  private final CurrentUser current;
  private final AuditService audit;
  private final EventService events;

  public AppointmentService(
      AppointmentRepository appointments,
      DoctorRepository doctors,
      PatientRepository patients,
      UserRepository users,
      AvailabilityRepository availability,
      CurrentUser current,
      AuditService audit,
      EventService events) {
    this.appointments = appointments;
    this.doctors = doctors;
    this.patients = patients;
    this.users = users;
    this.availability = availability;
    this.current = current;
    this.audit = audit;
    this.events = events;
  }

  @Transactional
  public View book(Book r) {
    UserAccount actor = current.require(Role.PATIENT);
    Patient patient = patients.findByUserId(actor.id).orElseThrow(ApiException::notFound);
    Doctor doctor = doctors.lockById(r.doctorId()).orElseThrow(ApiException::notFound);
    validateSlot(doctor, r.startTime(), r.endTime(), null);
    Appointment a = new Appointment();
    a.patientId = patient.id;
    a.doctorId = doctor.id;
    a.startTime = r.startTime();
    a.endTime = r.endTime();
    a.reason = r.reason();
    a.status = AppointmentStatus.PENDING;
    appointments.saveAndFlush(a);
    record(actor.id, a, "BOOKED");
    return view(a);
  }

  @Transactional
  public View reschedule(UUID id, Reschedule r) {
    UserAccount actor = current.require(Role.PATIENT);
    Appointment a = lock(id);
    own(a, actor);
    if (!a.status.active() || !a.startTime.isAfter(Instant.now()))
      throw ApiException.conflict("Only future active appointments may be rescheduled");
    Doctor doctor = doctors.findById(a.doctorId).orElseThrow(ApiException::notFound);
    validateSlot(doctor, r.startTime(), r.endTime(), a.id);
    a.startTime = r.startTime();
    a.endTime = r.endTime();
    a.status = AppointmentStatus.PENDING;
    a.updatedAt = Instant.now();
    appointments.saveAndFlush(a);
    record(actor.id, a, "RESCHEDULED");
    return view(a);
  }

  @Transactional
  public View transition(UUID id, AppointmentStatus target) {
    UserAccount actor = current.get();
    Appointment a = lock(id);
    own(a, actor);
    if (target == AppointmentStatus.CANCELLED) {
      if (actor.role != Role.PATIENT && actor.role != Role.DOCTOR) throw ApiException.forbidden();
    } else if (actor.role != Role.DOCTOR) throw ApiException.forbidden();
    a.status.requireTransition(target);
    Instant now = Instant.now();
    if (target == AppointmentStatus.COMPLETED && a.endTime.isAfter(now))
      throw ApiException.conflict("An appointment may only be completed after its end time");
    if (target != AppointmentStatus.COMPLETED && !a.startTime.isAfter(now))
      throw ApiException.conflict("This appointment has already started");
    a.status = target;
    a.updatedAt = now;
    appointments.saveAndFlush(a);
    record(actor.id, a, target.name());
    return view(a);
  }

  private Appointment lock(UUID id) {
    Appointment initial = appointments.findById(id).orElseThrow(ApiException::notFound);
    doctors.lockById(initial.doctorId).orElseThrow(ApiException::notFound);
    // Refresh the managed instance after obtaining the doctor lock: another transaction may have
    // changed it.
    entityManager.refresh(initial, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    return initial;
  }

  @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;

  private void validateSlot(Doctor d, Instant start, Instant end, UUID exclude) {
    if (!d.verified || !users.findById(d.userId).orElseThrow(ApiException::notFound).enabled)
      throw ApiException.conflict("Doctor is not accepting bookings");
    if (start.isAfter(Instant.now().plus(Duration.ofDays(90))))
      throw ApiException.badRequest("Appointments may be booked at most 90 days ahead");
    ScheduleRules.validate(
        start,
        end,
        ZoneId.of(d.timezone),
        availability.findByDoctorIdOrderByDayOfWeekAscStartTimeAsc(d.id),
        Instant.now());
    if (appointments.overlapping(d.id, start, end).stream().anyMatch(a -> !a.id.equals(exclude)))
      throw ApiException.conflict("The selected appointment slot is no longer available");
  }

  private void own(Appointment a, UserAccount actor) {
    boolean allowed =
        switch (actor.role) {
          case ADMIN -> true;
          case PATIENT ->
              patients.findByUserId(actor.id).map(p -> p.id.equals(a.patientId)).orElse(false);
          case DOCTOR ->
              doctors.findByUserId(actor.id).map(d -> d.id.equals(a.doctorId)).orElse(false);
        };
    if (!allowed) throw ApiException.forbidden();
  }

  @Transactional(readOnly = true)
  public View get(UUID id) {
    Appointment a = appointments.findById(id).orElseThrow(ApiException::notFound);
    own(a, current.get());
    return view(a);
  }

  public record PatientDetails(
      String fullName, String phoneNumber, LocalDate dateOfBirth, String gender) {}

  @Transactional(readOnly = true)
  public PatientDetails patientDetails(UUID id) {
    UserAccount actor = current.require(Role.DOCTOR);
    Appointment a = appointments.findById(id).orElseThrow(ApiException::notFound);
    own(a, actor);
    Patient p = patients.findById(a.patientId).orElseThrow(ApiException::notFound);
    UserAccount user = users.findById(p.userId).orElseThrow(ApiException::notFound);
    return new PatientDetails(user.fullName, user.phoneNumber, p.dateOfBirth, p.gender);
  }

  @Transactional(readOnly = true)
  public Pages.Result<View> mine(
      AppointmentStatus status, Instant from, Instant to, Pageable pageable) {
    UserAccount actor = current.get();
    UUID patient = null, doctor = null;
    if (actor.role == Role.PATIENT)
      patient = patients.findByUserId(actor.id).orElseThrow(ApiException::notFound).id;
    else if (actor.role == Role.DOCTOR)
      doctor = doctors.findByUserId(actor.id).orElseThrow(ApiException::notFound).id;
    else throw ApiException.forbidden();
    return list(status, doctor, patient, from, to, pageable);
  }

  @Transactional(readOnly = true)
  public Pages.Result<View> list(
      AppointmentStatus status,
      UUID doctor,
      UUID patient,
      Instant from,
      Instant to,
      Pageable pageable) {
    Specification<Appointment> spec =
        (root, q, cb) -> {
          var p = cb.conjunction();
          if (status != null) p = cb.and(p, cb.equal(root.get("status"), status));
          if (doctor != null) p = cb.and(p, cb.equal(root.get("doctorId"), doctor));
          if (patient != null) p = cb.and(p, cb.equal(root.get("patientId"), patient));
          if (from != null) p = cb.and(p, cb.greaterThanOrEqualTo(root.get("startTime"), from));
          if (to != null) p = cb.and(p, cb.lessThan(root.get("startTime"), to));
          return p;
        };
    return Pages.Result.from(
        appointments
            .findAll(spec, Pages.checked(pageable, "startTime", "createdAt", "status"))
            .map(this::view));
  }

  private void record(UUID actor, Appointment a, String action) {
    audit.record(actor, "APPOINTMENT_" + action, "APPOINTMENT", a.id);
    events.appointment(a, "APPOINTMENT_" + action);
  }

  private View view(Appointment a) {
    var p = patients.findById(a.patientId).orElseThrow(ApiException::notFound);
    var d = doctors.findById(a.doctorId).orElseThrow(ApiException::notFound);
    return new View(
        a.id,
        a.patientId,
        a.doctorId,
        users.findById(p.userId).orElseThrow(ApiException::notFound).fullName,
        users.findById(d.userId).orElseThrow(ApiException::notFound).fullName,
        a.startTime,
        a.endTime,
        a.status,
        a.reason,
        a.version,
        a.createdAt);
  }
}
