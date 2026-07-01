# Task Plan: 选举闭环对齐推进

## Goal
根据 `docs/选举闭环对齐路线图.md` 推进当前选举闭环任务：持续完成选举闭环各梯度的后端、管理端、C 端同步与验证。

## Scope
- 当前主仓库：`pangu` 后端。
- 同步仓库：`yaochi` 管理端、`shennong-app` C 端。
- 当前路线图：`docs/选举闭环对齐路线图.md`。
- 梯度 A：权限矩阵对齐，新增 `GOV_OPERATOR`，ELECTION 立项收紧为 G 端基层经办员，公示仍为街道办独占。
- 梯度 B：仅 ELECTION 走 `DRAFT -> PENDING_COMMITTEE -> PENDING_STREET -> PUBLISHED` 双签流程；GENERAL/MAJOR 保持原流程。
- 梯度 E1：ELECTION 立项即冻结分母快照，进度响应透出分母快照 ID 与 Merkle root。
- 梯度 E2a：成功投票写入后记录 Redis Bloom 与增量计数监控基线。
- 梯度 E2b：纸票占比阈值判定、快速连续写票告警与管理端监控查询面。
- 梯度 E3：Waiver / 候选人审查拒绝强制 C1-C5 reason_code + evidence_jsonb。
- 梯度 E4：Clock Suspend，HANDOVER_LOCK 期间暂停非选举议题倒计时并在 NORMAL 恢复后顺延。
- E 后续精细化：显式 `vote_channel` 写入契约，区分 C 端线上票 / 纸票 / 线下代录票。
- E 后续精细化：业主代表 / 楼栋长催票权限事件驱动激活，替代静态权限矩阵。
- E 后续精细化：worker 真实待催票列表接口，替换 shennong-app `listReminderTasks/listPendingOwners/markNotified` 的后端 mock 缺口。
- E 后续精细化：催票 outbox 消费后展开逐户投递明细，替代纯 mock 网关，为短信 / Push provider 接入提供落点。
- E 后续精细化：催票逐户投递明细调度状态机，领取 READY/FAILED 明细并回写 SUBMITTED/CONFIRMED/FAILED。
- E 后续精细化：管理端催票投递明细查询接口，按议题 / 楼栋 / 状态查看逐户投递结果。

