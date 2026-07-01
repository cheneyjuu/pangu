-- Prepare a repeatable READY delivery for local HTTP SMS provider smoke tests.
--
-- Usage:
--   docker exec -i pangu-postgres psql -U postgres -d pangu_db < scripts/prepare-http-sms-provider-smoke.sql
--
-- Expected backend runtime override:
--   platform.voting.sms-provider-mode=http
--   platform.voting.sms-provider.endpoint=http://127.0.0.1:19090/sms
--   platform.voting.sms-provider.provider-message-id-fields=data.smsId

BEGIN;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM c_owner_property
        WHERE opid = 1
          AND uid = 70001
          AND tenant_id = 10001
          AND building_id = 30001
          AND account_status = 1
    ) THEN
        RAISE EXCEPTION 'Missing seed owner property for HTTP SMS smoke: opid=1 uid=70001 building=30001';
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
    990481,
    10001,
    'CODEx smoke HTTP短信联调议题',
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

INSERT INTO t_outbox_event (
    event_id,
    event_type,
    business_ref_id,
    tenant_id,
    payload_json,
    status,
    attempts,
    last_error,
    create_time,
    last_attempt_at,
    confirmed_at
) VALUES (
    990481,
    4,
    990481,
    10001,
    '{"eventType":"VOTING_REMINDER_REQUESTED","subjectId":990481,"tenantId":10001,"buildingId":30001}'::jsonb,
    3,
    0,
    NULL,
    CURRENT_TIMESTAMP,
    NULL,
    CURRENT_TIMESTAMP
)
ON CONFLICT (event_id) DO UPDATE SET
    event_type = EXCLUDED.event_type,
    business_ref_id = EXCLUDED.business_ref_id,
    tenant_id = EXCLUDED.tenant_id,
    payload_json = EXCLUDED.payload_json,
    status = EXCLUDED.status,
    attempts = 0,
    last_error = NULL,
    last_attempt_at = NULL,
    confirmed_at = CURRENT_TIMESTAMP;

INSERT INTO t_voting_reminder_delivery (
    delivery_id,
    outbox_event_id,
    subject_id,
    tenant_id,
    building_id,
    opid,
    uid,
    phone,
    channel,
    message_template,
    message,
    delivery_status,
    attempts,
    created_at,
    last_attempt_at,
    submitted_at,
    confirmed_at,
    failed_at,
    provider_message_id,
    last_error
) VALUES (
    990481,
    990481,
    990481,
    10001,
    30001,
    1,
    70001,
    '13800000012',
    'SMS',
    'VOTE_REMINDER',
    '请尽快参与业委会换届投票',
    1,
    0,
    CURRENT_TIMESTAMP,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
)
ON CONFLICT (delivery_id) DO UPDATE SET
    outbox_event_id = EXCLUDED.outbox_event_id,
    subject_id = EXCLUDED.subject_id,
    tenant_id = EXCLUDED.tenant_id,
    building_id = EXCLUDED.building_id,
    opid = EXCLUDED.opid,
    uid = EXCLUDED.uid,
    phone = EXCLUDED.phone,
    channel = EXCLUDED.channel,
    message_template = EXCLUDED.message_template,
    message = EXCLUDED.message,
    delivery_status = 1,
    attempts = 0,
    created_at = CURRENT_TIMESTAMP,
    last_attempt_at = NULL,
    submitted_at = NULL,
    confirmed_at = NULL,
    failed_at = NULL,
    provider_message_id = NULL,
    last_error = NULL;

SELECT setval(
    't_outbox_event_event_id_seq',
    GREATEST(COALESCE((SELECT MAX(event_id) FROM t_outbox_event), 1), 990481),
    TRUE
);

SELECT setval(
    't_voting_reminder_delivery_delivery_id_seq',
    GREATEST(COALESCE((SELECT MAX(delivery_id) FROM t_voting_reminder_delivery), 1), 990481),
    TRUE
);

SELECT setval(
    't_voting_subject_subject_id_seq',
    GREATEST(COALESCE((SELECT MAX(subject_id) FROM t_voting_subject), 1), 990481),
    TRUE
);

COMMIT;

SELECT
    'ready' AS smoke_fixture,
    delivery_id,
    subject_id,
    delivery_status,
    attempts,
    provider_message_id
FROM t_voting_reminder_delivery
WHERE delivery_id = 990481;
