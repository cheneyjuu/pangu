# Findings: M3-1 业主异议升级管道

## 现状（落地前）
- M1 / M2-1 / M2-3 已经具备 RBAC + 双签锁 + 财务公示快照三大能力，但业主异议救济链路完全没有 Java 落地
- CONTEXT.md §异议升级（lines 729-839）已定义 4 类异议（业主行政异议 / 行政复议反制 / 质保期质量异议 / 线下票异议期）
- ADR-0004 锁定「单一 dispute 主表 + 业务附属」架构，PRD §6.x 要求异议沿 5 级行政链层级递进
- 现状只有零散文档，无 t_owner_dispute / Dispute 聚合根 / DisputeApplicationService / 5 级状态机校验代码

## 已锁定的 5 条边界（Plan §"已锁定的 5 条边界"）
1. **命名权威 = CONTEXT.md（不是 ADR-0004）**：CONTEXT.md 的 t_owner_dispute / t_dispute_evidence /
   t_dispute_review_decision 三表是最终命名，ADR-0004 的 t_dispute / t_dispute_business_attachment
   仅是早期摘要
2. **dispute_kind 起始集 = CONTEXT.md 4 类**：EXPENSE_VOUCHER_DISPUTE / PROPOSAL_QUALITY_DISPUTE /
   OFFLINE_VOTE_DISPUTE / ADMINISTRATIVE_REJECTION_DISPUTE，schema CHECK + Java enum 同步
3. **5 级层级 = SMALLINT + 应用层校验**：状态机分支多（4 类 × 5 级 × 3 决议结果），SQL trigger 写出来啰嗦且无法返回结构化异常码 → 应用层主导，DB CHECK + trigger 兜底
4. **business_payload 仅 JSONB 软关联**：业务字段走 JSONB，不加 FK 也不在 schema 加业务专属字段。验证只在 application 层 record + Jackson 反序列化时做 schema 校验
5. **诉讼路径独立 = LITIGATION_FILED 终态**：业主选择 Level 5 = `dispute.gotoLitigation()` 单向流转到 LITIGATION_FILED（与 DECIDED_LEVEL_4_REJECTED 并列的终态），后续判决回流（M3-4）通过 `concludeLitigation(outcome, doc_url)` 单独流转到 CLOSED_FINAL

## 两道 trigger 红线（V2.8 + V2.8.1）
| # | 关键约束 | 触发表 | 触发时机 |
|---|---|---|---|
| 10-a | status 含 LEVEL_N 时 N 必须 = current_review_level | t_owner_dispute | BEFORE INSERT/UPDATE |
| 10-b | UPDATE 路径 current_review_level 单调递增 + 不可逆；豁免 gotoLitigation（任意 level → 5/LITIGATION_FILED） | t_owner_dispute | BEFORE UPDATE |
| 10-c | closed_at 当且仅当 status ∈ {CLOSED_FINAL, WITHDRAWN}（LITIGATION_FILED 不算 closed） | t_owner_dispute | BEFORE INSERT/UPDATE |
| 11-a | decision.review_level 必须 ≤ 主表 current_review_level | t_dispute_review_decision | BEFORE INSERT |
| 11-b | 主表 status 必须为 DECIDED_LEVEL_N_<KIND> 时方可插入对应 level decision | t_dispute_review_decision | BEFORE INSERT |
| 11-c | dispute_id 必须存在（FK 也守护） | t_dispute_review_decision | BEFORE INSERT |
| chk_dispute_kind_level1 | 仅 EXPENSE_VOUCHER_DISPUTE 适用 Level 1 | t_owner_dispute | CHECK |
| uk_decision_dispute_level | (dispute_id, review_level) 唯一 | t_dispute_review_decision | UNIQUE |
| chk_dispute_litigation_outcome | outcome IN (PLAINTIFF_WON / DEFENDANT_WON / SETTLED / WITHDRAWN_LITIGATION) | t_owner_dispute | CHECK |

## 关键改动点
- `pangu-domain/.../model/dispute/Dispute` 聚合根：21 状态 EnumMap<Status, EnumSet<Status>>
  状态机表，仿 GovernanceLock 纯 Java 风格，不依赖 Spring/MyBatis
- `Dispute.open(...)` 工厂按 dispute_kind 自动决定 initialLevel（EXPENSE_VOUCHER=1，其余=2）
- `decide()` 严格按 update 主表 → DECIDED_LEVEL_N_<KIND> 顺序，再 insert decision；trigger 11 兜底反向顺序
- `escalate()`：仅 DECIDED_LEVEL_N_REJECTED 允许；Level 4 必须改走 `gotoLitigation()`
- `gotoLitigation()` 单向流转到 LITIGATION_FILED（与 CLOSED_FINAL 并列终态），closed_at 留 NULL（M3-4 判决回流后转 CLOSED_FINAL）
- `OwnerDisputeController` 8 个 C 端 endpoint 全 `@PreAuthorize("isAuthenticated()")` + 应用层 ABAC 守护
  （C_USER 不进 sys_permission 链路，沿用 V2.7 disclosure:view:owner 模式）