## Phases
| # | 内容 | Status |
|---|---|---|
| 1 | 核验当前工作树和路线图状态 | complete |
| 2 | 更新规划文件到当前任务 | complete |
| 3 | 跑 A 阶段完整后端验证 `mvn clean test` | complete |
| 4 | 若验证通过，启动 B 阶段后端最小切片 | complete |
| 5 | 更新路线图/进度并给出下一步验证口径 | complete |
| 6 | 补齐 B 阶段 `review_history` 审批轨迹落库 | complete |
| 7 | 补齐 B 阶段 controller 权限矩阵测试 | complete |
| 8 | 将 ELECTION 立项护栏从 `deptCategory=G` 精确到 `dept_type IN (2,5)` | complete |
| 9 | 预检 yaochi 管理端与 shennong-app C 端同步点 | complete |
| 10 | 同步 yaochi 管理端 B 阶段双签状态与操作 | complete |
| 11 | 同步 shennong-app C 端新增字段/状态类型 | complete |
| 12 | 三仓验证并更新路线图 | complete |
| 13 | 梯度 C-mini：租户任期锁闭环与管理端备案入口 | complete |
| 14 | 梯度 C：维修资金大额支取熔断第一切片 | complete |
| 15 | 梯度 C：公共收益划拨熔断第一切片 | complete |
| 16 | 梯度 C：信托制双签动账熔断第一切片 | complete |
| 17 | 梯度 C：备案通过后老主任密钥回收 mock 钩子 | complete |
| 18 | 梯度 D-mini：后端 SYS_USER 分身列表与切卡 API | complete |
| 19 | 梯度 D-mini：管理端 Topbar 切卡 UI 同步 | complete |
| 20 | D-mini 回归验证与路线图更新 | complete |
| 21 | 梯度 E1：ELECTION 立项分母冻结与 Merkle 进度存证 | complete |
| 22 | 梯度 E1：yaochi / shennong-app 进度类型同步 | complete |
| 23 | E1 回归验证与路线图更新 | complete |
| 24 | 梯度 E2a：投票写入侧 Bloom + 增量计数基线 | complete |
| 25 | E2a 回归验证与路线图更新 | complete |
| 26 | 梯度 E2b：纸票占比阈值判定 + 监控告警查询面 | complete |
| 27 | E2b yaochi API 同步、回归验证与路线图更新 | complete |
| 28 | 梯度 E3：C1-C5 拒绝理由码 + JSONB 证据链后端闭环 | complete |
| 29 | E3 yaochi 契约同步、回归验证与路线图更新 | complete |
| 30 | 梯度 E4：Clock Suspend 后端闭环 | complete |
| 31 | E4 yaochi / shennong-app 类型同步、回归验证与路线图更新 | complete |
| 32 | E 后续：显式 `vote_channel` 后端持久化 / 监控语义 / shennong-app 提交契约 | complete |
| 33 | E 后续：投票期动员权限事件驱动激活第一切片 | complete |
| 34 | E 后续：真实催票发送记录 / 通知 Outbox | complete |
| 35 | E 后续：线下代录写票管理端入口 | complete |
| 36 | E 后续：通知 Outbox 消费器 | complete |
| 37 | E 后续：worker 真实待催票列表接口 | complete |
| 38 | E 后续：催票逐户投递明细 | complete |
| 39 | E 后续：催票投递状态机与 mock provider | complete |
| 40 | E 后续：催票投递明细查询接口 | complete |
| 41 | E 后续：yaochi 表决看板展示催票投递明细 | complete |
| 42 | E 后续：可配置 HTTP 短信 provider 适配器 | complete |
| 43 | E 后续：shennong-app worker 催票真实分支说明与类型检查 | complete |
| 44 | E 后续：yaochi 催票投递明细楼栋 / 状态筛选 | complete |
| 45 | E 后续：yaochi 催票投递记录详情查看 | complete |
| 46 | E 后续：HTTP 短信 provider 通用签名 / 模板参数 / 回执字段映射 | complete |
| 47 | E 后续：shennong-app worker 催票真实接口 smoke 验证入口 | complete |
| 48 | E 后续：shennong-app worker 催票只读 live smoke | complete |
| 49 | E 后续：shennong-app worker 催票 pending/notify live smoke | complete |
| 50 | E 后续：HTTP provider 本地 smoke harness | complete |
| 51 | E 后续：HTTP provider dispatch 组合回归测试 | complete |
| 52 | E 后续：HTTP provider live smoke 编排脚本 | complete |
| 53 | E 后续：HTTP provider scheduler live smoke | complete |
| 54 | E 后续：HTTP provider 真实供应商参数化联调入口 | complete |
| 55 | E 后续：HTTP provider 联调前配置校验与自定义签名 header smoke | complete |
| 56 | E 后续：HTTP provider smoke 脚本自说明 help | complete |
| 57 | E 后续：HTTP provider dry-run 与外部供应商默认值语义修正 | complete |
| 58 | E 后续：HTTP provider 数字型回执 ID 兼容 | complete |
| 59 | E 后续：HTTP provider smoke 可调超时参数 | complete |
| 60 | E 后续：HTTP provider 业务成功码校验 | complete |
| 61 | E 后续：HTTP provider 业务失败码投递状态机覆盖 | complete |
| 62 | E 后续：HTTP provider 默认嵌套回执字段兼容 | complete |
| 63 | E 后续：短信当前验收策略切回 MOCK 优先 | complete |
| 64 | E 后续：MOCK provider 投递状态机组合测试 | complete |
| 65 | E 后续：MOCK provider 数据库集成复验 | complete |
| 66 | E 后续：MOCK 催票闭环手工验收复现 | complete |
| 67 | E 后续：yaochi 表决看板补发起催票入口 | complete |
| 68 | E 后续：shennong-app 催票工作台默认走真实接口 | complete |
| 69 | C 后续：信托制分期付款前置序号守卫第一切片 | complete |
| 70 | B/G 权限后续：候选人提名收紧为 G 端基层经办员 | complete |
| 71 | B/G 权限后续：candidate:nominate 权限矩阵清理与 yaochi 可见性同步 | complete |
| 72 | yaochi 表决看板 ELECTION 直公示入口收口 | complete |
| 73 | B/G 权限后续：ELECTION 立项/提交初审专属权限拆分 | complete |
| 74 | B/G 权限后续：ELECTION 双签角色分离兜底 | complete |
| 75 | B/G 权限后续：Waiver 双签角色分离兜底 | complete |

