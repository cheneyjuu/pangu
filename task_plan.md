# Task Plan: M3-1 业主异议升级管道（异议升级主表 + 业务附属）

## Goal
落地 ADR-0004「单一 dispute 主表 + 业务附属」，覆盖业主行政异议 5 级救济链
（业委会 / 街道办 / 区政府 / 市政府 / 行政诉讼），与 CONTEXT.md §异议升级 严格对齐。

## Scope
- Schema：V2.8 主迁移 + V2.8.1 增量（trigger 10-b gotoLitigation 跳级豁免）
- Domain：`Dispute` 聚合根 + 21 状态状态机 + 4 enum + 2 record + 3 repository port
- Application：`DisputeApplicationService` 9 use case + 8 command record + DisputeApplicationException
- Infrastructure：3 row entity + 3 mapper + 3 RepositoryImpl（乐观锁 / DuplicateDecisionException 转换）
- Web：DisputeErrorCode + DisputeExceptionTranslator + 11 endpoint + 4 DTO
- Tests：4 个测试类 / 49 用例（state machine / trigger 反例 / workflow / RBAC 矩阵）

## Phases
| # | 内容 | Status |
|---|---|---|
| 23 | V2.8 Schema 主迁移（3 表 + trigger 10/11 + 2 permission） | complete |
| 24 | V2.8.1 增量（gotoLitigation 跳级豁免） | complete |
| 25 | Domain 层（Dispute 聚合根 + 4 enum + 2 record + 3 port） | complete |
| 26 | Application 层（DisputeApplicationService + 8 command + Exception） | complete |
| 27 | Infrastructure 层（3 row + 3 mapper + 3 RepoImpl） | complete |
| 28 | Web 层（11 endpoint + 4 DTO + ErrorCode + Translator） | complete |
| 29 | 现有测试零回归 + Commit 1 | complete |
| 30 | Commit 2 — test(dispute) 矩阵 4 测试类 / 49 用例 | complete |
| 31 | Commit 3 — docs + push + PR | in_progress |

## 关键决策
| 决策 | 理由 |
|---|---|
| 命名权威 = CONTEXT.md（不是 ADR-0004 摘要） | t_owner_dispute / t_dispute_evidence / t_dispute_review_decision 是 CONTEXT.md 明确的最终命名 |
| 5 级状态机主导落 Java，DB CHECK + trigger 兜底 | 状态机分支多（4 类 × 5 级 × 3 决议结果），SQL trigger 写出来啰嗦且无法返回结构化异常码 |
| business_payload 用 JSONB 软关联，不加 FK | 业务侧 voucher / proposal / vote 表落地节奏不一，不把 dispute schema 锁死在某一业务上 |
| LITIGATION_FILED 与 CLOSED_FINAL 并列终态 | 行政诉讼判决回流需街道办手工录回（M3-4），LITIGATION_FILED 不算 closed（closed_at NULL） |
| trigger 10-b gotoLitigation 跳级豁免 | PROPOSAL/OFFLINE/ADMIN 类起步 Level 2，gotoLitigation 直达 Level 5 是合法越级；普通路径仍守护 +1 单调 |
| C_USER 不进 sys_permission 链路，C 端 endpoint 全 isAuthenticated() + 应用层 ABAC | 沿用 V2.7 disclosure:view:owner 模式，避免与 M1 RBAC sys_user 链路混淆；业主权限模型留待 M3-2 / M3-3 |
| 业主越权返 404（不是 403） | dispute 是个人隐私资源，存在性本身泄漏 → 与 V2.7 disclosure 模式有别 |
| Commit 拆分：feat / test / docs 三阶段 | 主代码与测试矩阵分离，与 M2-1 / M2-3 同 cadence |

## 验证清单
- `mvn clean install -DskipTests`：BUILD SUCCESS
- `mvn -pl pangu-bootstrap -am test -Dtest='Dispute*Test'`：49 / 0F / 0E
- `mvn clean test`：214 / 0F / 0E / 1S（baseline 165 → 214，+49 新用例）
- 触发器反例覆盖率：trigger 10-a/b/c + trigger 11-a/b/c + chk_dispute_kind_level1 + uk_decision_dispute_level + chk_dispute_litigation_outcome 全覆盖
- 推送 + PR 之前由用户确认（destructive remote ops）

## 与 plan §6 估算的偏差
- plan §6 估算 24~30 用例，实际 49 用例（+63%）
- 多覆盖：rehydrate 字段恢复、addEvidence 终态守护、gotoLitigation 多源状态正反例、SYS_USER 调 C 端 endpoint NOT_OWNER 路径、listJurisdiction level 过滤断言、各类匿名访问 403
