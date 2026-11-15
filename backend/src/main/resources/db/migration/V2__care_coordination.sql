CREATE TABLE care_plans (
 id uuid PRIMARY KEY, patient_id uuid NOT NULL REFERENCES patients(id),
 provider_id uuid NOT NULL REFERENCES doctors(id), source_appointment_id uuid NOT NULL REFERENCES appointments(id),
 title varchar(200) NOT NULL, goals varchar(4000) NOT NULL,
 status varchar(20) NOT NULL CHECK(status IN ('ACTIVE','COMPLETED','CANCELLED')),
 version bigint NOT NULL DEFAULT 0, created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX care_plans_patient_idx ON care_plans(patient_id,created_at);
CREATE INDEX care_plans_provider_idx ON care_plans(provider_id,created_at);
CREATE TABLE care_activities (
 id uuid PRIMARY KEY, plan_id uuid NOT NULL REFERENCES care_plans(id),
 type varchar(20) NOT NULL CHECK(type IN ('INVESTIGATION','FOLLOW_UP','CONSULTATION')),
 title varchar(200) NOT NULL, due_at timestamptz NOT NULL,
 status varchar(20) NOT NULL CHECK(status IN ('NOT_STARTED','SCHEDULED','IN_PROGRESS','COMPLETED','CANCELLED')),
 outcome varchar(4000), version bigint NOT NULL DEFAULT 0,
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX care_activities_plan_idx ON care_activities(plan_id,created_at);
CREATE INDEX care_activities_due_idx ON care_activities(due_at) WHERE status NOT IN ('COMPLETED','CANCELLED');
CREATE TABLE referrals (
 id uuid PRIMARY KEY, plan_id uuid NOT NULL REFERENCES care_plans(id),
 receiving_provider_id uuid NOT NULL REFERENCES doctors(id), clinical_reason varchar(2000) NOT NULL,
 priority varchar(16) NOT NULL CHECK(priority IN ('ROUTINE','URGENT')),
 due_at timestamptz NOT NULL, appointment_id uuid REFERENCES appointments(id),
 status varchar(24) NOT NULL CHECK(status IN ('CREATED','PENDING_ACCEPTANCE','ACCEPTED','SCHEDULED','IN_PROGRESS','COMPLETED','CANCELLED')),
 outcome varchar(4000), version bigint NOT NULL DEFAULT 0,
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX referrals_plan_idx ON referrals(plan_id,created_at);
CREATE INDEX referrals_receiver_idx ON referrals(receiving_provider_id,status,due_at);
CREATE UNIQUE INDEX referrals_appointment_idx ON referrals(appointment_id) WHERE appointment_id IS NOT NULL AND status <> 'CANCELLED';
CREATE TABLE care_history (
 id uuid PRIMARY KEY, plan_id uuid NOT NULL REFERENCES care_plans(id), referral_id uuid REFERENCES referrals(id),
 entity_id uuid NOT NULL, activity_type varchar(24) NOT NULL, action varchar(60) NOT NULL,
 status varchar(24) NOT NULL, actor_id uuid NOT NULL REFERENCES users(id),
 occurred_at timestamptz NOT NULL DEFAULT now(), entity_version bigint NOT NULL
);
CREATE INDEX care_history_plan_idx ON care_history(plan_id,occurred_at,id);
CREATE INDEX care_history_referral_idx ON care_history(referral_id,occurred_at,id);