## Key Decisions
| 决策 | 理由 |
|---|---|
| 先完成 A 后端验证，再进入 B | 路线图要求按梯度独立推进；A 当前已有较多改动，必须先确认全量回归 |
| B 阶段先做后端最小切片 | 当前工作区只有 pangu，可在后端先落 Flyway/domain/application/test，再交给前端跟进 |
| ELECTION 双签不引入 APPROVED 中间态 | 路线图 2026-06-27 决策：街道办终审通过即进入 PUBLISHED，降低现有状态机破坏面 |
| 候选人审查与 Waiver 保持独立 | 路线图已决策：候选人是个体状态机，议题双签是议题级状态机 |
| C-mini 不直接补 sys_tenant | 当前迁移不存在稳定 `sys_tenant` 表，先用 `t_tenant_term_state` 承载租户任期状态，避免牵连租户建模 |
| C-mini 保留查询派生兜底 | 持久锁优先，缺失时继续按在途 ELECTION 查询派生，兼容旧测试与直接 SQL 场景 |
| C 资金侧先补维修资金真实写路径 | 当前仓库没有 `FundExpenseService / TrustFundService / MaintenanceFundService`，但已有维修资金账户/流水表；先补最小动账服务让 HANDOVER_LOCK 覆盖真实写入 |
| C 公共收益划拨复用维修资金账户/流水模型 | V2.2 已定义 `t_fund_ledger_entry.business_type=3` 为公共收益划拨，可先落“入账到维修资金账户”的最小写路径 |
| C 信托制动账复用 GovernanceLock 双签 | 当前没有独立 TrustFundService；先用 `TRUST_FUND_PAYMENT` 治理锁证明业委会主任 + 街道办双签完成，再允许信托付款出账 |
| C 老主任密钥先做 mock 端口 | 当前没有真实密钥/证书系统；以 `CommitteeKeyRevocationGateway` 端口承载副作用，infrastructure 先日志 mock，后续可替换真实实现 |
| D-mini 先做 SYS_USER 内部分身切换 | 当前 JWT 已有 `account_id + identityType + activeIdentityId` 三元组；本轮不扩大到登录全屏选分身或 C_USER/SYS_USER 跨大类切换 UI，降低前端影响面 |
| 分身切换必须校验 account_id 归属 | `UserContextLoader` 装配查询已收紧到 `account_id + activeIdentityId`，防止伪造 token 只换 `activeIdentityId` 越权加载其他账号分身 |
| E1 复用 V2.3 分母快照表，不新增 V3.11 字段 | `t_voting_denominator_snapshot.aggregate_hash` 已是行级分母 Merkle root；本轮先把冻结时点前移到 ELECTION 立项并暴露证据字段 |
| E1 只对 ELECTION 立项强制冻结分母 | GENERAL/MAJOR 保持既有行为，避免要求所有非选举议题立项前都已有产权清洗数据 |
| E2a 以端口承载监控副作用 | 投票主链路保持业务正确性优先；Redis/Bloom 写失败只记录 warn，不影响投票结果 |
| E2a 当时暂以无签名票作为纸票/线下票候选基线 | E2a 时 `t_vote_item` 与 `CastVoteRequest` 尚无明确 vote_channel 字段；该临时口径已由后续 `vote_channel` 替换 |
| E2b 先做查询侧阈值判定，不新增表结构 | 当前 Redis 计数已能支持管理端告警查询；避免为第一版监控面引入持久告警表；E4 已使用 V3.12/V3.13，后续审计留痕迁移从 V3.14+ 扩展 |
| E2b 当时继续用 `signatureHash` 为空推断纸票候选 | 显式 `vote_channel` 已在后续精细化完成，当前监控纸票口径来自 `PAPER / OFFLINE_PROXY` |
| E3 拒绝证据以非空 JSON object 入库 | 后端接口接收 `rejectEvidence`，应用层要求拒绝时必须有 C1-C5 理由码与非空 JSON 对象；数据库以 JSONB 保存证据链 |
| E3 同时覆盖 Waiver 与候选人审查 | Waiver、党组审查、居委会审查都属于 G 端拒绝路径，必须统一 reason_code / evidence_jsonb 契约 |
| E3 只同步 yaochi，不改 shennong-app | C 端只读业主可见投票流程，不执行管理端拒绝动作，本轮没有新增 C 端可见字段或页面行为 |
| E4 暂停非选举 PUBLISHED/VOTING 议题 | HANDOVER_LOCK 由 ELECTION 结算触发，暂停对象应是被换届影响的 GENERAL/MAJOR 议题倒计时，不暂停触发锁的选举本身 |
| E4 用顺延 `vote_start_at/vote_end_at` 实现断点续传 | 现有开票/截止 scheduler 都基于这两个字段扫描；恢复时按 `now - clock_suspended_at` 顺延能复用既有状态机和前端时间展示 |
| E4 暂停状态透出但不做新页面 | `clockSuspendedAt / clockSuspendedBySubjectId` 进入管理端和 C 端类型，页面可后续优化；本轮先保证契约兼容与调度正确 |
| `vote_channel` 缺省为 ONLINE | 兼容旧客户端与旧构造器，同时停止把 C 端无签名线上票误判为纸票/线下票 |
| 继续沿用 `unsignedCount` 响应字段与 Redis key | 避免管理端监控接口连锁改名；字段语义从“无签名票”升级为“显式 PAPER/OFFLINE_PROXY 票” |
| shennong-app 正常投票固定传 `ONLINE` | C 端线上票不等价于签名票；GENERAL/ELECTION 可能无签名，但仍应计为线上票 |
| 催票权限先做事件驱动授权闭环 | 先在开票/结算/撤回事件中生成/失效楼栋级 `canRemind/canOfflineProxy`，比直接做通知发送更贴合“替代静态矩阵”的底层目标 |
| 动员权限覆盖 OWNER_REPRESENTATIVE 与 GRID_OPERATOR | 设计稿写“业主代表/楼栋长”，现有 seed 与责任田模型同时把网格员用于网格内催票/线下核销，第一切片一并支持 |
| 催票发送先按楼栋落业务记录 + Outbox | 现有 `shennong-app` worker 页是逐户标记 mock，本轮后端先交付授权楼栋级催票请求，真实短信/Push 消费器后续接 `VOTING_REMINDER_REQUESTED` |
| 线下代录复用统一写票主链路 | 管理端只负责校验动态 `canOfflineProxy` 并从 opid 反查 uid/building；实际写票仍走 `VoteSubmissionService`，保留重复投票、scope、候选人、监控计数规则 |
| MAJOR 线下代录不复用 C 端 L3 人脸闸门 | 线上票继续要求 L3；`PAPER/OFFLINE_PROXY` 属于线下凭证路径，固定显式通道并纳入纸票/线下监控口径 |
| 通知 Outbox 消费器默认展开逐户投递明细 | 外部短信/Push 供应商参数未在仓库内定义；先把 outbox 事件展开为 `t_voting_reminder_delivery` 明细，真实 provider 后续从 READY 明细接续 |
| worker 待催票列表按动态 `canRemind` 授权过滤 | 列表、待通知业主与标记已通知都必须绑定投票期 ACTIVE 权限，避免把静态角色权限误当长期催票权 |
| worker 逐户标记通知单独落表 | `t_voting_mobilization_reminder` 表达一次楼栋级催票请求；逐户“电话/上门/微信已通知”需要 `t_voting_mobilization_owner_notice` 独立记录渠道、备注与通知人 |
| shennong-app mock 分支保留 | `USE_MOCK=true` 仍服务本地演示；`USE_MOCK=false` 已有真实 API 契约，worker 催票页文案已同步为 mock/真实双分支说明 |
| 保留显式 mock delivery mode | `platform.voting.reminder-delivery-mode=mock` 可继续用于不落逐户明细的轻量测试；默认 `database` 更贴近真实通知链路 |
| 先用 mock 短信 provider 驱动真实状态机 | 外部短信供应商参数未定义，但 READY -> SUBMITTED -> CONFIRMED/FAILED、attempts、provider_message_id、last_error 等状态字段可以先闭环 |
| 管理端先暴露投递明细只读查询 | 催票投递状态已经由后端状态机维护，本轮不新增写契约；接口沿用 `voting:subject:audit` 并在应用层校验 subject tenant，供 yaochi 后续接详情页 |
| 真实短信接入先做通用 HTTP adapter | 具体供应商未定时不硬编码厂商 SDK；以可配置 HTTP endpoint + bearer token 承载真实网关接入，并继续复用现有投递状态机 |
| 真实短信接入签名保持通用配置 | 供应商未定时先支持 HMAC-SHA256 header、模板 code / templateParams、回执字段点路径映射；拿到具体供应商参数后只需配置或小范围适配 |
| shennong 真实联调先脚本化 | 真机联调需要工作端登录 token 和运行中的 pangu；先提供只读默认、显式写入的 smoke 脚本，避免每次手工点小程序排查接口契约 |

