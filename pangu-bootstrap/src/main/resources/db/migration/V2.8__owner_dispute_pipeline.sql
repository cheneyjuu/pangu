-- =============================================================================
-- V2.8: 业主异议升级管道（M3-1）
-- ADR-0004：单一 dispute 主表 + 业务附属（evidence / decision），不为每业务建私表。
-- 支持 5 级行政升级链路：业委会(1) / 街道办(2) / 区政府(3) / 市政府(4) / 行政诉讼(5)。
-- 与 CONTEXT.md §异议升级 (lines 729-839) 严格对齐。
-- =============================================================================

-- 主表：t_owner_dispute -----------------------------------------------------
CREATE TABLE t_owner_dispute (
    dispute_id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    raised_by_owner_id BIGINT NOT NULL,
    dispute_kind VARCHAR(40) NOT NULL,
    related_entity_type VARCHAR(40),
    related_entity_id BIGINT,
    current_review_level SMALLINT NOT NULL DEFAULT 1,
    status VARCHAR(40) NOT NULL DEFAULT 'RAISED',
    business_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    raised_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    escalated_at TIMESTAMP,
    closed_at TIMESTAMP,
    litigation_outcome VARCHAR(20),
    litigation_judgement_url VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_dispute_kind CHECK (dispute_kind IN (
        'EXPENSE_VOUCHER_DISPUTE',
        'PROPOSAL_QUALITY_DISPUTE',
        'OFFLINE_VOTE_DISPUTE',
        'ADMINISTRATIVE_REJECTION_DISPUTE'
    )),
    CONSTRAINT chk_dispute_level CHECK (current_review_level BETWEEN 1 AND 5),
    CONSTRAINT chk_dispute_status CHECK (status IN (
        'RAISED',
        'UNDER_REVIEW_LEVEL_1','DECIDED_LEVEL_1_UPHELD','DECIDED_LEVEL_1_REJECTED','DECIDED_LEVEL_1_PARTIAL',
        'UNDER_REVIEW_LEVEL_2','DECIDED_LEVEL_2_UPHELD','DECIDED_LEVEL_2_REJECTED','DECIDED_LEVEL_2_PARTIAL',
        'UNDER_REVIEW_LEVEL_3','DECIDED_LEVEL_3_UPHELD','DECIDED_LEVEL_3_REJECTED','DECIDED_LEVEL_3_PARTIAL',
        'UNDER_REVIEW_LEVEL_4','DECIDED_LEVEL_4_UPHELD','DECIDED_LEVEL_4_REJECTED','DECIDED_LEVEL_4_PARTIAL',
        'LITIGATION_FILED','CLOSED_FINAL','WITHDRAWN'
    )),
    CONSTRAINT chk_dispute_litigation_outcome CHECK (
        litigation_outcome IS NULL
        OR litigation_outcome IN ('PLAINTIFF_WON','DEFENDANT_WON','SETTLED','WITHDRAWN_LITIGATION')
    ),
    -- Level 1（业委会一审）仅 EXPENSE_VOUCHER_DISPUTE 适用；其他 dispute_kind 直接从 Level 2 起。
    CONSTRAINT chk_dispute_kind_level1 CHECK (
        dispute_kind = 'EXPENSE_VOUCHER_DISPUTE' OR current_review_level >= 2
    )
);

CREATE INDEX idx_dispute_owner ON t_owner_dispute(tenant_id, raised_by_owner_id);
CREATE INDEX idx_dispute_level_status ON t_owner_dispute(current_review_level, status);
CREATE INDEX idx_dispute_related ON t_owner_dispute(related_entity_type, related_entity_id)
    WHERE related_entity_type IS NOT NULL;

