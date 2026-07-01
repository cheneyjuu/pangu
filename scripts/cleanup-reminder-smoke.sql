-- Clean local reminder smoke fixture created by scripts/prepare-reminder-smoke.sql.
--
-- Usage:
--   docker exec -i pangu-postgres psql -U postgres -d pangu_db < scripts/cleanup-reminder-smoke.sql

BEGIN;

DELETE FROM t_vote_item
WHERE subject_id = 990480;

DELETE FROM t_voting_mobilization_owner_notice
WHERE subject_id = 990480;

DELETE FROM t_voting_reminder_delivery
WHERE subject_id = 990480;

DELETE FROM t_voting_mobilization_reminder
WHERE subject_id = 990480;

DELETE FROM t_voting_mobilization_permission
WHERE subject_id = 990480;

DELETE FROM t_voting_subject
WHERE subject_id = 990480;

COMMIT;

SELECT 'removed' AS smoke_fixture, 990480 AS subject_id;