- `GovDisputeController` 3 个 G 端 endpoint：list 用 `dispute:audit`，startReview / decide 用 `dispute:decide`（redline=1 → trigger 6 强制挂载角色 fixed_data_scope NOT NULL）

## 业主越权返 404（不是 403）
业主 A 调 `GET /api/v1/disputes/{B 的 dispute_id}` 返回 404 `DISPUTE_NOT_FOUND` —— 避免存在性泄漏（403 暗示该 dispute 真实存在）。这是与 M2-3 disclosure 不同的点：disclosure 是公示资源，存在性本身公开；dispute 是个人隐私资源。
对应实现在 `DisputeApplicationService.getDispute(disputeId, currentUserId)`：先 SELECT 主表，若 raisedByOwnerId != currentUserId 则同样抛 NOT_FOUND。

非 owner 调 mutating endpoint（escalate / withdraw / conclude / addEvidence）则抛 NOT_OWNER —— 这些操作不需要隐藏存在性，只要拦截非授权操作。

## 测试矩阵（Commit 2）
- DisputeStateMachineTest (14)：纯 domain，无 Spring，~30ms
- DisputeTriggerTest (11)：@SpringBootTest + JdbcTemplate，~2.8s
- DisputeWorkflowTest (10)：@SpringBootTest 全链路 + service 边角
- DisputePreAuthorizeMatrixTest (14)：MockMvc + V1.1 seed 用户 + JwtTokenProvider

合计 49 用例，165 → 214（plan §6 估算 24~30，超出 ~63%）。

## 趟过的坑
- **trigger 10-b 跳级守护与 gotoLitigation 冲突**：原 trigger 强制 current_review_level 单调 +1，但 PROPOSAL/OFFLINE/ADMIN 类起步 Level 2 → gotoLitigation 直达 Level 5 是越级。补 V2.8.1 增量迁移豁免 `target='LITIGATION_FILED' AND level=5` 路径；普通路径仍受守护。应用层 `Dispute.gotoLitigation()` 守护源状态合法（RAISED / DECIDED_LEVEL_4_REJECTED）
- **匿名访问返 403 不是 401**：Spring Security 6 默认无 AuthenticationEntryPoint 配置时，对受保护 endpoint 的匿名请求返 403。沿用 V2.7 disclosure / V1.4 PreAuthorize 矩阵 pattern，测试期望改 `isForbidden()`
- **trigger 11-c 与 FK 顺序**：t_dispute_review_decision.dispute_id REFERENCES t_owner_dispute(dispute_id) FK 在 PG 中先于 trigger 11 触发；插入不存在 dispute_id 时根因是 FK 约束 violation，不是 [trigger 11]。测试断言用 OR 接受任一关键词
- **`docker compose down -v` 被 auto-mode 拦截**：改走 Flyway 增量迁移 V2.8.1（CLAUDE.md 也禁止改写已迁移版本），避免触发器函数 in-place 修改造成 schema_history 校验和不匹配
- **JSONB 透传**：MyBatis SELECT 用 `business_payload::text AS business_payload` cast 成 String，避免 PgObject 反序列化；INSERT/UPDATE 用 `CAST(#{businessPayload} AS JSONB)`，仿 V2.7 OutboxEventMapper / FinanceDisclosureMapper

## 已知遗留（M3-1 边界外）
- ❌ 第三方异步通知 + 个保法 17 条告知（M3-1.5 单独阶段）
- ❌ 证据包 ZIP 导出 + SM2 签名 + 司法链锚点（M3-2 单独阶段）
- ❌ 密封档案 SM4 加密 + 解封审计（M3-3 单独阶段）
- ❌ Level 5 行政诉讼判决回流（M3-4 单独阶段，街道办手工录回 → litigation_outcome / litigation_judgement_url 字段已就位）
- ❌ JurisdictionalScope 区分街道办 / 区政府 / 市政府 dashboard（M3-2 / M3-3 引入；当前 dispute:audit 持有者按 fixed_data_scope 看辖区所有 level）
- ❌ DisputeBusinessPayloadValidator 按 dispute_kind 4 分派 schema 校验（等 voucher / proposal 表落地 M3-2 实施）

## C_USER permission 链路降级
plan §"已识别的剩余风险" #1 验证：grep `sys_user_role` / `sys_role_permission` 确认链路只挂在 sys_user 上，c_user 不进 sys_permission 链路。沿用 V2.7 disclosure:view:owner 模式：C 端业主 endpoint 全 `@PreAuthorize("isAuthenticated()")` + 应用层 SecurityUtils.getUid() + tenant 校验。该决策需在后续 M3-2 / M3-3 引入 c_user permission 链路时回头审视。

## ADR 偏离记录
- ADR-0004 早期摘要用 `t_dispute` / `t_dispute_business_attachment` 命名 → 实施按 CONTEXT.md `t_owner_dispute` / `t_dispute_evidence` / `t_dispute_review_decision`（plan §"已锁定的 5 条边界" #1 已锁定）
- ADR-0004 未提及 LITIGATION_FILED 终态独立性 → 实施按 CONTEXT.md §异议升级 与 CLOSED_FINAL 并列终态，等 M3-4 补 concludeLitigation 接口
