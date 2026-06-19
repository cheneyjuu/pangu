# 业主投票数据双表落库（聚合表 + 审计表）

> Status: accepted
> Date: 2026-06-19

## Context

业主投票存在两个矛盾诉求：

1. **匿名性**（C 端业主权益）：业主不希望物业 / 业委会 / 邻居知道自己投了"反对"——尤其在解聘物业 / 业委会主任改选这种场景，被业委会主任知道反对者身份后可能遭报复；
2. **实名审计**（G 端 / 法院 / 街道办权益）：异议升级到街道办仲裁 / 法院诉讼时，必须能调取"原始投票记录是否被篡改"，这要求每张票实名可追溯。

如果只用一张表存所有票（带 `voter_user_id`），匿名性瓦解；只存匿名票（去掉 user_id），审计性瓦解。

## Decision

落两张物理独立的表，写入时同一个事务内双写：

- **`t_proposal_vote_aggregate`** — 聚合 / 公开表：业委会、物业、其他业主、C 端 read-only。字段：`anonymized_voter_id`（用 tenant 级动态 salt 对 user_id 做 SM3 哈希）、`vote_choice`、`voted_at`、`voter_area_share`。**所有 B 端 / C 端展示统计只查这张表**。
- **`t_proposal_vote_audit`** — 审计专用表：仅 G 端 GOV_SUPER_ADMIN + 街道办仲裁员 + 法院司法链查询接口能查。字段：`real_voter_user_id`（SM4 加密落库）、`anonymized_voter_id`（与 aggregate 表一致）、其他元数据。**通过 `anonymized_voter_id` 反查 audit 表必须留下访问日志**（`t_audit_table_access_log`）。

salt 是 tenant 级且每次议案随机生成（`tenant.vote_salt_per_proposal`），即使同一业主在不同议案上投相同票，`anonymized_voter_id` 也不一致，杜绝跨议案画像。

## Consequences

- **正向**：业主端展示走 aggregate 表，物业 / 业委会无法反推个人投票立场；G 端审计走 audit 表，且每次反查留访问日志（街道办仲裁员越权查 audit 表会被 [CONTEXT.md - 异议升级 5 级] 上一级监督）。
- **反向**：双写事务复杂度提高，必须同事务原子（用 [Outbox] 兜底）；salt 永久保留——若 salt 遗失，audit 表的 `anonymized_voter_id` 与历史 aggregate 表无法对账；audit 表是 SM4 加密强敏感数据，备份策略需独立审计（不能简单丢到 OSS）。

## Considered Alternatives

- **单表 + ABAC 字段过滤**：被拒。ABAC 是软隔离，依赖 [DataScopeInterceptor] 不被绕过；任何 SQL 直查 / DBA 操作都能拿到原始数据，匿名承诺无法兑现。
- **完全匿名（删除 user_id）**：被拒。后续异议 / 法院诉讼无法举证投票真实性。
- **链上存证替代 audit 表**：被拒。链上存储成本过高 + 链上记录虽然不可篡改但**无法删除**——业主退出小区后的删除权（[GDPR-like 数据主体权]）无法满足。
