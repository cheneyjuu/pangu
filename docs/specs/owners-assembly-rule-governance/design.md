# 业主大会议事规则版本治理设计

> 业务关联：以受控状态机保存议事规则原件与结构化结论，并为正式业主大会冻结不可变执行快照。
>
> 状态：规则治理已实现；执行能力状态更新至 2026-07-20
>
> 需求依据：[requirements.md](requirements.md)

## 1. 现有能力审计

仓库已经存在两段可复用实现：

- `V3.99` 及 `OwnersAssemblyRuleService`：规则草稿、逐项核对、启用、替代和审计；
- `V3.100` 及 `OwnersAssemblyApplicationService`：正式安排时冻结规则快照，并在后续办理和计票中只读取快照。

本切片不重新建立第二套规则表。实施重点是用需求验收现有实现、补齐缺失测试，并保持历史 Flyway 文件不变。

历史 `RepairDecisionRule` 只对应旧维修流程，字段和生效方式均不足以成为新的权威规则来源。本切片不迁移或扩展它；维修工程接入统一表决时再关闭其新写入口。

## 2. 领域模型

### 2.1 规则聚合

`OwnersAssemblyRule` 是版本根，生命周期为：

```text
DRAFT -> PENDING_CONFIRMATION -> ACTIVE -> SUPERSEDED
```

- `DRAFT`：原件和配置可以修改；
- `PENDING_CONFIRMATION`：配置摘要锁定，只允许逐项核对；
- `ACTIVE`：可供新会议生成快照，不可修改；
- `SUPERSEDED`：历史只读，已生成快照继续有效。

`OwnersAssemblyRuleConfiguration` 保存可执行规则；`OwnersAssemblyRuleFieldConfirmation` 将每个字段的来源条款与确认人绑定到同一 `configurationSha256`，防止配置变化后沿用旧确认。

### 2.2 会次快照

`OwnersAssemblyRuleSnapshot` 是正式会议的值对象副本，通过 `sessionId` 与会次绑定。表决包保存 `ruleSnapshotId`，后续发布、送达、录票和计票均不得回读当前 `ACTIVE` 规则。

## 3. 权限设计

接口权限分为：

- `owners-assembly:rule:read`：查看版本、核对和审计；
- `owners-assembly:rule:draft`：创建、修改和提交草稿；
- `owners-assembly:rule:activate`：进入逐项核对和启用入口。

敏感权限只是第一道校验。应用服务还必须通过 `CommitteePositionRepository` 按当前租户和自然人查询有效职务，并只接受 `DIRECTOR` 或 `VICE_DIRECTOR`。请求体和令牌角色名称都不能代替该查询。

业委会职务记录的真实性由组织备案能力负责。本切片消费该可信事实，不通过议事规则页面创建或修改任期职务。

## 4. 持久化与并发

### 4.1 已有迁移

- `V3.99__owners_assembly_rule_registry.sql`：规则、字段核对、审计和每租户唯一有效版本；
- `V3.100__freeze_owners_assembly_rule_snapshot.sql`：会次规则快照及表决包关联；
- `V3.101__owners_assembly_formal_authority_and_material_links.sql`：正式办理权限和材料关系。

这些迁移已经进入历史，后续修正必须新增迁移，禁止修改原文件。

### 4.2 启用事务

启用动作在同一事务中：

1. 锁定 `t_tenant_community` 对应租户根记录；
2. 锁定待启用规则并复核状态、日期和全部字段确认；
3. 将原有效版本替代；
4. 启用新版本；
5. 写入替代和启用审计。

部分唯一索引 `tenant_id WHERE status = 'ACTIVE'` 作为并发兜底。任一步失败由事务整体回滚。

## 5. 接口契约

- `POST /api/v1/admin/owners-assembly-rules/drafts`：上传原件并创建草稿；
- `PUT /api/v1/admin/owners-assembly-rules/{ruleId}/draft`：修改草稿配置；
- `POST /api/v1/admin/owners-assembly-rules/{ruleId}/submit`：完整性校验并进入待核对；
- `POST /api/v1/admin/owners-assembly-rules/{ruleId}/field-confirmations/{field}/confirm`：当前主任或副主任逐项核对；
- `POST /api/v1/admin/owners-assembly-rules/{ruleId}/activate`：全部核对完成后启用；
- `GET /api/v1/admin/owners-assembly-rules/{ruleId}/preview-ticket`：生成短时原件预览凭证；
- 列表、当前有效版本、核对记录和审计接口均执行租户校验。

正式会议接口不接受客户端传入规则编号、渠道或计票阈值。确认会议安排时由服务端读取当前有效规则并生成快照。

## 6. 兼容与切换

- 新规则表不从旧 `t_repair_decision_rule` 自动导入数据，因为旧记录没有逐项来源和有权主体确认；
- 没有新规则时只允许会前草稿，不使用示范文本、社区默认值或旧维修规则继续正式办理；
- 统一表决内核现已支持纸质、线上、混合、三种未反馈认定以及需书面委托的代理纸票；只有规则字段缺失、组合矛盾或仍未实现的优先覆盖策略在正式安排前阻断；
- 新增执行能力只作用于新冻结的表决包，不修改历史规则快照和历史结果。

## 7. 测试策略

通过 HTTP 公共接口验证完整行为，数据库只用于准备隔离数据和核对跨事务结果：

- 草稿、提交、逐项核对、启用和替代主流程；
- 缺失来源条款、错误渠道组合和未来生效日期；
- 物业、普通委员和当前主任或副主任的权限差异；
- 跨租户编号访问；
- 已冻结规则快照在换版后保持不变；
- 尚未完整实现的规则能力阻断正式流程。
