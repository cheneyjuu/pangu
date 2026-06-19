# 司法链上链采用 mock + real 双轨而非单一 provider

> Status: accepted
> Date: 2026-06-19

## Context

Pangu 系统在多处场景需要"上司法链"作为不可篡改证据：[TRUST 模式双密码核销]、[隐蔽工程 checkpoint 不可逆]、[投票数据上链]、[安全事故分级处置]、[模式切换资金清算]、[公共收益经营对手方违约处置]。这些场景的法理诉求是"业主、物业、工程商、街道办、法院都能事后调取并验证"。

国内司法链 provider 有：最高院司法链、各省高院司法链、北互链、杭互链、长安链、蚂蚁链等。每家链上链费用、API 稳定性、可用区域、节点数差异巨大；开发期还面临"测试用真链每笔几毛 / 几块钱"的成本痛点。

## Decision

定义统一的 `JudicialChainGateway` 接口，提供两个实现：

- `MockJudicialChainGateway` — 默认在所有非生产环境启用：本地计算 SM3 哈希 + 写入 `t_judicial_chain_mock_record(record_id, payload_hash, signed_at, mock_tx_hash)`，立即返回伪 `tx_hash`；
- `RealJudicialChainGateway` — 生产环境启用：通过 `chain.provider` 配置（`SUPREME_COURT` / `BEIJING_INTERNET_COURT` / `HANGZHOU_INTERNET_COURT` / `ANT_CHAIN`）路由到具体 SDK，落 `t_judicial_chain_real_record(record_id, provider, payload_hash, real_tx_hash, signed_at, retry_count)`。

业务代码（ApplicationService）只调 `judicialChainGateway.submit(...)`，不感知具体链。失败重试通过 [Outbox 模式] 异步落地——即使 real 链不可用，业务流不阻塞，凭证最终一致。

## Consequences

- **正向**：开发 / 测试期零上链成本；provider 切换（如最高院司法链费率涨价 / 蚂蚁链关停）只改配置不改业务代码；mock 实现可在单元测试 / @SpringBootTest 中精确断言"上链了什么 payload"。
- **反向**：必须严格通过 `application.yml` 的 `chain.mode` 配置区分 mock vs real，**生产环境绝对不能误用 mock**——通过 Spring Profile 强制约束 + 启动时 `@PostConstruct` 二次校验（[CONTEXT.md - MQ / Outbox / 异步事件可靠性]）；mock 与 real 的哈希算法必须严格一致（SM3），否则切换 provider 后无法跨期验证。

## Considered Alternatives

- **单一 provider 强绑定**：被拒。任何一家链关停 / 停服都会导致历史证据无法验证；开发测试期上链成本不可控。
- **完全不上链改用本地数字签名**：被拒。法理诉求要求"第三方独立背书"，本地签名无法满足司法可采性。
