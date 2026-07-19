# 纸质投票闭环设计

> 业务关联：把纸质送达和纸票原件转换为可核验的统一送达及有效票事实。
>
> 状态：后端能力完成，三端页面纳入改造计划切片八（2026-07-19）
>
> 需求依据：[requirements.md](requirements.md)

## 1. 设计原则

纸质渠道采用“原始材料台账 -> 录入与复核 -> 统一执行内核”的单向转换：

```text
送达登记 --核对通过--> 统一送达记录
纸票回收 --录入并由第二人复核确认--> 统一有效票记录
                    \-> 无效 / 退回修订 / 重复材料（只留痕，不计票）
```

`t_voting_delivery_record` 和 `t_voting_ballot_record` 继续只表达已经生效的统一事实。待核对、无效、重复和录入分歧属于纸质渠道适配器，不污染统一有效票语义。

## 2. 领域模型

### 2.1 `PaperVotingDelivery`

保存一次纸质送达登记：

- 表决包、冻结名册项、接收人、送达方式和时间；
- 证据材料编号及摘要、经办人；
- `PENDING_REVIEW / CONFIRMED / REJECTED`；
- 核对人、核对时间和不通过原因；
- 核对通过后生成的统一 `deliveryId`。

同一登记不可覆盖。`CONFIRMED` 后只读。

### 2.2 `PaperBallot`

保存一张回收纸票的原始事实：

- 表决包、专有部分、票号、锁定模板摘要；
- 纸票原件材料编号及摘要、回收时间和经办人；
- `RECEIVED / IN_ENTRY / COMPLETED / VOIDED`。

票号在表决包内唯一，原件材料在表决包内只能登记一次。

### 2.3 `PaperBallotEntry`

保存某一录入人对整张纸票的一版解释。每份录入包含表决包全部事项的 `PaperBallotEntryItem`：

- `VALID` 时必须有 `VoteChoice`；
- `INVALID` 时必须有原因代码，可附说明；
- 录入人、版本和提交时间不可修改；
- `PENDING_REVIEW / CONFIRMED / REJECTED`，以及复核人、复核时间和退回说明。

复核人必须与本版录入人不同。退回后通过新增版本修订，原版本继续只读。

### 2.4 逐事项最终结果

复核人确认一版录入后，系统逐事项形成 `COUNTED / INVALID / DUPLICATE` 之一，并保存对应统一 `ballotId` 或冲突票据编号。复核退回不形成最终结果，也不允许直接修改原录入。

## 3. 持久化

新增后续 Flyway 迁移，不修改 `V3.110`：

- `t_paper_voting_delivery`：送达登记和核对；
- `t_paper_ballot`：回收纸票原件；
- `t_paper_ballot_entry`：不可变录入版本、复核结论和人员；
- `t_paper_ballot_entry_item`：逐事项解释；
- `t_paper_ballot_outcome`：复核确认后的逐事项最终结果。

数据库约束负责：

- 表决包内票号唯一；
- 表决包内原件材料唯一；
- 同一录入版本的录入人和复核人不能相同；
- 每份录入的事项不能重复；
- 最终结果与统一票据一一关联。

材料仍使用现有私有对象存储和 `OwnersAssemblyMaterial` 权限链路，纸质表不保存公开 URL。

## 4. 应用服务与事务边界

新增通用纸质渠道服务 `PaperVotingService`，上层业主大会只负责解析会次、材料和办理权限后调用：

- `registerDelivery`：校验冻结名册、规则送达方式和证据，建立待核对记录；
- `reviewDelivery`：确认或驳回；确认后调用 `VotingExecutionService.recordDelivery`；
- `registerBallot`：校验票号、模板、回收原件和冻结名册，不计票；
- `submitEntry`：保存一版不可变录入；
- `reviewEntry`：由不同人员确认或退回；确认后触发逐事项最终处理；
- `finalizeReadyItems`：逐事项调用统一内核形成有效票，或记录无效/重复结果；
- `getWorkbench`：只向具有复核权限的管理端身份返回业务可读的待办、原始录入和历史记录；业主端只返回本人汇总进度，不返回任何选择。

纸票最终解释与统一有效票写入分两个明确事务阶段：先提交不可变复核结论，再尝试写统一票。若并发唯一约束发现已有有效票，单独事务把该纸票事项标记为重复，避免回滚或覆盖原有效票。

## 5. API 调整

业主大会管理端使用：

- `POST /owners-assemblies/{sessionId}/paper-deliveries`：登记送达；
- `POST /owners-assemblies/{sessionId}/paper-deliveries/{deliveryId}/review`：核对送达；
- `POST /owners-assemblies/{sessionId}/paper-ballots`：登记回收纸票；
- `POST /owners-assemblies/{sessionId}/paper-ballots/{ballotId}/void`：作废尚未录入的错误登记并保留原因；
- `POST /owners-assemblies/{sessionId}/paper-ballots/{ballotId}/entries`：提交一版录入；
- `POST /owners-assemblies/{sessionId}/paper-ballots/{ballotId}/entries/{entryId}/review`：确认或退回录入；
- `GET /owners-assemblies/{sessionId}/paper-workbench`：查询送达和纸票工作台。

旧 `/paper-votes` 即时计票入口删除，不保留前端兜底或双写。

## 6. 权限与隐私

- 写操作继续使用当前投票事项办理权限，并由应用层验证当前租户、当前会次和真实系统用户；
- 录入与复核必须使用不同 `sys_user.user_id`；不以显示名称代替身份；
- 业主侧状态查询按当前账号的房屋关系和冻结名册交集过滤，只返回送达、参与和公开材料状态；
- 具体选择只在受限复核工作台和审计查询中按职责展示。

## 7. 测试策略

- 领域测试覆盖送达状态、不同录入人、逐事项一致/不一致和不可变性；
- 集成测试覆盖材料归属、冻结名册、模板摘要、送达生效时点及旧入口移除；
- 并发测试覆盖纸票复核与线上票同时提交时只产生一张有效票；
- Web 测试覆盖录入人不能自我复核、退回不计票、业主只看本人状态和租户隔离。
