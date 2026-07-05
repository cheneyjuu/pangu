-- V3.40: 社区设置 / 法权基数配置后端底座。
--
-- 该模块承载管理台“社区设置”四个页签：
-- 1. 行政区划与组织备案
-- 2. 物业区域与建筑名册
-- 3. 法定计票基数底座
-- 4. 自治规则与财务公示
--
-- 投票进行时仍以 t_voting_denominator_snapshot 的议题快照为准；本表保存的是
-- 当前租户最新的行政备案、物理资产台账、治理规则和可审计统计版本。

CREATE TABLE IF NOT EXISTS t_governance_policy (
    policy_id BIGSERIAL PRIMARY KEY,
    policy_code VARCHAR(64) NOT NULL UNIQUE,
    policy_name VARCHAR(128) NOT NULL,
    policy_version VARCHAR(32) NOT NULL,
    abstention_strategy VARCHAR(64) NOT NULL DEFAULT 'COUNT_PARTICIPATION_NOT_CONSENT',
    shared_ownership_strategy VARCHAR(64) NOT NULL DEFAULT 'REPRESENTATIVE_ONLY',
    owner_representative_strategy VARCHAR(64) NOT NULL DEFAULT 'UNIQUE_REPRESENTATIVE',
    unvoted_owner_strategy VARCHAR(64) NOT NULL DEFAULT 'KEEP_DENOMINATOR',
    summary_json JSONB NOT NULL DEFAULT '{}'::JSONB,
    status SMALLINT NOT NULL DEFAULT 1,
    effective_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_governance_policy_status CHECK (status IN (1, 2)),
    CONSTRAINT chk_governance_policy_abstention CHECK (
        abstention_strategy IN ('COUNT_PARTICIPATION_NOT_CONSENT', 'EXCLUDE_FROM_CONSENT_ONLY')
    ),
    CONSTRAINT chk_governance_policy_shared CHECK (
        shared_ownership_strategy IN ('REPRESENTATIVE_ONLY', 'PROPORTIONAL_SPLIT')
    )
);

COMMENT ON TABLE t_governance_policy IS '地方议事规则 / 治理规则模板配置';
COMMENT ON COLUMN t_governance_policy.abstention_strategy IS '弃权票处理策略';
COMMENT ON COLUMN t_governance_policy.shared_ownership_strategy IS '共有产权表决策略';
COMMENT ON COLUMN t_governance_policy.unvoted_owner_strategy IS '未投票业主处理策略';

