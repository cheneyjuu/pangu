# Findings: M1 RBAC 重写

## 现状（重写前）
- 权限模型是 `roles[]` 字符串数组写在 JWT 里，后端拦截器靠 role 名做 if/else
- `t_user` 单表混着政务/业主/物业三端身份，多 tenant 通过 `tenant_id` 字段强加，无法表达「一人多端」
- 数据 scope 由 `DataScopeInterceptor` 用 mock 上下文兜底，生产模式也会落到 mock 王小二，是反向漏洞
- `SysUser` 与 `CUser` 不分，业主投票与街道办审批共享一套权限缓存

## 七道触发器红线（V1.x + V1.4）
| # | 关键约束 | 触发表 | 触发时机 |
|---|---|---|---|
| 1 | role.allowed_dept_category 必须等于 dept.dept_category | sys_user_role | INSERT/UPDATE |
| 2 | OWNER_REPRESENTATIVE 必须有 sys_user_building 绑定 | sys_user_role | DEFERRED commit-time |
| 3 | role.fixed_data_scope NOT NULL 锁死 user.effective_data_scope | sys_user_role | INSERT/UPDATE |
| 4 | sys_dept.parent_id 的 tenant_id 必须 NULL 或与自身相等 | sys_dept | INSERT/UPDATE |
| 5 | sys_user_building.tenant_id 必须等于 user.dept.tenant_id；街道办 (tenant=NULL) 禁任命 | sys_user_building | INSERT |
| 6 | sys_role_permission：role.allowed_dept_category 必须出现在 permission.allowed_dept_categories；redline=1 要求 fixed_data_scope NOT NULL | sys_role_permission | INSERT |
| 7 | is_system=1 的预置角色禁止 DELETE | sys_role | BEFORE DELETE |

## 关键改动点
- `pangu-domain/.../context/UserContext` 改为 record，承载 accountId / identityType / userId / tenantId / permissions / opidList / authLevel / dataScope
- 新增 `UserContextLoader` 端口（domain）+ `DefaultUserContextLoader` 适配器（infrastructure），SQL JOIN sys_user → sys_user_role → sys_role_permission，C_USER 走 c_owner_property 反查 opid
- `JwtAuthenticationFilter` 解析 token 后调 loader 实时构 UserContext，再注入 SecurityContext
- `JwtTokenProvider.generateToken(accountId, identityType, userId, tenantId)` 不再写权限/角色
- `WaiverApplicationService` / `VotingApplicationService` 不再依赖 SecurityUtils，直接吃 UserContext
- `DataScopeInterceptor` 生产路径不再落 mock 王小二；Profile != prod 才走 mock

## 三段式登录 (AuthService)
1. 短信发码 / 密码核身
2. 签发"过渡 token"，并返回该 account 在多 tenant 下的可选身份列表
3. 用户选定身份 + tenant，签发"业务 token"（identityType + userId + tenantId 三件套写入）

## 测试矩阵（Commit 2）
- SysUserRoleTriggerTest：5 用例覆盖 trigger 1 / 2 / 3
- SysDeptTriggerTest：4 用例覆盖 trigger 4 / 5
- SysRolePermissionTriggerTest：4 用例覆盖 trigger 6 / 7（含 1 个反向正例）
- PreAuthorizeMatrixTest：5 用例覆盖 4 类典型用户对 5 个 endpoint 的拒绝路径
- SwitchTenantMatrixTest：4 用例覆盖 C 端业主切租户的合法 / 非法 / 缺 header

## 趟过的坑
- `mvn ... -Dtest=XxxTest` 在 pangu-domain 没匹配上时 surefire 会失败，要加 `-Dsurefire.failIfNoSpecifiedTests=false`
- `@PreAuthorize` 在 method invocation 时检查；`@RequestBody` 反序列化先于 PreAuthorize，body 字段类型错（List vs String）会 500 不是 403
- DEFERRED constraint trigger 抛错时机是 commit，要用 `TransactionTemplate` 显式包；异常类型可能是 `TransactionSystemException` 或 `DataAccessException`
- Flyway 把 V1.x 改了之后必须 `docker compose down -v` 让数据卷干净重放，否则 `flyway_schema_history` 校验和不匹配会报错

## 已知遗留
- 角色动态新建的 SaaS 管理后端 endpoint 本期不做（方案 B），permission schema 已就位
- `pangu-infrastructure` 目前依赖 `pangu-application`，是反向依赖，本期未修
- 生产环境 mock 兜底已移除，但生产模式下空 UserContext 的处理路径还需要在后续测试覆盖
