ALTER TABLE t_supplier_org_profile
    ALTER COLUMN unified_social_credit_code DROP NOT NULL,
    ALTER COLUMN contact_name DROP NOT NULL,
    ALTER COLUMN contact_phone DROP NOT NULL;

COMMENT ON COLUMN t_supplier_org_profile.unified_social_credit_code IS
    '统一社会信用代码；物业最小登记时可空，企业核验前必须补齐';
COMMENT ON COLUMN t_supplier_org_profile.contact_name IS
    '企业联系人；物业最小登记时可空，发送账号激活邀请前必须补齐';
COMMENT ON COLUMN t_supplier_org_profile.contact_phone IS
    '联系人手机号；物业最小登记时可空，发送账号激活邀请前必须补齐';