## Verification Checklist
- [x] `mvn clean test` 后端全绿。
- [x] A 阶段核心单测保持通过：`ProposalLifecycleServiceTest` / `ProposalHandoverGuardTest` / `ElectionProposeAndRouterTest`。
- [x] B 阶段新增状态枚举、迁移、服务动作有对应测试。
- [x] 现有 GENERAL/MAJOR `DRAFT -> PUBLISHED` 流程不回归。
- [x] ELECTION 直接 publish 行为按 B 规则收紧。
- [x] yaochi 管理端构建通过。
- [x] shennong-app C 端类型检查通过。
- [x] D-mini 分身切卡后端矩阵与责任田联动测试通过。
- [x] E1 分母冻结聚焦测试、E2E、后端全量、yaochi 构建、shennong-app 类型检查通过。
- [x] E2a 投票监控基线聚焦测试与后端全量通过。
- [x] E2b 监控查询聚焦测试、后端全量与 yaochi 构建通过。
- [x] E3 拒绝理由码/证据链聚焦测试、后端全量与 yaochi 构建通过。
- [x] E4 Clock Suspend 聚焦测试、后端全量、yaochi 构建与 shennong-app 类型检查通过。
- [x] `vote_channel` 后端聚焦测试通过。
- [x] `vote_channel` shennong-app 类型检查通过。
- [x] 动员权限事件驱动后端聚焦测试通过。
- [x] 动员权限 yaochi API 同步构建通过。
- [x] 催票发送记录 / 通知 Outbox 后端聚焦测试通过。
- [x] 催票发送 API 已同步 yaochi 与 shennong-app 契约并验证。
- [x] 线下代录写票管理端入口后端聚焦测试通过。
- [x] 线下代录 API 已同步 yaochi 与 shennong-app 契约并验证。
- [x] 通知 Outbox 消费器单测与真实 DB 领取/回写集成测试通过。
- [x] worker 待催票任务 / 待通知业主 / 标记已通知后端聚焦测试通过。
- [x] 催票 outbox 默认 database delivery 逐户展开集成测试通过。
- [x] 催票投递状态机单测、DB 集成和聚焦回归通过。
- [x] 催票投递明细查询接口权限矩阵、DB 查询与通知链路聚焦回归通过。
- [x] yaochi 表决看板已接入催票投递明细查询，`npm run build` 通过。
- [x] HTTP 短信 provider 单测与通知链路聚焦回归通过。
- [x] shennong-app worker 催票页 mock/真实双分支文案已更新，`npm run type-check` 通过。
- [x] yaochi 催票投递明细已支持楼栋 / 状态筛选，`npm run build` 通过。
- [x] yaochi 催票投递记录已支持详情查看，`npm run build` 通过。
- [x] HTTP 短信 provider 已支持 HMAC 签名、模板参数与可配置回执字段映射，通知链路聚焦回归通过。
- [x] shennong-app 已新增 worker 催票真实接口 smoke 脚本，`npm run smoke:reminder -- --help` 与 `npm run type-check` 通过。
- [x] shennong-app worker 催票只读 live smoke 已对当前 pangu 后端执行，`GET /reminder/tasks` 返回 200 且 count=0。
- [x] pangu 已新增本地 reminder smoke fixture 准备/清理 SQL。
- [x] shennong-app worker 催票 pending/notify live smoke 已通过，DB 已验证通知记录落表。
- [x] pangu 已新增 HTTP provider 本地 fake SMS server 与 READY delivery 准备/清理 SQL。
- [x] HTTP provider fake SMS 脚本语法检查通过，fixture 准备/清理 SQL 已执行验证。
- [x] HTTP provider 已补 `dispatchPending -> HttpVotingReminderSmsProvider -> markConfirmed` 组合回归测试。
- [x] HTTP provider live smoke 已新增一键编排脚本，静态检查通过。
- [x] HTTP provider scheduler live smoke 已通过，DB 回写 `delivery_status=3 / provider_message_id=fake-sms-990481 / attempts=1`。
- [x] HTTP provider live smoke 脚本已支持外部真实供应商 endpoint/token/signature/template/回执字段参数化。
- [x] HTTP provider live smoke 脚本已支持联调前脱敏配置摘要、布尔参数校验，并验证自定义签名 header 端到端可用。
- [x] HTTP provider live smoke 脚本已支持 `--help`，列出本地 smoke、外部供应商联调命令和所有关键环境变量。
- [x] HTTP provider live smoke 脚本已支持 `DRY_RUN=true`，且外部供应商模式不再默认注入本地 fake token/secret；`EXPECTED_PROVIDER_MESSAGE_ID=` 可按“任意非空回执”校验。
- [x] HTTP provider 已支持文本型与数字型 provider message id 回执，嵌套字段同样可解析数字。
- [x] HTTP provider live smoke 脚本已支持 `SMS_PROVIDER_TIMEOUT_MILLIS`，dry-run 可预检正整数超时并把值注入 Spring 配置。
- [x] HTTP provider 已支持可配置业务成功码校验，字段和值必须成对配置，避免 HTTP 2xx 但业务失败的短信响应被误记为 CONFIRMED。
- [x] HTTP provider dispatch 组合测试已覆盖 HTTP 200 + 业务失败码会 markFailed 且不 markConfirmed。
- [x] HTTP provider 默认回执字段已覆盖 `data.messageId / data.smsId / data.bizId / data.requestId` 等常见嵌套字段。
- [x] MOCK provider dispatch 组合测试已覆盖 `MockVotingReminderSmsProvider -> markConfirmed(mock-sms-{deliveryId})`。
- [x] MOCK provider 数据库集成测试已覆盖 READY delivery 领取、确认回写、`provider_message_id=mock-sms-*` 与查询回显。
- [x] shennong-app 开发配置已将催票工作台从全局 mock 中拆出，`USE_REMINDER_MOCK=false` 默认走真实 pangu 接口，`npm run type-check` 通过。
- [x] 信托制分期付款已补前置期确认 guard；第 N 期必须等前一期双签解锁且信托出账流水已写入，`MaintenanceFundHandoverGuardTest` 10/0F/0E。
- [x] ELECTION 候选人提名已在 service 层收紧为 `GOV_OPERATOR + dept_type IN (2,5)`；旧 `candidate:nominate` 授权角色无法绕过，候选人聚焦测试 36/0F/0E。
- [x] `V3.20__candidate_nominate_role_cleanup.sql` 已回收旧角色 `candidate:nominate`，只保留 GOV_OPERATOR；yaochi 提名按钮已按 `GOV_OPERATOR + dept_type IN (2,5)` 显示，后端 36/0F/0E、yaochi build 通过。
- [x] yaochi 表决看板已隐藏 ELECTION 的直接「公示」按钮，避免旧 `voting:subject:publish` 通用权限在看板暴露选举直公示入口；yaochi build 通过。
- [x] `V3.21__election_subject_create_permission.sql` 已新增 `voting:subject:create:election` 并只授 GOV_OPERATOR；ELECTION 立项和提交初审已在 controller 预授权层改用专属权限，后端矩阵 54/0F/0E、yaochi build 通过。
- [x] `V3.22__election_dual_review_role_separation.sql` 已回收街道办 `voting:subject:review:committee`，并在 `ProposalReviewService` 补居委会初审 / 街道终审角色兜底；聚焦测试 50/0F/0E。
- [x] `V3.23__waiver_dual_review_role_separation.sql` 已回收街道办 `waiver:approve:committee`，并在 `WaiverApplicationService` 补居委会初审 / 街道终审角色兜底；聚焦测试 11/0F/0E。
- [x] `V3.24__governance_lock_street_role_separation.sql` 已回收居委会 `lock:unlock:street`，并在 `GovernanceLockApplicationService` 补业委会主任初签 / 街道终签角色兜底；聚焦测试 25/0F/0E。
- [x] 资金公示动作分工已补 service 层角色兜底：compose 仅业委会主任/居委会，publish 仅业委会主任，compare 审计仅街道办/居委会；V2.7 权限数据无需新迁移，聚焦测试 22/0F/0E。
- [x] `V3.25__remove_legacy_fund_disclosure_publish_permission.sql` 已清理旧 `fund:disclosure:publish` 角色授权与权限目录项；披露聚焦测试 23/0F/0E。
- [x] `V3.26__candidate_review_role_separation.sql` 已回收街道办候选人两段审查授权；`ElectionCandidateService` 补 PARTY_SECRETARY 前置审查 / COMMUNITY_ADMIN 资格审查角色兜底；候选人聚焦测试 40/0F/0E，yaochi build 通过。
- [x] `V3.27__election_publish_requires_street_review.sql` 已回收街道办旧通用 `voting:subject:publish`；`ProposalLifecycleService.publish` 直接拒绝 ELECTION，选举发布只能走 `street-review` 并写 `review_history`；聚焦测试 84/0F/0E。
- [x] 路线图剩余差距口径已校准：多分身 D-mini 标为已完成；Waiver / 候选人审查明确按独立状态机实施；ELECTION 立项 / 发布表格改为专属权限与 service 兜底口径；分身/登录聚焦测试 13/0F/0E。
- [x] `V3.28__fund_ledger_chain_attestation.sql` 已给资金流水补链上存证字段；信托制第 N 期付款必须等前一期信托出账流水 `chain_attest_status=3` 且 `blockchain_tx_hash` 非空，资金/锁聚焦测试 19/0F/0E。

