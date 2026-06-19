# Progress: M1 RBAC 重写

## Session: 2026-06-17 ~ 2026-06-19

### Phase 1：Schema (V1.x + V1.4) — complete
- 重写 `V1__init_schema.sql` / `V1.1__seed_mock_data.sql` / `V1.2__sys_user_role.sql` / `V1.3__sys_menu.sql`
- 新增 `V1.4__sys_permission.sql`：17 个 permission_keys + 13 个系统角色 + 7 个 PG 触发器
- `docker compose down -v && up -d` 全量重放成功

### Phase 2：Domain — complete
- `DataScopeType` 三档：ALL_COMMUNITY / OWNER_GROUP / ORG_ONLY
- 新增 `UserContext` (record) / `UserContextHolder` / `UserContextLoader` 端口
- `AuthenticationLevel` 增加 L2 实名认证档
- 删除老的 `NaturalPerson` / `UserGateway`（已归并到 UserContextLoader）

### Phase 3：Infrastructure — complete
- `DefaultUserContextLoader`：JOIN sys_user → sys_user_role → sys_role_permission；C_USER 走 c_owner_property 反查 opid
- `AccountMapper` / `UserContextMapper` 替换原 `UserMapper`
- `DataScopeInterceptor`：生产路径不再 fallback mock
- `ThreadLocalUserContextHolder`：每请求绑定，`finally` 必清

### Phase 4：Application — complete
- `WaiverApplicationService` / `VotingApplicationService` 改用 UserContext
- Command 瘦身：`SubmitDraftCommand` / `CommitteeReviewCommand` / `StreetReviewCommand` 不再带 user / tenant，由 ApplicationService 从 UserContext 注入

### Phase 5：Interfaces — complete
- `JwtTokenProvider.generateToken(accountId, identityType, userId, tenantId)`，token 不带角色/权限
- `JwtAuthenticationFilter`：解 token → loader.load → 注入 SecurityContext + UserContextHolder
- `WaiverController` 改 @PreAuthorize 用 `hasAuthority('waiver:approve:committee')`
- `AuthService` 三段式：发码 → 过渡 token + 身份列表 → 业务 token

### Phase 6：测试适配 — complete
- `DataScopeTest` 改造（移除 mock 兜底假设）
- `WaiverStateMachineTest` / `ConcurrentWaiverSubmissionTest` / `ControllerIntegrationTest` 等 Spring Boot 集成测试全部跑通
- `mvn clean test`：74 tests / 0F / 0E / 1S（Skipped 是 @Disabled 测试）

### Phase 7：RBAC 测试矩阵 (Commit 2) — complete
- 新增 5 个测试类 / 22 用例：
  * SysUserRoleTriggerTest (5) — trigger 1 / 2 / 3 反例
  * SysDeptTriggerTest (4) — trigger 4 / 5
  * SysRolePermissionTriggerTest (4) — trigger 6 / 7
  * PreAuthorizeMatrixTest (5) — waiver 拒绝路径
  * SwitchTenantMatrixTest (4) — C 端业主切租户矩阵
- mvn test：74 → 96（0F / 0E / 1S）

### Phase 8：Docs + Push + PR (Commit 3) — in_progress
- 更新 `task_plan.md` / `findings.md` / `progress.md`
- 等待用户确认后 `git push origin m1/rbac-rewrite` + `gh pr create`

## Commits
| SHA | Type | Subject |
|---|---|---|
| 818cb89 | feat(rbac) | rewrite permission/identity model around sys_permission + multi-tenant trigger redlines |
| 7a4f9b0 | test(rbac) | add RBAC matrix covering 7 triggers + @PreAuthorize + switch-tenant |

## Test Results
| Test | Expected | Actual | Status |
|---|---|---|---|
| `mvn clean test` (Commit 1 后) | full pass | 74 / 0F / 0E / 1S | Pass |
| `mvn clean test` (Commit 2 后) | full pass | 96 / 0F / 0E / 1S | Pass |

## Error Log
| Error | 处理 |
|---|---|
| `mvn -Dtest=SysUserRoleTriggerTest` 在 pangu-domain 报"No tests matching" | 加 `-Dsurefire.failIfNoSpecifiedTests=false` |
| PreAuthorizeMatrixTest 2/5 返回 500 而非 403 | `reasonEvidenceKeys` 字段类型不匹配（List vs String），反序列化先于 PreAuthorize；从 body 中删除该字段 |
| Flyway 校验 V1.x 失败 | `docker compose down -v && up -d` 让数据卷干净重放（用户选 方案 C） |
