# FHIR integration

Selected format: HL7 FHIR R4 **4.0.1**, JSON only. HAPI FHIR **8.4.0** structures, instance validator and bundled R4 validation resources are pinned together. HAPI is used as a mapping/validation library; the application remains a modular monolith, not a HAPI JPA server. Dependency resolution and executable validation tests are required before treating this phase as verified.

`GET /api/v1/fhir/r4/care-plans/{planId}/bundle` returns `application/fhir+json`. Bearer authentication and the clinical care-plan ownership policy apply before any export. Patients can export their own plan; the responsible provider and administrators can export it. A receiving referral provider cannot export the referring provider's entire plan. Access is audited.

| Internal record | R4 resource | Mapping |
| --- | --- | --- |
| Patient profile | Patient | Stable ID/identifier, recorded name and optional date of birth; no inferred sex/gender |
| Assigned providers | Practitioner | Stable ID and recorded name |
| Care plan | CarePlan | ACTIVE/COMPLETED/CANCELLED to active/completed/revoked; plan intent, subject, author, title, goal text as description |
| Care activity | Task | Explicit requested/ready/in-progress/completed/cancelled lifecycle, subject, owner, plan reference and deadline |
| Referral | ServiceRequest | Draft before acceptance, active during execution, completed only after verified completion, revoked on cancellation; requester, performer, reason, priority and deadline |
| Linked appointment | Appointment | Pending/booked/fulfilled/cancelled with actual patient/provider references and scheduled interval |
| Provider-completed appointment | Encounter | Administrative finished ambulatory visit derived from recorded appointment completion; scheduled period, no invented clinical findings or diagnoses |
| Completed investigation outcome | Observation | Recorded outcome as valueString, activity title as code text, final status, author and recorded completion time; no invented laboratory code, units or numeric result |

The collection bundle includes all referenced patients, providers, plans, tasks, referrals and linked appointments. Derived Encounter and Observation IDs are stable name-based UUIDs. Identifier/fullUrl base defaults to `https://carebridge.example/fhir/r4`; set `APP_FHIR_BASE_URL` through Spring relaxed environment binding (or `app.fhir-base-url`) to the deployment's identifier namespace. This namespace does not imply a general resource-read API exists.

Exports run through HAPI's strict parser and R4 instance validator before serialization. Failure is a sanitized server error, not an invalid export. Tests must cover all eight resource types, invalid required fields and cross-patient denial. The bundle boundary is capped at 500 activities and 500 referrals per plan; larger exports return a client error rather than silently truncate.

Unsupported: incoming FHIR writes/import, `$validate` for arbitrary remote content, search, SMART authorization, subscriptions, regional implementation guides and a full FHIR CapabilityStatement. No remote terminology or patient-data transfer is configured. Free-text clinical descriptions are preserved rather than converted into unverified coded clinical assertions. This is a supported export subset, **not a claim of complete FHIR conformance**.

Implementation references: [HL7 R4 ServiceRequest](https://hl7.org/fhir/R4/servicerequest.html), [HL7 R4 CarePlan](https://hl7.org/fhir/R4/careplan.html), [HAPI instance validation](https://hapifhir.io/hapi-fhir/docs/validation/instance_validator.html).
