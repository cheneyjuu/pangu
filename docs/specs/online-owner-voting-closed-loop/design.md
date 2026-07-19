# 线上实名投票闭环设计

> 业务关联：把业主本人对锁定材料的阅读确认和整包选择转换为统一线上送达及有效票事实。
>
> 状态：实施中（2026-07-19）
>
> 需求依据：[requirements.md](requirements.md)

## 1. 设计原则

线上渠道采用“锁定材料阅读确认 -> 整包复核确认 -> 统一执行内核”的单向转换：

```text
阅读并确认锁定材料 --本人实名校验--> 统一线上送达记录
整包选择并再次确认 --原子校验与写入--> 统一有效票记录 + 本人回执
申请纸质协助 -----------------------> 纸质送达、回收和双人复核闭环
```

客户端只提交业务选择和当前表决包摘要。账号、自然人、表决代表、确认摘要和提交摘要均由后端基于可信上下文生成。

## 2. 领域模型

### 2.1 `OnlineVotingAcknowledgement`

保存当前自然人针对某个冻结名册项确认已阅读锁定材料的事实：表决包、名册项、账号、自然人、专有部分、表决包摘要、确认摘要和时间。每个表决包与名册项只保留一条有效确认。

### 2.2 `OnlineBallotSubmission`

保存一次不可变的整包在线提交：表决包、名册项、当前身份、幂等键、表决包摘要、选择清单摘要、服务端确认摘要、提交时间和状态。逐事项关联统一有效票编号，但面向业主的投影不返回选择。

### 2.3 `PaperAssistanceRequest`

保存互联网表决中的纸质协助请求。状态为 `REQUESTED / FULFILLED / WITHDRAWN`。纸质送达登记成功后转为已办理并不可撤回；该模型只决定该专有部分可否进入纸质适配器，不替代纸质送达或选票原件。

## 3. 事务和唯一性

- 阅读确认和统一线上送达在一个事务中写入；
- 整包提交先锁定当前名册项，再校验全部事项，随后在一个事务内逐事项调用统一执行内核；
- 数据库继续以“表决事项 + 冻结名册项”的活动票唯一约束兜底；
- 在线提交表以“表决包 + 冻结名册项”唯一，幂等键在当前表决包内唯一；
- 任一事项冲突时整次事务回滚，不留下部分在线票；纸票适配器仍按其原始材料规则保存后到的重复材料。

## 4. 应用服务和接口

`OnlineVotingService` 提供：

- `acknowledge`：校验 C 端活体实名身份和冻结表决代表，写阅读确认及线上送达；
- `submit`：校验阅读确认、表决包摘要、事项全集、明确确认和幂等键，原子写入全部有效票并返回回执；
- `requestPaperAssistance`：为当前专有部分申请纸质协助并关闭线上提交；
- `withdrawPaperAssistance`：只在没有纸质办理事实时撤回；
- `ownerParticipation`：仅返回本人各专有部分的资格、办理方式、进度和回执；
- `managerWorkbench`：仅返回数量、待提供纸票的专有部分和办理状态，不返回在线选择。

C 端接口挂在既有公开表决包资源下：

- `POST /me/owners-assembly-disclosures/{packageId}/online-acknowledgements`；
- `POST /me/owners-assembly-disclosures/{packageId}/online-ballots`，幂等键使用 `Idempotency-Key` 请求头；
- `POST /me/owners-assembly-disclosures/{packageId}/paper-assistance-requests`；
- `POST /me/owners-assembly-disclosures/{packageId}/paper-assistance-requests/{requestId}/withdraw`。

旧通用单事项投票入口发现正式表决包后直接拒绝，并提示从本次业主大会表决页面办理，不再双写。

## 5. 实际方式映射

- `WRITTEN_CONSULTATION + PAPER_ONLY` -> `PAPER`；
- `INTERNET + ONLINE_ONLY` -> `ONLINE_WITH_PAPER_ASSISTANCE`；
- `ONLINE_AND_OFFLINE + PAPER_AND_ONLINE` -> `PAPER_AND_ONLINE`，仅在有效本地规则明确允许时可配置。

三种方式都使用同一表决包、冻结名册、统一送达和有效票台账。线上为主仍锁定纸质票样，不意味着向所有业主并行开放纸质渠道。

## 6. 安全与隐私

- 必须为 `C_USER`、活体实名等级、当前租户和冻结表决代表；
- 后端从 `UserContext` 和冻结名册取得账号、自然人和专有部分；
- 业主端提交后不回显选择，回执只含编号、时间和校验摘要；
- 物业端不读取在线票面，计票仅由统一内核执行；
- 所有拒绝、幂等重试、跨渠道冲突和纸质协助状态变化进入审计。

## 7. 测试策略

- 领域测试覆盖方式映射、纸质协助状态和提交不可变性；
- 集成测试覆盖活体实名、冻结代表、阅读确认、事项全集、表决包摘要和跨渠道唯一；
- 事务测试覆盖某一事项冲突时整包零写入；
- Web 测试覆盖幂等重试、旧单事项入口关闭、业主隐私和租户隔离；
- 真实流程验收至少覆盖一名业主在线提交和另一名业主申请纸质协助后完成纸票复核。
