# 业主异议升级走单一 dispute 主表 + 业务附属表

> Status: accepted
> Date: 2026-06-19

## Context

Pangu 系统涉及多种异议场景：选举结果异议（[Q21 选举结果 5 天异议期]）、中标公告异议（[Q37.2 中标公告 5 天异议期]）、第三方反向异议（[CONTEXT.md - 第三方反向异议与业主诉权对抗]）、保修期异议（[Q39.4 保修期满公示]）、模式切换异议、议案变更异议、工程进度违约异议、验收质量纠纷、安全事故定责……每种异议都需要走 5 级升级链路（业委会 → 街道办 → 区政府 → 市政府 → 行政诉讼）。

理论上有两条数据建模路径：

1. **每业务私有 dispute 表**：`t_election_dispute` / `t_bidding_award_dispute` / `t_warranty_dispute` 各建一张，字段独立；
2. **单一 dispute 主表 + 业务附属表**：`t_dispute(dispute_id, tenant_id, dispute_kind ENUM, escalation_level, current_handler_org_id, ...)` + `t_dispute_business_attachment(dispute_id, business_kind, business_ref_id, payload JSONB)`。

## Decision

采用**单一 dispute 主表 + 业务附属**：

- `t_dispute` 持有所有跨业务通用字段：`dispute_id`、`tenant_id`、`dispute_kind ENUM(ELECTION_RESULT/BIDDING_AWARD/WARRANTY_PERIOD_END_REPAIR/WARRANTY_PERIOD_END_FRAUD/THIRD_PARTY_REFERENCE/CONTRACT_CHANGE/MILESTONE_BREACH/QUALITY_ACCEPTANCE/SAFETY_INCIDENT/MODE_SWITCH_AUDIT_FRAUD/...)`、`opened_by_user_id`、`opened_at`、`escalation_level INT`（1-5 对应业委会 → 街道办 → 区政府 → 市政府 → 行政诉讼）、`current_handler_org_id`、`status ENUM(OPENED/UNDER_REVIEW/MEDIATING/CONCLUDED/ESCALATED/EXPIRED)`、`final_verdict`、`final_verdicted_at`、`hash_chain_tx`；
- `t_dispute_business_attachment` 持有业务私有 payload：`dispute_id` FK + `business_kind` + `business_ref_id`（指向 `t_election` / `t_bidding_award` / `t_warranty_claim` / etc.）+ `payload JSONB`（业务专有字段）。

升级链路状态机、5 天 / 7 天 / 14 天等时间窗校验、街道办仲裁员的 G 端工作台、上链 / 通知 / 推送等通用能力**统一一份代码**，业务字段差异通过附属表的 JSONB 承载。

## Consequences

- **正向**：5 级升级链路只实现一次（一份状态机 / 一份时间窗 job / 一份 G 端工作台 UI）；新增业务异议类型只需扩 enum + 加附属 payload，无新表；街道办仲裁员能在统一 G 端 dashboard 看到辖区所有异议（含 escalation_level 排序），不需切换业务模块。
- **反向**：JSONB 附属字段没有强 schema 约束，需在 ApplicationService 层用 record / Jackson 校验；某种异议想加专属业务字段时不能简单 ALTER TABLE，要走 JSONB 演进；强类型约束（如外键）只能落在 `business_ref_id` + `business_kind` 双键软关联，需 application 层校验完整性。

## Considered Alternatives

- **每业务私有 dispute 表**：被拒。5 级升级链路要重复实现 N 份；街道办仲裁员要切 N 个 dashboard；新增业务（如未来"物业费拖欠业主追缴"异议）成本高。
- **强类型继承 + 多表 JOIN**：被拒。MyBatis + jsqlparser 的 `@DataScope` 重写在多表 JOIN 上的稳定性不如单表；JSONB + GIN 索引在 PostgreSQL 上性能足够。