## Error Log
| Error | Attempt | Resolution |
|---|---|---|
| `~/.codex/superpowers/.codex/superpowers-codex bootstrap` 不存在 | 初始仓库指令尝试执行 | 记录为环境缺失，继续按仓库和路线图执行 |
| `mvn -pl pangu-bootstrap -Dtest=... test` 运行时拿到旧上游字节码 | 聚焦测试第 1 次 | 改用 `-am` 并加 `-Dsurefire.failIfNoSpecifiedTests=false` 后通过 |
| `mvn clean test` 大量 SpringBootTest/Mockito errors | 沙箱内全量测试第 1 次 | PostgreSQL 连接失败 + ByteBuddy self-attach 被环境限制；改用提升权限重跑 |
| `mvn clean test` 2 个 handover E2E 失败 | 提升权限全量测试第 1 次 | 共享 seed 在途 ELECTION 污染测试租户；E2E 用例临时 suppress 非本测试在途选举并在 afterEach 恢复，随后全量 404/0F/0E |
| `VotingLifecycleTriggerTest.chkSubjectStatus_outOfRangeRejected` 失败 | B 后端切片后全量测试 | B 新增状态 7/8 合法，测试改用 status=9 验证约束仍拒绝越界值；随后全量 414/0F/0E |
| `ElectionProposeAndRouterTest` 编译失败缺 `Map` import | C-mini 聚焦测试第 1 次 | 补 `java.util.Map` import 后聚焦测试 30/0F/0E |
| `mvn clean test` 中 `ElectionWorkflowEndToEndTest` 清理被 `t_tenant_term_state` FK 阻挡，随后污染 `DataScopeTest` | C-mini 全量测试第 1 次 | 在 E2E 清理中先删除指向测试选举的任期状态，再删议题；聚焦链路与全量随后通过 |
| D-mini seed 新网格分身占用 30001/30002，导致责任田同角色互斥测试失败 | D-mini 全量测试第 1 次 | 追加 V3.10 将 800006 楼栋占用迁到隔离楼栋 39999，并把责任田冲突测试改为覆盖第二个网格员真实冲突路径；随后聚焦与全量通过 |
| `ElectionWorkflowEndToEndTest` 立项从 201 变 500 | E1 全量测试第 1 次 | 新规则要求 ELECTION 立项即冻结分母；将该 E2E 的业主/房产 seed 前移到立项前，并断言快照 total/root，随后聚焦与全量通过 |
| E4 全量回归中 `ElectionWorkflowEndToEndTest` 清理被 `clock_suspended_by_subject_id` 自引用 FK 阻挡，并污染 `DataScopeTest` | E4 全量测试第 1 次 | 新增 V3.13 将自引用 FK 改为 `ON DELETE SET NULL`，并在相关测试清理中先清空暂停引用；聚焦链路和全量随后通过 |
| `npm run type-check` 自动审批失败 | `vote_channel` shennong-app 验证 | Codex 使用限额触发自动拒绝；已在 Phase 33/29 后续验证中补跑通过 |
| `npm run build` 写入 `.vite-temp` 被 EPERM 拦截 | 催票 Outbox yaochi 验证 | yaochi 在当前 pangu 写根之外；用授权模式重跑同一命令后构建通过 |
| shennong-app 注释补丁与 `npm run type-check` 被审批层拒绝 | worker 真实待催票列表接口同步 | 后续已补充 worker 催票页 mock/真实双分支说明，并通过 `npm run type-check` |
| `GET /reminder/tasks` 在 8080 返回 500 | shennong live smoke 第 1 次 | 8080 是 2026-06-28 由 IntelliJ 启动的旧 JVM；改在 18080 启动当前代码后只读 smoke 通过 |
| `GET /reminder/tasks` 返回空列表 | shennong live smoke 第 2 次 | 当前本地库没有活跃 VOTING + ACTIVE `canRemind` 催票任务；pending/notify 分支按 smoke 设计跳过 |
| HTTP provider live smoke 启动 pangu 提权被拒 | provider live smoke 第 1 次 | Codex 使用限额触发自动审批拒绝；已停止 fake SMS 服务并清理 990481 fixture，保留可重复 harness，待额度恢复或用户显式允许后执行 |
| HTTP provider live smoke 启动失败：`No default constructor found` | provider live smoke 第 2 次 | `HttpVotingReminderSmsProvider` 同时存在生产构造器和测试构造器，Spring 未能选择生产构造器；给生产构造器补 `@Autowired` 后定向测试与 live smoke 通过 |

