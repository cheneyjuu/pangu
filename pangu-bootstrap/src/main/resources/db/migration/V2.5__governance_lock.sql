-- ===================================================================
-- V2.5 — Governance Lock 通用双签锁定机制
-- 详见：M2路线图.md §2 / 实施计划：declarative-cuddling-micali.md
--
-- 设计目标：把 M1 PartyRatioWaiver 验证过的「业委会主任 + 街道办」双签
-- 范式提取为通用表，给后续 M2-3 / M2-4 / M2-5 等阶段直接挂入，无需再开 schema。
--
-- 边界：
--   - M1 schema 完全冻结：t_party_ratio_waiver 不动；
--   - 本期 entityType 固定 3 种：FINANCE_DISCLOSURE / ELECTION_DISCLOSURE /
--     FUND_LEDGER_PUBLISH，CHECK 约束兜底；
--   - PartyRatioWaiver 与 t_governance_lock 本期 disjoint，下个阶段再评估
--     是否合并双签来源。
-- ===================================================================

CREATE TABLE t_governance_lock (
    lock_id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    entity_type VARCHAR(32) NOT NULL,
    entity_id BIGINT NOT NULL,
    locked_by_user_id BIGINT NOT NULL,
    locked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lock_payload_hash VARCHAR(64) NOT NULL,

    status SMALLINT NOT NULL DEFAULT 1,

    unlock_committee_user_id BIGINT,
    unlock_committee_at TIMESTAMP,
    unlock_committee_signature VARCHAR(128),
    unlock_street_user_id BIGINT,
    unlock_street_at TIMESTAMP,
    unlock_street_signature VARCHAR(128),
    unlock_at TIMESTAMP,

    version BIGINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_lock_status CHECK (status IN (1, 2, 3)),
    CONSTRAINT chk_lock_entity_type CHECK (
        entity_type IN ('FINANCE_DISCLOSURE','ELECTION_DISCLOSURE','FUND_LEDGER_PUBLISH')
    ),
    CONSTRAINT chk_unlock_signers_diff CHECK (
        unlock_committee_user_id IS NULL
        OR unlock_street_user_id IS NULL
        OR unlock_committee_user_id <> unlock_street_user_id
    ),
    CONSTRAINT chk_lock_payload_hash_len CHECK (char_length(lock_payload_hash) = 64)
);

CREATE UNIQUE INDEX uidx_lock_entity ON t_governance_lock(tenant_id, entity_type, entity_id);
CREATE INDEX idx_lock_tenant_status ON t_governance_lock(tenant_id, status);

COMMENT ON TABLE t_governance_lock IS '通用双签锁定表（业委会主任初签 + 街道办终签解锁）';
COMMENT ON COLUMN t_governance_lock.entity_type IS '锁定实体类型：FINANCE_DISCLOSURE / ELECTION_DISCLOSURE / FUND_LEDGER_PUBLISH';
COMMENT ON COLUMN t_governance_lock.entity_id IS '业务实体主键（与 entity_type 共同唯一标识被锁实体）';
COMMENT ON COLUMN t_governance_lock.lock_payload_hash IS '锁定瞬间整体快照 SHA256（64 hex）';
COMMENT ON COLUMN t_governance_lock.status IS '状态：1-LOCKED, 2-COMMITTEE_SIGNED, 3-FULLY_UNLOCKED（不可逆）';
COMMENT ON COLUMN t_governance_lock.unlock_committee_user_id IS '业委会主任解锁初签人（hasAuthority(''lock:unlock:committee'')）';
COMMENT ON COLUMN t_governance_lock.unlock_street_user_id IS '街道办解锁终签人（hasAuthority(''lock:unlock:street'')），与 committee 不可同人';
COMMENT ON COLUMN t_governance_lock.unlock_at IS '双签齐备瞬间填充；trigger 8 强制原子性';
COMMENT ON COLUMN t_governance_lock.version IS '乐观锁版本号';

