# Progress: M3-1 业主异议升级管道

## Session: 2026-06-19

落地 ADR-0004「单一 dispute 主表 + 业务附属」，覆盖业主行政异议 5 级救济链
（业委会 / 街道办 / 区政府 / 市政府 / 行政诉讼）。

### Phase 1：Schema (V2.8 + V2.8.1) — complete
- V2.8：3 张表 + 2 个 trigger（10 / 11）+ 2 个 G 端 permission（dispute:audit / dispute:decide redline=1）
- V2.8.1：放行 `gotoLitigation` 合法跳级（任意 level → 5/LITIGATION_FILED），保留普通路径单调 +1 守护
- chk_dispute_kind_level1：仅 EXPENSE_VOUCHER_DISPUTE 适用 Level 1，其余 dispute_kind 起步 Level 2
- chk_dispute_litigation_outcome：PLAINTIFF_WON / DEFENDANT_WON / SETTLED / WITHDRAWN_LITIGATION

### Phase 2：Domain — complete
- `Dispute` 聚合根（纯 Java，仿 GovernanceLock）：21 状态 + EnumMap<Status, EnumSet<Status>> 状态机表
- 工厂 `Dispute.open(...)` 按 dispute_kind 自动决定 initialLevel（EXPENSE_VOUCHER=1 / 其余=2）
- 流转 API：`startReview / decide / escalate / gotoLitigation / withdraw / concludeFinal`
- enum：`DisputeKind`（4 个）/ `DisputeStatus`（21 个）/ `DecisionKind`（3 个）/ `EvidenceKind`（5 个）
- value object：`Decision` / `DisputeEvidence` 双 record
- 3 个 repository port：`DisputeRepository` / `DisputeEvidenceRepository` / `DisputeDecisionRepository`
- 内部异常：`OptimisticLockException` / `DuplicateDecisionException`

### Phase 3：Application — complete
- `DisputeApplicationService` 9 条 use case：open / startReview / decide / escalate / gotoLitigation /
  withdraw / concludeFinal / addEvidence / getDispute（含 ABAC：业主只能查自己 raised dispute）
- `decide` 严格按 `update 主表 → DECIDED_LEVEL_N_<KIND> → insert decision` 顺序（trigger 11 兜底反向顺序）
- 8 个 command record：`OpenCommand` / `StartReviewCommand` / `DecideCommand` / `EscalateCommand` /
  `GotoLitigationCommand` / `WithdrawCommand` / `ConcludeCommand` / `AddEvidenceCommand`
- `DisputeApplicationException` + 10 个 Reason：NOT_FOUND / INVALID_TRANSITION / NOT_OWNER /
  LEVEL_EXCEEDED / CONCURRENT_MODIFICATION（needRetry=true）/ DECISION_DUPLICATE / TYPE_LEVEL_MISMATCH /
  ESCALATE_REQUIRES_REJECTED / ALREADY_CLOSED / EVIDENCE_DISPUTE_CLOSED

### Phase 4：Infrastructure — complete
- 3 个 row entity：`OwnerDisputeRow` / `DisputeEvidenceRow` / `DisputeDecisionRow`
- 3 个 mapper + XML：`OwnerDisputeMapper` / `DisputeEvidenceMapper` / `DisputeDecisionMapper`
  * SELECT 统一用 `business_payload::text AS business_payload`（PG JSONB → String 透传）
  * INSERT/UPDATE 用 `CAST(#{businessPayload} AS JSONB)`
  * `update` 含 `WHERE version = #{old}` + `SET version = version + 1` 乐观锁兜底
  * `selectByJurisdiction` 挂 `@DataScope(deptAlias="raised_by_owner_id")`（M3-2 引入 JurisdictionalScope 时再细化）
- 3 个 RepositoryImpl：捕获 `DuplicateKeyException` → `DuplicateDecisionException`，rowsAffected=0 → `OptimisticLockException`

### Phase 5：Web — complete
- `DisputeErrorCode` 10 个 enum 常量（401xx / 411xx 区段）
- `DisputeExceptionTranslator`：Reason → ErrorCode 映射，errorChain 链路保留
- `OwnerDisputeController` C 端 8 个 endpoint，`@PreAuthorize("isAuthenticated()")`：
  * POST `/api/v1/disputes` open
  * GET `/api/v1/disputes/{id}` getMine
  * GET `/api/v1/disputes` listMine
  * POST `/api/v1/disputes/{id}/escalate`
  * POST `/api/v1/disputes/{id}/litigation`
  * POST `/api/v1/disputes/{id}/withdraw`
  * POST `/api/v1/disputes/{id}/conclude`
  * POST `/api/v1/disputes/{id}/evidence`
