-- 关联业务：把供相关业主决定/授权审查的提案冻结，与授权后可定商、签约和付款的实施方案锁定分开。

ALTER TABLE t_repair_project
    DROP CONSTRAINT chk_repair_project_status,
    ADD CONSTRAINT chk_repair_project_status CHECK (
        status IN (
            'DRAFT', 'AUTHORIZATION_IN_PROGRESS', 'PLAN_LOCKED', 'GOVERNANCE_IN_PROGRESS', 'AUTHORIZED',
            'CONTRACT_EFFECTIVE', 'IN_PROGRESS', 'PENDING_ACCEPTANCE',
            'COMPLETED', 'WARRANTY', 'ARCHIVED', 'CANCELLED'
        )
    );

ALTER TABLE t_repair_plan_version
    ADD COLUMN authorization_snapshot_hash CHAR(64),
    ADD COLUMN authorization_frozen_by_user_id BIGINT REFERENCES sys_user(user_id),
    ADD COLUMN authorization_frozen_at TIMESTAMP,
    DROP CONSTRAINT chk_repair_plan_status,
    ADD CONSTRAINT chk_repair_plan_status CHECK (
        status IN ('DRAFT', 'AUTHORIZATION_FROZEN', 'LOCKED', 'SUPERSEDED')
    ),
    DROP CONSTRAINT chk_repair_plan_lock_shape,
    ADD CONSTRAINT chk_repair_plan_lock_shape CHECK (
        (status = 'DRAFT'
            AND authorization_snapshot_hash IS NULL
            AND authorization_frozen_by_user_id IS NULL
            AND authorization_frozen_at IS NULL
            AND snapshot_hash IS NULL
            AND locked_by_user_id IS NULL
            AND locked_at IS NULL)
        OR (status = 'AUTHORIZATION_FROZEN'
            AND authorization_snapshot_hash IS NOT NULL
            AND authorization_frozen_by_user_id IS NOT NULL
            AND authorization_frozen_at IS NOT NULL
            AND snapshot_hash IS NULL
            AND locked_by_user_id IS NULL
            AND locked_at IS NULL)
        OR (status = 'LOCKED'
            AND snapshot_hash IS NOT NULL
            AND locked_by_user_id IS NOT NULL
            AND locked_at IS NOT NULL)
        OR (status = 'SUPERSEDED'
            AND (
                (snapshot_hash IS NOT NULL AND locked_by_user_id IS NOT NULL AND locked_at IS NOT NULL)
                OR (authorization_snapshot_hash IS NOT NULL
                    AND authorization_frozen_by_user_id IS NOT NULL
                    AND authorization_frozen_at IS NOT NULL
                    AND snapshot_hash IS NULL
                    AND locked_by_user_id IS NULL
                    AND locked_at IS NULL)
            ))
    ),
    ADD CONSTRAINT chk_repair_plan_authorization_snapshot_hash CHECK (
        authorization_snapshot_hash IS NULL OR authorization_snapshot_hash ~ '^[0-9a-f]{64}$'
    );

COMMENT ON COLUMN t_repair_plan_version.authorization_snapshot_hash IS
    '供相关业主决定或授权审查的提案快照哈希；它不是可施工、可定商或可付款的实施方案锁定哈希。';
COMMENT ON COLUMN t_repair_plan_version.authorization_frozen_at IS
    '提案进入决定/授权程序的冻结时间；之后须形成有效授权，才能写入最终实施方案锁定。';
COMMENT ON TABLE t_repair_plan_allocation_room IS
    '授权提案或实施方案冻结的费用承担房屋和面积分母快照；已有提案冻结不得因后续名册变化改写。';