CREATE TABLE IF NOT EXISTS t_tenant_community (
    tenant_id BIGINT PRIMARY KEY,
    tenant_code VARCHAR(64) NOT NULL UNIQUE,
    tenant_short_code VARCHAR(16) NOT NULL UNIQUE,
    tenant_name VARCHAR(128) NOT NULL,
    property_area_name VARCHAR(128),
    property_area_code VARCHAR(64),
    developer_name VARCHAR(128),
    developer_account_id BIGINT,

    province_code VARCHAR(16),
    province_name VARCHAR(64),
    city_code VARCHAR(16),
    city_name VARCHAR(64),
    district_code VARCHAR(16),
    district_name VARCHAR(64),
    street_code VARCHAR(32),
    street_name VARCHAR(64),
    community_code VARCHAR(32),
    community_name VARCHAR(64),
    address VARCHAR(256),

    planned_household_count INT NOT NULL DEFAULT 0,
    delivered_household_count INT NOT NULL DEFAULT 0,
    registered_property_unit_count INT NOT NULL DEFAULT 0,
    registered_voting_owner_count INT NOT NULL DEFAULT 0,

    total_planned_building_area NUMERIC(14, 2) NOT NULL DEFAULT 0,
    total_exclusive_area NUMERIC(14, 2) NOT NULL DEFAULT 0,
    registered_voting_total_area NUMERIC(14, 2) NOT NULL DEFAULT 0,
    excluded_parking_area NUMERIC(14, 2) NOT NULL DEFAULT 0,
    public_area NUMERIC(14, 2) NOT NULL DEFAULT 0,

    building_count INT NOT NULL DEFAULT 0,
    unit_count INT NOT NULL DEFAULT 0,
    parking_space_count INT NOT NULL DEFAULT 0,
    plot_ratio NUMERIC(8, 2),

    owners_assembly_established SMALLINT NOT NULL DEFAULT 0,
    committee_established SMALLINT NOT NULL DEFAULT 0,
    current_committee_term_name VARCHAR(128),
    transition_org_type VARCHAR(64),
    transition_org_status VARCHAR(64),

    rule_config_id BIGINT REFERENCES t_governance_policy(policy_id),
    shared_ownership_strategy VARCHAR(64) NOT NULL DEFAULT 'REPRESENTATIVE_ONLY',
    fund_managed_enabled SMALLINT NOT NULL DEFAULT 0,
    financial_control_config_id VARCHAR(64) NOT NULL DEFAULT 'TIERED_JOINT_REVIEW',
    quarterly_disclosure_deadline_day INT NOT NULL DEFAULT 15,

    statistics_version BIGINT NOT NULL DEFAULT 1,
    statistics_updated_at TIMESTAMP,
    governance_status VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_tenant_flags CHECK (
        owners_assembly_established IN (0, 1)
        AND committee_established IN (0, 1)
        AND fund_managed_enabled IN (0, 1)
    ),
    CONSTRAINT chk_tenant_area_non_negative CHECK (
        total_planned_building_area >= 0
        AND total_exclusive_area >= 0
        AND registered_voting_total_area >= 0
        AND excluded_parking_area >= 0
        AND public_area >= 0
    ),
    CONSTRAINT chk_tenant_counts_non_negative CHECK (
        planned_household_count >= 0
        AND delivered_household_count >= 0
        AND registered_property_unit_count >= 0
        AND registered_voting_owner_count >= 0
        AND building_count >= 0
        AND unit_count >= 0
        AND parking_space_count >= 0
    ),
    CONSTRAINT chk_tenant_quarter_deadline CHECK (quarterly_disclosure_deadline_day BETWEEN 1 AND 31),
    CONSTRAINT chk_tenant_shared_strategy CHECK (
        shared_ownership_strategy IN ('REPRESENTATIVE_ONLY', 'PROPORTIONAL_SPLIT')
    ),
    CONSTRAINT chk_tenant_governance_status CHECK (
        governance_status IN ('NORMAL', 'FINANCIAL_LOCKED', 'HANDOVER_LOCK')
    ),
    CONSTRAINT chk_tenant_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE INDEX IF NOT EXISTS idx_tenant_community_region
    ON t_tenant_community(province_code, city_code, district_code);
CREATE INDEX IF NOT EXISTS idx_tenant_community_street
    ON t_tenant_community(street_code, community_code);
CREATE INDEX IF NOT EXISTS idx_tenant_community_status
    ON t_tenant_community(status, governance_status);

COMMENT ON TABLE t_tenant_community IS '核心租户小区配置与法权基数当前版本';
COMMENT ON COLUMN t_tenant_community.property_area_code IS '物业管理区域官方编码';
COMMENT ON COLUMN t_tenant_community.developer_account_id IS '建设单位法人账户 ID，非 G 端响应需脱敏';
COMMENT ON COLUMN t_tenant_community.total_exclusive_area IS '法定专有部分总面积，计票基数分母核心字段';
COMMENT ON COLUMN t_tenant_community.registered_voting_total_area IS '系统已登记可计票专有面积当前参考值';
COMMENT ON COLUMN t_tenant_community.statistics_version IS '计票名册基数统计版本号，重新校对时自增';

CREATE TABLE IF NOT EXISTS t_tenant_statistics_snapshot (
    snapshot_id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES t_tenant_community(tenant_id),
    statistics_version BIGINT NOT NULL,
    total_area NUMERIC(14, 2) NOT NULL,
    total_owner_count BIGINT NOT NULL,
    item_count BIGINT NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_ref_id BIGINT,
    audit_hash CHAR(64) NOT NULL,
    created_by BIGINT REFERENCES sys_user(user_id),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tenant_statistics_version UNIQUE (tenant_id, statistics_version),
    CONSTRAINT chk_tenant_statistics_source CHECK (
        source_type IN ('SYSTEM_RECALCULATE', 'GOV_REVIEW_APPROVED')
    )
);

COMMENT ON TABLE t_tenant_statistics_snapshot IS '租户计票基数版本快照，用于历史表决审计回放';

CREATE TABLE IF NOT EXISTS t_tenant_denominator_review_request (
    request_id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES t_tenant_community(tenant_id),
    requested_total_area NUMERIC(14, 2) NOT NULL,
    requested_owner_count BIGINT NOT NULL,
    requested_unit_count BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    requested_by BIGINT NOT NULL REFERENCES sys_user(user_id),
    reviewed_by BIGINT REFERENCES sys_user(user_id),
    review_comment VARCHAR(500),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    review_time TIMESTAMP,
    CONSTRAINT chk_tenant_denominator_review_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT chk_tenant_denominator_review_values CHECK (
        requested_total_area > 0
        AND requested_owner_count > 0
        AND requested_unit_count > 0
    )
);

CREATE INDEX IF NOT EXISTS idx_tenant_denominator_review_tenant
    ON t_tenant_denominator_review_request(tenant_id, status, create_time DESC);

COMMENT ON TABLE t_tenant_denominator_review_request IS '业委会主任发起、G 端复核的计票基数变更申请';

CREATE TABLE IF NOT EXISTS t_tenant_community_settings_audit (
    audit_id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES t_tenant_community(tenant_id),
    operation_type VARCHAR(64) NOT NULL,
    payload_json JSONB NOT NULL DEFAULT '{}'::JSONB,
    operator_user_id BIGINT REFERENCES sys_user(user_id),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tenant_community_settings_audit
    ON t_tenant_community_settings_audit(tenant_id, create_time DESC);

COMMENT ON TABLE t_tenant_community_settings_audit IS '社区设置变更审计日志';

INSERT INTO t_governance_policy (
    policy_id, policy_code, policy_name, policy_version,
    abstention_strategy, shared_ownership_strategy, owner_representative_strategy,
    unvoted_owner_strategy, summary_json, status
) VALUES (
    1,
    'SH_DEFAULT_MAJORITY_2026',
    '上海市电子投票默认多数票规则2026版',
    '2026.1',
    'COUNT_PARTICIPATION_NOT_CONSENT',
    'REPRESENTATIVE_ONLY',
    'UNIQUE_REPRESENTATIVE',
    'KEEP_DENOMINATOR',
    '{
      "abstentionStrategy": "弃权计入参与人数与面积分母，但不计入同意票。",
      "sharedOwnershipStrategy": "同一专有部分必须确认唯一表决代表，防止人数票重复。",
      "unvotedOwnerStrategy": "未投票不随多数，技术参与处理；议案快照保持冻结。"
    }'::JSONB,
    1
) ON CONFLICT (policy_code) DO UPDATE SET
    policy_name = EXCLUDED.policy_name,
    policy_version = EXCLUDED.policy_version,
    summary_json = EXCLUDED.summary_json,
    update_time = now();

SELECT setval('t_governance_policy_policy_id_seq',
              GREATEST((SELECT COALESCE(MAX(policy_id), 1) FROM t_governance_policy), 1),
              true);

WITH scoped AS (
    SELECT op.*,
           ROW_NUMBER() OVER (
               PARTITION BY op.tenant_id, op.room_id
               ORDER BY op.is_voting_delegate DESC, op.opid ASC
           ) AS room_rn
    FROM c_owner_property op
    WHERE op.account_status = 1
),
stats AS (
    SELECT tenant_id,
           COUNT(*) FILTER (WHERE room_rn = 1) AS unit_count,
           COUNT(DISTINCT uid) FILTER (WHERE room_rn = 1) AS owner_count,
           COUNT(DISTINCT building_id) AS building_count,
           COALESCE(SUM(build_area) FILTER (WHERE room_rn = 1), 0)::NUMERIC(14, 2) AS total_area
    FROM scoped
    GROUP BY tenant_id
),
seed AS (
    SELECT tenant_id,
           CASE tenant_id
               WHEN 10001 THEN '沪浦-求是-2026-0001'
               WHEN 10002 THEN '沪浦-求是-2026-0002'
               WHEN 10003 THEN '沪浦-求是-2026-0003'
               ELSE 'COMM-' || tenant_id::TEXT
           END AS tenant_code,
           CASE tenant_id
               WHEN 10001 THEN 'QS0001'
               WHEN 10002 THEN 'QS0002'
               WHEN 10003 THEN 'QS0003'
               ELSE 'C' || tenant_id::TEXT
           END AS tenant_short_code,
           CASE tenant_id
               WHEN 10001 THEN '求是花园物业管理区域'
               WHEN 10002 THEN '求是东区物业管理区域'
               WHEN 10003 THEN '求是西区物业管理区域'
               ELSE '租户 ' || tenant_id::TEXT
           END AS tenant_name,
           CASE tenant_id
               WHEN 10001 THEN '求是花园物业管理区域'
               WHEN 10002 THEN '求是东区物业管理区域'
               WHEN 10003 THEN '求是西区物业管理区域'
               ELSE '物业管理区域 ' || tenant_id::TEXT
           END AS property_area_name,
           CASE tenant_id
               WHEN 10001 THEN '沪浦物区-2026-0001'
               WHEN 10002 THEN '沪浦物区-2026-0002'
               WHEN 10003 THEN '沪浦物区-2026-0003'
               ELSE 'AREA-' || tenant_id::TEXT
           END AS property_area_code,
           unit_count,
           owner_count,
           building_count,
           total_area
    FROM stats
)
INSERT INTO t_tenant_community (
    tenant_id, tenant_code, tenant_short_code, tenant_name,
    property_area_name, property_area_code, developer_name, developer_account_id,
    province_code, province_name, city_code, city_name, district_code, district_name,
    street_code, street_name, community_code, community_name, address,
    planned_household_count, delivered_household_count, registered_property_unit_count,
    registered_voting_owner_count, total_planned_building_area, total_exclusive_area,
    registered_voting_total_area, building_count, unit_count, parking_space_count,
    owners_assembly_established, committee_established, current_committee_term_name,
    transition_org_type, transition_org_status, rule_config_id, fund_managed_enabled,
    financial_control_config_id, quarterly_disclosure_deadline_day, statistics_updated_at
)
SELECT tenant_id,
       tenant_code,
       tenant_short_code,
       tenant_name,
       property_area_name,
       property_area_code,
       '上海晨晖置业有限公司',
       9131000000000276,
       '310000', '上海市',
       '310100', '上海市',
       '310115', '浦东新区',
       '310115013', '花木街道',
       '310115013018', '求是居民委员会',
       '上海市浦东新区晨晖路 188 弄',
       unit_count::INT,
       unit_count::INT,
       unit_count::INT,
       owner_count::INT,
       total_area,
       total_area,
       total_area,
       building_count::INT,
       building_count::INT,
       0,
       1,
       1,
       '第三届业委会-2026',
       '换届筹备组',
       '已锁定，换届流程身份锚定完成',
       (SELECT policy_id FROM t_governance_policy WHERE policy_code = 'SH_DEFAULT_MAJORITY_2026'),
       1,
       'TIERED_JOINT_REVIEW',
       15,
       CURRENT_TIMESTAMP
FROM seed
ON CONFLICT (tenant_id) DO NOTHING;

INSERT INTO sys_permission (permission_key, description, permission_group, allowed_dept_categories, is_legal_redline)
VALUES
    ('community:settings:read', '查看社区设置与法权基数', 'COMMUNITY', 'GBS', 0),
    ('community:settings:official:write', 'G端维护行政区划与组织备案', 'COMMUNITY', 'G', 1),
    ('community:settings:asset:write', '维护物业区域与建筑名册', 'COMMUNITY', 'GBS', 1),
    ('community:settings:policy:write', '维护自治规则与财务公示配置', 'COMMUNITY', 'GB', 1),
    ('community:settings:denominator:reconcile', 'G端复核并发布法定计票基数版本', 'COMMUNITY', 'G', 1)
ON CONFLICT (permission_key) DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_key)
SELECT r.role_id, p.permission_key
FROM sys_role r
JOIN (
    VALUES
        ('GOV_SUPER_ADMIN', 'community:settings:read'),
        ('COMMUNITY_ADMIN', 'community:settings:read'),
        ('PARTY_SECRETARY', 'community:settings:read'),
        ('GOV_OPERATOR', 'community:settings:read'),
        ('COMMITTEE_DIRECTOR', 'community:settings:read'),
        ('COMMITTEE_MEMBER', 'community:settings:read'),
        ('COMMITTEE_SECRETARY', 'community:settings:read'),
        ('PROPERTY_MANAGER', 'community:settings:read'),
        ('PROPERTY_STAFF', 'community:settings:read'),

        ('GOV_SUPER_ADMIN', 'community:settings:official:write'),
        ('COMMUNITY_ADMIN', 'community:settings:official:write'),
        ('PARTY_SECRETARY', 'community:settings:official:write'),
        ('GOV_OPERATOR', 'community:settings:official:write'),

        ('GOV_SUPER_ADMIN', 'community:settings:asset:write'),
        ('COMMUNITY_ADMIN', 'community:settings:asset:write'),
        ('PARTY_SECRETARY', 'community:settings:asset:write'),
        ('GOV_OPERATOR', 'community:settings:asset:write'),
        ('COMMITTEE_DIRECTOR', 'community:settings:asset:write'),
        ('PROPERTY_MANAGER', 'community:settings:asset:write'),

        ('GOV_SUPER_ADMIN', 'community:settings:policy:write'),
        ('COMMUNITY_ADMIN', 'community:settings:policy:write'),
        ('PARTY_SECRETARY', 'community:settings:policy:write'),
        ('GOV_OPERATOR', 'community:settings:policy:write'),
        ('COMMITTEE_DIRECTOR', 'community:settings:policy:write'),

        ('GOV_SUPER_ADMIN', 'community:settings:denominator:reconcile'),
        ('COMMUNITY_ADMIN', 'community:settings:denominator:reconcile'),
        ('PARTY_SECRETARY', 'community:settings:denominator:reconcile'),
        ('GOV_OPERATOR', 'community:settings:denominator:reconcile')
) AS p(role_key, permission_key) ON p.role_key = r.role_key
ON CONFLICT (role_id, permission_key) DO NOTHING;

