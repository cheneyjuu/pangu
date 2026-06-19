-- ===================================================================
-- V2.7 — 财务公示快照与 W/R/N 差分（M2-3）
-- 详见：M2路线图.md §4 / 实施计划：declarative-cuddling-micali.md
--
-- 设计目标：
--   1) 把"按期间公示 + 不可篡改 + 历史可追溯"作为一等概念落地为 t_finance_disclosure_snapshot；
--   2) 公示发布需挂 M2-1 的 t_governance_lock(entity_type='FINANCE_DISCLOSURE')，
--      由通用双签机制兜底"修订需双签解锁"；
--   3) 业主与审计端可拉单期 + 比对差分，写入 t_disclosure_audit_compare 留痕。
--
-- 边界：
--   - V2.6 预留给 M2-2 statistics_version 注册中心，本期跳过编号；Flyway 允许版本不连续；
--   - 本期仅 MAINTENANCE_FUND 真正可用；COMMON_FUND 仅作为枚举占位，
--     application 层调用走 COMMON_FUND 直接抛 DISCLOSURE_TYPE_NOT_SUPPORTED；
--   - V2.2 (t_maintenance_fund_account / t_fund_ledger_entry) 不动，只读聚合走 SQL JOIN。
-- ===================================================================

-- -------------------------------------------------------------------
-- 1. t_finance_disclosure_snapshot —— 财务公示快照
-- -------------------------------------------------------------------
CREATE TABLE t_finance_disclosure_snapshot (
    snapshot_id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    period VARCHAR(20) NOT NULL,                          -- '2026Q1' / '2026-06'
    disclosure_type VARCHAR(32) NOT NULL,                 -- COMMON_FUND / MAINTENANCE_FUND
    status SMALLINT NOT NULL DEFAULT 1,                   -- 1=DRAFT 2=LOCKED 3=PUBLISHED 4=REVISING
    data_payload JSONB NOT NULL,                          -- compose 产物：账户余额 + 流水汇总
    statistics_version INT NOT NULL DEFAULT 1,            -- 同 (tenant,type,period) 内自增
    payload_hash VARCHAR(64) NOT NULL,                    -- SHA-256(canonical(data_payload + meta))

    composed_by_user_id BIGINT NOT NULL,
    composed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    governance_lock_id BIGINT REFERENCES t_governance_lock(lock_id),
    locked_at TIMESTAMP,
    published_at TIMESTAMP,

    version BIGINT NOT NULL DEFAULT 0,                    -- 乐观锁
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_disc_status CHECK (status IN (1, 2, 3, 4)),
    CONSTRAINT chk_disc_type CHECK (disclosure_type IN ('COMMON_FUND','MAINTENANCE_FUND')),
    CONSTRAINT chk_disc_payload_hash_len CHECK (char_length(payload_hash) = 64),
    CONSTRAINT chk_disc_period_format CHECK (period ~ '^[0-9]{4}(-(0[1-9]|1[0-2])|Q[1-4])$'),
    CONSTRAINT uk_disc_period UNIQUE (tenant_id, disclosure_type, period, statistics_version)
);

-- 同 (tenant,type,period) 在 LOCKED/PUBLISHED 态唯一（DRAFT/REVISING 允许多版并存）
CREATE UNIQUE INDEX uidx_disc_latest
    ON t_finance_disclosure_snapshot (tenant_id, disclosure_type, period)
    WHERE status IN (2, 3);
CREATE INDEX idx_disc_status ON t_finance_disclosure_snapshot(tenant_id, status);

COMMENT ON TABLE  t_finance_disclosure_snapshot IS '财务公示快照（按 tenant+type+period+statistics_version 唯一）';
COMMENT ON COLUMN t_finance_disclosure_snapshot.period IS '期间标识：YYYY-MM 或 YYYYQ[1-4]';
COMMENT ON COLUMN t_finance_disclosure_snapshot.disclosure_type IS '公示类型：COMMON_FUND（占位未启用）/ MAINTENANCE_FUND';
COMMENT ON COLUMN t_finance_disclosure_snapshot.status IS '状态：1-DRAFT 2-LOCKED 3-PUBLISHED 4-REVISING（trigger 9 强制流转）';
COMMENT ON COLUMN t_finance_disclosure_snapshot.data_payload IS '公示数据（JSONB，compose 产物）';
COMMENT ON COLUMN t_finance_disclosure_snapshot.statistics_version IS '同 (tenant,type,period) 内的版本号，从 1 起自增';
COMMENT ON COLUMN t_finance_disclosure_snapshot.payload_hash IS 'SHA-256(canonical JSON + tenant+type+period) 64 hex';
COMMENT ON COLUMN t_finance_disclosure_snapshot.governance_lock_id IS '挂入的 t_governance_lock(entity_type=FINANCE_DISCLOSURE)';
COMMENT ON COLUMN t_finance_disclosure_snapshot.version IS '乐观锁版本号';

-- -------------------------------------------------------------------
-- 2. t_disclosure_audit_compare —— 快照差分审计
-- -------------------------------------------------------------------
CREATE TABLE t_disclosure_audit_compare (
    compare_id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    prev_snapshot_id BIGINT NOT NULL REFERENCES t_finance_disclosure_snapshot(snapshot_id),
    curr_snapshot_id BIGINT NOT NULL REFERENCES t_finance_disclosure_snapshot(snapshot_id),
    diff_json JSONB NOT NULL,                             -- {writes:[...], reads:[...], unchanged_count:N}
    write_count INT NOT NULL,
    read_count INT NOT NULL,
    no_change_count INT NOT NULL,

    audited_by_user_id BIGINT,
    audited_at TIMESTAMP,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_audit_pair_diff CHECK (prev_snapshot_id <> curr_snapshot_id),
    CONSTRAINT chk_audit_counts_nonneg CHECK (
        write_count >= 0 AND read_count >= 0 AND no_change_count >= 0
    )
);