COMMENT ON TABLE  t_owner_dispute IS '业主异议主表（ADR-0004 单一主表 + 业务附属）';
COMMENT ON COLUMN t_owner_dispute.dispute_kind IS '异议类型 ENUM 4 选 1：见 chk_dispute_kind';
COMMENT ON COLUMN t_owner_dispute.current_review_level IS '当前审查层级 1-5：业委会/街道办/区政府/市政府/行政诉讼（trigger 10 校验单调递增不可跳级）';
COMMENT ON COLUMN t_owner_dispute.status IS '状态机：RAISED → UNDER_REVIEW_LEVEL_N → DECIDED_LEVEL_N_<UPHELD|REJECTED|PARTIAL> → ESCALATE 或 CLOSED_FINAL；LITIGATION_FILED 与 CLOSED_FINAL 并列终态';
COMMENT ON COLUMN t_owner_dispute.business_payload IS 'JSONB 软关联业务字段（如 voucher_id / disputed_amount），不加 FK，按 dispute_kind 在应用层校验 schema';
COMMENT ON COLUMN t_owner_dispute.litigation_outcome IS 'Level 5 终判结果（M3-4 判决回流时填充）';

-- 附属表：t_dispute_evidence -----------------------------------------------
CREATE TABLE t_dispute_evidence (
    evidence_id BIGSERIAL PRIMARY KEY,
    dispute_id BIGINT NOT NULL REFERENCES t_owner_dispute(dispute_id),
    evidence_kind VARCHAR(40) NOT NULL,
    content_url VARCHAR(500) NOT NULL,
    description VARCHAR(500),
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_evidence_kind CHECK (
        evidence_kind IN ('IMAGE','VIDEO','PDF','THIRD_PARTY_REPORT','OTHER')
    )
);
CREATE INDEX idx_evidence_dispute ON t_dispute_evidence(dispute_id);

COMMENT ON TABLE t_dispute_evidence IS '异议证据附属表（M3-2 阶段补 SM2 签名 + 司法链锚点）';

-- 附属表：t_dispute_review_decision ----------------------------------------
CREATE TABLE t_dispute_review_decision (
    decision_id BIGSERIAL PRIMARY KEY,
    dispute_id BIGINT NOT NULL REFERENCES t_owner_dispute(dispute_id),
    review_level SMALLINT NOT NULL,
    decided_by_user_id BIGINT NOT NULL,
    decision_kind VARCHAR(20) NOT NULL,
    decision_content VARCHAR(2000) NOT NULL,
    decision_doc_url VARCHAR(500),
    decided_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_decision_level CHECK (review_level BETWEEN 1 AND 4),
    CONSTRAINT chk_decision_kind CHECK (decision_kind IN ('UPHELD','REJECTED','PARTIAL_UPHELD')),
    CONSTRAINT uk_decision_dispute_level UNIQUE (dispute_id, review_level)
);
CREATE INDEX idx_decision_dispute ON t_dispute_review_decision(dispute_id);

COMMENT ON TABLE t_dispute_review_decision IS '行政机关层级决议（每 dispute 每 level 一条；Level 5 行政诉讼判决走主表 litigation_outcome）';

-- =============================================================================
-- Trigger 10：dispute 主表 status / current_review_level / closed_at 一致性
--   (a) status 含 LEVEL_N 时 N 必须 = current_review_level
--   (b) UPDATE 路径 current_review_level 单调递增、不可跳级、不可逆
--   (c) closed_at 当且仅当 status ∈ {CLOSED_FINAL, WITHDRAWN}
--       注：LITIGATION_FILED 不算 closed（等 M3-4 判决回流后转 CLOSED_FINAL）
-- =============================================================================
CREATE OR REPLACE FUNCTION fn_dispute_state_consistency() RETURNS TRIGGER AS $$
DECLARE
    v_level_in_status SMALLINT;
