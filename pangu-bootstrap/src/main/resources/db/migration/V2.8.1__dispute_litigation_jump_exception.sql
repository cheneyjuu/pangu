-- =============================================================================
-- V2.8.1：放行 gotoLitigation 这条合法跳级
--   原 trigger 10-b 强制 current_review_level 单调 +1 → 阻挡 RAISED(level=2 起步的
--   PROPOSAL_QUALITY_DISPUTE / OFFLINE_VOTE_DISPUTE / ADMINISTRATIVE_REJECTION_DISPUTE)
--   直达 LITIGATION_FILED(level=5) 的合法路径。
--   修正：当目标 status='LITIGATION_FILED' 且 level=5 时跳级豁免（M3-1 通过 service
--   层 Dispute.gotoLitigation() 守护非法路径，不依赖 trigger 防御）。
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
        -- gotoLitigation 合法跳级：直达 level=5/LITIGATION_FILED（应用层守护源状态合法）
        IF NEW.current_review_level > OLD.current_review_level + 1
           AND NOT (NEW.status = 'LITIGATION_FILED' AND NEW.current_review_level = 5) THEN
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
