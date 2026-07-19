-- 关联业务：允许维修授权提案按已冻结的费用承担房屋集合建立精确表决范围。

ALTER TABLE t_voting_subject DROP CONSTRAINT chk_subject_scope;
ALTER TABLE t_voting_subject
    ADD CONSTRAINT chk_subject_scope CHECK (scope IN (1, 2, 4));

ALTER TABLE t_voting_execution_package DROP CONSTRAINT chk_voting_execution_scope;
ALTER TABLE t_voting_execution_package DROP CONSTRAINT chk_voting_execution_scope_reference;
ALTER TABLE t_voting_execution_package
    ADD CONSTRAINT chk_voting_execution_scope CHECK (scope IN (1, 2, 4));
ALTER TABLE t_voting_execution_package
    ADD CONSTRAINT chk_voting_execution_scope_reference CHECK (
        (scope = 1 AND scope_reference_id IS NULL)
        OR (scope IN (2, 4) AND scope_reference_id IS NOT NULL)
    );

ALTER TABLE t_voting_electorate_snapshot DROP CONSTRAINT chk_voting_electorate_scope;
ALTER TABLE t_voting_electorate_snapshot
    ADD CONSTRAINT chk_voting_electorate_scope CHECK (scope IN (1, 2, 4));

COMMENT ON COLUMN t_voting_subject.scope IS
    '分母范围：1-小区，2-楼栋，4-维修方案冻结费用承担房屋；3-单元暂未实现';
COMMENT ON COLUMN t_voting_execution_package.scope IS
    '本次决定范围：1-小区，2-楼栋，4-维修方案冻结费用承担房屋';
COMMENT ON COLUMN t_voting_execution_package.scope_reference_id IS
    'scope=2 时为 building_id；scope=4 时为 repair plan_id';
