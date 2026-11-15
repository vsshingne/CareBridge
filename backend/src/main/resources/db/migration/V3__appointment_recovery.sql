CREATE TABLE waiting_entries (
 id uuid PRIMARY KEY, patient_id uuid NOT NULL REFERENCES patients(id), doctor_id uuid REFERENCES doctors(id),
 specialty varchar(120), available_from timestamptz NOT NULL, available_to timestamptz NOT NULL,
 priority integer NOT NULL DEFAULT 0 CHECK(priority BETWEEN 0 AND 10),
 notify_in_app boolean NOT NULL DEFAULT true, active boolean NOT NULL DEFAULT true,
 created_at timestamptz NOT NULL DEFAULT now(), CHECK(available_to>available_from),
 CHECK((doctor_id IS NOT NULL) <> (specialty IS NOT NULL))
);
CREATE INDEX waiting_active_idx ON waiting_entries(doctor_id,priority DESC,created_at) WHERE active;
CREATE TABLE appointment_offers (
 id uuid PRIMARY KEY, cancelled_appointment_id uuid NOT NULL REFERENCES appointments(id),
 entry_id uuid NOT NULL REFERENCES waiting_entries(id), patient_id uuid NOT NULL REFERENCES patients(id),
 doctor_id uuid NOT NULL REFERENCES doctors(id), start_time timestamptz NOT NULL, end_time timestamptz NOT NULL,
 status varchar(16) NOT NULL CHECK(status IN ('OPEN','ACCEPTED','EXPIRED','INVALIDATED')),
 expires_at timestamptz NOT NULL, accepted_appointment_id uuid REFERENCES appointments(id),
 idempotency_key uuid, created_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(cancelled_appointment_id,entry_id), UNIQUE(patient_id,idempotency_key)
);
CREATE INDEX offers_patient_idx ON appointment_offers(patient_id,created_at);
CREATE INDEX offers_expiry_idx ON appointment_offers(expires_at) WHERE status='OPEN';
CREATE TABLE care_notifications (
 id uuid PRIMARY KEY, user_id uuid NOT NULL REFERENCES users(id), entity_id uuid NOT NULL,
 event_id uuid NOT NULL, event_type varchar(60) NOT NULL, created_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(user_id,event_id)
);
CREATE INDEX care_notifications_user_idx ON care_notifications(user_id,created_at);
