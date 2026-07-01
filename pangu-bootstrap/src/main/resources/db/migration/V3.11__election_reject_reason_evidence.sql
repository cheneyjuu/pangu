-- E3：G 端拒绝必须挂 C1-C5 客观理由码 + JSONB 证据链。

ALTER TABLE t_party_ratio_waiver
    ADD COLUMN committee_reject_reason_code VARCHAR(2),
    ADD COLUMN committee_reject_evidence_json JSONB,
    ADD COLUMN street_reject_reason_code VARCHAR(2),
    ADD COLUMN street_reject_evidence_json JSONB;

ALTER TABLE t_party_ratio_waiver
    ADD CONSTRAINT chk_waiver_committee_reject_reason_code
        CHECK (committee_reject_reason_code IS NULL OR committee_reject_reason_code IN ('C1','C2','C3','C4','C5')),
    ADD CONSTRAINT chk_waiver_street_reject_reason_code
        CHECK (street_reject_reason_code IS NULL OR street_reject_reason_code IN ('C1','C2','C3','C4','C5')),
    ADD CONSTRAINT chk_waiver_committee_reject_evidence
        CHECK (
            committee_reject_reason_code IS NULL
            OR (
                committee_reject_evidence_json IS NOT NULL
                AND jsonb_typeof(committee_reject_evidence_json) = 'object'
                AND committee_reject_evidence_json <> '{}'::jsonb
            )
        ),
    ADD CONSTRAINT chk_waiver_street_reject_evidence
        CHECK (
            street_reject_reason_code IS NULL
            OR (
                street_reject_evidence_json IS NOT NULL
                AND jsonb_typeof(street_reject_evidence_json) = 'object'
                AND street_reject_evidence_json <> '{}'::jsonb
            )
        );

COMMENT ON COLUMN t_party_ratio_waiver.committee_reject_reason_code IS 'E3 居委会初审驳回客观理由码：C1-C5';
COMMENT ON COLUMN t_party_ratio_waiver.committee_reject_evidence_json IS 'E3 居委会初审驳回 JSONB 证据链';
COMMENT ON COLUMN t_party_ratio_waiver.street_reject_reason_code IS 'E3 街道办终审驳回客观理由码：C1-C5';
COMMENT ON COLUMN t_party_ratio_waiver.street_reject_evidence_json IS 'E3 街道办终审驳回 JSONB 证据链';

ALTER TABLE t_election_candidate
    ADD COLUMN reject_reason_code VARCHAR(2),
    ADD COLUMN reject_evidence_json JSONB,
    ADD COLUMN reject_reviewer_user_id BIGINT,
    ADD COLUMN reject_review_stage VARCHAR(32),
    ADD COLUMN rejected_at TIMESTAMP;

ALTER TABLE t_election_candidate
    ADD CONSTRAINT chk_candidate_reject_reason_code
        CHECK (reject_reason_code IS NULL OR reject_reason_code IN ('C1','C2','C3','C4','C5')),
    ADD CONSTRAINT chk_candidate_reject_evidence
        CHECK (
            reject_reason_code IS NULL
            OR (
                reject_evidence_json IS NOT NULL
                AND jsonb_typeof(reject_evidence_json) = 'object'
                AND reject_evidence_json <> '{}'::jsonb
            )
        ),
    ADD CONSTRAINT chk_candidate_reject_stage
        CHECK (reject_review_stage IS NULL OR reject_review_stage IN ('PARTY_REVIEW','COMMITTEE_REVIEW'));

COMMENT ON COLUMN t_election_candidate.reject_reason_code IS 'E3 候选人审查驳回客观理由码：C1-C5';
COMMENT ON COLUMN t_election_candidate.reject_evidence_json IS 'E3 候选人审查驳回 JSONB 证据链';
COMMENT ON COLUMN t_election_candidate.reject_reviewer_user_id IS 'E3 候选人审查驳回人 sys_user.user_id';
COMMENT ON COLUMN t_election_candidate.reject_review_stage IS 'E3 候选人审查驳回阶段：PARTY_REVIEW / COMMITTEE_REVIEW';
COMMENT ON COLUMN t_election_candidate.rejected_at IS 'E3 候选人审查驳回时间';