## Next Tasks
- shennong-app：开发环境保留全局 `USE_MOCK=true`，但催票工作台已独立 `USE_REMINDER_MOCK=false` 默认走真实接口；本地 pending/notify live smoke 已跑通，后续如需复验，先执行 `scripts/prepare-reminder-smoke.sql`，完成后可用 `scripts/cleanup-reminder-smoke.sql` 清理 fixture。
- yaochi：投递明细已接入表决看板，支持楼栋 / 状态筛选与单条详情查看；表决中议题已补「发起催票」入口，可直接调用 `mobilization-reminders` 生成 outbox。
- 短信当前策略：先按 MOCK 验收；`application.yml` 默认 `platform.voting.sms-provider-mode=mock`，当前闭环不再被真实短信供应商参数阻塞。
- MOCK 复验：`VotingReminderOutboxRepositoryIntegrationTest` 已验证真实 Spring + PostgreSQL/Redis 测试环境下的 `READY -> CONFIRMED / mock-sms-*`；2026-07-01 又用真实 HTTP 手工链路复现 `990480 -> outbox 990491 -> delivery 990488 -> mock-sms-990488`。
- 真实供应商：作为后续可选项保留；拿到参数后再运行 `DRY_RUN=true START_FAKE_SMS=false ... scripts/smoke-http-sms-provider.sh` 做真实网关联调。
- V3.28 已用于资金流水链上存证字段与信托分期链上确认 guard；后续迁移从 V3.29+ 开始。
- `voting:subject:create` 仍保留给 GENERAL/MAJOR 通用立项；ELECTION 立项 / 提交初审已切到 `voting:subject:create:election`。`voting:subject:publish` 仍保留给 GENERAL/MAJOR 日常公示，ELECTION 发布只能走 `voting:subject:review:street`。