- `GovDisputeController` G 端 3 个 endpoint：
  * GET `/api/v1/gov/disputes` `hasAuthority('dispute:audit')`
  * POST `/api/v1/gov/disputes/{id}/review/start` `hasAuthority('dispute:decide')`
  * POST `/api/v1/gov/disputes/{id}/review/decide` `hasAuthority('dispute:decide')`
- 4 个 DTO：`OpenDisputeRequest` / `DisputeResponse` / `DecideRequest` / `AddEvidenceRequest`
- `GlobalExceptionHandler` 增 `DisputeApplicationException` 分支

### Phase 6：测试矩阵 — complete
- `DisputeStateMachineTest` (14)：纯 domain，open 工厂 / 4 级正向链路 / escalate 守护 /
  gotoLitigation 通路 / withdraw / concludeFinal / decide 守护 / rehydrate
- `DisputeTriggerTest` (11)：trigger 10-a/b/c + trigger 11-a/b/c + chk_dispute_kind_level1 +
  uk_decision_dispute_level + chk_dispute_litigation_outcome 全反例
- `DisputeWorkflowTest` (10)：@SpringBootTest 全链路 EXPENSE_VOUCHER L1→L3 upheld+conclude /
  诉讼旁路 RAISED → LITIGATION_FILED / 业主越权 NOT_FOUND / 非 owner mutating NOT_OWNER /
  Level 4 LEVEL_EXCEEDED 后改走 gotoLitigation / addEvidence 守护 / list 租户过滤
- `DisputePreAuthorizeMatrixTest` (14)：V1.1 求是小区 seed 用户全矩阵 — 陈网格员（GRID_OPERATOR）/
  刘主任（COMMUNITY_ADMIN role_id=2 audit+decide）/ 李四（C_USER uid=70002）；G 端 / C 端 isAuthenticated
  通路 / SYS_USER 调 C 端 endpoint NOT_OWNER 41102 / 匿名 403

### Phase 7：Docs (Commit 3) — in_progress
- 更新 `task_plan.md` / `findings.md` / `progress.md`
- 等待用户确认后 push origin m3/dispute-pipeline + gh pr create

## Commits
| SHA | Type | Subject |
|---|---|---|
| 03e006b | feat(dispute) | introduce t_owner_dispute + 5-level escalation state machine |
| 02063f6 | test(dispute) | dispute matrix + trigger 10/11 reverse cases + RBAC matrix |

## Test Results
| Test | Expected | Actual | Status |
|---|---|---|---|
| `mvn clean test` (Commit 1 后) | full pass | 165 / 0F / 0E / 1S | Pass |
| `mvn -Dtest=Dispute*Test`（Commit 2 后单元） | 0F/0E | 49 / 0F / 0E | Pass |
| `mvn clean test`（Commit 2 后全量） | 0F/0E | 214 / 0F / 0E / 1S | Pass |

baseline 165 → M3-1 后 214（+49 用例，超 plan §6 估算 24~30）。

## Error Log
| Error | 处理 |
|---|---|
| `DisputeWorkflowTest.litigationBypass_fromRaised` 抛 `[trigger 10] 不可跳级：2 -> 5` | 新增 V2.8.1 修正 trigger 10-b：`gotoLitigation` 直达 LITIGATION_FILED 是合法路径，应放行；应用层 Dispute.gotoLitigation() 守护源状态合法（RAISED 或 DECIDED_LEVEL_4_REJECTED） |
| `DisputePreAuthorizeMatrixTest.anonymousAccess_*` 期望 401 实际 403 | 与 V2.7 disclosure / V1.4 PreAuthorize 矩阵一致 — Spring Security 无 EntryPoint 配置时匿名访问受保护 endpoint 返 403；改测试为 `isForbidden()` |
| 不能 `docker compose down -v` 重置 DB（auto-mode 拦截破坏性操作） | 改走 Flyway 增量迁移 V2.8.1，避免触发器函数 in-place 修改造成 schema_history 校验和不匹配 |
