CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE TABLE users (
 id uuid PRIMARY KEY, full_name varchar(120) NOT NULL, email varchar(254) NOT NULL UNIQUE,
 password_hash varchar(100) NOT NULL, phone_number varchar(30),
 role varchar(16) NOT NULL CHECK (role IN ('PATIENT','DOCTOR','ADMIN')), enabled boolean NOT NULL DEFAULT true,
 created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL
);
CREATE TABLE patients (
 id uuid PRIMARY KEY, user_id uuid NOT NULL UNIQUE REFERENCES users(id), date_of_birth date, gender varchar(40),
 address varchar(500), emergency_contact varchar(120), created_at timestamptz NOT NULL
);
CREATE TABLE doctors (
 id uuid PRIMARY KEY, user_id uuid NOT NULL UNIQUE REFERENCES users(id), specialization varchar(120) NOT NULL,
 qualification varchar(200) NOT NULL, experience_years integer NOT NULL CHECK(experience_years BETWEEN 0 AND 80),
 consultation_fee numeric(12,2) NOT NULL CHECK(consultation_fee >= 0), clinic_address varchar(500) NOT NULL,
 biography varchar(2000), timezone varchar(80) NOT NULL, verified boolean NOT NULL DEFAULT false, created_at timestamptz NOT NULL
);
CREATE INDEX doctors_specialization_idx ON doctors(specialization);
CREATE TABLE availability (
 id uuid PRIMARY KEY, doctor_id uuid NOT NULL REFERENCES doctors(id), day_of_week integer NOT NULL CHECK(day_of_week BETWEEN 1 AND 7),
 start_time time NOT NULL, end_time time NOT NULL, slot_duration_minutes integer NOT NULL CHECK(slot_duration_minutes BETWEEN 5 AND 240),
 active boolean NOT NULL DEFAULT true, CHECK(end_time > start_time)
);
CREATE INDEX availability_doctor_idx ON availability(doctor_id);
CREATE TABLE appointments (
 id uuid PRIMARY KEY, patient_id uuid NOT NULL REFERENCES patients(id), doctor_id uuid NOT NULL REFERENCES doctors(id),
 start_time timestamptz NOT NULL, end_time timestamptz NOT NULL,
 status varchar(16) NOT NULL CHECK(status IN ('PENDING','CONFIRMED','CANCELLED','COMPLETED','REJECTED')),
 reason varchar(500), created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL, version bigint NOT NULL DEFAULT 0,
 CHECK(end_time > start_time),
 EXCLUDE USING gist (doctor_id WITH =, tstzrange(start_time,end_time,'[)') WITH &&) WHERE (status IN ('PENDING','CONFIRMED'))
);
CREATE INDEX appointments_patient_start_idx ON appointments(patient_id,start_time);
CREATE INDEX appointments_doctor_start_idx ON appointments(doctor_id,start_time);
CREATE TABLE refresh_tokens (
 id uuid PRIMARY KEY, user_id uuid NOT NULL REFERENCES users(id), token_hash varchar(64) NOT NULL UNIQUE,
 family_id uuid NOT NULL, expires_at timestamptz NOT NULL, revoked boolean NOT NULL DEFAULT false
);
CREATE INDEX refresh_family_idx ON refresh_tokens(family_id);
CREATE TABLE outbox_events (
 id uuid PRIMARY KEY, aggregate_type varchar(40) NOT NULL, aggregate_id uuid NOT NULL,
 event_type varchar(60) NOT NULL, topic varchar(80) NOT NULL, payload text NOT NULL,
 created_at timestamptz NOT NULL, published_at timestamptz, status varchar(16) NOT NULL,
 retry_count integer NOT NULL DEFAULT 0, next_attempt_at timestamptz NOT NULL
);
CREATE INDEX outbox_due_idx ON outbox_events(next_attempt_at) WHERE status = 'PENDING';
CREATE TABLE processed_events (
 id uuid PRIMARY KEY, event_id uuid NOT NULL, consumer_name varchar(80) NOT NULL, processed_at timestamptz NOT NULL,
 UNIQUE(event_id,consumer_name)
);
CREATE TABLE notifications (
 id uuid PRIMARY KEY, user_id uuid NOT NULL REFERENCES users(id), event_id uuid NOT NULL, appointment_id uuid NOT NULL,
 event_type varchar(60) NOT NULL, created_at timestamptz NOT NULL, UNIQUE(user_id,event_id)
);
CREATE INDEX notifications_user_idx ON notifications(user_id,created_at);
CREATE TABLE audit_logs (
 id uuid PRIMARY KEY, actor_id uuid REFERENCES users(id), action varchar(80) NOT NULL,
 entity_type varchar(40) NOT NULL, entity_id uuid, timestamp timestamptz NOT NULL, metadata varchar(1000) NOT NULL
);
CREATE INDEX audit_time_idx ON audit_logs(timestamp);
