-- Clean local HTTP SMS provider smoke fixture created by
-- scripts/prepare-http-sms-provider-smoke.sql.
--
-- Usage:
--   docker exec -i pangu-postgres psql -U postgres -d pangu_db < scripts/cleanup-http-sms-provider-smoke.sql

BEGIN;

DELETE FROM t_voting_reminder_delivery
WHERE delivery_id = 990481
   OR subject_id = 990481
   OR outbox_event_id = 990481;

DELETE FROM t_voting_mobilization_reminder
WHERE subject_id = 990481
   OR outbox_event_id = 990481;

DELETE FROM t_voting_mobilization_permission
WHERE subject_id = 990481;

DELETE FROM t_outbox_event
WHERE event_id = 990481
   OR (event_type = 4 AND business_ref_id = 990481);

DELETE FROM t_voting_subject
WHERE subject_id = 990481;

COMMIT;

SELECT 'removed' AS smoke_fixture, 990481 AS subject_id;