-- ===================================================================
-- Trigger 8：unlock 三字段一致性 + 状态机不可逆
-- 详见：实施计划 §1
--
-- 规则：
--   (a) committee_user_id / committee_at 必须同时填或同时空
--   (b) street_user_id / street_at 必须同时填或同时空
--   (c) unlock_at 当且仅当（committee 双字段齐备 AND street 双字段齐备）时填充
--   (d) status 与字段填充情况一致
--       - status=2 (COMMITTEE_SIGNED) 要求 committee 字段齐备 AND street 字段为空
--       - status=3 (FULLY_UNLOCKED) 要求 unlock_at NOT NULL
--   (e) UPDATE 路径上 status 不可逆（单调递增）
-- ===================================================================
CREATE OR REPLACE FUNCTION fn_governance_lock_unlock_atomicity() RETURNS TRIGGER AS $$
BEGIN
    -- (a) committee 字段成对填充
    IF (NEW.unlock_committee_user_id IS NULL) <> (NEW.unlock_committee_at IS NULL) THEN
        RAISE EXCEPTION
            '[trigger 8] unlock_committee_user_id / unlock_committee_at 必须同时填充';
    END IF;

    -- (b) street 字段成对填充
    IF (NEW.unlock_street_user_id IS NULL) <> (NEW.unlock_street_at IS NULL) THEN
        RAISE EXCEPTION
            '[trigger 8] unlock_street_user_id / unlock_street_at 必须同时填充';
    END IF;

    -- (c) unlock_at 当且仅当双签齐备
    IF NEW.unlock_at IS NOT NULL
       AND (NEW.unlock_committee_user_id IS NULL OR NEW.unlock_street_user_id IS NULL) THEN
        RAISE EXCEPTION
            '[trigger 8] unlock_at 仅当委员会 + 街道办双签齐备时方可填充';
    END IF;
    IF NEW.unlock_at IS NULL
       AND NEW.unlock_committee_user_id IS NOT NULL
       AND NEW.unlock_street_user_id IS NOT NULL THEN
        RAISE EXCEPTION
            '[trigger 8] 双签齐备时 unlock_at 必须同步填充';
    END IF;

    -- (d) status 与字段填充情况一致
    IF NEW.status = 3 AND NEW.unlock_at IS NULL THEN
        RAISE EXCEPTION
            '[trigger 8] FULLY_UNLOCKED 必须有 unlock_at';
    END IF;
    IF NEW.status = 2
       AND (NEW.unlock_committee_user_id IS NULL OR NEW.unlock_street_user_id IS NOT NULL) THEN
        RAISE EXCEPTION
            '[trigger 8] COMMITTEE_SIGNED 状态字段不一致：committee 需齐备且 street 必须为空';
    END IF;
    IF NEW.status = 1
       AND (NEW.unlock_committee_user_id IS NOT NULL OR NEW.unlock_street_user_id IS NOT NULL
            OR NEW.unlock_at IS NOT NULL) THEN
        RAISE EXCEPTION
            '[trigger 8] LOCKED 状态下不应存在任何 unlock_* 字段';
    END IF;

    -- (e) status 不可逆（仅 UPDATE 路径检查）
    IF TG_OP = 'UPDATE' AND NEW.status < OLD.status THEN
        RAISE EXCEPTION
            '[trigger 8] 状态不可逆：% -> %', OLD.status, NEW.status;
    END IF;

    NEW.update_time := CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_governance_lock_atomicity
    BEFORE INSERT OR UPDATE ON t_governance_lock
    FOR EACH ROW EXECUTE FUNCTION fn_governance_lock_unlock_atomicity();

-- ===================================================================
-- 新增 2 个 lock permission（追加到 sys_permission），并挂到对应预置角色
--   - lock:unlock:committee allowed='B' redline=1 → COMMITTEE_DIRECTOR(id=5, fixed=ALL_COMMUNITY)
--   - lock:unlock:street    allowed='G' redline=1 → GOV_SUPER_ADMIN(id=1) / COMMUNITY_ADMIN(id=2)
--
-- redline=1 要求 role.fixed_data_scope NOT NULL（trigger 6 强制）；
-- 上述 3 个目标角色在 V1__/V1.4__ 中均已 fixed_data_scope='ALL_COMMUNITY'，可挂入。
-- ===================================================================
INSERT INTO sys_permission (permission_key, description, permission_group, allowed_dept_categories, is_legal_redline) VALUES
    ('lock:unlock:committee', '业委会主任解锁初签（governance_lock）', 'LOCK', 'B', 1),
    ('lock:unlock:street',    '街道办解锁终签（governance_lock）',     'LOCK', 'G', 1);

INSERT INTO sys_role_permission (role_id, permission_key) VALUES
    (5, 'lock:unlock:committee'),  -- COMMITTEE_DIRECTOR
    (1, 'lock:unlock:street'),     -- GOV_SUPER_ADMIN
    (2, 'lock:unlock:street');     -- COMMUNITY_ADMIN
