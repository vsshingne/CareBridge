CREATE TABLE care_gap_rules (
 id uuid PRIMARY KEY, owner_provider_id uuid REFERENCES doctors(id), name varchar(160) NOT NULL,
 activity_type varchar(24) NOT NULL CHECK(activity_type IN ('FOLLOW_UP','INVESTIGATION','CONSULTATION','REFERRAL')),
 trigger_condition varchar(24) NOT NULL CHECK(trigger_condition IN ('OVERDUE','UNSCHEDULED')),
 interval_hours integer NOT NULL CHECK(interval_hours BETWEEN 0 AND 8760),
 severity varchar(16) NOT NULL CHECK(severity IN ('INFO','WARNING','HIGH')),
 notification_policy varchar(16) NOT NULL CHECK(notification_policy IN ('IN_APP','NONE')),
 responsible_role varchar(16) NOT NULL CHECK(responsible_role IN ('DOCTOR','ADMIN')),
 enabled boolean NOT NULL DEFAULT true, created_by uuid NOT NULL REFERENCES users(id),
 created_at timestamptz NOT NULL DEFAULT now(), version bigint NOT NULL DEFAULT 0,
 CHECK(trigger_condition<>'UNSCHEDULED' OR activity_type='REFERRAL')
);
CREATE TABLE care_gap_alerts (
 id uuid PRIMARY KEY, rule_id uuid NOT NULL REFERENCES care_gap_rules(id), rule_version bigint NOT NULL,
 plan_id uuid NOT NULL REFERENCES care_plans(id), entity_id uuid NOT NULL,
 activity_type varchar(24) NOT NULL, severity varchar(16) NOT NULL,
 resolved_at timestamptz, created_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(rule_id,rule_version,entity_id)
);
CREATE INDEX care_gap_alerts_plan_idx ON care_gap_alerts(plan_id,created_at);
CREATE INDEX care_gap_alerts_active_idx ON care_gap_alerts(entity_id) WHERE resolved_at IS NULL;
ALTER TABLE care_history ADD COLUMN snapshot jsonb;
