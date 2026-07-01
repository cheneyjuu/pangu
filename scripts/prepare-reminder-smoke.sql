-- Prepare a repeatable local fixture for shennong-app reminder smoke tests.
--
-- Usage:
--   docker exec -i pangu-postgres psql -U postgres -d pangu_db < scripts/prepare-reminder-smoke.sql
--
-- Fixture:
--   subject_id = 990480
--   worker     = 13800000004 / sys_user 800004 / GRID_OPERATOR
--   owner      = uid 70001 / building 30001 / room 30001101

BEGIN;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM c_owner_property
        WHERE tenant_id = 10001
          AND building_id = 30001
          AND uid = 70001
          AND account_status = 1
    ) THEN
        RAISE EXCEPTION 'Missing seed owner property for reminder smoke: tenant=10001 building=30001 uid=70001';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM sys_user
        WHERE user_id = 800004
          AND account_id = 999804
          AND status = '0'
    ) THEN
        RAISE EXCEPTION 'Missing seed worker for reminder smoke: sys_user=800004 account=999804';
    END IF;
END $$;

INSERT INTO t_voting_subject (
    subject_id,
    tenant_id,
    title,
    subject_type,
    scope,
    scope_reference_id,
    status,
    publish_at,
    vote_start_at,
    vote_end_at,
    party_ratio_floor,
    max_winners,
    version,
    create_time,
    proposed_by_user_id,
    review_history,
    clock_suspended_at,
    clock_suspended_by_subject_id
) VALUES (
    990480,
    10001,
    'CODEx smoke 催票联调议题',
    1,
    2,
    30001,
    3,
    CURRENT_TIMESTAMP - INTERVAL '1 day',
    CURRENT_TIMESTAMP - INTERVAL '1 hour',
    CURRENT_TIMESTAMP + INTERVAL '14 days',
    0.50,
    1,
    0,
    CURRENT_TIMESTAMP,
    800004,
    '[]'::jsonb,
    NULL,
    NULL
)
ON CONFLICT (subject_id) DO UPDATE SET
    tenant_id = EXCLUDED.tenant_id,
    title = EXCLUDED.title,
    subject_type = EXCLUDED.subject_type,
    scope = EXCLUDED.scope,
    scope_reference_id = EXCLUDED.scope_reference_id,
    status = EXCLUDED.status,
    publish_at = EXCLUDED.publish_at,
    vote_start_at = EXCLUDED.vote_start_at,
    vote_end_at = EXCLUDED.vote_end_at,
    party_ratio_floor = EXCLUDED.party_ratio_floor,
    max_winners = EXCLUDED.max_winners,
    proposed_by_user_id = EXCLUDED.proposed_by_user_id,
    review_history = EXCLUDED.review_history,
    settled_at = NULL,
    cancelled_at = NULL,
    cancelled_by_user_id = NULL,
    cancel_reason = NULL,
    clock_suspended_at = NULL,
    clock_suspended_by_subject_id = NULL;

INSERT INTO t_voting_mobilization_permission (
    subject_id,
    tenant_id,
    building_id,
    user_id,
    role_key,
    can_remind,
    can_offline_proxy,
    activated_at,
    expires_at,
    deactivated_at,
    status
) VALUES (
    990480,
    10001,
    30001,
    800004,
    'GRID_OPERATOR',
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP + INTERVAL '14 days',
    NULL,
    1
)
ON CONFLICT (subject_id, user_id, building_id) DO UPDATE SET
    can_remind = TRUE,
    can_offline_proxy = TRUE,
    activated_at = CURRENT_TIMESTAMP,
    expires_at = CURRENT_TIMESTAMP + INTERVAL '14 days',
    deactivated_at = NULL,
    status = 1;

-- Keep the fixture pending and idempotent across repeated smoke runs.
DELETE FROM t_voting_mobilization_owner_notice
WHERE subject_id = 990480;

DELETE FROM t_vote_item
WHERE subject_id = 990480;

SELECT setval(
    't_voting_subject_subject_id_seq',
    GREATEST(
        COALESCE((SELECT MAX(subject_id) FROM t_voting_subject), 1),
        990480
    ),
    TRUE
);

COMMIT;

SELECT
    'ready' AS smoke_fixture,
    s.subject_id,
    s.title,
    s.status,
    p.user_id,
    p.building_id,
    op.uid,
    op.opid
FROM t_voting_subject s
JOIN t_voting_mobilization_permission p
  ON p.subject_id = s.subject_id
JOIN c_owner_property op
  ON op.tenant_id = p.tenant_id
 AND op.building_id = p.building_id
 AND op.account_status = 1
WHERE s.subject_id = 990480
ORDER BY op.opid;
