-- 关联业务：在正式表决包中冻结跨渠道重复票规则，并保存有效票替代关系。

ALTER TABLE t_voting_execution_package
    ADD COLUMN duplicate_ballot_policy VARCHAR(32);

UPDATE t_voting_execution_package
SET duplicate_ballot_policy = CASE
    WHEN collection_mode = 'PAPER_AND_ONLINE' THEN 'FIRST_VALID_WINS'
    ELSE 'NOT_APPLICABLE'
END;

ALTER TABLE t_voting_execution_package
    ALTER COLUMN duplicate_ballot_policy SET NOT NULL,
    ADD CONSTRAINT chk_voting_execution_duplicate_policy CHECK (
        (collection_mode = 'PAPER_AND_ONLINE'
            AND duplicate_ballot_policy IN ('FIRST_VALID_WINS', 'ONLINE_PREVAILS', 'PAPER_PREVAILS'))
        OR
        (collection_mode <> 'PAPER_AND_ONLINE' AND duplicate_ballot_policy = 'NOT_APPLICABLE')
    );

ALTER TABLE t_voting_ballot_record
    ADD COLUMN supersedes_ballot_id BIGINT REFERENCES t_voting_ballot_record(ballot_id),
    ADD COLUMN resolution_policy VARCHAR(32),
    ADD COLUMN resolution_reason VARCHAR(500),
    ADD CONSTRAINT chk_voting_ballot_resolution CHECK (
        (supersedes_ballot_id IS NULL AND resolution_policy IS NULL AND resolution_reason IS NULL)
        OR
        (supersedes_ballot_id IS NOT NULL
            AND resolution_policy IN ('ONLINE_PREVAILS', 'PAPER_PREVAILS')
            AND resolution_reason IS NOT NULL)
    );

CREATE INDEX idx_voting_ballot_supersedes
    ON t_voting_ballot_record(supersedes_ballot_id)
    WHERE supersedes_ballot_id IS NOT NULL;

COMMENT ON COLUMN t_voting_execution_package.duplicate_ballot_policy IS
    '本次冻结的跨渠道重复票处理方式；只在纸质与线上并行时适用';
COMMENT ON COLUMN t_voting_ballot_record.supersedes_ballot_id IS
    '本票按冻结规则替代的上一张有效票；原票继续保留为无效审计记录';
COMMENT ON COLUMN t_voting_ballot_record.resolution_policy IS
    '触发本次替代的冻结重复票规则';
COMMENT ON COLUMN t_voting_ballot_record.resolution_reason IS
    '对业务人员和审计人员可解释的替代原因';