INSERT INTO sys_menu (
    menu_id, parent_id, route_id, menu_name, path, icon, order_num, visible, status,
    required_permission, required_any_permissions, required_role_keys
) VALUES (
    9060, 9000, 'community-settings', '社区设置', '/community-settings', NULL, 35, 1, '0',
    'community:settings:read', NULL, NULL
) ON CONFLICT (menu_id) DO UPDATE SET
    parent_id = EXCLUDED.parent_id,
    route_id = EXCLUDED.route_id,
    menu_name = EXCLUDED.menu_name,
    path = EXCLUDED.path,
    icon = EXCLUDED.icon,
    order_num = EXCLUDED.order_num,
    visible = EXCLUDED.visible,
    status = EXCLUDED.status,
    required_permission = EXCLUDED.required_permission,
    required_any_permissions = EXCLUDED.required_any_permissions,
    required_role_keys = EXCLUDED.required_role_keys;

WITH child_grants AS (
    SELECT DISTINCT r.role_id, m.menu_id, m.parent_id
    FROM sys_role r
    JOIN sys_menu m ON m.menu_id = 9060
    WHERE r.status = '0'
      AND EXISTS (
          SELECT 1
          FROM sys_role_permission rp
          WHERE rp.role_id = r.role_id
            AND rp.permission_key = 'community:settings:read'
      )
),
all_grants AS (
    SELECT role_id, menu_id FROM child_grants
    UNION
    SELECT child_grants.role_id, parent.menu_id
    FROM child_grants
    JOIN sys_menu parent ON parent.menu_id = child_grants.parent_id
    WHERE parent.visible = 1
      AND parent.status = '0'
)
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT role_id, menu_id
FROM all_grants
ON CONFLICT (role_id, menu_id) DO NOTHING;

SELECT setval('sys_menu_menu_id_seq',
              GREATEST((SELECT COALESCE(MAX(menu_id), 1) FROM sys_menu), 9060),
              true);
