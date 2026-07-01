-- Event-driven voting-period mobilization permissions.
-- Open voting activates building-scoped reminder/offline-proxy rights for assigned
-- OWNER_REPRESENTATIVE / GRID_OPERATOR users; closing/canceling the subject deactivates them.
CREATE TABLE IF NOT EXISTS t_voting_mobilization_permission (
    permission_id BIGSERIAL PRIMARY KEY,
    subject_id BIGINT NOT NULL REFERENCES t_voting_subject(subject_id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL,
    building_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL REFERENCES sys_user(user_id),
    role_key VARCHAR(64) NOT NULL,
    can_remind BOOLEAN NOT NULL DEFAULT TRUE,
    can_offline_proxy BOOLEAN NOT NULL DEFAULT TRUE,
    activated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ,
    deactivated_at TIMESTAMPTZ,
    status SMALLINT NOT NULL DEFAULT 1,
    CONSTRAINT uk_voting_mobilization_subject_user_building
        UNIQUE (subject_id, user_id, building_id),
    CONSTRAINT chk_voting_mobilization_status CHECK (status IN (1, 2))
);

CREATE INDEX IF NOT EXISTS idx_voting_mobilization_user_active
    ON t_voting_mobilization_permission(user_id, subject_id, status);

CREATE INDEX IF NOT EXISTS idx_voting_mobilization_subject_active
    ON t_voting_mobilization_permission(subject_id, status, building_id);

COMMENT ON TABLE t_voting_mobilization_permission IS
    '投票期事件驱动动员权限：开票激活楼栋长/网格员催票与线下代录能力，结束后失效';
COMMENT ON COLUMN t_voting_mobilization_permission.status IS '1-ACTIVE, 2-INACTIVE';