BEGIN
    IF NEW.status ~ 'LEVEL_\d' THEN
        v_level_in_status := CAST(substring(NEW.status FROM 'LEVEL_(\d)') AS SMALLINT);
        IF v_level_in_status <> NEW.current_review_level THEN
            RAISE EXCEPTION '[trigger 10] status 含 LEVEL_% 但 current_review_level=% 不一致',
                v_level_in_status, NEW.current_review_level;
        END IF;
    END IF;

    IF TG_OP = 'UPDATE' THEN
        IF NEW.current_review_level < OLD.current_review_level THEN
            RAISE EXCEPTION '[trigger 10] current_review_level 不可逆：% -> %',
                OLD.current_review_level, NEW.current_review_level;
        END IF;
        IF NEW.current_review_level > OLD.current_review_level + 1 THEN
            RAISE EXCEPTION '[trigger 10] current_review_level 不可跳级：% -> %',
                OLD.current_review_level, NEW.current_review_level;
        END IF;
    END IF;

    IF NEW.status IN ('CLOSED_FINAL','WITHDRAWN') AND NEW.closed_at IS NULL THEN
        RAISE EXCEPTION '[trigger 10] 终态 % 必须有 closed_at', NEW.status;
    END IF;
    IF NEW.status NOT IN ('CLOSED_FINAL','WITHDRAWN') AND NEW.closed_at IS NOT NULL THEN
        RAISE EXCEPTION '[trigger 10] 非终态 status=% 不应有 closed_at', NEW.status;
    END IF;

    NEW.update_time := CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_dispute_state_consistency
    BEFORE INSERT OR UPDATE ON t_owner_dispute
    FOR EACH ROW EXECUTE FUNCTION fn_dispute_state_consistency();

-- =============================================================================
-- Trigger 11：decision 与主表状态一致性
--   - decision.review_level 必须 ≤ 主表 current_review_level
--   - 主表 status 必须为 DECIDED_LEVEL_<level>_<KIND> 时方可插入对应 level 的 decision
--   - dispute_id 必须存在
-- =============================================================================
CREATE OR REPLACE FUNCTION fn_decision_main_consistency() RETURNS TRIGGER AS $$
DECLARE
    v_main_level SMALLINT;
    v_main_status VARCHAR(40);
BEGIN
    SELECT current_review_level, status INTO v_main_level, v_main_status
        FROM t_owner_dispute WHERE dispute_id = NEW.dispute_id;
    IF v_main_level IS NULL THEN
        RAISE EXCEPTION '[trigger 11] dispute_id=% 不存在', NEW.dispute_id;
    END IF;
    IF NEW.review_level > v_main_level THEN
        RAISE EXCEPTION '[trigger 11] decision.review_level=% 超过主表 current_review_level=%',
            NEW.review_level, v_main_level;
    END IF;
    IF v_main_status NOT LIKE 'DECIDED_LEVEL_' || NEW.review_level || '_%' THEN
        RAISE EXCEPTION '[trigger 11] 主表 status=% 不允许插入 LEVEL_% decision',
            v_main_status, NEW.review_level;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_decision_main_consistency
    BEFORE INSERT ON t_dispute_review_decision
    FOR EACH ROW EXECUTE FUNCTION fn_decision_main_consistency();

-- =============================================================================
-- 异议相关权限：仅 G 端
--   - C 端业主提起 / 撤回异议走 isAuthenticated() + 应用层 SecurityUtils.getUid() + tenant 校验
--     （C_USER 不进 sys_permission 链路，与 V2.7 disclosure:view:owner 同模式）
--   - dispute:decide 是法理红线 (redline=1)，trigger 6 要求挂载角色必须 fixed_data_scope NOT NULL
--   - 起始挂载：GOV_SUPER_ADMIN(1) / COMMUNITY_ADMIN(2) / PARTY_SECRETARY(3)
-- =============================================================================
INSERT INTO sys_permission (permission_key, description, permission_group, allowed_dept_categories, is_legal_redline) VALUES
    ('dispute:decide',   '行政机关出具异议层级决议（街道办/区政府/市政府）', 'DISPUTE', 'G', 1),
    ('dispute:audit',    '查看辖区所有异议（仲裁工作台 dashboard）',         'DISPUTE', 'G', 0);

INSERT INTO sys_role_permission (role_id, permission_key) VALUES
    (1, 'dispute:decide'), (2, 'dispute:decide'), (3, 'dispute:decide'),
    (1, 'dispute:audit'),  (2, 'dispute:audit'),  (3, 'dispute:audit');
