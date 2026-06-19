# 三种物业模式作为 tenant 级运行时配置而非分库

> Status: accepted
> Date: 2026-06-19

## Context

Pangu 系统支持三种物业管理模式：包干制（LUMP_SUM）/ 筹金制（FUND_RAISING）/ 信托制（TRUST）。三者在数据范围（ABAC 字段过滤）、决策流程（双签 / 双密码）、资金账户结构（物业过路户 vs 业主独立账户 vs 信托账户）、C 端业主穿透度（年度公示 / 季度公示 / 实时穿透）、外部集成（司法链上链与否）上全部不同。

理论上有两条实现路径：

1. **物理分库**：每种模式独立部署一套服务 + 一套库（甚至独立 tenant 命名空间），逻辑上彻底隔离；
2. **逻辑配置**：单库单服务，模式作为 tenant 级运行时配置字段，所有业务代码以 `if (tenant.propertyMode == TRUST) { ... }` 分支处理。

## Decision

采用**逻辑配置**：`tenant.property_mode ENUM(LUMP_SUM/FUND_RAISING/TRUST)` 作为 tenant 级运行时配置字段，配合 `tenant.property_mode_history JSONB` 记录历史切换。所有业务代码（ApplicationService / Domain Engine / Mapper）共享同一套数据模型，差异化通过 `PropertyModeStrategy` 接口分派。

## Consequences

- **正向**：单库单服务降低运维成本；模式切换（[CONTEXT.md - 模式切换议案的法理门槛]）可以作为运行时议案推进，而非"迁移工程"；跨模式聚合查询（街道办看辖区内所有模式的小区财务）走单库直查。
- **反向**：业务代码处处需要分支判断模式；新增一种模式需要梳理所有 `PropertyModeStrategy` 实现；包干制本应"完全看不到物业内账"的隐私约束靠 ABAC 字段过滤而非物理隔离实现，必须通过 [DataScopeInterceptor] + 严格的 mapper 测试兜底。

## Considered Alternatives

- **物理分库**：被拒。模式切换会变成"跨库迁移工程"，违反 [CONTEXT.md - 历史数据保留 + 语义边界标记] 的"不破坏既有不变量"原则；街道办跨模式聚合查询需引入额外联邦层。
- **每模式独立 service 共享库**：被拒。代码重复严重，且业委会换届 / 异议升级等跨模式共享流程需要双写。
