# 维修工程接入统一表决设计

> 业务关联：设计维修授权提案与统一表决内核之间的防腐层，确保工程真相和投票真相各自唯一。
>
> 状态：实施中（2026-07-19）

## 1. 设计决策

### 1.1 单一规则来源

新维修表决只读取 `OwnersAssemblyRule` 当前 `ACTIVE` 版本。旧 `RepairDecisionRule` 不再作为新流程输入；其端点和表仅用于历史查询，迁移完成后关闭写入。

### 1.2 精确维修决定范围

在通用 `VotingScope` 中新增 `REPAIR_ALLOCATION`，`scopeReferenceId` 固定为 `planId`。该值只用于说明名册来源，不由通用分母解析器按实时楼栋查询。维修适配器从 `t_repair_plan_allocation_room` 的冻结房屋集合读取候选名册，再调用统一表决服务的精确名册冻结入口。

精确冻结入口与社区/楼栋入口共用相同的校验、摘要、持久化、事项发布和审计逻辑，不能复制第二套表决内核。

### 1.3 维修表决关联

新增 `t_repair_project_voting`：

- `project_id + plan_id` 唯一，禁止同一授权提案并行发起两场表决；
- 保存 `subject_id`、`execution_package_id`、规则和配置摘要、实际收集方式；
- 保存准备、投票中、已结算、作废状态及通过结果；
- 保存创建、开始、结算经办人和时间；
- 不复制选票、分母或计票明细。

### 1.4 决定事项类型

当前维修工程模型只覆盖维修实施和既有维修资金的使用，不包含筹集维修资金、改建重建或改变共有部分用途。因此新建维修表决事项固定为 `GENERAL`，并在服务端校验资金与工程类型仍处于本能力边界。任何重大事项迹象都返回“当前流程不支持该类重大共同决定”，不得由前端任意切换阈值。

### 1.5 原子边界

准备表决在一个事务中完成：

1. 锁定维修项目和活动方案；
2. 读取已启用规则并校验本次方式；
3. 读取、核对精确费用承担房屋；
4. 创建维修表决事项和统一表决包；
5. 关联事项并冻结精确名册；
6. 写入维修表决关联和工程事件。

任一步失败时不允许留下孤立事项、表决包、分母或关联。

## 2. 规则到执行的映射

| 本次方式 | 规则会议形式 | 规则渠道策略 | 重复票策略 |
| --- | --- | --- | --- |
| 纸质投票 | `WRITTEN_CONSULTATION` | `PAPER_ONLY` | `NOT_APPLICABLE` |
| 互联网表决并提供纸质协助 | `INTERNET` | `ONLINE_ONLY` | `NOT_APPLICABLE` |
| 纸质与线上并行 | `ONLINE_AND_OFFLINE` | `PAPER_AND_ONLINE` | `FIRST_VALID_WINS` |

计票使用规则中 `DecisionType.GENERAL` 对应的四个精确阈值。`FOLLOW_MAJORITY`、把未反馈计为弃权、纸票优先或线上优先在当前执行内核尚未支持时应阻断，不得降级为未参与。

## 3. 服务划分

### 3.1 `RepairProjectVotingService`

- `prepare`：创建并冻结维修表决；
- `open`：到达开始时间后开始收票；
- `closeAndSettle`：截止并按规则快照结算；
- `details`：返回管理端可读进度，不含个人线上选择；
- `findOwnerTask`：返回业主本人可参与的维修表决投影。

### 3.2 统一能力复用

- 名册、包状态和跨渠道唯一：`VotingExecutionService`；
- 纸票登记、录入、双人复核：`PaperVotingService`；
- 线上阅读确认、实名提交、纸质协助：`OnlineVotingService`；
- 结果快照：`VotingApplicationService` 和 `VotingResultRepository`。

业务适配层只负责权限、维修提案校验和异常翻译，不复制投票规则。

## 4. API 设计

管理端：

- `POST /api/v1/admin/repair-projects/{projectId}/voting/prepare`
- `POST /api/v1/admin/repair-projects/{projectId}/voting/open`
- `POST /api/v1/admin/repair-projects/{projectId}/voting/settle`
- `GET /api/v1/admin/repair-projects/{projectId}/voting`
- 纸票登记、录入、复核使用维修前缀的薄适配端点。

业主端：

- `GET /api/v1/me/repair-projects/voting-tasks`
- `GET /api/v1/me/repair-projects/{projectId}/voting`
- `POST .../acknowledgements`
- `POST .../ballots`
- `POST .../paper-assistance`
- `GET .../receipt`

接口以当前身份和冻结表决人记录做权限判断，不接受客户端传入 `uid`、计票分母、规则摘要或表决结果。

## 5. 数据库与迁移

1. 新增 `V3.114`：扩充范围检查约束、创建维修表决关联表及必要索引。
2. 旧维修表决表不删除；增加禁止新写的应用层守卫，待历史数据迁移报告确认后再做物理清理。
3. 新关联表只保存外键和流程状态，不复制历史选票。
4. 所有外键均带租户查询校验；关键状态更新使用版本或状态条件防并发重复。

## 6. 验证策略

1. 单元测试：方式与规则映射、精确房屋核对、一般事项计票策略。
2. 集成测试：准备事务回滚、范围漂移、规则缺失、跨渠道重复、未到时间、截止结算、通过和未通过回写。
3. 迁移测试：旧表可读、新写受阻、同一项目不得双轨办理。
4. 三端真实流程：创建维修项目、冻结提案、准备表决、纸质/线上/混合收票、结算、确定施工单位和合同入口解锁。
