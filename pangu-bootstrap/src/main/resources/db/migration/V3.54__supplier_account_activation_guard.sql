-- 同一供应商、同一手机号只允许一个待消费激活邀请。
-- 重发邀请时应用层先撤销旧邀请，再创建新邀请，避免多个入口同时有效。
WITH ranked AS (
    SELECT invitation_id,
           ROW_NUMBER() OVER (
               PARTITION BY supplier_dept_id, contact_phone
               ORDER BY create_time DESC, invitation_id DESC
           ) AS rn
    FROM t_supplier_activation_invitation
    WHERE status = 'PENDING'
)
UPDATE t_supplier_activation_invitation i
SET status = 'CANCELLED'
FROM ranked r
WHERE i.invitation_id = r.invitation_id
  AND r.rn > 1;

CREATE UNIQUE INDEX uk_supplier_activation_pending
    ON t_supplier_activation_invitation(supplier_dept_id, contact_phone)
    WHERE status = 'PENDING';

CREATE INDEX idx_supplier_activation_phone_status
    ON t_supplier_activation_invitation(contact_phone, status, expires_at);
