-- Kafka-driven reconciliation has no authenticated HTTP actor.
ALTER TABLE care_history ALTER COLUMN actor_id DROP NOT NULL;
