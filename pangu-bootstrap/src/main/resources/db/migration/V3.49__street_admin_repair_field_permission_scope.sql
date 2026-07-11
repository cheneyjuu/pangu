-- 街道办只做维修监管和治理审批，不进入物业现场核验/纠偏动作链。
DELETE FROM sys_role_permission
WHERE role_id = 1
  AND permission_key = 'repair:workorder:field';