CREATE INDEX idx_audit_curr ON t_disclosure_audit_compare(curr_snapshot_id);
CREATE INDEX idx_audit_tenant ON t_disclosure_audit_compare(tenant_id);

COMMENT ON TABLE  t_disclosure_audit_compare IS '快照差分审计（W/R/N 三路统计）';
COMMENT ON COLUMN t_disclosure_audit_compare.write_count IS 'W：当期写入 / 修改的字段数（path 在 prev/curr 都存在但值不同，或仅在 curr）';
COMMENT ON COLUMN t_disclosure_audit_compare.read_count  IS 'R：当期未写入但引用的历史字段数（path 仅在 prev 存在）';
COMMENT ON COLUMN t_disclosure_audit_compare.no_change_count IS 'N：两期完全一致的字段数';

-- ===================================================================
-- Trigger 9：disclosure 状态机 + governance_lock_id 一致性 + 状态不可逆
-- 详见：实施计划 §1
--
-- 规则：
--   (a) status=2(LOCKED)   要求 governance_lock_id IS NOT NULL；
--   (b) status=3(PUBLISHED) 要求 published_at IS NOT NULL；
--   (c) status=4(REVISING) 仅可由 PUBLISHED 进入，governance_lock_id 仍保留；
--   (d) UPDATE 路径 status 只允许如下流转：
--         1→1 / 1→2 / 2→2 / 2→3 / 3→3 / 3→4 / 4→4 / 4→1（新版本周期开始）
--       任何跨级（如 1→3）或逆向（如 3→2）均禁止；
--   (e) BEFORE INSERT 仅校验 (a)(b)；INSERT 通常以 status=1(DRAFT) 起步。
-- ===================================================================
CREATE OR REPLACE FUNCTION fn_disclosure_atomicity() RETURNS TRIGGER AS $$
BEGIN
    -- (a) LOCKED 状态必须挂治理锁
    IF NEW.status = 2 AND NEW.governance_lock_id IS NULL THEN
        RAISE EXCEPTION
            '[trigger 9] LOCKED 状态必须挂治理锁 governance_lock_id';
    END IF;

    -- (b) PUBLISHED 状态必须有 published_at
    IF NEW.status = 3 AND NEW.published_at IS NULL THEN
        RAISE EXCEPTION
            '[trigger 9] PUBLISHED 状态必须有 published_at';
    END IF;

    -- (d) UPDATE 路径状态机
    IF TG_OP = 'UPDATE' THEN
        IF NOT (
               (OLD.status = 1 AND NEW.status IN (1, 2))
            OR (OLD.status = 2 AND NEW.status IN (2, 3))
            OR (OLD.status = 3 AND NEW.status IN (3, 4))
            OR (OLD.status = 4 AND NEW.status IN (4, 1))
        ) THEN
            RAISE EXCEPTION
                '[trigger 9] 公示状态非法流转：% -> %', OLD.status, NEW.status;
        END IF;
    END IF;

    NEW.update_time := CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_disclosure_atomicity
    BEFORE INSERT OR UPDATE ON t_finance_disclosure_snapshot
    FOR EACH ROW EXECUTE FUNCTION fn_disclosure_atomicity();

-- ===================================================================
-- 新增 3 个 disclosure permission（追加到 sys_permission），并挂到对应预置角色
--
--   - disclosure:compose  (DISCLOSURE 组, GB 端, redline=0)
--       → COMMITTEE_DIRECTOR(5, B/fixed=ALL_COMMUNITY) + COMMUNITY_ADMIN(2, G/fixed=ALL_COMMUNITY)
--   - disclosure:publish  (DISCLOSURE 组, B  端, redline=1)
--       → COMMITTEE_DIRECTOR(5)：redline=1 要求 fixed_data_scope NOT NULL（trigger 6）
--   - disclosure:audit    (DISCLOSURE 组, G  端, redline=0)
--       → GOV_SUPER_ADMIN(1) + COMMUNITY_ADMIN(2)
--
-- 备注：V1.4 已存在 fund:disclosure:publish（FUND 组，更宽泛），保留不动；
-- M2-3 通路使用新的 disclosure:* 系，避免与既有挂载发生回归。
-- 业主端拉公示走 anyRequest().authenticated() 兜底（C_USER 不进 sys_permission 链路），
-- 由 application 层做 tenant 一致性 + status=PUBLISHED 校验。
-- ===================================================================
INSERT INTO sys_permission (permission_key, description, permission_group, allowed_dept_categories, is_legal_redline) VALUES
    ('disclosure:compose', '财务公示聚合 compose（业委会/居委会）', 'DISCLOSURE', 'GB', 0),
    ('disclosure:publish', '财务公示锁定并发布（业委会主任，redline）', 'DISCLOSURE', 'B',  1),
    ('disclosure:audit',   '财务公示差分审计（街道办/居委会）',     'DISCLOSURE', 'G',  0);

INSERT INTO sys_role_permission (role_id, permission_key) VALUES
    (5, 'disclosure:compose'),  -- COMMITTEE_DIRECTOR (B/ALL_COMMUNITY)
    (2, 'disclosure:compose'),  -- COMMUNITY_ADMIN    (G/ALL_COMMUNITY)
    (5, 'disclosure:publish'),  -- COMMITTEE_DIRECTOR (redline=1, fixed 已锁)
    (1, 'disclosure:audit'),    -- GOV_SUPER_ADMIN    (G/ALL_COMMUNITY)
    (2, 'disclosure:audit');    -- COMMUNITY_ADMIN    (G/ALL_COMMUNITY)
