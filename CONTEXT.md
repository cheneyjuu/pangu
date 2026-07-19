# Pangu Context

社区治理 / 业主自治 SaaS。租户 = 单个小区。多角色三端：C 端业主小程序、B 端物业 + 业委会管理后台、G 端街道办 / 居委会 / 党建监管后台。

## Language

### 治理主体

**Tenant**:
单个小区，系统的多租户隔离单位。`tenant_id` 是行级数据隔离的最高维度。
_Avoid_: 社区、组织（这两个在 PRD 里另有所指）

**业委会 (Owners' Committee)**:
业主大会选举产生的常设代表机构。任期通常 3 年，期满或纠纷触发换届。
_Avoid_: 业主自治组织、Committee（用全称避免歧义）

**业主大会 (Owners' Assembly)**:
全体业主参加的法定决议机构。**不是业委会的上级，而是法人意义上的最高议事机关**。模式切换、大额支出等"重大决议"必须经业主大会双过半表决（专有面积 ≥ 2/3 AND 人数 ≥ 2/3）。
_Avoid_: 全体业主大会、Assembly

**街道办 / 居委会 (G-end)**:
法律上的"指导、监督"角色，**不替代**业主大会决策。在系统里负责实名备案、合规审查、模式切换的执行性落库。
_Avoid_: 政府、监管方、上级（暗示行政上下级关系，与法律定位矛盾）

### 物业管理模式（management_mode）

**LUMP_SUM（包干制）**:
物业公司一次性收取物业费，自负盈亏。物业费内账对业主**完全不可见**。系统隐藏物业费内部行政开支、外包合同明细等私企商业机密菜单。

**FUND_RAISING（筹金制）**:
业主出资设立小区运营账户，物业经理代为日常支付。每笔开支单需业委会主任**事后初审**才核销资金。第三方审计师有"全小区只读"权限做年度离任审计。

**TRUST（信托制）**:
物业公司彻底降级为"职业经理人"，资金存入信托账户。每笔支出**双密码会师**（物业经理密码 A + 业委会主任密码 B），双签 hash 通过 Outbox 同步上司法链产生 TxHash。C 端业主能看到秒级实时流水（含发票切片原图）。
_Avoid_: 委托制、托管制（信托制是法律意义上的 trust，含特定法律效力）

### 状态机

**HANDOVER_LOCK**:
**任期状态机**的"换届锁定"态。由换届触发或纠纷举报激活，由新业委会 G 端实名备案完成自动解除。**只锁三类资金接口**：公共收益大额划拨、信托资金核销、维修资金大额支取（阈值由 `tenant.large_amount_threshold` 决定）。**不锁** voting / disclosure / 筹金制日常小额开支单。落地为 `tenant.tenure_status SMALLINT` 列 + application 层 `@TenureGuard` 注解。
_Avoid_: 换届冻结、突击锁（前者太宽、后者太窄）

**模式切换 (MANAGEMENT_MODE_CHANGE)**:
独立于任期状态机的正交流程。业委会发起 `VotingSubject(scope=COMMUNITY, subjectType=MANAGEMENT_MODE_CHANGE)` → `MajorDecisionEngine` 双过半 → 通过后落 `t_management_mode_change_proposal(status=APPROVED)` → G 端街道办在 admin 后台**执行性**落库（permission `tenant:mode:execute`）。G 端**只能**因合同手续未齐 / 备案失效 / 议案 hash 不一致**三种合规理由**拒绝，**不能**做政治审查。
_Avoid_: 模式审批、模式变更（前者暗示 G 端有否决权，后者过弱）

**议案有效期 (Proposal Expiry)**:
模式切换议案 `t_management_mode_change_proposal` 的 `expires_at = approved_at + 90 days` 刚性默认。状态机 `APPROVED → EXECUTED` 主路径；`now() ≥ expires_at AND status = APPROVED` → `EXPIRED_BY_TIMEOUT` 由 `ModeChangeProposalExpiryScheduler` 每日 00:30 扫描批量推进。**90 日 = 业委会换届 45-90 日常态生命周期 + 上海/北京《业主大会议事规则示范文本》"决议自通过之日起 90 日内执行"立法惯例**。**HANDOVER_LOCK 一次性 30 日续期**：议案在 `APPROVED` 期内首次撞上 HANDOVER_LOCK 时 `expires_at += 30 days`，落 `proposal.lock_extended_at`，**最多续一次**——承认换届不可抗力但不无限续。**前置完成态校验失败的拒绝不重置 expires_at**：拒一次少一次机会，倒逼 G 端别拖延。**业主侧预警**：`expires_at - 14 days` 给业主大会"X 议案将于 14 天后过期"通知；`EXPIRED_BY_TIMEOUT` 时给业委会主任 + G 端"X 议案因 90 日未落库已过期"事故通知。
_Avoid_: 议案过期、决议时效（前者太弱，后者技术词不到位）

### 换届与议案封存

**大额阈值双轨机制 (Large Amount Threshold Two-Track)**:
`tenant.large_amount_threshold_mode IN ('AUTO_RATIO','FIXED')` 双轨：
- **默认 AUTO_RATIO**：`large_threshold = MAX(account.total_balance × 0.05, 50000.00)`，自动适配小区规模（10 万账户 → 5 万阈值；1000 万账户 → 50 万阈值）；
- **FIXED**：业主大会双过半决议**永久切**为固定值（如 8 万），落 `tenant.large_amount_threshold_fixed_value` + `override_proposal_id BIGINT`（强制关联议案 hash，**禁止 G 端管理员后台直接改阈值**——必须有业主大会决议）。

5% 比例 + 5 万 floor 是平台默认值，落 `application.yml` 的 `platform.handover-lock.{default-threshold-ratio: 0.05, default-threshold-floor: 50000}`，全平台统一。绝大多数小区用 AUTO_RATIO 不折腾，特殊小区按需切固定值。
_Avoid_: 大额阈值（语义太弱，没体现"双轨 + 业主大会决议门槛"）

**换届议案分阶段封存 (Handover Subject Staged Hold)**:
HANDOVER_LOCK 触发时按议案三阶段差异化处理（与 [乘船到岸原则] 对齐——议案归 `created tenure` 民意场域，执行归 `current tenure` 资金通路）：
- **REGISTERED（民意未形成）→ `HANDOVER_HOLD_REGISTERED`** + `frozen` 释放回 available。新业委会备案后由新业委会决定是否重新登记，**旧议案不自动复活**（没民意基础不应留议程给新业委会）；
- **IN_VOTE（民意形成中）→ 不打断**，表决正常走完。打断 = 侵犯业主已开始的意志表达。表决通过 → 进入下一阶段处理；表决否决 → `REJECTED` 释放 frozen；
- **APPROVED_PENDING_EXEC（民意已成决议）→ 按金额二分**：
  - 小额 (`< large_amount_threshold`) **正常执行**——小修小补不能停；
  - 大额 (`≥ large_amount_threshold`) **`HANDOVER_HOLD_LARGE`**——`frozen` 不释放，议案锁到新业委会备案后**自动激活**继续执行（业主大会决议在新业委会无权撤销，除非新业主大会重投否决）。

落 `t_voting_subject.handover_hold_status SMALLINT` (0=NOT_HELD / 1=HOLD_REGISTERED / 2=HOLD_LARGE) + `handover_lock_id BIGINT` + `held_at` / `released_at` / `release_reason` 四列。
_Avoid_: 换届冻结议案（语义太宽，没区分三阶段差异化）

**HANDOVER_LOCK 期间登记策略 (Handover Registration Policy)**:
HANDOVER_LOCK 期间议案登记不全禁——**小额可登记，大额拒**：
- `subject.budget < large_amount_threshold` → 正常 [议案登记预校验] + [议案登记预冻结] 流程，表决/执行不受换届影响；
- `subject.budget ≥ large_amount_threshold` → 直接抛 `SUBJECT_REJECTED_BY_HANDOVER_LOCK`，议案进不了表决。

**法理**：(1) 小区日常运转不被阻塞（30 户小区一周可能十来个 < 5 万的小额议案）；(2) 大额议案被堵死才是 HANDOVER_LOCK 的本意（堵"突击花钱"漏洞）；(3) 老业委会想卸任前推大额议案？登记瞬间即拒；(4) 业主有真实大额刚性需求？走 [GOV_EMERGENCY_TAKEOVER] 兜底，不需要登记新议案。
_Avoid_: 换届期完全禁登记、大额白名单（前者过严阻塞日常，后者口子开了就是突击花钱漏洞）

### 财务与公示

**财务公示快照 (FinanceDisclosureSnapshot)**:
"瞬时快照 + 不可篡改"语义。M2-3 落地，按 `(tenant, type, period, statistics_version)` 唯一。状态机 DRAFT(1) → LOCKED(2) → PUBLISHED(3) → REVISING(4) → DRAFT(1)。**仅 PUBLISHED 对业主可读**。
_Avoid_: 财务报告、公示报表

**W / R / N 差分**:
两个快照间的字段级比对结果。**W (Write)**: 当期写入或修改；**R (Read)**: path 仅在 prev 中（当期未写入但引用历史值）；**N (No-change)**: 两期完全一致。
_Avoid_: 增删改、diff（W/R/N 是 PRD 内的固定术语）

### 投票与计票

**dual 2/3 quorum (双过半)**:
专有面积 ≥ 2/3 AND 人数 ≥ 2/3。`AbstractVotingEngine.settle()` 在 `final` 模板方法里强制校验，子类引擎不能绕过。
_Avoid_: 双 2/3、双门槛

**VotingScope**:
`COMMUNITY` / `BUILDING` / `UNIT` 三档。决定计票"分母"——`BUILDING` 议案只取该楼栋的总面积/总户数为分母，"局部共有，局部表决，局部分摊"。
_Avoid_: 投票范围（中文歧义大）

### 维修资金与议案绑定

**决定范围不等于资金路径**:
`VotingScope` 只决定共同权利人的决定范围和计票基数，不能从 `BUILDING`、`UNIT` 或 `COMMUNITY` 直接推导专项维修资金、公共收益、物业合同、保修或责任人承担。每个维修工程必须先有经确认的责任认定，再以该认定的资金路径和真实账簿/责任依据生成后续快照。

**专项维修资金账簿校验**:
确认走专项维修资金的工程，才按其已确认的费用承担范围读取对应可信账簿并核验可用余额。没有账簿或余额不足时，系统可以保留报修、勘验、草案和参考询价，但不能形成最终实施方案锁定、定商、合同或付款资格。账簿层级与范围对应关系必须来自当地实际账户结构，不能由界面默认值或跨级兜底补造。

**公共收益是独立路径，不是专项维修资金的补足项**:
公共收益的归属、用途、授权和账簿必须独立确认。属于专项维修资金使用范围的工程不得因为专项维修资金不足而改以公共收益绕过该路径；未接入可信公共收益余额和授权快照时，也不得生成最终实施方案锁定。

**授权提案冻结与最终实施锁定分离**:
需要相关业主决定或业主大会授权的工程，先冻结工程范围、预算、费用承担基数和施工单位选择规则，作为不可变的授权提案；该阶段不冻结余额、不产生中选、合同或付款资格。决定/授权生效后，服务端重新校验责任认定、授权依据和资金账簿，再形成最终实施方案锁定。未通过的提案回到草案修订，原提案和结果只读留痕。

**余额披露**:
对已接入的专项维修资金或公共收益账簿，业主侧应区分总余额、已占用/冻结金额、可用余额和占用依据；未接入的账簿必须明确标示为“尚未接入可信账簿”，不能展示推算余额。

### G 端公共维修前置审查

**公共维修 G 端前置审查 (Public Repair G-end Pre-Review)**:
[VotingScope]=COMMUNITY 大额维修议案表决通过后，资金科目**不直接核销**，状态机切到 `PENDING_GOV_REVIEW`，由 `PARTY_SECRETARY(3) 党组织书记` 在 G 端工作台前置审查。**去政治化原则**：审查内容仅限**客观可证据化**的合规事实，禁止政治正确性 / 意识形态 / 主观工程价值判断。系统将权力**死锁**在 [五条客观拒绝理由] 内。BUILDING / UNIT 议案**不**走该前置审查（局部维修的资金来源是局部业主，行政干预无法律依据）。
_Avoid_: 政治审查、街道办审批（前者法律词错位，后者把"前置审查"误认为可否决）

**五条客观拒绝理由 (Five Objective Rejection Codes)**:
G 端拒绝公共维修大额划拨的**唯一合法理由**清单——任何拒绝必须命中其一并提交证据：
- **C1** 招投标程序不合规（< 3 家投标 / 单一来源未公示），证据 = `t_engineering_bid_record`；
- **C2** 合同手续不齐（无施工资质 / 无安全协议 / 无监理单位），证据 = `t_engineering_contract.attachments`；
- **C3** 议案 hash 与表决落库 hash 不一致（议案被篡改），证据 = `t_voting_subject.subject_hash vs proposal.subject_hash`；
- **C4** 业委会成员/物业经理与工程商**经穿透核查**有 3 代直系亲属 / 配偶 / 共同投资人关系，证据 = [关联方申报 + 穿透核查] 双轨结果；
- **C5** 价格异常（高于政府指导价 30% 以上 或 显著低于市场价 50% 以下，疑似围标/虚高），证据 = `t_engineering_price_reference`。

落表 `t_engineering_proposal_review(rejection_reason_code IN ('C1','C2','C3','C4','C5'), rejection_evidence_json JSONB NOT NULL)`，CHECK 约束 `(review_result=1) OR (review_result=2 AND rejection_reason_code IN (C1-C5) AND rejection_evidence_json IS NOT NULL)`——**拒绝必带证据，无证据即数据库拒绝**。
_Avoid_: 审查清单、合规理由（前者太宽，后者没体现"五条封闭"）

**关联方申报 + 穿透核查 (Relation Disclosure Two-Track)**:
[五条客观拒绝理由] C4 的实施分两轨：
- **轨道 1（V1 必落）**：业委会成员就任时强制填 `t_relation_disclosure_record(亲属姓名 + 身份证 + 共同投资公司)`，G 端审查时手工核查工程商法人是否在表内+ 截图证据 + 文字说明。**业委会瞒报后果**：罢免 + 民事追偿（依《民法典》第 286 条业委会忠实义务），震慑大于穿透深度有限的技术缺陷。
- **轨道 2（V2 接入时启用）**：G 端实时调用公安户籍 API + 工商股权穿透 API 自动构建关系图，按 `platform.relation-check.api-provider` 配置启停。

V1 仅轨道 1 + 手工证据，C4 拒绝的 evidence_json 由 G 端手工录入。轨道 2 接入后自动构建关系图作为 evidence_json 内容。
_Avoid_: 关联关系核查、利益冲突审查（前者太弱，后者英文翻译失味）

**行政复议反制 (Administrative Reconsideration with Score Hook)**:
G 端拒绝后业主端"财务监督看板"**强制公开**拒绝代码 + 证据简述。业主可对 G 端拒绝提**行政复议** → 升级到街道办 (`GOV_SUPER_ADMIN`，区一级) 二审 → 复议结果落 `t_engineering_review_appeal`。**G 端拒绝率 + 行政复议被推翻率纳入党组织书记年度考核分扣减项**——用利益钩子杀死"为拒而拒"的官僚主义形式主义。业委会反制路径：C4 亲属关联拒绝可换工程商重投（被拒工程商永久拉黑该 tenant）；C1/C2/C5 可补正后再投；C3 hash 不一致**必须**重投完整业主大会双过半（无补正路径）；**业委会不能绕过 G 端**——大额划拨的 G 端审查是法定前置，绕过即违法。
_Avoid_: 申诉、复议（前者太弱无利益钩子，后者没体现考核分反制机制）

### 锁与原子性

**GovernanceLock**:
M2-1 落地的通用治理锁。`entity_type` 是预留枚举（含 `FINANCE_DISCLOSURE`）。**用于"双签对称解锁"语义**（如解锁财务公示需主任 + 街道办两方），**不**用于"民意通过 + 行政落库"语义（后者用 `t_management_mode_change_proposal` 这种轻量提案表）。
_Avoid_: 业务锁、状态锁

### 安全戒律

**幽灵角色物理熔断 (Ghost Role Circuit-Breaker)**:
**法理主体不存在 → 系统中不允许该角色以任何形式存在**。下线对应法理主体（如 LUMP_SUM 模式下的 auditor、未换届时的"准业委会候选人"）的角色，必须从**账号注册层**就拒绝该 role_code 在当前 (tenant, mode) 下被激活——不只是 RBAC 权限为空、不只是 endpoint 拒绝、不只是菜单隐藏。该角色账号在受限模式下应**根本无法登录**。这条戒律比 RBAC 严格一档：RBAC 控的是"能做什么"，本戒律控的是"能不能存在"。
**实施处**：(1) `sys_role` 加 `enabled_modes JSONB` 列；(2) 模式切换 service 同事务回收 `sys_user_role` 行，status 标 `DISABLED_BY_MODE_CHANGE`（**软冻结**而非物理删除，留审计追溯）；(3) `JwtAuthenticationFilter` 在签发 + 验证两端**双重校验**`tenant.management_mode ∩ sys_role.enabled_modes`（验证端必做，否则切换瞬间老 token 还能用 1h）；(4) 模式切换 commit 后 Redis pub `mode-changed:{tenantId}` 立即拉黑活跃 token，T+0 生效不给宽限期。
_Avoid_: 角色禁用、权限为空（语义都太弱）

**资金可见性矩阵 (Fund Visibility Matrix)**:
9 行 (3 模式 × 3 资金科目) × 5 列（C 端业主 / 物业经理 / 业委会主任 / 街道办 / 审计师）。是 ABAC 数据过滤的源规则。两层物理实现分工：**`DataScopeInterceptor`** 做"当前身份在当前模式下能不能看到这一行"的动态过滤（SQL 层整表去留）；**`mode_at_event` 行级冗余列**做"历史数据产生时是哪个模式"的物理冻结（Java 层行内字段加工），仅出现在 `t_finance_disclosure_snapshot` / `t_fund_ledger_entry` / `t_property_fee_expense_voucher` 三张报表面表上。
_Avoid_: 数据权限矩阵（太宽）

**mode_at_event**:
报表面表上的行级冗余列，`SMALLINT NOT NULL`（1=LUMP_SUM / 2=FUND_RAISING / 3=TRUST），**写入时 default 取当时 `tenant.management_mode`，落库后永远不再改**。取值时点选 **created_at**（不是 settled_at），因为 created_at 是 100% 存在的时点，settled_at 在很多事件上不存在。其作用是让历史报表保持产生时的可见性规则——切换 mode 不能改写历史可见性。
_Avoid_: snapshot_mode、original_mode（太弱）

**乘船到岸原则 (In-Flight Transaction Rule)**:
模式切换 commit 那一秒**不杀任何在飞交易**，每条交易在自己的状态机里走到终态、按其 `mode_at_event` 规则结算。但有**议案 vs 资金的劈裂**：议案归 `created mode`（民意场域锁定），执行落库归 `current mode`（资金通路锁定）。例：FUND_RAISING 模式下通过的 30 万采购议案，切到 TRUST 后**议案按 FUND_RAISING 投票规则继续走完**，但**资金动账走 TRUST 双密码 + 上链**——因为业主对 TRUST 的预期是切换瞬间起所有资金流上链。
_Avoid_: 在飞交易归属、跨模式结算

**前置完成态校验 (Mode-Switch Pre-Flight Check)**:
模式切换的执行性 endpoint 在事务最开始查五项强约束交易，任一不为 0 拒切：(1) `t_trust_dual_sign_voucher.status='PENDING_PASSWORD_B'`、(2) `t_outbox_event.event_type='TRUST_CHAIN' AND status='PENDING'`、(3) `t_audit_engagement.status IN ('OPEN','REPORTING')`、(4) `t_governance_lock.entity_type='LARGE_FUND_TRANSFER' AND status='LOCKED'`、(5) `tenant.tenure_status='HANDOVER_LOCK'`。第 5 项是 **HANDOVER_LOCK 与 mode_change 解耦但在最终落库会师**的设计——老业委会任内通过的模式切换议案不能立即落库。
_Avoid_: 切换前置检查

### 信托制双签与上链

**双密码会师 (Dual-Sign Voucher)**:
信托制 (TRUST) 下每笔资金动账要求物业经理 (密码 A) + 业委会主任 (密码 B) 各自独立签名。**密码形态阶段 1 用 SM2 USBKey**（国密硬件，私钥不出芯片），**阶段 2 增 Passkey/WebAuthn 双轨**。**生物 OTP 永久禁用**——OTP seed 在后端服务器，无法满足 PRD"杜绝运营方跑路"的硬性约束（链上独立可验性）。
**后端落地**：`DualSignVerifier` (pangu-domain port) 可插拔接口 + `Sm2UsbKeyVerifier` / `PasskeyWebAuthnVerifier` 两个 adapter；application 层按 `voucher.sign_algorithm` 路由。
_Avoid_: 双重签名、双因子认证（语义都偏弱）

**JudicialChainGateway**:
最高院司法链对接的 pangu-domain port。**阶段 1 mock，阶段 M5+ 接真**。两个实现 by `@ConditionalOnProperty("platform.judicial-chain.provider")`：`MockJudicialChainGateway`（mock，本地伪造 TxHash 落 `t_judicial_chain_mock_record`，便于测试回查）和 `HighCourtJudicialChainGateway`（stub，方法体抛 `UnsupportedOperationException`，等 M5+ 真接）。Outbox 行 `chain_provider='MOCK'` **不具备法律效力**——生产切换 high-court provider 前所有 mock 期 voucher **必须补链**（payload + 签名不变，重调 publish 取真 TxHash）。**业主 C 端 mock 期隐藏"链上验真"按钮，不展示假 TxHash**——诚实优先。
_Avoid_: ChainGateway、BlockchainGateway（前者太宽，后者技术词不对位）

**TRUST_CHAIN Outbox 状态机**:
`t_outbox_event.event_type='TRUST_CHAIN'` 行四态：`PENDING → PUBLISHING → CONFIRMED` 主路径；`PUBLISHING → FAILED → PENDING` 重试路径。`CONFIRMED` 行必须落 `tx_hash` + `chain_confirmed_at` + `chain_provider` 三列；`chain_provider` 是 mock 期债务追溯的关键索引。
_Avoid_: 上链队列、Outbox 状态

**时点签名原则 (Timestamping Principle)**:
证书撤销只影响**未来**签名，不改写**已签**效力——`signedAt < revokedAt` 永远有效，`signedAt ≥ revokedAt` 永远无效。这是 CA + 司法链体系的核心承诺，落到代码上：(1) 链上历史 (`status=CONFIRMED`) 的 voucher 法律效力**不**因后续证书撤销而失效；(2) 未上链 Outbox (`PENDING/PUBLISHING/FAILED`) 在双签已完成时**继续推**上链，因签名时点有效；(3) 在飞 voucher (`PENDING_PASSWORD_A/B`) 因签名未完成而**必须 ABORT**；(4) `Sm2UsbKeyVerifier.verify()` 比较 `revokedAt` 与 `signedAt` 先后，对历史回查同样适用。
_Avoid_: 时间戳验证、历史时点（前者太宽，后者太弱）

**签名继任禁止 (No Signature Succession)**:
新主任**不**继承老主任的待签 voucher——让继任者被动追认前任决策违反"双密码会师代表两个独立主体意志"的法律前提。已离任主任的所有在飞 voucher 全部 `ABORTED_BY_KEY_REVOCATION`，由物业经理**重新发起**新 voucher 由新主任签。这是法律雷区，**不可优化**。
_Avoid_: 签名继承、签名委托

**验签双查 (Dual-Source Cert Verification)**:
`Sm2UsbKeyVerifier.verify()` 同时查两个证书状态源：(1) 本地 `t_dual_sign_certificate_revocation` 表（业务侧立即生效，事务内已写）；(2) CA OCSP 在线查询 / CRL 兜底（CA 侧最终一致）。任一源标记撤销且 `revokedAt < signedAt` 即拒。双查保证业务侧"立即拒绝"不依赖 CA 外部网络可用性。
_Avoid_: OCSP 验证、CRL 检查（单源词都太窄）

**TRUST 防共谋三规则 (TRUST Collusion Circuit Rules)**:
[防共谋断路器] R1/R2/R3 在 TRUST 模式下复用但**阈值与处置差异化**——TRUST 模式单笔金额可能远高于 FUND_RAISING（50 万级 vs 几千几万），共谋破坏力更大但同时业主端 SLA 是"动态秒级实时穿透"，处置不能锁死：
- **R1 (30s 秒签)**：物业经理输入密码 A 后开支单进 `PENDING_PASSWORD_B` 状态记 `password_a_signed_at`；主任输入密码 B 时校验 `now() - password_a_signed_at < 30s` → `TRUST_INSTANT_DUAL_SIGN_REJECTED`，**双签作废** + voucher 进 `ABORTED_BY_COLLUSION_R1` 终态。物业经理必须**新建**开支单（不能复制旧单），同物业经理对同一笔费用 5 分钟冷却期内拒新提交——杜绝"等够 30s 再秒批"的旁路；
- **R2 (高频)**：阈值从 FUND_RAISING 的 `< 60s` 收紧到 `< 90s`，处置改为该主任接下来一周所有 `PENDING_PASSWORD_B` 单据**强制延时 5 分钟双签**（密码 B 输入后系统延 5 分钟才发 Outbox 上链，给主任反悔时间），不锁死保 SLA；
- **R3 (100% 通过)**：处置同 R2 强制延时 + 给街道办（`GOV_SUPER_ADMIN`）周报，街道办主动调阅最近 30 笔人工核查；不锁死维持上链 SLA——上链就是法律意义上的不可篡改终态，事后行政程序追究即可。

落 `t_trust_dual_sign_voucher` 增列 `password_a_signed_at` / `password_b_signed_at` / `collusion_circuit_break_rule_code VARCHAR(8)` / `delayed_until TIMESTAMP`。
_Avoid_: 双签防共谋（语义太弱，没体现"R1 必落 + R2/R3 柔性化 + 处置差异化"三层）

**TRUST 大额冷静期 (TRUST Large-Amount Cooling)**:
TRUST 专属规则——单笔开支 ≥ `large_amount_threshold` 时，主任输入密码 B 前**必须**先看一段强制阅读 30 秒的"风险告知文本"（金额 / 用途 / 工程商 / 法律责任），30 秒内不得跳过。强制阅读 30s + R1 双签 30s = 该笔单据从密码 A 完成到上链至少耗时 60s，给主任**强制冷静期**避免高金额冲动批准。前端倒计时 + 后端校验 `password_a_signed_at + 30s ≤ password_b_signed_at`（前端不可信，后端兜底）。落 `t_trust_dual_sign_voucher.forced_cooling_period_seconds INT`。
_Avoid_: 大额二次确认、慢审批（前者太弱不专属，后者反治理理念）

**TRUST 业主季度抽查 (TRUST Quarterly Owner Sampling)**:
TRUST 专属——季度公示时业主大会可双过半发起"专项审计"对该季度 5% 抽样上链交易做事后核查。审计师走 [Audit Engagement] engagement-bound 路径，看 mock 期 vs 真链期的双签 hash + payload + TxHash 是否完全一致。这是 TRUST 制"全网穿透"承诺的兑现——业主用大会决议触发审计，不需要每笔都自己核查。`audit_type=SAMPLING` 是新增枚举值，与 `ANNUAL` / `SPECIAL` 同级。
_Avoid_: 抽样审计（太宽，没说清"业主大会触发 + TRUST 专属"两条硬约束）

**业委会整体罢免 (Committee Recall by Assembly)**:
业主大会通过罢免议案 → G 端街道办执行性落库 → 触发整个业委会全员证书撤销（`RevocationCommand(revokedUserIds=[全员], reason=RECALLED_BY_ASSEMBLY)`）+ 自动激活 HANDOVER_LOCK 锁住大额资金划拨 → 等新业委会选出 + G 端实名备案完成后 HANDOVER_LOCK 解锁。**业委会不允许内部投票罢免自己**——既荒唐又会被钻空子。这是 [模式切换] 与 [HANDOVER_LOCK] 两条状态机在密钥层面的**协同点**。
_Avoid_: 委员会解散、整体撤销

### 审计与 engagement

**Audit Engagement (审计任命)**:
第三方审计师 (auditor) 是 **engagement-bound 账号**，**不**是常驻账号——每次审计创建一个 engagement，engagement 结束账号自动 disabled。落地为 `t_audit_engagement` 表，五态机 `PROPOSED → ACTIVE → {EXPIRED, COMPLETED, TERMINATED_BY_ASSEMBLY}`。**业委会主任单边发起 + G 端街道办审批激活**——年度法定审计不必走业主大会；专项审计 (`audit_type=SPECIAL`) 加业主大会双过半前置。engagement 自身有效期最长 180 天（DB CHECK 约束兜底）。
_Avoid_: 审计任务、审计委托

**AUDITOR_ENGAGEMENT_SCOPE**:
新增 `DataScopeType` 枚举值 + `@AuditorEngagementScope` 注解。`DataScopeInterceptor` 在审计师身份命中时，从 `SecurityContext.engagementId` 反查 `t_audit_engagement`，注入两条 WHERE：(1) `composed_at BETWEEN engagement.period_start AND period_end`（**审计期时间窗**）；(2) `mode_at_event IN ('FUND_RAISING','TRUST')`（**Q3 矩阵硬约束**：审计师对 LUMP_SUM 期物业费内账无访问权，即使在 FUND_RAISING/TRUST 时期被任命）。审计师**不**走 `@PreAuthorize("hasAuthority(...)")` 链路，无任何固定 permission，**只有 engagement 注入的 scope**——这是 Q3 矩阵第 5 列的"激活后怎么管"细节。
_Avoid_: 审计数据范围、AuditScope（前者太宽，后者重名风险）

**审计师签报告 (Auditor Report Signing)**:
审计师 engagement 终态 `COMPLETED` 必须有终审报告 PDF + 审计师签名。**用 Passkey/电子签章服务，不上 SM2 USBKey**——审计报告不是资金动账，签名等级要求低于 [双密码会师]，Passkey 即开即用，避免每位审计师都走 USBKey 采购 + CA 证书签发的 1 周流程。`EXPIRED` 是审计师懈怠未交报告，落 `t_audit_engagement_misconduct` 留痕。
_Avoid_: 审计报告签名

### 筹金制风控（FUND_RAISING 主任事后初审）

筹金制下每笔物业费开支单走**三流并行状态机**：正常流（主任 72h 内审批）/ 懒政流（主任静默 → 全员并行 → G 端代签兜底）/ 腐败流（实时熔断 → 纪检人工核实）。三流互斥，但腐败流可在正常流上**插入熔断**（即主任正在审批却触发秒批阈值，正常流被腰斩转入腐败流）。

**ESCALATED_PARALLEL (业委会全员并行表决)**:
开支单 `PENDING_DIRECTOR_REVIEW` 持续 72h 主任零操作触发。状态机切到 `ESCALATED_PARALLEL`，资金继续冻结。参与人 = `tenant_id` 下所有 `role_key IN ('COMMITTEE_DIRECTOR','COMMITTEE_MEMBER','COMMITTEE_SECRETARY')` 且 `sys_user_role.status='ACTIVE'` 的账号；计票走**简单多数**（≥ 50% 通过即放）—— **有意不走 [dual 2/3 quorum]**，因为日常开支单不是业主大会议案，委员会内部审议历来按过半数。落 `t_committee_internal_vote` 表（明显不入 `t_voting_subject` 业主大会决议链路），走 `GeneralDecisionEngine.calculate(scope=COMMITTEE_INTERNAL, ...)` 新分支。
_Avoid_: 升级审批、副主任审批（前者太宽，后者刚性单点在 5 人小业委会会陷入"时空幽灵单"死锁——副主任不是法定常设岗）

**GOV_EMERGENCY_TAKEOVER (G 端托管代签)**:
`ESCALATED_PARALLEL` 持续 48h 全员仍未达过半数 + `is_emergency_rigid=true` 触发。代签人 = `PARTY_SECRETARY(3)` 党组织书记一人即可，无双签——这已是兜底兜底，刚需流程不能再加门槛。代签后状态 `GOV_TAKEN_OVER_APPROVED`，业主端"财务监督看板"**强制公开** "本笔开支因业委会全员失能由 [党组织书记 X] 于 [日期] 行政托管代签，业主可对此提出行政异议"。**法理边界**：G 端代签 ≠ 替代业委会决策，合法性仅在三个条件**全部成立**时才有：(1) 主任 72h 静默；(2) 全员 48h 失能；(3) `is_emergency_rigid=true` 且事后核查为真。任一不成立即越权。
_Avoid_: 街道办备案、自动驳回——前者只留纸质证据没干预物理瘫痪（消防爆裂、电梯故障刚性开支），后者直接造成小区物理瘫痪

**紧急刚性开支 (Emergency Rigid Expense)**:
物业经理提交开支单时必填 `is_emergency_rigid BOOLEAN` + `emergency_rigid_reason VARCHAR(500)` + `emergency_evidence_image_urls JSONB`（≥ 3 张现场图）。**月度配额 5 笔**（`platform.fund-raising.urgent-quota-per-manager-month: 5`），超额必须升级流程或拆单——故意拆单滥用走 `PROPERTY_MANAGER_RISK_AUDIT`。**滥用兜底**：街道办（`GOV_SUPER_ADMIN`）每月抽查 `GOV_TAKEN_OVER_APPROVED` 列表 + 物业经理配额耗尽，事后核查不真实紧急 → 物业经理纪律处分。
_Avoid_: 紧急开支、刚性需求

**防共谋断路器 (Speed-Limit Circuit Breaker)**:
开支单审批 commit 瞬间走 `CircuitBreakerEvaluator` 同步检查三规则：
- **R1 (秒批)**：`now() - submitted_at < 30s`（开支单含金额 + 票据切片 + 批文 + 用途，最少 30s 阅读时间，<30s 完成审批必属共谋或盲签）；
- **R2 (高频)**：该主任最近 7 日审批的 `median(响应秒) < 60s` 且笔数 ≥ 5 → 该主任本月余下所有审批锁死；
- **R3 (100% 通过)**：该主任最近 30 日通过率 = 100% 且笔数 ≥ 8 → 同 R2。

任一命中 → 状态强制 `PENDING_GOV_AUDIT`，资金不放，写 `t_expense_voucher_circuit_break(voucher_id, rule_code, triggered_at, evidence_json)`。**实时同步检查**而非周报警——周报警太晚，等周日 23:00 跑批 5 笔虚假秒批的 10 万元已经放出去。R1 阈值 30s 落 `application.yml` 方便基于线上数据微调。
_Avoid_: 风控告警、行为审计（前者太弱不熔断，后者批处理太晚）

**PENDING_GOV_AUDIT (纪检人工核实)**:
[防共谋断路器] 命中后的终态前置态。解锁通路：G 端纪检（`PARTY_SECRETARY` / `GOV_SUPER_ADMIN`）人工查证后双选：`PENDING_GOV_AUDIT → APPROVED_BY_GOV_AUDIT`（放款）或 `PENDING_GOV_AUDIT → REJECTED_BY_GOV_AUDIT`（退回 + 物业经理风险记）。**与 GOV_EMERGENCY_TAKEOVER 的区分**：前者是腐败流（主动作恶），后者是懒政流的兜底（被动失能），处置主体一致（G 端）但语义完全相反。
_Avoid_: 待审核、风控冻结

**业主行政异议 (Owner Administrative Dispute)**:
业主对 `GOV_TAKEN_OVER_APPROVED` 单据可提出 `dispute`，14 日异议期（`platform.fund-raising.dispute-window-days: 14`）。状态机：`DISPUTED → 街道办 GOV_SUPER_ADMIN 复核 → DISPUTE_UPHELD (维持原审批) / DISPUTE_REJECTED (撤销原审批 + 资金追回)`。**资金追回的法律链路**：DISPUTE_REJECTED 触发街道办起诉物业 + 主任民事追偿——数据库状态回退只是数据层动作，真正的资金追回走民法层面。落 `t_expense_voucher_dispute(dispute_id, voucher_id, raised_by_owner_id, reason, raised_at, resolved_by_user_id, resolution SMALLINT, resolved_at)`。
_Avoid_: 业主投诉、撤销申请

**时点初审稳定性 (Approval Timestamping Stability)**:
[防共谋断路器] R1/R2/R3 只对"未来 / 当下提交瞬间"生效——已 `APPROVED` 落库且资金已放的单据**不可回溯熔断**。理由有三：(1) 资金已外部支付，状态回退只是 DB 状态变化无真实意义；(2) 与 [时点签名原则] 对称——FUND_RAISING 的初审通过即生效应有同等时点稳定性；(3) 历史虚假开支走 [业主行政异议] → 街道办复核 → 资金民事追偿这条**有正当法律程序**的路径，不是数据库状态批量回退。新发现的历史虚假开支不能逆向触发 R2/R3 把同主任过去 30 日所有 APPROVED 单据回退 `PENDING_GOV_AUDIT`——那是越界的。
_Avoid_: 风控回溯、批量审计回退（前者技术词，后者法律不允许）

### 议案级动态权限

**议案级动态权限 (Subject-Scoped Dynamic Authority)**:
`OWNER_REPRESENTATIVE(8)` / `VOLUNTEER(9)` 静态 RBAC **不含**催票 / 线下票核销权限——这两类权限只在议案 `IN_VOTE` 期间临时激活，议案出 `IN_VOTE` 即收回，避免楼代/志愿者持有"常态写权限"诱发民意操控。落地走 **C+D 混合架构**：
- **C（议案级落表）**：`t_subject_dynamic_authority(authority_id, tenant_id, subject_id, granted_user_id, granted_role_key, authority_code, granted_at, revoked_at, revocation_reason)`，`uk_subject_user_authority UNIQUE (subject_id, granted_user_id, authority_code)`，议案级精准记录授予了谁哪些动态权限——作为审计追溯；
- **D（切面运行时判定）**：endpoint 标 `@SubjectScopeAuthorize("subject:promote")`，切面双重判定 (a) `currentUser ∈ sys_user_building.lookupByBuilding(subject.referenceId)`（地理范围）+ (b) `t_subject_dynamic_authority` 中存在 `revoked_at IS NULL` 的行（时间窗口）。

**Lifecycle**：议案进 `IN_VOTE` 由 `SubjectVoteStartedEvent` listener 自动激活；议案出 `IN_VOTE`（不论 `APPROVED` 或 `REJECTED`）由 `SubjectVoteEndedEvent` 立即标记 `revoked_at = now()` —— **不删行保留审计**。仅对 `VotingScope IN (BUILDING, UNIT)` 议案激活；`COMMUNITY` 议案催票由业委会主任做，不需要楼代动态权限。
_Avoid_: 临时角色、动态 RBAC（前者技术词错位，后者破坏静态 RBAC 一致性）

**楼代权限完全自动激活原则 (Owner-Rep Autonomous Activation)**:
[议案级动态权限] 是**完全自动**激活——`sys_user_building` 反查到的全部楼代/志愿者无差别授予，**不**给业委会主任"手动指定 / 临时撤销"开关。理由：(1) 楼代是经业主大会选举产生（V1 假设），忠实义务由法律强制；(2) 主任手动审批增加流程负担，与"局部维修敏捷推进"理念矛盾；(3) 主任手动撤销开关 = 给主任增加微观操控民意的工具，反而是治理隐患；(4) 楼代叛变（如被开发商收买恶意催票）属**管理问题**——走"业委会发起 OWNER_REP 资格罢免议案"法律路径，不在议案进行中加额外阀门。
_Avoid_: 主任审批激活、白名单激活（两者都是同一类反治理设计）

### 工程款分期与核销节奏

**议案分期付款双字段 (Two-Track Frozen / Paid)**:
工程款几乎都是分期（开工 30% / 中期 50% / 尾款 20%），但议案 APPROVED 时账户对**议案总额**的预占承诺必须保留到结案，否则工程进行中扣尾款时账户余额已被别的议案吃光。`t_maintenance_fund_account` 不动 schema：`frozen_balance` 仍锁议案 APPROVED 时的**总额** + 不随每期付款递减；新增 `t_proposal_payment_installment` 跟付款节点（`installment_no` / `amount` / `payment_node KICKOFF/MILESTONE_50/FINAL/EXTRA` / `paid_at` / `voucher_id` / `tx_hash` / `disclosure_event_id`），`paid_amount = SUM(amount) WHERE proposal_id` 现算或在 `t_proposal_state` 加冗余列。议案手动结案时一次性 `release_amount = frozen - paid` 释放剩余 frozen 回 total（未付部分自然留账户、不再支付）；`total_balance` 每期付款时同步扣减实付金额。**可用余额**（防囤积阈值用）= `total - frozen` 不变，但 UI 同时展示 `(frozen - paid) = 还需付` 给业委会做工程进度参考。
_Avoid_: 单字段 frozen 递减（工程量追加时无法回头补冻 + 扣尾款无锁兜底）、单字段 frozen 一次锁死不分期（虚高挤占防囤积阈值）

**TRUST 每期独立双签上链 (Per-Installment Dual-Sign)**:
TRUST 模式下分期付款的每一期**独立触发** [信托双签证票上链]：物业经理 + 主任各自双签 → mock TxHash → frozen 不变 / paid += 本期金额 / `t_proposal_payment_installment` 记 `voucher_id` + `tx_hash`。同议案下三期产生三个独立 voucher / 三条独立 TxHash，业主端 C 端动态秒级穿透看板逐笔切片，符合 PRD"动态实时穿透"。**不**走"议案 APPROVED 一次性双签后由系统自动按节点扣款"——那等于把链上信任压回到链下系统，违背 TRUST 的"全网穿透"承诺。
_Avoid_: 议案级一次双签、自动扣款（前者把每期支付的链上不可篡改性弱化为 1 条 hash，后者完全失去逐笔穿透能力）

**LUMP_SUM/FUND_RAISING 分期公示节奏 (Non-TRUST Per-Installment Disclosure)**:
LUMP_SUM / FUND_RAISING 不上链，但每期付款应触发 [财务公示快照] 输入事件——而非只在结案时一次性公示。理由：(1) 工程跨度长（30~90 天），季度公示窗口一定有"工程进行中已付 9 万"的中间态，掩盖等于 PRD §5.2 "动账穿透"承诺打折；(2) 与 TRUST 模式 C 端展示的"每期一笔切片"对称，业主在不同模式下看到的财务粒度一致——只是链上信任度不同。落地：`t_proposal_payment_installment` 写完 INSERT outbox event `event_type=DISCLOSURE_PAYMENT_INSTALLMENT`，由 `FinanceDisclosureApplicationService` 在下次 `compose` 时拉取这些事件计入当期 payload。**不**改成"每期付款立即触发 compose"——compose 是季度/月度快照动作，每期付款触发等于 compose 失去快照语义。
_Avoid_: 每期触发 compose、只在结案公示（前者把 compose 退化成事件流，后者掩盖工程中态）

**议案变更 (追加/扣减) 与 frozen 补冻 (Proposal Amendment)**:
工程量追加（30 万 → 35 万）走**议案变更子流程**而非新发起议案：业委会主任发起 `ProposalAmendment(amendment_id, original_proposal_id, delta_amount, reason)`，**变更议案需重走原议案的投票门槛**（COMMUNITY 议案重走双 2/3 + 党组前置审查；BUILDING/UNIT 议案重走局部双过半）—— 5 万的追加不能因为"小"而绕开民意。变更通过后 `frozen_balance += delta_amount`、`t_proposal_state.amended_total = 35万`；扣减场景反之 `frozen -= delta` 释放回 total。**变更议案不可压死老议案**——老议案的合规审查 / 双签历史 / 已付款保留，变更只增量贴新合规链。这是把 [Q12 G端前置审查] 的不可绕开性带到工程进行中。
_Avoid_: 主任直接调金额、追加议案（前者越权一票，后者会让"变更"沦为新议案破坏付款节奏一致性）

**变更议案分级处置 (Amendment Vote-And-Pay Concurrency)**:
变更议案 `IN_VOTE` 期间原议案的付款节奏按变更性质分级：
- **追加 (delta > 0)**：原已批 30 万额度内的剩余付款节点正常走（第 2 期 15 万照付），变更只锁追加部分 5 万——若变更被否，差额由物业 / 工程商按合同条款"未经业主大会变更同意的工程量调整不进入小区资金核销"自担责；
- **扣减 (delta < 0)**：原议案付款 hard freeze 直到变更议案出终态，避免多付——施工方案已变化，强行付款会成"无法回收的多付"；
- **工程商更换 / 施工方案重大变更**：hard freeze + 强制走"暂停 → 变更 → 续付"链路，因为支付对象都换人了，原合同实质已废。

落 `t_proposal_amendment.amendment_kind` 枚举（`SCOPE_INCREASE` / `SCOPE_DECREASE` / `CONTRACTOR_CHANGE` / `PLAN_REVISION`），application 层 `AmendmentConcurrencyEvaluator.canPayInstallment(proposalId, installmentNo)` 在写 `t_proposal_payment_installment` 前做 gate。
_Avoid_: 变更全冻、变更全放（前者工程现场停工 7-14 天索赔成本远超变更金额，后者一旦扣减场景多付资金无法回收）

**变更议案阈值差异化 (Amendment Threshold Tiering)**:
变更议案的投票门槛按 |delta| / original_amount 的比例分三档：
- **<10%**：走 COMMITTEE_INTERNAL 业委会内部决议（简单多数 ≥ 50%，复用 [Q9 ESCALATED_PARALLEL] 的 `t_committee_internal_vote`）——5 万追加上 30 户业主重投反治理常识；
- **10% ~ 30%**：走简化民意征询（C 端推送 + 7 日异议期，无异议或异议数 < 5% 自动通过；命中异议阈值升级到完整投票）；
- **≥30%**：必须重走原议案完整门槛（COMMUNITY 双 2/3 + 党组审查；BUILDING/UNIT 局部双过半）；
- **|delta| ≥ `large_amount_threshold`**：不论比例都要重走党组前置审查（[Q12 G端前置审查] 五条客观拒绝理由 C1-C5 重新走一次），把住法理红线。

阈值落 `application.yml.platform.proposal-amendment.minor-threshold-ratio=0.10` / `consult-threshold-ratio=0.30` / `consult-window-days=7` / `consult-objection-ratio=0.05`。**与小区规模解耦**——30 户和 3000 户用同一比率合理；固定金额（5 万 / 20 万）跨规模不合理。
_Avoid_: 变更走原门槛、变更全部委员会内部决议（前者业主疲劳，后者绕开民意红线）

**变更议案与换届封存 (Amendment in HANDOVER_LOCK)**:
[HANDOVER_LOCK] 期间变更议案分阶段处置（与 [Q14 换届议案分阶段封存] 对称）：
- 老业委会任内**已 APPROVED 的工程**：HANDOVER_LOCK 期间**禁止发起任何变更议案** —— 避免老业委会下台前突击改金额绕开新班子审视；
- 新业委会就任前**已签约的在飞工程**：变更只能由新业委会重新发起，老议案的金额承诺保留；
- 紧急工程量调整（如施工中突发管线腐蚀）：走 [Q9 GOV_EMERGENCY_TAKEOVER] 街道办行政托管路径，党组书记 (`PARTY_SECRETARY`) 代签 + 公开异议期 + 强制公示"该变更经街道办行政托管，业主可提行政异议"。

落 `AmendmentLifecycleGuard` 在 `tenant.tenure_status='HANDOVER_LOCK'` 时拒绝 `proposeAmendment`，但放行 `is_emergency_rigid=true` 走 GOV_EMERGENCY_TAKEOVER 通路。
_Avoid_: 换届期内全锁（紧急工程瘫痪）、换届期内全放（老业委会突击改金额）

**变更议案进模式切换硬约束 (Amendment in Mode Switch Strong Constraints)**:
[模式切换] 第六项强约束：`t_proposal_amendment.status='IN_VOTE'` 任一不为 0 拒切（与原 5 项的 `PENDING_PASSWORD_B` / `TRUST_CHAIN PENDING` / `audit_engagement OPEN` / `governance_lock LARGE_FUND_TRANSFER` / `HANDOVER_LOCK` 同级）。变更议案与原议案是同一议事单元，不能在投票途中改变身份语义（FUND_RAISING 民意场域 → TRUST 上链场域）。等变更议案出 `IN_VOTE`（不论通过否决）才放行模式切换。**这条与 [模式切换的议案场域锁定] 也对称**：议案归 created mode 的承诺意味着变更也归原 mode 的投票规则。
_Avoid_: 模式切换不查变更议案（投票场域漂移破坏 created mode 锁定承诺）

### 局部维修与定向推送

**业主住所路由 vs 治理人员路由 (Owner-Property vs User-Building Tables)**:
两张表语义独立，绝不合并：
- **`sys_owner_property`** —— 业主-房产关联，每位业主对每套房 1 行：`(owner_id, building_id, unit_id, room_id, area, ratio)`。**业主本人投票推送、计票分母、ABAC 资金账户匹配**全部走这表。本期若 V1.x 没有则补 V2.8 引入；`reference_id` 走 ancestors 物化路径与 `t_maintenance_fund_account` 4 级树（COMMUNITY/BUILDING/UNIT/ROOM）对齐：业主可关联 BUILDING/UNIT/ROOM 任一级；
- **`sys_user_building`** —— 用户-楼栋的**治理关联**（楼代 / 网格员 / 志愿者反查），不是业主住所——业主本人不进这表。[议案级动态权限] / [楼代权限完全自动激活原则] 都基于这表。

混用导致的反例：把楼代催票权限发给随便一个住在 1 号楼但与治理无关的业主，或把推送只发给 1 号楼楼代而漏掉 1 号楼业主——前者是治理失格，后者是民意漏发。
_Avoid_: 业主关联表（太宽，区分不出住所 vs 治理）

**议案推送服务端扇出 (Server-Side Notification Fan-Out)**:
议案进 `IN_VOTE` 时按 [业主住所路由] 反查 owner 列表，逐条 INSERT `t_owner_notification(notification_id, tenant_id, owner_id, subject_id, channel, status, payload, retry_count, sent_at)`，由 `NotificationDispatcher` 异步派发微信订阅消息 / SMS / app push。**优点**：每位业主的未读 / 已读 / 失败重试 / 渠道偏好可追溯，状态机闭环。**量级控制**：BUILDING 议案天然 30~80 户不会爆；COMMUNITY 议案 3000 户走 `batchInsertNotifications` + 队列削峰（每秒 100 条）；UNIT 议案仅 12~24 户。**反方案——单条 MQ 广播 + 业主端 ABAC 过滤**：未在线业主拿不到主动推送（订阅消息需服务端调微信 API），漏发严重。
_Avoid_: 广播过滤、订阅推送（前者把推送语义压成"业主主动拉"，后者技术词太宽）

**投票分母创建时点快照 (Voting Quorum Snapshot)**:
议案创建（`t_voting_subject` insert）时把"参与该议案的业主总面积 + 总户数"快照写到 `quorum_denominator_total_area NUMERIC(18,4)` + `quorum_denominator_owner_count INT` + `quorum_snapshot_at TIMESTAMP` + `quorum_denominator_payload JSONB`（含面积与户数明细 + 计算时间），**投票期内即使有业主过户、分户、合并也不动**。`AbstractVotingEngine.settle()` 用 snapshot 而非现算。理由：(1) 与 [时点签名原则] 对称——投票分母是议事条件的一部分，时点固定才能事后审计；(2) 投票期内业主权属变更属罕见事件，频度远低于投票发生；(3) 实时计算会造成"我前一秒能投，后一秒因为新业主过户分母变大被算成无效投"的诡异 UX。**例外**：HANDOVER_LOCK 导致投票期超过 60 天的议案可以由街道办申请"分母刷新"，需双 2/3 业主大会决议解锁——这是兜底，不是常规。
_Avoid_: 实时分母、议事时刻分母（前者技术对治理错，后者词义模糊）

**UNIT 议案动态权限范围严格匹配 (UNIT Subject Authority Strict Scope)**:
[议案级动态权限] 在 `scope=UNIT` 议案下激活范围严格等于 `unit_id` 这一单元——只激活 `sys_user_building.unit_id = subject.unit_id` 的楼代 / 志愿者，**不溢出到整栋楼**。例：1 号楼 1 单元电梯故障议案，只 1 单元的楼代能催票 / 核销，1 号楼 2-6 单元的楼代不能。理由：避免不相干楼栋的治理人员对 1 单元业主施加民意压力。BUILDING 议案对称——激活范围 = 整栋楼楼代但不溢出到全小区。**这是 [Q19.4] 的核心边界**：议案 scope 与动态权限激活范围 1:1 严格匹配，不放宽不收窄。
_Avoid_: 整栋楼激活、治理人员都激活（前者破坏 scope 一致性，后者权限过载）

**MULTI_BUILDING 议案 (Cross-Building Common Property)**:
跨楼共有部分（地下车库 / 消防泵房 / 中央监控）涉及的议案不能强行归 COMMUNITY（不相干楼业主无义务为其他楼共有部分掏钱）也不能归单 BUILDING（多栋业主权益不能让单栋决定）。新增 `VotingScope.MULTI_BUILDING`，`t_voting_subject` 列加 `reference_ids JSONB`（如 `[1001, 1002, 1003]`）。
- **计票分母**：聚合 `reference_ids` 中所有楼栋的面积与户数（按 [投票分母创建时点快照] 走 snapshot）；
- **ABAC 推送**：三栋楼业主合集走 [议案推送服务端扇出]；
- **资金账户**：依共有部分归属——若属"按面积比例分摊"则在 V2.2 fund tree 加 `account_level=2.5 (MULTI_BUILDING)` 临时账户（不存在），实际落地为创建多笔关联到不同 BUILDING 账户的子流水；若属"全小区维修资金兜底"则走 COMMUNITY 账户；
- **动态权限激活**：三栋楼楼代合集，仍不溢出到非关联楼栋。

`AbstractVotingEngine.settle()` 增 MULTI_BUILDING 分支与 BUILDING 同走双 2/3 但参数不同。
_Avoid_: 跨楼归 COMMUNITY、跨楼按主楼归 BUILDING（前者反直觉，后者不公平）

### 投票方式异构性（线上 / 线下票 / 委托）

**线下票三段录入 (Offline Paper Vote Three-Stage)**:
中老年业主多的小区线下纸质票占比可能 > 50%，必须三段式不可压缩：
1. **楼代上传**：`OWNER_REPRESENTATIVE` / `VOLUNTEER` 在工作台扫描纸质票照片 → INSERT `t_offline_vote_paper(paper_id, subject_id, owner_id, vote_choice, image_url, signed_at, scanned_by_user_id, scanned_at, status='PENDING_DIRECTOR_VERIFY')`；
2. **主任核销**：业委会主任 (`COMMITTEE_DIRECTOR`) 在工作台逐张验证（业主签名 + 印章 + 日期 + 选项），通过 → `status=VERIFIED` + 同步 INSERT `t_voting_record`；
3. **业主异议期**：投票期 + 7 日异议期内业主在 C 端"我的投票"看到代投结果，可发起 `dispute` → `status=DISPUTED` → 楼代/主任纪律调查 + 该票作废 → 该业主可重投。

`t_offline_vote_paper.status` 五态：`PENDING_DIRECTOR_VERIFY → VERIFIED → DISPUTED → REVOKED / UPHELD`。**不允许楼代单方录入即生效**——楼代代投却业主不知情是民意造假最高发场景；**不依赖 SMS 业主二次验证**——超老业主可能没手机或不识字。
_Avoid_: 楼代直接录入、自动核销（前者楼代单点造假，后者绕过主任审视）

**双投防御 + 业主自投优先 (Self-Vote Overrides Proxy)**:
`t_voting_record uk_voting_record(subject_id, owner_id)` 唯一约束兜底。冲突时**业主本人线上自投永远优先于楼代代投**——
- 若线下票先 VERIFIED 入库，线上自投触发 `t_offline_vote_paper.status = REVOKED_BY_OWNER_SELF_VOTE` + 业主自投覆盖原线下结果；
- 若线上自投先入库，楼代上传线下票时 `OfflineVotePaperService.upload` 校验 `existsSelfVote(subject_id, owner_id)` 命中 → 拒，提示"业主已自投"；
- 投票期结束后不可改（与 [投票截止以业主签字时间为准] 对齐）。

业主意志最高是治理铁律，不能因为楼代上传得早而被覆盖。
_Avoid_: 先到先得、按时间戳裁决（前者破坏自由意志，后者忽略授权链）

**受限一对一委托投票 (Restricted Voting Proxy)**:
落 `t_voting_proxy(proxy_id, tenant_id, principal_owner_id, agent_owner_id, valid_from, valid_to, evidence_image_url, approved_by_director_user_id, approved_at, status)`：
- **业主本人 C 端发起** + 上传授权委托书照片 + **业委会主任审批激活**——委托人池开放给所有业主，但每位委托关系经主任核签把关；
- **一对一**：`uk_proxy_principal UNIQUE (principal_owner_id, status='ACTIVE')` 保证一位业主同期只委托一位代理人；
- **代理人上限 5**：DB CHECK 配 application 层 `agentActiveCountUnder5(agent_owner_id) ≤ 5`，防"票贩子"批量收委托空降；
- **不可嵌套**：A 委托 B、B 自动失去再被委托资格（`existsAsAgent` 与 `existsAsPrincipal` 互斥校验），合约链路深度防爆；
- **有效期 ≤ 90 天**（与 [议案有效期] 对齐），到期失效需重发起；
- **业主可随时撤销**：C 端撤销立即生效，已投票不追溯；
- 代理人投票时记 `t_voting_record.cast_by_agent_owner_id` + `proxy_id` 双字段，C 端公示透明（被代投业主能看到"由代理人 X 于日期 Y 投了 Z"）。

代理人不限"必须是楼代"——业主选谁代投是私权。
_Avoid_: 限治理人员代投、自由委托无限制（前者越界，后者票贩子高发）

**线下票造假离线监控 (Offline Vote Corruption Detector)**:
异议期是事后兜底，但需要主动发现机制。议案投票结束后 `OfflineVoteCorruptionDetector` 离线 batch（每日 02:00）跑三条规则：
- **R1 异常配对**：同一 (楼代, 主任) 组合下"100% 通过率 + 平均核销间隔 < 30s"的议案数 ≥ 3 → flag；
- **R2 异常占比**：单楼代单议案上传 ≥ 50% 楼栋户数的纸质票 → flag（远超合理代投比例）；
- **R3 异常签字时间**：签字时间集中在凌晨 / 深夜 / 周内同一时段 → flag（人为成批盖章征兆）。

flag 落 `t_offline_vote_anomaly_flag(flag_id, subject_id, rule_code, evidence_json, flagged_at)` 通报街道办，街道办决定是否走电话回访 10% 抽样验证 / 启动纪律调查。**不实时熔断**——线下票本就是慢节奏，实时熔断没意义且会卡死投票流程；事后离线 + 行政程序是正确处置。
_Avoid_: 实时风控告警（误触发卡流程）、人工巡检（无规则化批处理）

**投票截止以业主签字时间为准 (Voter-Signed-At Cutoff)**:
线下纸质票的有效性看 `t_offline_vote_paper.signed_at`（业主手写日期，OCR 提取或楼代手动录入），落库时校验 `signed_at ≤ subject.vote_end_at`；主任核销时间 (`verified_at`) **不限**——业主意志的真实时点是签字那一刻，行政核销只是确认手续。例：业主 23:59:58 签字、楼代次日 09:00 上传、主任次日 14:00 核销 → 这张票仍有效。
_Avoid_: 核销时间为准、上传时间为准（前者把行政流程作为业主意志门槛，后者把网络速度作为业主意志门槛）

### 议题→议案上游链路

**业主联署立议门槛 (Owner Initiation Quorum)**:
业主提议 COMMUNITY / BUILDING / UNIT 议题需达到联署门槛才能进议案池。**比例 + 下限取大**保证小区规模解耦：
- COMMUNITY 议案：5% 户数 / 10 户取大；
- BUILDING 议案：该楼栋 10% / 5 户取大；
- UNIT 议案：该单元 30% / 3 户取大（单元小，比例需稍宽）。

落 `t_subject_initiation(initiation_id, tenant_id, scope, reference_id, title, description, initiated_by_owner_id, signature_count, signature_required, status, opened_at, closed_at)` + `t_subject_initiation_signature(signature_id, initiation_id, owner_id, signed_at, evidence)`。**联署有效期 30 天**：未达门槛自动 `ABANDONED_BY_TIMEOUT` + 通知发起人。配 `application.yml`: `proposal-initiation.owner-signature-ratio` / `owner-signature-floor` / `signature-collection-window-days`。
_Avoid_: 固定人数（小区规模差异严重不公平）、单业主立议（民意基础不足）

**业委会自提议无需联署 (Committee Self-Initiation)**:
业委会代议制本身就是"业主授权的议案发起器"，自提议（换物业 / 修订管理规约 / 启动审计）走业委会内部决议简单多数 ≥ 50%（复用 [Q9 ESCALATED_PARALLEL] 的 `t_committee_internal_vote`）即可立项，**不需要业主再联署**——双层授权多余且效率低。落 `t_subject_initiation.initiated_by_role=COMMITTEE` + `committee_resolution_id` 关联具体决议行。**主任单方提议**不被允许——单点权力过大，反治理。
_Avoid_: 主任单方提议、业委会决议 + 业主联署（前者权力集中，后者效率低）

**物业 / G 端 / 党组提议路径差异 (Cross-Role Initiation Paths)**:
不同角色提议路径严格分级：
- **物业经理 (`PROPERTY_MANAGER`)**：可写议题到 `t_subject_initiation` 但状态 `PROPERTY_PROPOSAL`，需**业委会主任 / 业委会内部决议采纳**才进议案池——物业不是业主授权代表，提议必须经业主代议机构审视。月提议上限 5 条；
- **街道办 (`GOV_SUPER_ADMIN`)**：可发起 `subject_type=ADMINISTRATIVE_MANDATE` **行政整改议题**（垃圾分类 / 消防改造等国家政策强制项），跳过业委会审议直接进议案池；投票门槛与普通议案一致（双 2/3 / 局部双过半），但**业主反对结果只触发"行政复议程序"** 而非否决——国家政策强制不可被小区民意推翻；行政整改议题不限月配额；
- **党组书记 (`PARTY_SECRETARY`)**：作为 [Q12 G端前置审查] 的裁判员，**不能**直接提议——否则裁判员 + 运动员混淆，破坏前置审查的中立性。

落 `t_subject_initiation.initiated_by_role` + `subject_type` 双列标识来源。
_Avoid_: 物业直接进议案池、党组书记可提议（前者绕开业委会代议制，后者裁判员运动员混淆）

**议题审议 30 天硬约束 (Committee Deliberation 30-Day Hard Limit)**:
议题进议案池后业委会必须在 30 天内出结论：
- **采纳**：业委会会议形成决议 → `status=PROMOTED_TO_SUBJECT` → 走 [Q13 议案登记预校验] 三层校验后进 `t_voting_subject`；
- **驳回**：作废 + `rejection_reason` + 通知发起人；
- **修改后采纳**：业委会修改议题描述 / 资金来源 / scope 后采纳，需**通知所有联署人重新确认**（联署人 ≥ 50% 同意修改方为有效），保护原联署人意愿不被篡改；
- **30 天过期未审**：自动 `ABANDONED_BY_COMMITTEE` + escalation 通知街道办——业委会**不能压议题不审议**，沉默就是治理失格。

落 `application.yml.platform.proposal-initiation.committee-deliberation-window-days: 30`。
_Avoid_: 业委会无限期审议、修改不通知联署人（前者堵民意通道，后者篡改业主意志）

**议题滥用配额 (Initiation Abuse Quota)**:
防恶意刷议题占用业委会审议带宽 / 民意通道堵塞：
- 业主单月提议上限 3 条（`platform.proposal-initiation.owner-monthly-limit: 3`），超过拒；
- 联署不达门槛 3 次的发起人 → 6 个月冷却期；
- 业委会驳回率 100% 且 ≥ 5 条的发起人 → 6 个月冷却期；
- 物业经理月提议上限 5 条；
- G 端行政整改议题不限——国家政策强制不能用配额堵。

落 `t_subject_initiation_quota(user_id, monthly_count, ytd_rejected_count, ytd_signature_failed_count, cooling_period_until)`。
_Avoid_: 无限提议、固定 1 条上限（前者刷议题瘫痪，后者把正当民意需求堵死）

**业委会缺位时街道办临时议题托管 (Committee-Vacancy Trusteeship)**:
未成立业委会 / 业委会全员被罢免 ([业委会整体罢免]) 期间议题审议交街道办：`PARTY_SECRETARY` / `GOV_SUPER_ADMIN` 行使临时业委会职责，议题审议走 G 端工作台；通过的议案 `subject_type=GOV_TRUSTEESHIP_PERIOD`，业主端可清晰看到"由街道办行政托管期间发起"。新业委会就任后托管期议案的审议权移交，但已 APPROVED 的托管期议案保留效力（避免回溯)。
_Avoid_: 缺位即停摆、街道办成为常态议题审议人（前者治理瘫痪，后者越位）

### 议案结算与下游 hook 链

**议案结算双轨触发 (Settle Dual-Trigger)**:
`AbstractVotingEngine.settle()` 不依赖单一调度，主路径 + 懒触发双轨：
- **主路径**：`@Scheduled(fixedRate = 60000)` 每分钟扫 `t_voting_subject WHERE vote_end_at < now() - INTERVAL '60 seconds' AND status='IN_VOTE'`，逐个调 settle；
- **懒触发**：业主访问议案 / 业委会查看统计 / 工程款付款前置校验时，若发现议案到期未结算立即同步 settle；
- **去重**：Redis `lock:settle:subject:{id}` 锁（30s TTL）+ `t_voting_subject.version` 乐观锁双层。冷议案不会"无人访问永远 IN_VOTE"，HANDOVER_LOCK 检查也不失效。

**不**走 lazy-only / DB pg_cron / 单 @Scheduled——前者冷议案永漏，中者业务下沉到数据库不可维护，后者纯定时最大延迟 60s 但无懒触发兜底。
_Avoid_: 业主拉触发、定时任务（前者冷议案漏掉，后者无懒触发兜底）

**Settle 事务最小化 + Outbox 下游 (Settle Tx Minimal + Outbox Hooks)**:
settle 事务**只**动两张表 + 写 outbox：
1. `t_voting_subject.status` 推进到 APPROVED / REJECTED；
2. `t_maintenance_fund_account.frozen_balance` 调整：APPROVED 议案 `frozen += approvedAmount`（若未在 [Q13 议案登记预冻结] 锁）；REJECTED 议案 `frozen -= preFrozenAmount` 释放预冻结；
3. INSERT `t_outbox_event(event_type='SUBJECT_SETTLED', subject_id, result, payload)`。

下游所有 hook（财务公示快照刷新 / 通知发起人 / 触发 [Q12 党组审查 PENDING_PARTY_AUDIT] / 触发 [模式切换] 强约束检查 / 触发 [业委会整体罢免] 证书撤销 / 触发 [议案级动态权限] 撤销）**全部走 outbox 异步消费**——党组审查是天级流程不能阻塞 settle；通知发送是外部 API 高失败率。outbox 自带重试 + 死信队列，复用 [TRUST_CHAIN Outbox 状态机] 同构基础设施。

settle 失败 → 整个事务回滚，无副作用；hook 失败 → outbox 重试不影响议案状态。
_Avoid_: settle 同事务执行所有 hook、settle 通过 PendingApproval 回写状态（前者外部失败压死议案结算，后者状态机污染）

**Settle 失败处置 (Settle Failure Handling)**:
- **瞬时重试**：[议案结算双轨触发] 下次扫到自动重试；懒触发返回 `SETTLE_PENDING` 异常 + 业主端"结算中"提示，60s 后客户端重拉；
- **连续 5 次失败**：议案进 `SETTLE_FAILED` 终态前置态，需街道办 (`GOV_SUPER_ADMIN`) 工作台手工介入审核 + 重置 `version` 后再触发 settle；
- **永不静默失败**：失败必落 `t_subject_settle_failure(failure_id, subject_id, attempt_count, last_error_message, last_attempted_at, evidence_payload)` 留痕，作为事后审计根据。

不允许"失败 → 自动 REJECTED"或"失败 → 自动 APPROVED"任一兜底——议案民意结果不能由系统故障决定。
_Avoid_: 失败默认通过、失败默认拒绝（任一都把系统错误推给业主）

**投票截止缓冲期 + SERIALIZABLE (60-Second Buffer + Serializable)**:
`vote_end_at` 后给 60 秒缓冲期，settle 任务**跳过**该议案：
- 缓冲期内允许线下票延迟上传 / 异议提交 / 楼代核销补录（与 [投票截止以业主签字时间为准] 对齐——签字时间 ≤ `vote_end_at` 但楼代/主任行政流程慢的情况合法）；
- 缓冲期 60s 后才结算（`vote_end_at + 60s < now()`）；
- 结算事务用 `SERIALIZABLE` 隔离级别——避免 phantom read（settle 读 N 票时同时有人投了第 N+1 票）；
- `SERIALIZABLE` 性能成本是单议案 settle 一次，可接受。

落 `application.yml.platform.voting.settle-buffer-seconds: 60`。
_Avoid_: 截止瞬间立即 settle（漏掉缓冲期内的线下补录）、READ_COMMITTED（phantom read）

**Settle 不被 HANDOVER_LOCK 阻塞 (Settle Bypasses Tenure Lock)**:
settle 是**民意结算**而非资金动账——`@TenureGuard` 不拦截 settle 本身。但下游执行 hook（frozen_balance 调整 / 资金划拨 / 大额支付）由 `@TenureGuard` 拦截：
- HANDOVER_LOCK 期间 settle 正常出 APPROVED；
- outbox event `SUBJECT_SETTLED` 派发；
- 下游"frozen_balance 大额操作"消费时被 `@TenureGuard` 拒绝 → outbox event 进 `STATUS=RETRY` 队列；
- 等 HANDOVER_LOCK 解除（新业委会备案完成）后 outbox 自动重试 → frozen_balance 操作放行。

这是把 [Q14 换届议案分阶段封存] 的"小额可登记大额 hold"机制天然落到 outbox 重试节奏上，无需新增专用状态。
_Avoid_: HANDOVER_LOCK 锁住 settle、settle 后跳过 TenureGuard（前者民意被行政程序冻结，后者老业委会突击花钱）

### 投票期中间结果可见性

**业主中间可见性分级 (Owner Mid-Vote Visibility Tiering)**:
投票期 `IN_VOTE` 内业主可见数据按粒度分级，避免**羊群效应**（早投票者引导后投票者）但保参与率：
- **始终可见**：议案标题 / 描述 / 资金来源 / 投票期 / 业主自己的投票时点 + 选项；
- **总参与度可见**（赞反比例屏蔽）：`已投票人数 / 应投票人数`（如 "已投 142 / 250 户"）让业主感知热度；
- **投票期最后 24 小时**：开放赞成 / 反对比例（让中间犹豫的业主基于民意趋势决定）；
- **投票期结束后**：完整结果公开（赞成数 / 反对数 / 弃权数 / 各楼栋分布）。

应用层 `VotingVisibilityResolver.resolve(subject, role)` 按时点 + 角色返回可见字段集。
_Avoid_: 完全开放（羊群效应）、完全屏蔽（业主无感参与率跌）

**楼代/主任未投名单可见 + 选项屏蔽 (Catcher Sees Who-Pending Not What-Voted)**:
[议案级动态权限] 激活的楼代 / 业委会主任在 `IN_VOTE` 期可见**未投票业主名单**（业主姓名 + 楼号 + 联系方式 + 联系记录），用于催票；**不能**看到任何"已投业主选了什么"。物业经理仅可见"总参与度"——物业不是治理代议人，无催票权。这条规则的核心：催票只能催"快投票"不能催"投赞成 / 反对"——所以"已投业主选项"必须屏蔽，否则楼代精准说服反对者改投破坏独立意志。落 `VotingProgressView.pendingOwners(subject_id, currentUserRole)` 仅返回 `[(owner_id, name, building_id, phone, contact_log)]` 不返回 `vote_choice`。
_Avoid_: 楼代知选项、物业可催票（前者破坏独立意志，后者越权）

**G 端中间可见性边界 (Gov-End Mid-Vote Boundaries)**:
- **党组书记 (`PARTY_SECRETARY`)**：投票期内**全屏蔽**——党组作为 [Q12 G端前置审查] 裁判员仅在议案 APPROVED 后进审查环节，投票期内提前介入即裁判员越界进民意场域；落地为 `VotingVisibilityResolver` 对 `PARTY_SECRETARY` 在 IN_VOTE 时返回与普通业主同粒度（不见赞反比例）；
- **街道办 (`GOV_SUPER_ADMIN`)**：投票期内可见全粒度（赞反比例 / 个人选项 / 楼栋分布），行政监督权限**只读不可写**——系统不向街道办开放"修改投票" API；
- **审计师 ([Audit Engagement])**：engagement 内可见 [AUDITOR_ENGAGEMENT_SCOPE] 范围议案全粒度，同样只读。
_Avoid_: 党组书记可见、街道办可写（前者裁判员越界，后者行政干预民意）

**投票统计批处理刷新 (Voting Progress Batch Refresh)**:
`VotingProgressCalculator` 每 5 分钟扫所有 `IN_VOTE` 议案，统计参与度 / 赞反比例 / 楼栋分布写到 Redis `voting:progress:subject:{id}` (TTL 6 分钟)；业主端 / 楼代端通过 Redis 读取，不直接打 DB。**5 分钟延迟对民意场域无意义**（投票决策周期是天级），却避免 3000 户大区每秒 N 次 DB 查询压垮主库。投票最后 24 小时收紧到 1 分钟刷新（业主紧迫感 + 进入 [业主中间可见性分级] 第二档）。落 `application.yml.platform.voting.progress-refresh-seconds: 300` / `progress-refresh-final-day-seconds: 60`。
_Avoid_: 实时刷新（DB 压力）、定时归档（节奏太慢业主无感）

**投票数据永久保留与分级查询 (Voting Data Retention)**:
议案归档后投票数据**永久保留**但分级查询：
- 业主：自己投了什么 + 议案总结果 + 自己楼栋的分布（前两者无限期可查，后者议案归档后 1 年内可查）；
- 业委会主任：议案归档后 3 年内可查个人投票明细（用于换届审计的 [业委会整体罢免] / [换届议案分阶段封存] 流程取证）；
- 街道办：永久查任何粒度；
- 3 年后业主投票明细物理归档到冷库（如 PG `pg_partition` 时间分区 + 异步拉），查询走 Async Job。

冷库归档配置 `application.yml.platform.voting.archive-after-years: 3`。**不**做"1 年销毁"——社区治理事后追溯（罢免、行政诉讼、换届审计）的法律时效远长于 1 年。
_Avoid_: 1 年销毁（事后追溯失效）、永久热存（DB 体积爆炸）

### 工程验收与议案归档

**工程验收分级 (Acceptance Tiering)**:
预算金额决定验收主体——单点权力随金额阶梯放大：
- **|预算| < `large_amount_threshold`（默认 5 万）**：业委会主任单方验收；
- **5 万 ≤ |预算| < 50 万**：业委会内部决议过半数验收（复用 `t_committee_internal_vote`）；
- **|预算| ≥ 50 万**：业委会内部决议过半数 + **3 户业主代表现场监督签字**（业主代表由议案联署人中**业务系统随机抽签**产生，避免主任指定亲信）；
- **特定专业工程**（电梯改造 / 消防系统 / 防水大修）：业委会决议 + 第三方专业机构检测报告 + 街道办备案——这条与金额阶梯**叠加**，即专业工程不论金额都需第三方报告。

落 `t_proposal_acceptance(acceptance_id, proposal_id, accepted_kind, accepted_by_committee_resolution_id, owner_witness_ids JSONB, third_party_cert_url, accepted_at, status)` + `accepted_kind ∈ {DIRECTOR_SOLE, COMMITTEE_RESOLUTION, COMMITTEE_PLUS_OWNER_WITNESS, COMMITTEE_PLUS_THIRD_PARTY}`。
_Avoid_: 主任一刀切验收、业委会内决议一刀切（前者大额单点风险，后者小额工程效率拖死）

**决算金额双轨 + 差额事后审查 (Final Amount + Variance Audit)**:
议案 CLOSED 时 `t_proposal_state.final_amount = SUM(t_proposal_payment_installment.amount)` 实付落库；同时**决算 PDF 必须上传**到 `t_proposal_settlement_report(report_id, proposal_id, total_paid, total_budget, variance_ratio, invoice_summary_json, pdf_url, signed_by_director_user_id, signed_at)`，含发票切片 + 各期付款明细 + 验收报告：
- 差额 |variance_ratio| ≥ 10%：必须有书面解释 + 业委会决议同意；
- 差额 |variance_ratio| ≥ 30%：自动触发 [Q12 G端前置审查] **事后抽查**——党组书记复审决算合规，发现舞弊触发追偿议案。

**事前审查 vs 事后审查对称**：[Q12 G端前置审查] 防"突击花钱"，决算超支审查防"超支舞弊"，两者由同一 `PARTY_SECRETARY` 主体走同样的 C1-C5 客观证据链路，避免"事前批了事后无人复查"的责任真空。
_Avoid_: 实付即决算（超支无追溯）、决算 = 预算（与现实脱节）

**议案 CLOSED 四态 (Proposal Close Kind)**:
`t_proposal_state.close_kind` 分四种语义，frozen 释放节奏不同：
- `NORMAL_CLOSED`：正常完工，所有付款节点付清 → `frozen -= paid` 完全释放，paid = 议案总额；
- `EARLY_CLOSED`：工程量减少 / 协商解约 → `frozen -= paid` 释放，未付部分自然留账户、不再支付；
- `ABNORMAL_CLOSED`：工程商违约 / 工程烂尾 → 剩余 frozen **不一刀切释放**，由业委会发起"违约金追偿议案"重新立项追责，frozen 保留作为诉讼追偿期内的资金兜底，等追偿议案出终态再决定释放金额；
- `RESCINDED_BY_AMENDMENT`：被 [议案变更] 覆盖（变更议案重立 + 新议案接管原工程） → frozen 转移到新议案，不释放也不重复冻结。

落 `application.yml.platform.proposal.abnormal-close-litigation-window-months: 24`（异常 close 后追偿议案的法律窗口期，超期 frozen 自动释放）。
_Avoid_: close 一刀切释放 frozen（异常 close 时资金被吃光无法追偿）、close 不分类型（语义混乱）

**验收报告公示与 TRUST 议案级别上链 (Acceptance Report On-Chain)**:
- **LUMP_SUM / FUND_RAISING**：决算 PDF 进 [财务公示快照] 下个公示窗口 payload，业主可下载；议案完结事件以 `event_type=PROPOSAL_CLOSED` 进 outbox，触发下次 compose 拉取；
- **TRUST**：议案完结时**触发一次议案级别**的 [信托双签证票上链] —— 物业经理 + 主任对决算 PDF hash + 议案 ID + 累计支付总额 hash 拼包后双签 → 进 outbox `event_type=TRUST_CHAIN, kind=PROPOSAL_CLOSE` 上链。这是 TRUST 模式下 **议案级别**（与每期付款级别独立）的最后一次上链——业主在 C 端能看到"工程完结存证 TxHash"作为法律意义上的不可篡改终态证明；
- 业主在 C 端"我的小区 / 工程公示"看到验收报告 + (TRUST 模式下额外的) 完结 TxHash + 链上区块号。

议案级别上链不能省——逐期付款 hash 链 + 议案完结 hash 形成完整可验闭环，缺其一即业主无法在司法层面证明"这个工程整体完结了"。
_Avoid_: 仅每期上链不议案完结（缺整体存证）、仅议案上链不每期（缺过程穿透）

**质保期质量异议 (Warranty Period Quality Dispute)**:
议案 CLOSED 后业主对工程质量不满走独立链路，**不**回退议案状态：
- 议案 CLOSED 时记 `t_proposal_acceptance.warranty_period_months`（默认 24 个月）；
- **质保期内**业主联署门槛**降低**（3% / 5 户取大，比 [业主联署立议门槛] 低一档），可发起"工程质量异议"议题 → 业委会审议 → 委托第三方检测 → 出报告；
- 若证实质量问题：发起**追偿议案**重新立项追工程商责任（独立的 `t_voting_subject` 行 + 独立 frozen + 独立 G 端审查），与原 CLOSED 议案脱钩；
- **CLOSED 议案状态不回退**——已结算的就是结算了，不能逆向修改账面；追偿走民事诉讼路径。

落 `t_proposal_quality_dispute(dispute_id, original_proposal_id, raised_by_owner_id, evidence_image_urls, third_party_inspection_report_url, status, follow_up_proposal_id)`。
_Avoid_: CLOSED 状态可回退（账面被反复修改无审计意义）、CLOSED 后不可异议（质保期民法权利被剥夺）

**议案再发起 6 个月冷却期 (Same-Topic Cooling)**:
同主题议案（按 `subject_kind` + `reference_id` + `purpose_hash` 匹配）CLOSED 后 6 个月内不能再发起，避免"投不通过就不停发起"刷议题或"工程刚做完又要做"重复立项。配置 `application.yml.platform.proposal.same-topic-cooling-months: 6`。**例外**：[质保期质量异议] 走独立链路不受冷却限制；紧急工程（如灾害修复）走 [Q9 GOV_EMERGENCY_TAKEOVER] 街道办行政托管路径绕过冷却。
_Avoid_: 无冷却（民意通道堵塞）、1 年冷却（连环漏水问题被堵死）

### 招投标与工程商资质

**招投标分级发包 (Procurement Tiering)**:
预算金额决定采购形态——单点权力随金额阶梯放大：
- **|预算| < 5 万 (`large_amount_threshold`)**：业委会主任 / 物业经理可单方指定（`procurement_kind=DIRECT_AWARD`），小额工程效率优先；
- **5 万 ≤ |预算| < 50 万**：业委会内部决议过半数 → ≥ 3 家工程商**比价采购**（`COMPETITIVE_QUOTATION`）；
- **|预算| ≥ 50 万**：必须**正式招投标**（`PUBLIC_TENDER`）—— ≥ 5 家投标 + 业主代表评标 + 街道办备案；
- **业主大会双 2/3 议决工程**：在议案 IN_VOTE **之前**确认工程商，议案投票时披露中标方与合同细节，业主投的不是"做不做"而是"做不做 + 是 X 工程商做"——避免"先批预算后选商"导致主任在选商环节舞弊。

落 `t_proposal_procurement(procurement_id, proposal_id, procurement_kind, candidate_count, opened_at, sealed_at, awarded_vendor_id, awarded_amount, public_announcement_url)`。
_Avoid_: 主任单方发包、一刀切招投标（前者大额舞弊高发，后者小额工程效率拖死）

**工程商资质平台 + 小区双层 (Two-Tier Vendor Qualification)**:
- **平台级 vendor 库** `t_vendor_qualification(vendor_id, business_license_no, license_type, qualification_levels JSONB, blacklist_until, certified_by_gov, status)`：工商执照 + 行业资质（防水二级 / 电梯安装一级 / 物业服务二级等）+ G 端街道办或行业协会认证；跨小区共享省去重复入库；
- **小区级 vendor 偏好** `t_tenant_vendor_preference(tenant_id, vendor_id, status IN ('WHITELIST','BLACKLIST'), reason, evidence_url)`：业委会可对特定 vendor 加黑或加白；
- 工程招标时**默认仅向平台级 status=ACTIVE 且未在小区级 BLACKLIST 的 vendor 开放**；小区级 WHITELIST 不能绕过平台级 BLACKLIST（平台 > 小区，防业委会强加白被平台拉黑的 vendor）。

**反方案**：纯小区级（重复劳动严重）、纯平台级不可拒绝（业委会被强迫接受平台 vendor 反治理）。
_Avoid_: 工程商白名单、Vendor 注册（前者太宽不分层级，后者技术词不带语义）

**工程商双层黑名单 (Two-Tier Vendor Blacklist)**:
- **平台黑名单**（影响所有小区）触发条件：工商执照吊销 / 业绩造假 / 司法判决违约 ≥ 3 起 / 监管处罚通报。落 `status=BLACKLIST` + 街道办背书；
- **小区黑名单**（仅本小区）触发条件：议案完结后 [质保期质量异议] 成立 → 业委会内部决议过半数加黑；投标过程中围标 / 串标证据 / 行贿 ≥ 1 起；业委会投诉 + 街道办核查证实；
- **默认期限 24 个月**，到期自动 `status=ACTIVE`，但可手动延长。

落 `t_vendor_blacklist_history(history_id, vendor_id, scope IN ('PLATFORM','TENANT'), reason, evidence_url, blacklisted_at, blacklisted_until, lifted_at)`，永久保留作为事后审计根据。
_Avoid_: 永久黑名单（vendor 整改无路径）、无追溯（黑名单恢复后无审计链）

**中标公告完整披露 + 报价匿名 (Full Award Disclosure)**:
中标后业主在 C 端可见：
- **中标方公开**：公司名 / 资质等级 / 历史业绩；
- **报价**：中标价 + 同期所有投标方均价 / 中位数 / 最高最低，**未中标方具体公司名保密**（保竞争秘密但价位公开）；
- **评标过程**：业主代表 / 评分细则 / 评分结果；
- **关联方申报**（[Q12 关联方申报+穿透核查双轨]）：业委会成员是否与中标方有亲属 / 合伙 / 同班同学等关系穿透核查。

未中标方匿名是行业惯例（透明价格但保留参与各方商业秘密），关联方申报是反舞弊核心——这两条不可省。
_Avoid_: 仅披露中标方、披露所有投标方公司名（前者不透明，后者破坏竞争秘密）

**紧急 + 不可替代豁免招投标 (Procurement Exemptions)**:
两条豁免路径**互斥**，但都不豁免关联方申报：
- **紧急豁免**：`is_emergency=true` + `emergency_evidence_image_urls JSONB` + 业委会主任先签批，30 天内必须由业委会内部决议追认；30 天内未追认自动转入 [Q9 GOV_EMERGENCY_TAKEOVER] 行政托管异议期；月度配额 ≤ 3 笔（与 [紧急刚性开支] 配额对齐）；
- **不可替代豁免**：`is_irreplaceable=true` + 第三方专业评估报告（电梯品牌原厂限定 / 消防系统专利限定）+ 业委会内部决议过半数 + 街道办备案；不限月度配额但所有 IRREPLACEABLE 案例进 G 端年度抽查池——抽查率 100%（vs 普通议案抽样）；
- **共同硬约束**：豁免不豁免关联方申报——业委会成员仍按 [Q12 关联方申报] 披露，否则即使豁免也算违规。

落 `t_proposal_procurement.exemption_kind ∈ {NONE, EMERGENCY, IRREPLACEABLE}` + `exemption_evidence_url`。
_Avoid_: 紧急豁免一切、专业豁免免申报（前者绕开业委会代议制，后者关联方舞弊高发）

**招标流标兜底 (Procurement Stall Resolution)**:
投标家数不足或中标价超预算时分级处置：
- 投标家数 < 法定最低（比价 3 家 / 招投标 5 家）→ `procurement_kind=FAILED` 流标，必须重新邀标或议案变更；
- 中标价超预算 ≥ 30% → 业委会决议是否流标，流标后走 [Q18 议案变更阈值差异化]；
- 同议案最多重新招标 3 次，3 次仍流标议案进 `STALLED` 终态前置态 + 触发**业主大会决议**是否撤回原议案（撤回后 frozen_balance 释放走 `RESCINDED_BY_AMENDMENT` 路径）。

`application.yml.platform.procurement.max-retry: 3` / `over-budget-stall-ratio: 0.30`。
_Avoid_: 流标即关、无限重招（前者议案僵死，后者拖死施工窗口）

### 司法链切换与多 provider 共存

**Mock 链 + 真链双轨并存 (Mock Chain & Real Chain Dual-Track)**:
TRUST 模式 M3 内测期用 mock chain provider 跑通双签 → outbox → mock TxHash 全链路；M5 接最高院 / 地方司法链等真 provider。**不**做 cut-over 一刀切：
- `t_outbox_event` 增列 `chain_provider VARCHAR(32)` + `chain_provider_version VARCHAR(16)`（值如 `mock-v1` / `supreme-court-v1` / `ant-chain-v1`）；
- 真链上线 T0 后**所有新 voucher 走真链**，但 mock 历史数据保留**只读**业主端继续可见；
- 业主 C 端看板对 mock 数据加灰色徽章 "mock 链 (M3 内测期)"，对真链数据加 "司法链存证"；
- **mock TxHash 永不重新生成**——重新生成等于篡改历史，破坏 [证书撤销时点效力] 的时点稳定性。

落 `application.yml.platform.judicial-chain.active-provider: supreme-court-v1`，旧 provider 数据通过 `chain_provider` 列检索。
_Avoid_: 一刀切 cut-over、双链都写（前者业主历史数据失访，后者双倍成本无意义）

**Mock 数据不 backfill 真链 (No Mock-Era Backfill)**:
mock 期已上链的 voucher（M3 期 6 个月可能积累 1 万行）**不**补登真司法链：
- mock 数据语义即"M3 内测期数据"，业主已知测试性质；
- backfill 等于把测试数据提交真司法链，法律层面可能引发歧义（"补登"构成事后伪造嫌疑）；
- 真链上线公告中明示"M3 内测期数据为系统试运行记录，正式法律证据从 T0 开始"；
- 业主想升级单笔 voucher 也不开放申请通道——单笔补登的复杂度 + 法律风险 > 用例价值。

mock 数据进入真链通路一律拒：`JudicialChainGateway.publish(voucher)` 校验 `voucher.chain_provider` 与当前 active-provider 一致才放行。
_Avoid_: 全量 backfill、业主可申请 backfill（前者法律风险，后者复杂度过高）

**Mock vs 真链证据等级差异 (Evidence Tier Difference)**:
- **Mock 期 voucher**：系统对外明示 mock 期数据**不具备司法链不可篡改性**；mock TxHash 可作为**普通电子证据**（双签 + 时间戳 + 付款记录）走民事诉讼证据规则，但不享受司法链等级的"举证倒置 / 自动采信"待遇；
- **真链 voucher**：上司法链后享受司法链证据规则（如《最高人民法院关于互联网法院审理案件若干问题的规定》第 11 条）；
- 业主端 UI 必须显示这个差异，避免业主误以为 mock 数据具有等同司法链效力。

**真链上线公告**是法律切换的时点锚点（写到 `t_legal_announcement(announcement_id, announcement_kind='CHAIN_PROVIDER_GO_LIVE', effective_at, content_url)` + 街道办背书 + C 端置顶推送）。
_Avoid_: mock = 真链等价（法律不允许）、不告知业主差异（侵害业主知情权）

**Provider 抽象 + 单 voucher 链 ID 不可改 (Provider Abstraction)**:
- `JudicialChainGateway` 接口在 mock 阶段已存在，真链阶段用 `SupremeCourtChainGateway` / `LocalCourtChainGateway` 实现，业务侧零侵入；
- 单 voucher 上链后 `chain_provider` 落库**永久不可改**——历史数据不能因 provider 变更被改写；
- 不同时间窗口可用不同 provider（业主端按 chain_provider 反查对应链浏览器跳转）；
- 未来切换新 provider（如最高院 → 蚂蚁链）走"双轨期"渐进切：旧 voucher 仍可在旧链 / 旧链浏览器上验证，新 voucher 走新链；
- **多 provider 同时活跃**也允许（如不同租户用不同 provider），`tenant.judicial-chain-provider` 列覆盖 application.yml 默认值。

不强制全部迁移到新 provider 与 [Mock 数据不 backfill 真链] 同源——历史不可改写。
_Avoid_: 单 provider 锁死、强制全量迁移（前者灵活性丧失，后者历史改写）

**SM2 USBKey 证书与 provider 解耦 (Crypto Decoupled from Chain)**:
SM2 USBKey 证书由 CA 签发，与司法链 provider **无关**：
- 切链时证书 / 签名格式 / [验签双查] 流程**全部不变**；
- 司法链 provider 接收 SM2 签名 + 证书链验证（CA 公钥），与本地 [Sm2UsbKeyVerifier] 一致；
- 例外：若某 provider 不支持 SM2 必须 RSA / ECDSA → 是 provider 选型阶段就该排除的情况，不应到运行期才发现——若发生，单独走"证书与签名格式重新签发 + 业主重新培训"流程；
- 这条与 [模式切换瞬间不杀在飞交易] 同源——基础设施切换不应让用户感知到加密层重新培训。
_Avoid_: 切链刷新证书、绑定 provider 的证书（前者运营成本极高，后者破坏抽象）

### 业主异议升级链路统一通路

**异议主表 + 业务附属 (Unified Dispute Master Table)**:
业主异议（[业主行政异议] / [行政复议反制] / [质保期质量异议] / [线下票异议期]）归一到统一通路，避免 union 4 张表 + 升级路径割裂：
- **主表** `t_owner_dispute(dispute_id, tenant_id, raised_by_owner_id, dispute_kind ENUM, related_entity_type, related_entity_id, current_review_level SMALLINT, status, raised_at, escalated_at, closed_at, business_payload JSONB, ...)`；
- **附属表** `t_dispute_evidence(evidence_id, dispute_id, evidence_kind, content_url, uploaded_at)` —— 业主上传的证据材料（照片、文档、视频、第三方鉴定书）；
- **附属表** `t_dispute_review_decision(decision_id, dispute_id, review_level SMALLINT, decided_by_user_id, decision_kind ENUM(UPHELD/REJECTED/PARTIAL_UPHELD), decision_content, decision_doc_url, decided_at)` —— 每一级行政机关的决定书；
- `dispute_kind ∈ {EXPENSE_VOUCHER_DISPUTE, PROPOSAL_QUALITY_DISPUTE, OFFLINE_VOTE_DISPUTE, ADMINISTRATIVE_REJECTION_DISPUTE}` 区分语义，业务专属字段进 `business_payload JSONB`（如 `EXPENSE_VOUCHER_DISPUTE` 存 `voucher_id` / `disputed_amount`，`OFFLINE_VOTE_DISPUTE` 存 `proposal_id` / `voter_uid_list`）；
- 业主"我的异议" C 端页面 `SELECT * FROM t_owner_dispute WHERE raised_by_owner_id = ?`，单表查询统一时间线；
- 不走 [选项 A 每类异议独立表]——每类一张表会让"我的异议"页 union 4 表 + 升级路径状态机分裂。
_Avoid_: 每类异议独立表、完全统一字段无 JSONB（前者割裂查询路径，后者过度抽象牺牲业务语义）

**异议升级 5 级状态机 (5-Level Escalation State Machine)**:
异议从一审到行政诉讼层级递进，**严禁跳级**（区政府不可直接告市政府）：
- **Level 1：业委会一审** —— 仅 `EXPENSE_VOUCHER_DISPUTE` 适用（业委会内部争议），7 日出结论；
- **Level 2：街道办二审** —— 业委会 7 日不回复或驳回 → 升 Level 2，14 日内出结论；
- **Level 3：区政府三审** —— 街道办 14 日不回复或驳回 → 升 Level 3，30 日内出结论（[行政复议法] 法定期限）；
- **Level 4：市政府四审** —— 区政府 30 日不回复或驳回 → 升 Level 4；
- **Level 5：行政诉讼** —— 业主放弃行政复议路径直接走法院（与 Level 1-4 互斥，业主选择诉讼即不走行政复议链）；

`t_owner_dispute.status` 状态机：
`RAISED → UNDER_REVIEW_LEVEL_N → DECIDED_LEVEL_N (UPHELD/REJECTED/PARTIAL_UPHELD) → ESCALATED_TO_LEVEL_N+1 → ... → CLOSED_FINAL`

每升级一次 `current_review_level += 1`，不重置 `raised_at`（保留原始时点便于追溯总耗时）。
_Avoid_: 允许跳级、合并 Level 1-4 与 Level 5（前者破坏行政体系，后者忽略业主诉权选择）

**升级时点：DECIDED 或当前级别期限届满 (Escalation Triggers)**:
业主升级到下一级的两条触发：
1. **当前级别 DECIDED REJECTED**：业主拿到本级驳回决定书，可立即点 [升级] 按钮；
2. **当前级别期限届满未回复**：街道办 14 日 / 区政府 30 日不出结论 = 视为"消极拒绝"，业主可点 [升级]；

升级动作：
- 业主在 C 端点 [升级到 Level N+1]，系统校验 `current_review_level < 5` + （`status = DECIDED_*` 或当前 level 期限已届满）；
- 落 `escalated_at` + `current_review_level += 1` + `status = ESCALATED_TO_LEVEL_N+1`；
- 自动通知上一级行政机关账号（街道办账号 / 区政府账号）+ 待办列表入项；
- 上级有限期 + 自动催办（到期前 3 日推送 + 到期当日推送 G 端）；

**不允许**：DECIDED UPHELD 或 PARTIAL_UPHELD 后业主仍升级——本级已部分支持，强行升级视为滥用诉权（业主可选 [接受决定] 或 [对未支持部分单独升级]，后者作为新 dispute 立项）。
_Avoid_: 必须 DECIDED 才能升级（消极拒绝无法救济）、随时可升级（破坏行政层级）

**Level 5 系统作为证据档案库 (System as Evidence Archive)**:
业主升 Level 5 行政诉讼后系统**不参与诉讼审理**，但**必须保留完整证据链不可篡改**：
- **证据保全清单**：原始单据 + Level 1-4 各级决议书 + 双签证票（TRUST 模式）+ 司法链 TxHash + 投票记录 + 推送日志 + IP / 设备指纹（与 [线下票造假离线监控] 同库）；
- **一键导出诉讼证据包**：业主 C 端点 [导出诉讼证据包] → 系统打包成 ZIP（含 PDF 时间线 + 证据清单 + 司法链验证 URL + 数字签名验证脚本），业主下载后递交法院；
- 证据包带平台 SM2 签名 + 时间戳，确保法院端可独立验证完整性（不依赖平台在线服务）；
- **法院判决回流**：业主 / 街道办手工录回 `t_owner_dispute.litigation_outcome ENUM(PLAINTIFF_WON/DEFENDANT_WON/SETTLED/WITHDRAWN)` + `litigation_judgement_url`（判决书 PDF），作为该 dispute 的真正终态；
- 不集成法院 API（无对接通路 + 法律层面不允许平台直接对接审判系统）；
- 不冻结相关数据（影响其他业务）。
_Avoid_: 系统集成法院 API、诉讼期冻结所有相关数据（前者不可行，后者瘫痪业务流）

**异议升级不冻结主业务 (Dispute Doesn't Block Main Business)**:
异议升级期间相关业务（议案 / voucher / 工程 / frozen_balance）状态保留，**不自动状态回退**：
- `EXPENSE_VOUCHER_DISPUTE` 升级期间该 voucher 资金已划拨**不回退**（与 [业主行政异议] 设计一致——资金追回走民事追偿议案）；
- `PROPOSAL_QUALITY_DISPUTE` 升级期间原议案 [CLOSED_NORMAL] 状态保留，追偿议案独立立项（与 [质保期质量异议] 链路一致）；
- `OFFLINE_VOTE_DISPUTE` 投票结果保留，仅楼代纪律调查（除非 dispute Level 1 已 UPHELD 直接撤销该业主线下票，重算结果走 [Settle 失败处置] 反向回滚）；
- `ADMINISTRATIVE_REJECTION_DISPUTE` 升级期间党组书记原驳回决定保留，议案不重启；
- **最终判决后由街道办（GOV_SUPER_ADMIN）手工根据判决书"修正"业务状态**：
  - 发起反向追偿议案；
  - 撤销原议案（若判决书要求）；
  - 退款（若判决书要求 + 资金来源充足，否则走民事追偿）；
  - 撤销原投票结果（若判决书要求）；
- 系统**不自动**根据 `litigation_outcome` 触发状态回退——判决书的执行口径必须人工解读。
_Avoid_: 异议升级冻结主业务、根据判决书自动状态回退（前者瘫痪业务，后者忽略判决书人工解读必要性）

### 异议证据包的可验证性 vs 隐私边界

**证据包三 Tier 脱敏 + 密封档案凭证 (Tiered Redaction + Sealed Archive)**:
业主一键导出诉讼证据包对**第三方个人 / 机构敏感字段**做分级脱敏，业主端拿脱敏版，原始数据进密封档案：
- **Tier 1 公开字段**（姓名、职务、议案投票选项等业委会成员公开履职信息）：原样保留；
- **Tier 2 业务字段**（双签时间戳、SM2 证书序列号、司法链 TxHash、议案预算金额、各级决议书内容）：原样保留——业务公示链路已含；
- **Tier 3 个人敏感字段**（手机号、身份证号、IP、设备指纹、邮寄地址）：**脱敏后给业主**（如 `138****5678` / `IP: ***.***.45.12` / 身份证 `110***************01`），完整原始数据进密封档案；
- 系统侧 `t_dispute_sealed_archive(archive_id, dispute_id, sealed_payload SM4_ENCRYPTED, key_custodian='SUBDISTRICT', sealed_at)` —— 用 SM4 加密原始数据，密钥托管在街道办（街道办账号才有解密 KMS 权限）；
- 证据包附 [密封档案查询凭证]（含 `archive_id` + 平台 SM2 签名 + 业主身份 + 法院应通过哪个公文向街道办申请解封的指引）—— 法院诉讼立案后凭法院 [调取证据通知书] 向街道办申请解封，街道办校验法院公文真实性后从系统调用 `SealedArchiveService.unseal(archive_id, court_doc_url)` 提取原始数据；
- 解封动作落 `t_sealed_archive_unseal_log(log_id, archive_id, requested_by_court, unsealed_by_subdistrict_user, court_doc_url, unsealed_at)`，业主 / 第三方均可在 C 端 / B 端查看；
- 业主自己拿到的就是脱敏版，不构成 [个保法] 第 13 条越权使用；脱敏版仍允许法院基于姓名 / 职务 / 时间链接定位证据，不破坏诉讼可用性。
_Avoid_: 证据包不脱敏（违反个保法）、所有第三方数据全脱敏到无法识别（破坏诉讼证据有效性）

**证据包 manifest 集中签名 + 司法链锚点 (Manifest Sign + Chain Anchor)**:
证据包**完整性**通过平台 SM2 签名 + 司法链锚点保证，让法院相信业主未中途篡改：
- 导出时平台后端步骤：
  1. 计算每个证据文件 SHA-256 哈希；
  2. 生成 `manifest.json` 含所有文件哈希 + 导出时间 + 业主身份 + 关联 `dispute_id` + 关联 `archive_id` 列表；
  3. 平台 SM2 私钥对 `manifest.json` 签名生成 `manifest.sig`（密钥托管 KMS，业主无法接触）；
  4. **`manifest.json` 哈希值同步上司法链**生成 `export_anchor_tx_hash` 落 `t_dispute_export_audit.export_anchor_tx_hash`；
- ZIP 结构：
  - `evidence/` 各证据原文件；
  - `manifest.json` 哈希清单；
  - `manifest.sig` 平台 SM2 签名；
  - `verify-instructions.md` 法院验证步骤（含平台 SM2 公钥 CA 查询路径 + 司法链浏览器 URL + 司法链锚点 TxHash）；
- 法院收到证据包后：用平台公开 SM2 公钥（CA 签发，CA 公钥可独立查询）验证 `manifest.sig` → 逐文件比对哈希 → 查司法链验证 `export_anchor_tx_hash` 锚定时点；任一步失败可直接判定证据被篡改；
- 不走每文件独立签名（单文件签名无关联，业主可整文件替换）；manifest 集中签名是工程标准做法。
_Avoid_: 业主端 ZIP 直接下载无签名、每文件独立签名（前者证据失效，后者无完整性关联）

**证据包导出限速 + 审计 + 街道办可见 (Export Audit & Rate Limit)**:
单 `dispute_id` 业主限 5 次导出，每次导出留审计：
- 配额上限 5 次覆盖业主反复打官司 / 上诉的合理需求（一审 / 二审 / 再审 / 行政诉讼 / 民事追偿 5 个独立场景）+ 拦截滥用；
- `t_dispute_export_audit(audit_id, dispute_id, exported_by_owner_id, exported_at, manifest_hash, export_anchor_tx_hash, ip, user_agent)` 每次导出落审计行；
- 街道办（`GOV_SUPER_ADMIN`）可见所有导出记录，发现滥用（如同一 dispute 1 日内 ≥10 次 / 同 IP 多 dispute 集中导出）触发账号级限流（24h 冷却）；
- 超过 5 次后业主可向街道办申请额外配额（C 端 [申请额外导出配额] 表单 + 业主说明用途 + 街道办审批）—— 避免合法业主诉权被绝对配额压制；
- 不走仅一审驳回后才能导出（阻断业主提早准备诉讼）；
- **导出配额按 `dispute_id` 计数，不按业主总配额**——避免业主一次大异议把全部配额吃光后续无法导其他 dispute。
_Avoid_: 业主无限次导出、仅街道办 / 一审驳回后才允许导出（前者滥用风险，后者破坏诉权）

**第三方异步通知 + 自身字段查询权 (Third-Party Notification & Self-Inspection)**:
业主导出证据包后系统**异步通知**所有出现在证据包内的第三方个人（业委会主任 / 物业经理 / 楼代等），履行 [个保法] 第 17 条告知义务：
- 通知内容：「您的履职信息已作为某起业主诉讼证据被业主 [脱敏] 在 [时间] 导出，密封档案 ID xxx，您可在 C 端 / B 端查看您被引用的字段清单。」
- 通知方式：站内信 + 短信（短信不含敏感细节，仅引导登录系统查看详情）；
- **第三方查询权严格限定为"自己被引用的字段"**：
  - 仅可查自己的字段（姓名 / 职务 / 引用上下文）；
  - **不可**查业主诉求 / 业主身份 / 其他第三方的字段（避免反向打击报复 + 业主诉权被反向调查）；
  - 业主身份对第三方脱敏显示（如 `业主王先生（X 单元 X 室）`）；
- 第三方对自己被引用内容有异议（如认为业主越权引用其私人数据 / 引用片段失实 / 业委会公开履职边界争议）→ 走 [异议主表 + 业务附属] 反向起 `THIRD_PARTY_REFERENCE_DISPUTE` dispute（新增 `dispute_kind`），首审街道办；
- 不走选项 A 不通知（被动隐私权受损，[个保法] 第 17 条告知义务不满足）；
- 不走选项 C 仅街道办知道（第三方完全不知情同样违反告知义务）。
_Avoid_: 不通知第三方、第三方可查全部字段（前者违反个保法，后者破坏业主诉权）

**业主端无控制 + 系统侧快照保留 7 年 (Snapshot Retention 7 Years)**:
证据包导出后生命周期分两侧：
- **业主端**：业主下载 ZIP 后由业主自己本地管理，系统**不能**远程删除业主已下载文件（不构成对业主已合法持有数据的越权干预）；
- **系统侧**：`t_dispute_export_snapshot(snapshot_id, audit_id, manifest_json, sealed_archive_id_list, snapshot_zip_oss_url, expire_at)` 保留**导出时刻**完整快照 7 年（[档案法] 法定期限 + 覆盖一审 / 二审 / 再审窗口）；
  - 7 年后系统侧清理快照（OSS 文件删除 + 数据库行清理）；
  - **永久保留**字段：`manifest_hash` / `export_anchor_tx_hash` / `audit_id` / `dispute_id`（TxHash 本身不占空间且司法链不可改，便于 100 年后业主后人查询）；
- 与 [TRUST 模式 voucher 永久保留] 不冲突——voucher 本身永久（业务数据），仅"导出动作快照"7 年（衍生数据）；
- 法院日后向平台调取核对（如再审阶段需复核当时导出的证据包内容是否被篡改）凭法院公文向街道办申请，从 OSS 拉取 `snapshot_zip_oss_url` 重新比对 manifest 与 export_anchor_tx_hash。
_Avoid_: 永不销毁、3 个月后清理（前者存储成本无限膨胀，后者短于诉讼周期）

### 第三方反向异议与业主诉权对抗

**THIRD_PARTY_REFERENCE_DISPUTE 白名单四类 (Whitelist of Reverse Dispute Reasons)**:
第三方对自己被引用的内容仅可基于以下四类合法理由反向起诉，避免业主诉权被无差别反向调查：
- **(a) 引用片段失实**：业主提交的"业委会主任 XXX 在 [时间] 说 [内容]"与会议纪要 / 录音 / 议案投票记录不符；
- **(b) 越权引用私人数据**：业主引用了第三方履职范围**外**的私人信息（如业委会主任的家庭住址、配偶信息、子女学校、个人医疗记录）；公开履职信息（姓名 / 职务 / 议案投票选项 / 公开会议发言）不构成越权；
- **(c) 引用上下文断章取义**：业主引用了完整决议的某一句话扭曲原意（如把"建议暂缓施工以核实预算"改写为"反对施工"）；
- **(d) 平台脱敏失败**：[Tier 3 个人敏感字段] 没正确脱敏，泄露了第三方的手机号 / 身份证号；

`t_owner_dispute.dispute_kind` 新增枚举值 `THIRD_PARTY_REFERENCE_DISPUTE`，`business_payload JSONB` 含 `referenced_evidence_path`（指向证据包内具体文件 + 字段路径）+ `dispute_subkind ∈ {a/b/c/d}`；
不在白名单内的异议（如"我不喜欢自己被告"、"姓名不该被提及"）系统直接拦截 + 退回第三方 + 通知"履职信息引用属业主合法诉权范围"。
_Avoid_: 第三方可对任何引用提异议、第三方仅可对脱敏失败提异议（前者业主诉权被反向调查，后者断章取义无救济）

**业主已导出 ZIP / 已立案诉讼不冻结 (No Freeze on Owner Evidence)**:
第三方起 `THIRD_PARTY_REFERENCE_DISPUTE` 后业主已导出的 ZIP 与已立案的诉讼**完全不冻结**，业主诉权 > 第三方反向异议：
- 业主**已导出 ZIP** 是业主合法持有的数据，第三方异议不能让业主下架（与 [业主端无控制 + 系统侧快照保留 7 年] 一致——系统不能远程删除业主已下载文件）；
- 业主**已立案的诉讼**继续走法院流程——法院自己判定证据有效性，不需要平台介入；
- 第三方异议**仅作平台侧"事后纠错通路"**：异议成立后系统侧 `t_dispute_export_snapshot` + `t_dispute_sealed_archive` 打 `dispute_tag ENUM(FACTUAL_INACCURACY/PRIVATE_DATA_OVERREACH/OUT_OF_CONTEXT/REDACTION_FAILURE)` 标签（不删除内容）；
- 未来其他诉讼引用同一证据时争议标签同步可见，提示法院 / 业主"该证据存在第三方异议争议"；
- 业主**未来对同一 dispute 的导出**（5 次配额剩余次数）允许继续，但脱敏字段按异议类型自动强化（详见 [反向异议成立后分级处置]）。
_Avoid_: 冻结业主证据包 + 暂停诉讼、仅冻结争议字段（前者反向打击业主诉权，后者实质等同不冻结徒增复杂度）

**直接街道办一审 + 区政府二审 (Direct Subdistrict Adjudication)**:
`THIRD_PARTY_REFERENCE_DISPUTE` 与 [异议升级 5 级状态机] 走差异化路径：
- **不走业委会一审**——业委会主任可能既是异议人又是审理人，回避缺失；且业主诉权与业委会内部争议是两条独立链路；
- **直接 Level 2 街道办一审**：14 日内出结论；街道办校验白名单（Q29.1 a-d）+ 双方提交证据 + 出 `decision_kind ∈ {UPHELD/REJECTED/PARTIAL_UPHELD}`；
- **驳回后升 Level 3 区政府二审**：30 日内出结论；
- **不升 Level 4 / Level 5**：[行政复议法] 对第三方反向异议无 Level 4 区分；Level 5 行政诉讼业主诉权一旦立法院介入，第三方反向通路应在法院内部解决（如法院判定证据有效性时同步处理第三方异议），不应另起平台侧诉讼链；
- 业主在街道办 / 区政府审理期间作为应诉方有提交反驳证据的权利，平台 C 端 [我的异议-应诉] 入口与业主 [我的异议] 入口区分。
_Avoid_: 业委会一审、业委会其他成员一审（前者回避缺失，后者业委会成员利益绑定）

**反向异议三级频率限制 (Three-Tier Rate Limit)**:
防止第三方滥用反向异议系统性拖延业主诉权：
- **同 dispute 维度**：第三方对同一业主的同一 `dispute_id` **限 1 次** 反向异议（异议被驳回后不可对同一证据再起，避免反复纠缠）；
- **同业主维度**：第三方对同一业主跨 `dispute_id` **1 个月内限 3 次** 反向异议（避免借第三方身份系统性骚扰单个业主）；
- **总量维度**：第三方 1 个月内总反向异议数 **限 10 次**（覆盖业委会主任等多人引用的合理场景，超过则需街道办审批配额）；
- 反向异议被 REJECTED **累计 ≥ 3 次** 的第三方账号自动打 `reverse_dispute_abuse_risk_tag = TRUE`，街道办（GOV_SUPER_ADMIN）可见；
- 配额维度落 `t_reverse_dispute_quota(third_party_user_id, target_owner_id, dispute_id, raised_at)` 单表 SQL 聚合查询（不引入独立计数表，避免事务一致性问题）；
- 超 dispute 维度配额时 C 端入口直接拦截 + 提示"该证据已起过反向异议，如需对其他证据起异议请选择对应文件"；超总量配额走街道办申请通道（防止业委会主任 vs 多业主合理引用场景被压制）。
_Avoid_: 第三方无限次反向异议、第三方仅可起 1 次反向异议（前者瘫痪业主诉权，后者多诉讼场景合理需求被压制）

**反向异议成立后分级处置 (Tiered Remediation by Subkind)**:
街道办 / 区政府出 `UPHELD` / `PARTIAL_UPHELD` 决定后按 `dispute_subkind` 分级处置，避免一刀切惩戒业主：
- **(a) 引用片段失实** / **(c) 引用上下文断章取义**（业主主观选择性引用）：
  - 业主端 [我的异议] 标 `dispute_tag = FACTUAL_INACCURACY` / `OUT_OF_CONTEXT`；
  - 未来导出 ZIP 时强制附 `THIRD_PARTY_DISPUTE_NOTICE.pdf`（含简要决议 + 街道办盖章扫描件），manifest 哈希链同步更新；
  - 业主**无配额惩戒**——记录在档影响诉讼说服力（法院会看），但不剥夺业主继续诉讼的权利；
- **(b) 越权引用私人数据**（业主越权）：
  - 业主端**强制召回原 ZIP**——系统通知业主"原 ZIP 因 [越权引用私人数据] 异议成立，请勿提交法院；新 ZIP 已重新生成（强化脱敏版，越权字段彻底移除）已发送至您的导出列表"；
  - 系统侧 `t_dispute_export_snapshot.recall_status = RECALLED` + 新 ZIP 落新 audit 行；
  - 业主拒不更换 → 街道办可向法院发函说明该 ZIP 含越权引用证据（与 [Level 5 系统作为证据档案库] 一致——平台不是审判方但可向法院说明事实）；
  - 业主累计 ≥ 2 次越权引用异议成立 → 业主侧导出配额冷却 30 天（仅惩戒 b 类越权，不波及 a / c 类）；
- **(d) 平台脱敏失败**（平台技术问题）：
  - **平台担责**——平台向第三方书面致歉 + 落 `t_platform_redaction_incident(incident_id, dispute_id, redaction_failure_reason, fixed_version)`；
  - 业主 ZIP 系统侧重新打包脱敏版自动替换（业主已下载 ZIP 由业主决定是否使用旧版本，但平台主动向法院说明"该 ZIP 存在脱敏漏洞，强化版已发送业主"）；
  - 业主端**无任何惩戒**——业主无主观过错；
  - 触发 [Tier 3 个人敏感字段] 脱敏规则升级评审（平台侧技术债，与业主 / 第三方业务流无关）；
- 不走选项 A 直接惩戒业主（业主可能本身无主观恶意，特别 (d) 平台脱敏失败业主完全无过错）。
_Avoid_: 一刀切直接惩戒业主、仅记录不通知（前者过度惩戒侵害诉权，后者业主继续提交问题证据增大法律风险）

### 业委会换届与人员交接

**换届双轨触发 (Dual-Track Handover Triggers)**:
业委会换届触发口径分两条：
- **正常届满触发**：业委会任期固定 3 年（[业主大会议事规则] 法定上限），创建业委会时落 `t_committee.term_end_date`；
  - 到期前 90 天系统自动 `committee_status = NEAR_EXPIRY`，触发 C 端业主推送 + 街道办 / 党组通知；
  - 任期届满当日 / 后 **业委会主任 / 街道办任一方** 点 [发起换届] 即进入 `IN_HANDOVER`；
  - 超期 30 天主任仍未发起 → 街道办强制发起（`handover_kind = SUBDISTRICT_FORCED`），避免主任拖延占位；
- **异常触发**：业主联署 / 街道办 / 党组发起 [罢免业委会] 议案通过后系统自动切 `IN_HANDOVER`，`handover_kind = RECALL_PASSED`；
- 触发动作落 `t_committee_handover(handover_id, committee_id, handover_kind ENUM(NORMAL_EXPIRY/RECALL_PASSED/SUBDISTRICT_FORCED), initiated_by_user_id, initiated_at, status, completed_at)`；
- 不走 [选项 A 仅主任手动发起]（主任可故意拖延占位）；不走 [选项 C 仅街道办触发]（侵蚀业委会自治）。
_Avoid_: 仅主任手动发起、仅街道办可触发（前者主任可拖延，后者破坏自治）

**换届期分级冻结 (Tiered Freeze in Handover)**:
旧业委会在 `IN_HANDOVER` 期间权限分级冻结：
- **保留权限**（旧业委会仍可执行）：
  - 日常 voucher 核销（< 5000 元）；
  - 紧急议案立项（电梯故障 / 漏水 / 火警等不可拖延场景，需 [emergency_flag = TRUE] 标记）；
  - 已通过议案的执行（按 [Q24 工程验收] 链路推进剩余分期付款）；
- **冻结权限**（与 [HANDOVER_LOCK 大额资金] 一致）：
  - 新立项议案预算 ≥ 阈值（与 SCHEME_C 大额阈值同步）；
  - 模式切换（包干 / 筹金 / 信托互转，与 [模式切换硬约束] 一致）；
  - 印章 / 法人代表变更；
  - 工程款支付（除已立项议案的剩余分期）；
- **完全禁止**：换届相关的 ABAC 数据修改（改业主名册 / 改楼栋归属 / 改业委会成员 / 改委员会规模）—— 防止旧业委会篡改选民数据影响选举公正；
- 紧急议案保留通路下旧主任仍可拍板小额维修，避免 [选项 A 完全冻结] 导致业主受损；不走 [选项 C 旧业委会全权运行]（旧主任可卸任前突击操作）。
_Avoid_: 完全冻结旧业委会、旧业委会全权运行直到新业委会就职（前者紧急议案瘫痪，后者卸任前突击风险）

**换届权力真空期街道办接管 (Subdistrict Caretaker in Vacuum Period)**:
新业委会选举结果出且 [选举结果异议期] 期满到新业委会宣誓就职之间的"权力真空期"由街道办暂行接管：
- 选举结果公示 5 天异议期满后 `committee_status = TRANSITION`；
- **街道办（GOV_SUPER_ADMIN）暂行业委会职能**：
  - 紧急议案由街道办拍板（与 [GOV_TAKEN_OVER_APPROVED] 链路一致）；
  - **有限期最长 7 天**，超期街道办需向党组 / 区政府书面说明延期理由；
  - 街道办接管期间所有动作落 `t_subdistrict_caretaker_action(action_id, committee_id, transition_id, action_kind, performed_by_user_id, performed_at, justification)`；
- **新业委会候任人列席观察权限**：B 端可看议案 / voucher / 工程进度但不可投票（只读 ABAC 范围 = 该 committee_id 全部数据），便于交接熟悉业务；
- **旧业委会账号**：自选举结果出且公示期满时**完全冻结**——仅可查看历史数据不可写；
- 落 `t_committee.transition_started_at` / `transition_ended_at` 字段；
- 不走 [选项 A 旧业委会继续行使全部职能]（卸任前突击风险）；不走 [选项 C 新业委会立即生效不需宣誓]（缺仪式可能引发合法性争议）。
_Avoid_: 旧业委会真空期继续运行、新业委会无宣誓即生效（前者卸任突击，后者法律层面合法性瑕疵）

**旧业委会未结议案 / voucher 分类归属 (Tiered Inheritance Across Handover)**:
旧业委会任期内立项但未结的议案 / voucher 在新业委会就职后按四类归属：
- **(a) 已通过议案的执行阶段**（业主投票通过、工程在施工 / 验收）：
  - **自动转归新业委会，不可拒绝**——业主多数已同意，新业委会无权单方推翻；
  - 如需推翻走 [变更议案分级处置] 重新立项；
- **(b) 未投票议案 / 投票未达 quorum 的议案**：
  - 自动 `proposal_status = SUSPENDED_PENDING_NEW_COMMITTEE_REVIEW`；
  - 新业委会就职 30 天内决定 [继续推进] / [撤回] / [修改后重提]；
  - 超 30 天默认 [撤回]，原议案归档为 `CLOSED_INHERITED_TIMEOUT`；
- **(c) voucher 已开票未支付**：
  - 旧主任已签 USBKey 但物业未签的 voucher → 自动转新业委会，新主任可 [追认签名] 或 [作废]，30 天默认作废；
  - 新主任已签但物业未签 → 走 [双签作废方案-甲] 单方撤回链路；
- **(d) frozen_balance 占用**：
  - 保留原冻结状态（不释放）；
  - 新业委会接管后按 [议案分期付款双字段] 继续核销或在 [变更议案] 链路中追加扣减；
  - 与 (a) 已通过议案归属一致——frozen 是议案执行的财务侧映射；
- 不走 [选项 A 全部转新业委会状态保留]（新业委会被迫继承未表决的政策包袱）；不走 [选项 C 全部作废重来]（浪费业主已表决的政策成本）。
_Avoid_: 全部转新业委会状态保留、全部作废重来（前者强加政策包袱，后者浪费表决成本）

**选举结果 5 天异议期 + 街道办一审 (Election Result Dispute Period)**:
新业委会选举结果生效前的争议救济通路：
- 选举结果出后落 `election_status = PUBLISHED_PENDING_DISPUTE_PERIOD` **5 天异议期**（与 [线下票异议期] 5 天对齐）；
- 业主对选举结果异议（候选人资格 / 投票统计错误 / 程序违法）→ 走 [异议主表 + 业务附属]，`dispute_kind = ELECTION_RESULT_DISPUTE`；
  - **直接 Level 2 街道办一审**（与 `THIRD_PARTY_REFERENCE_DISPUTE` 同源——业委会无权审本届选举争议）；
  - 街道办 14 日审理期；
  - 街道办驳回升 Level 3 区政府二审（30 日），不升 Level 4 / 5；
- 5 天异议期内**新业委会不能就职**，所有 `IN_HANDOVER` 状态保持，街道办继续暂行接管；
- 异议期内有未决异议 → 选举结果状态切 `ELECTION_RESULT_DISPUTED`：
  - 街道办 UPHELD → 选举重做（落 `t_election_redo_audit` 含 UPHELD 决议书 + 重新启动 [ElectionVotingEngine]）；
  - 街道办 REJECTED → 新业委会进入 `TRANSITION` 状态进而宣誓就职；
- 5 天异议期后无异议 → 自动 `committee_status = TRANSITION` 进入 [权力真空期]；
- 不走 [选项 A 选举即生效] (合法性真空风险)；不走 [选项 C 选举不可异议] (业主选举权救济被堵)。
_Avoid_: 选举即生效争议事后处理、选举结果不可异议（前者合法性真空，后者堵死救济通路）

### 业委会成员个人资格全生命周期

**委员失格白名单七类事由 (Member Disqualification Whitelist)**:
单个委员资格丧失的合法事由限定在白名单七类，避免行政干预过深 / 主任滥权：
- **(a) 主动辞职**：成员通过 B 端 [辞职申请]，业委会内部 ≥ 2/3 委员同意接受 + 街道办备案后生效；
- **(b) 死亡 / 完全民事行为能力丧失**：街道办（GOV_SUPER_ADMIN）凭死亡证明 / 法院宣告书登记；
- **(c) 不再是业主**：成员卖房后失去业主身份（与 [t_owner.is_active = FALSE] 联动）→ 系统自动失格（[业主大会议事规则] 法定要求）；
- **(d) 业主大会单一委员罢免议案通过**：业主联署门槛 ≥ 1/5 业主 + 议案双 2/3 通过 + 街道办备案；
- **(e) 业委会内部不信任投票通过**：业委会其他委员 ≥ 2/3 同意 + 该委员有 [严重失职 / 违纪] 事由白名单（长期缺席议案 / 单笔越权操作 / 利益输送嫌疑被确认）；
- **(f) 刑事判决生效**：成员被判处刑罚（含缓刑），街道办凭法院判决书登记 → 系统自动失格（[业主大会议事规则] 强制要求）；
- **(g) G 端强制免职**：街道办 / 党组 / 纪检机关凭书面公文强制免职（极端场景：严重违法 / 涉黑 / 失联超 6 个月），需双签 G 端审批 + 党组备案；

落 `t_committee_member_disqualification(disq_id, member_id, disq_reason ENUM(a..g), evidence_doc_url, decided_by_user_id, effective_at)`；
不走 [选项 A 仅死亡 / 主动辞职]（恶劣行为成员无法被踢出）；不走 [选项 C 主任单方罢免]（违背业委会集体决策原则）。
_Avoid_: 仅死亡 / 主动辞职、主任有权单方罢免（前者无救济通路，后者主任滥权）

**失格后事中操作分类处置 (Tiered In-Flight Operation Handling)**:
委员失格当下其参与的进行中议案 / 已签 voucher / 待表决议案与 [Q30.4 旧业委会未结议案分类归属] 一致，并补充委员个体维度：
- **该委员已投票的进行中议案**：投票结果**保留**——已投票动作是历史事件不可撤销（与 [投票截止以业主签字时间为准] 同源——投票动作的法律效力时点锚定在签署当下）；
- **该委员已签 USBKey 但 voucher 未完成双签**：
  - 失格事由 (a)/(b)/(c)/(d) 合法常规事由 → voucher 状态切 `WAITING_REPLACEMENT_SIGNATURE`，新委员补位后凭其 USBKey 重新签署（不复用原失格委员签名）；
  - 失格事由 (e)/(f)/(g) 违纪 / 刑事 / 强制免职 → voucher **强制作废** + 街道办审计是否涉嫌利益输送，作废理由 `voucher_void_reason = SIGNER_DISQUALIFIED_DUE_TO_MISCONDUCT`；
- **该委员主导立项但未通过的议案**：归 [SUSPENDED_PENDING_NEW_COMMITTEE_REVIEW]（与 [旧业委会未结议案分类归属] 一致），不一刀切作废；
- **账号 ABAC 数据范围权限**：失格生效时点立即冻结写权限，仅保留只读权限 30 天（便于审计），30 天后账号完全停用 `account_status = DEACTIVATED`；
- 不走 [选项 A 全部作废重来]（浪费表决成本）；不走 [选项 C 全部按原计划继续]（失格成员仍行使权力违背语义）。
_Avoid_: 全部作废重来、失格后所有操作继续按原计划（前者浪费成本，后者违背失格语义）

**失格后分级补位 (Tiered Replacement Mechanism)**:
委员失格后空缺按主任 / 普通委员 / 违纪事由分级补位：
- **(a) 主任 / 副主任失格**：业委会内部从现任委员 ≥ 2/3 投票选出代理主任 / 副主任，代理任期至本届任期结束（不重新全员选举，节省成本，原任期边界不变）；不走街道办指派（避免侵蚀自治）；
- **(b) 普通委员失格**：
  - 若委员会规模 > 法定下限（[业主大会议事规则] 通常 ≥ 5 人）：允许空缺至本届任期结束（业委会仍可正常运作）；
  - 若失格后规模 < 法定下限：从上届选举的"备选名单"按得票顺序询问意愿，前 3 位拒绝后由业委会发起 [增补委员] 议案 + 业主大会简单多数通过；
- **(c) 失格事由为 (e)/(f)/(g) 违纪 / 刑事 / 强制免职**：补位议案需街道办前置审查（与 [行政复议反制] 同源——重大违纪事由需 G 端介入），防止违纪成员的关联人接替；
- 落 `t_committee_member_replacement(replacement_id, vacated_member_id, replacement_member_id, replacement_kind ENUM(INTERNAL_DEPUTY/CANDIDATE_LIST_NEXT/SUPPLEMENT_ELECTION), approved_by_subdistrict, started_at)`；
- 不走 [选项 A 自动从候选名单递补]（候选人意愿未确认侵害选择权）；不走 [选项 C 必须重新全员选举]（成本过高且业务流空窗）。
_Avoid_: 自动递补不询问意愿、单一委员失格触发全员重新选举（前者侵害候选人选择权，后者成本过高）

**SM2 证书系统侧撤销 + 物理回收尽力而为 (Cert Revocation in System)**:
委员失格后 USBKey 在系统层面通过证书撤销硬绑定，不依赖物理回收：
- 失格生效瞬间系统调 `Sm2CertRevocationService.revoke(committee_member_id, revocation_reason)` 撤销该委员 SM2 证书绑定；
- 撤销后即使该委员持物理 USBKey 尝试签 voucher，[Sm2UsbKeyVerifier 验签双查] 因证书已撤销直接 `SignatureRejected: CERT_REVOKED`；
- 落 `t_cert_revocation_audit(audit_id, committee_member_id, revoked_at, revocation_reason ENUM(MEMBER_DISQUALIFIED/DEATH/CRIMINAL_JUDGMENT/SUBDISTRICT_FORCED), revoked_by_user_id, related_disq_id)`；
- 物理回收 USBKey 走街道办尽力而为（联系成员 / 上门），系统侧**不依赖物理回收成功**——证书撤销时点是法律时点（与 [证书撤销时点效力] 同源）；
- **已撤销证书曾签的历史 voucher（撤销之前签的）仍有效，不追溯**——历史动作的法律效力时点锚定在签署当下，与 [投票截止以业主签字时间为准] 同源；
- 不走 [选项 A 物理回收]（成员失联 / 拒交时不可靠）；不走 [选项 C 撤销允许"补救签名"]（创造法律灰色地带）。
_Avoid_: 依赖物理回收 USBKey、撤销后允许补救签名（前者不可靠，后者法律灰色地带）

**SUSPENDED 中间状态 + 6 个月可续 1 次 (Suspension State Between Active & Disqualified)**:
委员任期内的合理暂停（怀孕休假 / 长期住院 / 出国半年）通过 SUSPENDED 中间状态承接，避免被迫辞职：
- `committee_member_status ∈ {ACTIVE, SUSPENDED, DISQUALIFIED}`；
- 委员发起 [暂停履职申请] 落 `t_member_suspension(suspension_id, member_id, suspension_reason ENUM(MATERNITY/MEDICAL/OVERSEAS_BUSINESS/PERSONAL_AFFAIRS), planned_duration_days, started_at, planned_end_at, actual_ended_at, status)`；
- **期限上限 6 个月**，最多续期 1 次（共 12 个月）；超期自动转 `DISQUALIFIED` 理由 `SUSPENSION_TIMEOUT`；
- SUSPENDED 状态下：
  - 系统侧账号写权限 + USBKey 签名权全部冻结（与 [失格] 等价的"暂时禁用"——但保留成员身份）；
  - 投票权 / 议案立项权 / voucher 双签权全部转代理（业委会内部 ≥ 2/3 投票选出代理人，落 `t_member_suspension.deputy_member_id`）；
  - **失格的 (c) 非业主 / (f) 刑事判决等硬条件不适用** SUSPENDED——只有 ACTIVE 状态下成员才会因 (c)(f) 被自动失格；SUSPENDED 期间这些事件被检测到自动 `SUSPENDED → DISQUALIFIED` 转换；
  - **不计入** [Q31.3 委员会规模 ≥ 法定下限] 检查——避免暂停期触发不必要的补位议案；
- 复职：成员主动 [复职申请] + 业委会 ≥ 1/2 同意 + 街道办备案，恢复 ACTIVE，原代理人代理权终止；
- 不走 [选项 A 无暂停状态]（合理情形被迫辞职过度严苛）；不走 [选项 C 仅街道办决定暂停]（侵蚀成员个人意愿）。
_Avoid_: 无暂停中间状态、街道办单方决定暂停（前者过度严苛，后者侵蚀个人意愿）

### 业主大会的法定召开与议事规则

**业主大会议事规则版本 (OwnersAssemblyRule)**:
以规则原件、逐项可回查的结构化配置和确认审计共同构成的生效依据。生命周期为
`DRAFT → PENDING_CONFIRMATION → ACTIVE → SUPERSEDED`；物业或业委会经办可以登记草稿，只有当前届主任或副主任可以启用。没有 `ACTIVE` 版本时仅允许内部筹备，禁止进入正式召开、公示、选票送达、表决、计票等环节。
_Avoid_: 平台默认规则、上传即生效

**业主大会规则快照 (OwnersAssemblyRuleSnapshot)**:
每次业主大会进入正式办理前，冻结当时 `ACTIVE` 规则的原件摘要和结构化配置；之后规则新版本不能改写在办或历史会议。
_Avoid_: 当前规则、运行时重新解析原件

**业主大会四类合法触发 (Four Convening Triggers)**:
业主大会作为业主自治根本机关，召开动作有四类合法触发：
- **(a) 年度法定召开**：每个会计年度末业委会必须召开 1 次年度业主大会（[业主大会议事规则] 强制要求）；
  - 系统在 `t_committee.last_annual_meeting_date + 365 days - 30 days` 自动通知业委会主任发起；
  - 超期 60 天未召开 → 街道办强制启动；
  - 超期 90 天 → 触发 [Q31.1 (e) 业委会内部不信任投票] 或 [Q30.1 (b) 异常触发罢免业委会议案]；
- **(b) 临时业主大会业主联署发起**：业主联署 ≥ 1/5 业主**或** ≥ 1/5 专有面积，触发 [临时业主大会] 议程；
- **(c) 业委会主动召集**：业委会内部 ≥ 1/2 委员同意可主动召集临时大会；
- **(d) 街道办强制召集**：业委会失能 / 重大公共事件（如 [HANDOVER_LOCK 触发] 后业委会瘫痪）街道办凭书面公文强制召集；

落 `t_owners_meeting(meeting_id, tenant_id, meeting_kind ENUM(ANNUAL/EXTRAORDINARY), trigger_kind ENUM(a/b/c/d), convened_at, scheduled_at, ended_at, status, ...)`；
不走 [选项 A 仅业委会主任发起]（主任不开则永远不开）；不走 [选项 C 不需召开动作]（违背 [业主大会议事规则] 集合议程的法律地位）。
_Avoid_: 仅业委会主任发起、业主大会不需召开动作（前者主任拖延，后者违背法律地位）

**业主大会"议程包"主体 + 议程项独立计票 (Meeting as Agenda Package)**:
业主大会本身作为"议程包"主体，承载多议程项独立投票：
- **主表** `t_owners_meeting`（见上条触发模型）；
- **议程项表** `t_owners_meeting_agenda(agenda_id, meeting_id, agenda_kind ENUM(WORK_REPORT/FINANCIAL_REPORT/PROPOSAL/RULES_REVISION/ELECTION/RECALL/OTHER), proposal_id NULLABLE, agenda_content_url, voting_mechanism ENUM(SIMPLE_MAJORITY/DOUBLE_TWO_THIRDS), display_order)`；
- 议程项类型 `PROPOSAL` 关联具体议案 `proposal_id`（`t_proposal` 行，与既有议案链路无缝衔接）；其他议程项（WORK_REPORT / FINANCIAL_REPORT / RULES_REVISION 等）独立持议程内容；
- **每个议程项独立计票**——业主可对议程 1 投赞成对议程 2 投反对，**绝不打包**；
- 业主大会本身状态机：`SCHEDULED → IN_SESSION → VOTING_CLOSED → SETTLED → CLOSED`；
- PROPOSAL 类议程项按 [voting engine quorum 规则] 双 2/3 表决；WORK_REPORT / FINANCIAL_REPORT 类简单多数；RULES_REVISION 类双 2/3（与重大议案同门槛）；
- 不走 [选项 A 议程独立成议案]（弱化业主大会法律地位）；不走 [选项 C 议程合并打包表决]（强制绑定业主对单议程的不同立场）。
_Avoid_: 议程独立成议案弱化业主大会、议程合并打包表决（前者弱化大会法律地位，后者强制绑定立场）

**会议成立 1/2 门槛 + 第二次会议 1/3 兜底 (Meeting Quorum Decoupled from Proposal Quorum)**:
业主大会成立门槛与议案表决门槛**解耦**——大会成立是程序门槛，议案通过是结果门槛：
- **第一次会议成立门槛**：业主参与（含线上 + 线下）≥ 1/2 总专有面积**或** ≥ 1/2 总业主人数（双轨择一即可）；
- 会议成立后议程项按各自的 `voting_mechanism` 计票：
  - PROPOSAL 类双 2/3（与 [voting engine] 一致）；
  - WORK_REPORT / FINANCIAL_REPORT 简单多数（参与人 > 1/2 同意）；
  - RULES_REVISION 双 2/3；
- **会议未达 1/2 成立门槛** → `meeting_status = MEETING_FAILED_QUORUM`，所有议程项**全部撤回**自动转 [SUSPENDED_PENDING_NEW_MEETING]；
- **第二次会议兜底**：30 天内业委会必须召开第二次会议（成本由业委会承担），第二次会议门槛降为 ≥ 1/3（[业主大会议事规则] 法律对业主参与不积极的兜底）；
- 第二次会议仍不达 1/3 → 议程项作废，状态归 `CLOSED_INVALID_DUE_TO_QUORUM_FAILURE`；
- 不走 [选项 A 无门槛]（违背业主大会集体属性）；不走 [选项 C 成立门槛 = 议案 2/3 门槛]（业主参与门槛过高实质阻碍大会召开）。
_Avoid_: 业主大会无成立门槛、成立门槛与议案表决 2/3 门槛对齐（前者违背集体属性，后者实质堵死大会）

**会议形式三类 + 全线上街道办前置审批 (Three Meeting Formats)**:
业主大会的物理形式 `meeting_format ENUM` 三类，覆盖不同场景：
- **(a) LINE_OFFLINE_PRIMARY 线下为主 + 线上备份**：线下签到为主，线上备份投票通道（覆盖出差业主），是默认形式；
- **(b) LINE_HYBRID 线下 / 线上等同**：线下会议室 + 线上视频会议直播同步进行，[ElectionVotingEngine] 同时接收线上 / 线下票（与 [线下票三段录入] 链路无缝接入）；
- **(c) LINE_ONLINE_FULL 全线上召开**：无线下会场所有业主线上参与；
  - 适用场景限定：[HANDOVER_LOCK 后街道办强制召开] / [疫情等公共卫生事件] / [自然灾害不便聚集] 等；
  - **必须街道办前置审批**——避免业委会主任借全线上规避业主线下议事权；
  - 街道办批准落 `t_owners_meeting.online_full_approval_doc_url`；
- 街道办可对小区指定默认形式（如老龄化严重小区默认 LINE_OFFLINE_PRIMARY）；业委会发起会议时选具体形式但受默认形式约束；
- 不走 [选项 A 必须线下]（与 SaaS 脱节 + 业主参与门槛高）；不走 [选项 C 仅 SaaS 全线上]（不符合 [业主大会议事规则] 关于线下议事的法定要求）。
_Avoid_: 必须线下召开、仅 SaaS 全线上召开（前者业主参与门槛高，后者不符合法定议事要求）

**议事规则分级修改权限 (Tiered Rules Revision)**:
业主大会议事规则的修订权限分级，法定刚性 vs 小区自治：
- **法定刚性条款不可改**：[业主大会议事规则] 法定的最低参与门槛（1/2 / 1/3 兜底）/ 双 2/3 表决 / 委员会规模 ≥ 5 人 / 业委会任期上限 3 年等；
  - 这些是**法律下限**，小区**不能放宽**（不能改成 1/3 业主就能成立大会）；
  - 小区**可以更严格**（如改成 3/4 表决门槛）；
  - 系统侧由 `t_community_meeting_rules.is_more_strict_than_legal` 校验确保不放宽；
- **小区自治条款可改**：
  - 议事流程细节（召集通知期 5 / 7 / 14 天）；
  - 投票方式具体细节（线上 / 线下比例上限）；
  - 文档存档要求 / 议事录音保留期限；
  - 街道办指定默认形式的覆盖；
- 修改议程项 `agenda_kind = RULES_REVISION` 走双 2/3 表决（与重大议案同等门槛）+ 街道办 / 党组备案审查（确保不突破法定下限）；
- 落 `t_community_meeting_rules(rules_id, community_id, version, content_url, approved_by_meeting_id, effective_at, is_more_strict_than_legal, ...)`；
- **新规则不追溯**——已开始的会议按旧规则走完，新规则从 `effective_at` 起对新会议生效（与 [证书撤销时点效力] 同源——历史动作时点稳定）；
- 不走 [选项 A 全国统一不可改]（堵死合理自治需求）；不走 [选项 C 不可任何形式修改]（同上）。
_Avoid_: 议事规则全国统一不可任何修改、议事规则可放宽法定下限（前者堵死自治，后者突破法律底线）

### 业主资格生命周期与不动产登记联动

**不动产登记系统为业主资格唯一权威源 (Real Estate Registry as Authoritative Source)**:
业主资格的真理来源**不在平台**，是当地不动产登记中心（自然资源局）：
- 平台通过 OpenAPI / 政务云接口对接不动产登记中心：每日全量同步 + 实时事件订阅；
- 平台 `t_property(property_id, address, area, committee_id, current_owner_id_list, ownership_kind, property_registration_id)` 与登记中心 `property_registration_id` 强绑定；
- **平台无独立修改权**——业委会主任 / 物业 / 街道办**都不能**手工新增 / 删除业主资格，所有变更必须以不动产登记中心数据为准；
- 同步异常（接口故障）走 `t_property_sync_failure(failure_id, sync_batch_id, failure_reason, retry_count, failed_at)` 落库 + 告警街道办；**不**触发业主资格变更——保持上次同步状态稳定，避免同步故障导致业主诉权被错误剥夺；
- 历史变更落 `t_property_owner_history(history_id, property_id, owner_id_list, ownership_kind ENUM(SOLE/JOINT_SPOUSE/JOINT_MULTIPLE), valid_from, valid_to, transfer_kind ENUM(PURCHASE/INHERITANCE/DIVORCE/JUDICIAL_FORCED/GIFT))`，[voting engine] 计票分母按 `valid_from <= 投票时点 < valid_to` 的业主取值；
- 不走 [选项 A 平台自维护业主名册]（业委会主任手动维护可冒名 / 漏录）；不走 [选项 C 业主自报 + 业委会审核]（自报数据无法核验造假风险高）。
_Avoid_: 平台自维护业主名册、业主自报 + 业委会审核（前者业委会可操纵，后者无核验机制）

**业主资格生效时点 = 不动产登记完成日 (Effective Date Tied to Registry)**:
业主从买房到拿到系统侧权限的时点严格锚定不动产登记完成日：
- `t_property_owner_history.valid_from` = 不动产登记中心的产权登记完成日；
- 平台每日同步检测到新产权登记 → 自动创建业主账号（如未存在）+ 关联 `t_property` + 发欢迎短信；
- **预购买阶段**（已签买卖合同未完成产权登记）：业主**无投票权 / 议案立项权**；可申请 [访客权限] 看小区公开公示但不能参与议案；落 `t_pre_purchase_visitor(visitor_id, property_id, prospective_buyer_user_id, granted_by_committee, valid_from, valid_to)`；
- **过户中**（不动产登记中心受理但未完成）：原业主仍持有完整业主权利至登记完成日（与 [证书撤销时点效力] 同源——以登记中心时点为准，平台不擅自提前剥夺权利）；
- 不走 [选项 A 交房当日生效]（交房与产权登记可能差几个月业主无投票权）；不走 [选项 C 业委会确认日生效]（引入业委会主观判断，与 [平台无独立修改权] 冲突）。
_Avoid_: 交房日生效、业委会确认日生效（前者与产权状态脱节，后者业委会主观判断）

**业主失效后历史不追溯 + 进行中权限即冻结 (Historical Actions Preserved on Disqualification)**:
业主卖房后的善后处置：
- **历史动作不追溯**：原业主投过的票 / 联署的议案 / 已签的反馈**全部保留**——投票动作的法律效力时点锚定在签署当下（与 [投票截止以业主签字时间为准] / [证书撤销时点效力] / [SM2 证书系统侧撤销 + 物理回收尽力而为] 同源）；
- **进行中权限即时冻结**：失效时点 `t_property_owner_history.valid_to` 起原业主无新投票权 / 新议案立项权 / 新 voucher 双签权（如该业主同时是业委会委员）；
- **委员身份联动失格**：原业主同时是业委会委员 → 触发 [Q31.1 (c) 不再是业主] 自动失格 → 启动 [Q31.3 分级补位]；
- **C 端账号保留 90 天只读**：原业主可继续看历史议案 / 历史投票 / 自己的 [我的异议]；90 天后账号转 `account_status = INACTIVE_HISTORICAL`，仅 G 端 / 法院凭公文可查询；
- **正在进行的 dispute / 异议**：原业主已起的 dispute 继续走流程——业主已起的诉求不因失格被剥夺（与 [业主已导出 ZIP / 已立案诉讼不冻结] 同源）；
- **frozen_balance 占用**：与 [议案分期付款双字段] 一致——原业主的财务责任已通过议案表决从专项维修资金中冻结，与个人无关，**不解冻**；
- 不走 [选项 A 失效瞬间所有关联业务回退]（破坏历史时点稳定性）；不走 [选项 C 失效后仍有完整投票权直至任期结束]（卖房业主行使新业主权利违背业主自治原则）。
_Avoid_: 失效瞬间业务回退、失效后仍有完整投票权（前者破坏时点稳定性，后者违背自治原则）

**一房一票 + 共有人协议指定代表 (One Property One Vote with Joint Owner Representative)**:
共有产权（夫妻 / 多人共有）的投票权分配遵循一房一票 + 共有人协议指定代表：
- **一房一票**：无论共有人多少（夫妻 / 兄弟 / 父子），该房产作为投票主体计 1 票，投票权重 = 该房产专有面积，与 [voting engine 一房一户] 设计一致；
- **共有人协议指定代表**：共有人通过 C 端 [指定投票代表] 协议指定 1 人为投票代表；
  - 落 `t_property_voting_representative(prop_id, representative_owner_id, agreed_at, agreed_by_owner_id_list, valid_from, valid_to)`；
  - 协议要求**所有共有人均同意**（C 端电子签名链 + SM2 签名 + 时间戳），任一共有人未签名协议无效；
  - 有效期默认 1 年自动续期，任一共有人可单方面解除（解除后回退到下一规则）；
- **未指定代表时默认规则**：按不动产登记中心的"第一持有人"为代表（夫妻共有时一般是登记在前的一方）；
- **共有结构变更**（离婚 / 继承等）：与 [不动产登记系统] 同步联动，共有人变化触发原协议自动失效，需重新指定；
- **共有人内部冲突**（如代表协议失效后第一持有人投赞成、其他共有人异议）：走 [异议主表] `dispute_kind = JOINT_OWNER_VOTING_DISPUTE`，**直接街道办一审**（业委会无权审业主家庭事务，与 [THIRD_PARTY_REFERENCE_DISPUTE 直接街道办一审] 同源）；
- 不走 [选项 A 一房 N 票]（破坏 [voting engine 一房一户] 设计）；不走 [选项 C 按共有比例拆分权重]（小数比例计算复杂且 voting engine 不支持）。
_Avoid_: 一房 N 票、按共有比例拆分投票权重（前者破坏一房一户设计，后者复杂度高引擎不支持）

**业主信息分类授权 (Owner Profile Tiered Authorization)**:
业主在 C 端可维护的信息分三档：
- **不动产登记数据（只读）**：`property_id` / `address` / `area` / `current_owner_id_list` / `ownership_kind` / `valid_from` 等以登记中心为准，业主**只能查看不可修改**；
  - 发现错误 → 走 [向不动产登记中心申请变更] 通路（系统提供链接 + 申请表 PDF 模板，不直接对接申请——属于政务流程）；
  - 申请变更链接 + 模板由街道办或区不动产登记中心提供；
- **业主自维护数据**：联系手机号 / 备用邮箱 / 紧急联系人 / 通讯偏好（短信 / 推送 / 邮件）/ 暂住地址（与登记地址不同时）—— 业主与平台的"通讯档案"，业主完全可自主维护；落 `t_owner_profile(owner_id, contact_phone SM4_ENCRYPTED, backup_email, emergency_contact, notification_pref, temp_address, ...)`；
- **半自治数据**：业主姓名（涉及法定身份）/ 身份证号——业主可申请变更但需提交身份证扫描件 + 街道办审批；如改名走政务流程后系统侧从登记中心同步（与 [不动产登记系统为权威源] 一致）；
- 双表分离 `t_owner_profile`（业主自维护）vs `t_property`（登记数据）；
- 不走 [选项 A 业主可维护所有信息]（业主可改房产面积 / 持有人破坏权威源）；不走 [选项 C 全部以不动产登记为准]（政务系统未存储联系手机号业主无法自维护通讯档案）。
_Avoid_: 业主可维护所有信息、业主信息全部以不动产登记为准（前者破坏权威源，后者通讯档案无处存储）

### 多小区数据隔离与跨小区聚合

**一小区一 tenant + 聚合 tenant 类型 (One Community One Tenant)**:
`t_tenant` 严格遵循一小区 = 一 tenant，聚合需求通过专用 tenant 类型承接：
- `t_tenant(tenant_id, tenant_kind ENUM(COMMUNITY/SUBDISTRICT_AGGREGATOR/CITY_AGGREGATOR), community_id NULLABLE, parent_aggregator_tenant_id NULLABLE, ...)`；
- **`COMMUNITY` 类**：标准 tenant 对应一个小区，所有业务数据（议案 / voucher / 业主名册等）归属此 tenant，是业务写操作的根；
- **`SUBDISTRICT_AGGREGATOR` 类**：街道办专用聚合 tenant，无业务写权限，仅作 ABAC 数据范围聚合根（可读辖区内所有 COMMUNITY tenant 数据）；
- **`CITY_AGGREGATOR` 类**：市政府 / 党组聚合 tenant，类似 SUBDISTRICT_AGGREGATOR 但范围更大（含多个 SUBDISTRICT_AGGREGATOR）；
- 物业公司管 N 个小区时通过 `t_property_company_tenant_map(company_id, tenant_id, role ENUM(LUMP_SUM/FUND_RAISING/TRUST), valid_from, valid_to)` 多对多绑定，物业经理账号在 N 个 tenant 下分别独立账号（不混账号）；
- **与现有 [TenantContext ThreadLocal<Long>] 完全兼容**——一次请求只在一个 tenant 上下文中，无跨 tenant 副作用；
- 不走 [选项 A 一物业公司一 tenant]（物业管多小区时数据混在一起，业委会跨小区看到不属于自己的数据违背自治）；不走 [选项 C tenant = 街道办]（数据范围过大业委会自治边界被冲淡）。
_Avoid_: 一物业公司一 tenant、tenant = 街道办（前者业委会自治被破坏，后者自治边界被冲淡）

**单一 user_id + 多 owner_membership (Single Identity, Multi-Tenant Membership)**:
跨小区业主通过单一身份 + 多业主资格条目承接：
- **唯一身份层** `t_user(user_id, id_card_number SM4_ENCRYPTED, mobile, real_name, ...)`：业主以身份证号为根（实名认证），全平台一个 user_id；
- **多业主资格层** `t_owner_membership(membership_id, user_id, tenant_id, property_id, role ENUM(OWNER/COMMITTEE_MEMBER/COMMITTEE_DIRECTOR), valid_from, valid_to, status ENUM(ACTIVE/SUSPENDED/INACTIVE_HISTORICAL))` —— 同一 user_id 可有多条 membership 对应不同 tenant；
- **C 端登录后小区切换**：用户登录后 C 端首页列出该 user_id 持有的所有有效 membership（小区 A 业主 / 小区 B 业委会主任），用户选 [当前活跃小区] 切换 TenantContext；
- 所有业务请求均在切换后的 tenant 上下文中，UI 仅显示当前 tenant 数据；
- **委员身份独立计算**：业主在小区 A 是委员同时在小区 B 是普通业主，[Q31 委员失格条件] / [committee_member_status] 仅作用于"小区 A 的 membership"，不波及 B；
- **跨小区 dispute / 异议**：业主在 A 起的 dispute 与在 B 起的 dispute 是两条独立链路（`t_owner_dispute.tenant_id` 不同，互不影响）；
- **失效 90 天只读**与 [业主失效后历史不追溯 + 进行中权限即冻结] 一致——业主在某 tenant 卖房只对该 membership 生效，user_id 本身保留；
- 不走 [选项 A 跨小区共享一账号 + 多 tenant 切换 但权限混淆]；不走 [选项 C 每小区独立账号 + 不同登录手机号]（管理 N 个账号体验差）。
_Avoid_: 同账号跨 tenant 权限混淆、每小区独立账号独立手机号（前者权限边界混乱，后者业主管理 N 账号体验差）

**街道办挂聚合 tenant + ABAC 自动展开 (Aggregator Tenant + Auto-Expand)**:
跨小区数据聚合通过聚合 tenant + DataScopeInterceptor 自动展开实现：
- 街道办账号 `t_user_account.tenant_id` 指向 `SUBDISTRICT_AGGREGATOR` 类 tenant；
- 该聚合 tenant 通过 `t_aggregator_tenant_link(aggregator_tenant_id, target_community_tenant_id)` 关联辖区内所有 COMMUNITY tenant；
- 街道办账号请求时 [TenantContextInterceptor] 设置 `TenantContext = aggregator_tenant_id`；
- [DataScopeInterceptor] 检测到当前 tenant 是 SUBDISTRICT_AGGREGATOR / CITY_AGGREGATOR 类型时**自动展开**：原 `WHERE tenant_id = ?` 改写为 `WHERE tenant_id IN (target_community_tenant_id_list)`；
- 街道办**只读**所有辖区内 tenant 数据，**不能写**——避免街道办越权直接修改业委会数据；
- 写动作走 [街道办暂行接管] / [GOV_TAKEN_OVER_APPROVED] / [SUBDISTRICT_FORCED 强制召集] / [SUBDISTRICT_FORCED 强制发起换届] 等明确链路，每次写都落 `t_subdistrict_caretaker_action(action_id, aggregator_tenant_id, target_community_tenant_id, action_kind, performed_by_user_id, performed_at, justification)`；
- 不走 [选项 A 街道办账号绕过 TenantContext 直接查全表]（安全绕过通路风险）；不走 [选项 C 每次跨小区查询单独发起]（街道办跨小区报表 N 次请求性能差）。
_Avoid_: 街道办绕过 TenantContext 查全表、每次跨小区查询单独发起（前者安全风险，后者性能差）

**物业公司跨小区数据三级共享 (Tiered Cross-Tenant Sharing)**:
物业公司 X 管多小区时数据按三级分层共享：
- **物业公司私有数据**（自动跨 tenant 共享）：物业公司内部规章制度 / 内部培训记录 / 物业人员档案 / 标准服务流程模板；
  - 落 `t_property_company_internal(internal_id, company_id, ...)` **与 tenant 解耦**——直接以 `company_id` 为隔离键；
  - 不涉及业委会自治权范围，自动跨小区共享不需任何授权；
- **业委会授权共享**（需各小区业委会逐个授权）：工程商资质 / 工程商黑名单 / 已签订承包合同 / 维修历史 / 工程评价等小区业务数据；
  - 业委会通过议案授权可跨小区分享给物业公司其他客户；
  - 落 `t_cross_tenant_data_sharing(sharing_id, source_tenant_id, target_tenant_or_company_id, data_kind ENUM(ENGINEER_QUALIFICATION/BLACKLIST/CONTRACT_TEMPLATE/MAINTENANCE_HISTORY/...), authorized_by_committee_meeting_id, valid_from, valid_to)` 审计；
- **完全私有数据**（绝不跨 tenant 共享）：业主名册 / 业主联系方式 / 投票记录 / 财务流水 / 议案投票详情等业主敏感数据；
  - 即使物业公司想分享业委会也**不能授权**（与 [个保法] 一致——业主未授权不能跨 tenant 流转）；
  - 业主个人 dispute / 异议数据同样属此类绝不共享；
- 不走 [选项 A 物业公司所有数据全跨 tenant 共享]（业委会自治被冲淡 + 业主敏感数据泄露）；不走 [选项 C 全严格 tenant 隔离]（物业公司跨小区运营效率被砸行业不可行）。
_Avoid_: 物业公司数据全跨 tenant 共享、物业公司数据全严格 tenant 隔离（前者侵蚀自治泄露隐私，后者行业不可行）

**街道办 ADVISORY 跨小区议案模板 (Subdistrict Advisory Cross-Community Template)**:
街道办协调辖区资源的合理需求通过 ADVISORY 跨小区议案模板承接，**绝不强制**：
- `t_cross_community_proposal_template(template_id, initiated_by_subdistrict_user_id, content_url, target_community_tenant_id_list, advisory_or_mandatory ENUM(ADVISORY), created_at, valid_until)`；
- **强制限定 ADVISORY**——`MANDATORY` 选项不存在 + 数据库 CHECK 约束 `advisory_or_mandatory = 'ADVISORY'`，避免后续误用；
- 模板自动分发到所有目标 tenant 落地为各 tenant 内的独立议案 `t_proposal`（`t_proposal.from_cross_community_template_id` 可追溯模板来源），由各小区业委会决定是否启用 + 各小区独立投票；
- **任一小区业委会可拒绝采纳模板**（拒绝理由记录到 `t_proposal.rejection_reason`，但不影响其他小区议案进度）；
- 各小区表决结果聚合落 `t_cross_community_proposal_aggregate(aggregate_id, template_id, community_tenant_id, local_proposal_id, decision_result, decided_at)`；
- 街道办可看汇总报表（如 [辖区内 12 个小区电梯品牌团购议案] 8 通过 / 3 否决 / 1 未表决）；
- 投票结果实施完全由各小区自治决定，街道办的"建议"无强制关联；
- 不走 [选项 A 强制接受]（违背业主自治原则违反民法典）；不走 [选项 C 街道办无权发起]（实际场景街道办协调辖区资源合理需求被堵）。
_Avoid_: 跨小区议案强制接受、街道办无权发起跨小区议案（前者违背业主自治，后者堵死合理协调）

### MQ / Outbox / 异步事件可靠性

**统一 t_outbox_event 主表 + JSONB payload (Unified Outbox Schema)**:
平台所有异步事件走单一 Outbox 主表，避免每模块独立实现重复逻辑：
- `t_outbox_event(event_id, tenant_id, event_kind ENUM(VOUCHER_PUBLISHED/PROPOSAL_VOTING_NOTIFICATION/DISPUTE_ESCALATION/CHAIN_PUBLISH/SETTLEMENT_DOWNSTREAM/CROSS_COMMUNITY_DISTRIBUTION/...), aggregate_type, aggregate_id, payload JSONB, status ENUM(PENDING/PUBLISHED/FAILED/DEAD_LETTER), retry_count, next_retry_at, max_retries DEFAULT 5, created_at, last_attempted_at, chain_provider VARCHAR(32), chain_provider_version VARCHAR(16), idempotency_key VARCHAR(128) UNIQUE)`；
- **唯一 `idempotency_key`**：业务侧生成的 SHA-256（如 `sha256(event_kind + aggregate_id + business_event_subkey)`）—— 数据库唯一约束兜底，业务侧重复落 outbox 时直接 `INSERT ... ON CONFLICT DO NOTHING`；
- 业务字段进 `payload JSONB`，不同 event_kind payload schema 不同（如 `VOUCHER_PUBLISHED` 含 voucher_id / amount，`PROPOSAL_VOTING_NOTIFICATION` 含 proposal_id / target_owner_id_list）；
- 配套统一 `OutboxEventDispatcher` 后台 worker：批量拉 `status=PENDING + next_retry_at <= NOW()` 的事件，按 event_kind 分发到不同 MQ topic（`topic.voucher.published` / `topic.proposal.notification` / `topic.dispute.escalation` 等）；
- 与 [Mock 链 + 真链双轨并存] 的 `chain_provider` / `chain_provider_version` 字段已有，无需新增；
- 不走 [选项 A 每业务模块独立 outbox 表]（重复实现）；不走 [选项 C 完全无 outbox]（业务事务 vs MQ 投递无原子性）。
_Avoid_: 每业务模块独立 outbox 表、完全无 outbox 直接调 MQ（前者重复实现易漏，后者无原子性）

**统一 t_outbox_consume_log + 框架幂等 (Unified Idempotency via Consume Log)**:
MQ 消费者侧幂等通过统一消费日志表 + 消费框架封装承接，业务侧不重复实现：
- `t_outbox_consume_log(consume_id, event_id, consumer_name VARCHAR(64), consumed_at, status ENUM(SUCCESS/RETRY/FAILED/SKIPPED), processed_payload_hash, retry_count, last_error_message, ...)`；
- `(event_id, consumer_name)` UNIQUE 约束 —— 同一事件同一消费者只可成功消费 1 次；
- 业务侧 consumer 实现 `IdempotentConsumer` 接口；框架在调 `consumer.handle(event)` 之前先查 `t_outbox_consume_log`：
  - `(event_id, consumer_name)` 已 SUCCESS → 跳过 + 返回 ACK；
  - 已 FAILED 且 retry < max → 重试；
  - 已 FAILED 且 retry ≥ max → 死信不再消费（与 [Q35.3] 死信通路合流）；
- 业务方法保持简单 idempotent（基于业务键，如以 voucher_id 为键检查是否已发推送）—— 框架兜底防止重复，业务侧只需保证"自己已经处理过 X 次时跳过"语义；
- 与 [Settle 事务最小化 + Outbox 下游] 一致——下游消费者无副作用幂等；
- 不走 [选项 A 消费者自行实现幂等]（每模块重复实现新增模块容易漏）；不走 [选项 C MQ exactly-once]（性能 / 实现成本极高且业务不可控）。
_Avoid_: 消费者自行实现幂等、依赖 MQ exactly-once 语义（前者重复实现漏点多，后者性能成本极高）

**死信表 + 三种人工处置 (Dead Letter & Manual Resolution)**:
重试 ≥ max_retries（默认 5 次）后死信处置：
- `t_outbox_event.status = DEAD_LETTER`；
- 自动落 `t_outbox_dead_letter(dl_id, event_id, original_payload, last_error_message, dead_at, resolved_at, resolved_by_user_id, resolution_kind ENUM(REQUEUE/MANUAL_FIX/DISCARD))`；
- 平台告警系统（短信 / 邮件 / 钉钉）发 [街道办 / 平台运维] 双通道告警；
- **人工介入通路**：街道办 / 运维 G 端管理后台看死信列表，三种处置：
  - **REQUEUE 重新入队**：原 payload 不变重新设 PENDING + 重置 retry_count；
  - **MANUAL_FIX 手工修复后入队**：运维修改 payload 字段（如修复某个错位的 aggregate_id）后重新入队；
  - **DISCARD 确认作废**：完全跳过该事件，落 `resolution_kind = DISCARD` + 必须填写 `discard_reason`（审计留痕）；
- **关键事件 P0 告警**：`event_kind IN (CHAIN_PUBLISH/VOUCHER_PUBLISHED/DISPUTE_ESCALATION/SETTLEMENT_DOWNSTREAM)` 等司法 / 财务事件死信触发 P0，必须 24h 内人工介入；其他事件 P2 48h；
- 不走 [选项 A 直接丢弃]（数据丢失）；不走 [选项 C 自动重试无上限]（死循环失败消息阻塞队列）。
_Avoid_: 死信直接丢弃、自动重试无上限（前者数据丢失，后者死循环阻塞队列）

**Transactional Outbox Pattern (Business + Outbox in Same Transaction)**:
业务事务 + Outbox 落库**同一事务**，MQ 调用走异步 worker：
- 业务侧 `@Transactional` 方法**必须**包含"业务表更新 + `t_outbox_event` insert"两步，原子提交；
- 提交后**不在事务内调 MQ**——避免 MQ 调用失败回滚已提交业务数据；
- 后台 `OutboxEventDispatcher` 定时（每 1 秒）拉 PENDING 事件 → 调 MQ → **仅**更新 `t_outbox_event.status = PUBLISHED`；
- 业务表已稳定，最坏情况是消息延迟 1 秒（业务一致性 > 推送实时性）；
- 这就是经典 **Transactional Outbox Pattern**；
- 与 [Settle 事务最小化] 一致——Settle 事务内仅操作 t_proposal + t_outbox_event，下游 hook（推送 / 上链 / 公示）走 Outbox 异步；
- 与 [TRUST 模式 voucher 双签 + Outbox] 同源——双签业务表 + outbox 同事务，链上发布走 worker；
- 不走 [选项 A 业务 + outbox 各自独立事务]（业务提交后 outbox 失败消息永远不发）；不走 [选项 C 分布式事务 XA/2PC/Saga]（复杂度极高业务团队不可控）。
_Avoid_: 业务 + outbox 独立事务、分布式事务 XA/2PC/Saga（前者业务与消息不一致，后者复杂度极高）

**Outbox 分级保留 + 归档表 (Tiered Retention & Archive)**:
`t_outbox_event` 表清理策略分级：
- **`status = PUBLISHED` 普通事件**：`created_at < NOW() - 30 days` 每日定时归档到 `t_outbox_event_archive`，原表清理；
- **`status = DEAD_LETTER`**：永远保留主表（不归档）—— 死信需长期可追溯（涉及司法 / 财务事件可能多年后被法院调取）；
- **`status = FAILED`** 进入 max_retries 之前保留 30 天；超 30 天若仍未变 PUBLISHED 自动转 DEAD_LETTER（与 [死信表] 通路合流）；
- **关键事件永不归档**：`event_kind IN (CHAIN_PUBLISH/VOUCHER_PUBLISHED/DISPUTE_ESCALATION/SETTLEMENT_DOWNSTREAM)` 等司法 / 财务关键事件**永远保留主表**；
  - 与 [Mock TxHash 永不重新生成] / [TRUST 模式 voucher 永久保留] / [反向异议成立后 manifest_hash 永久保留] 同源——历史不可改写；
  - 法院 / 街道办需要随时调取，不归入归档表（避免冷查询性能问题）；
- 归档表 `t_outbox_event_archive` 与主表 schema 一致 + 索引精简，仅做历史查询；
- 不走 [选项 A 永不清理]（表无限增长性能下降）；不走 [选项 C 每 7 天清理所有 PUBLISHED 事件]（短期事件查询丢失）。
_Avoid_: 永不清理、每 7 天清理所有 PUBLISHED 事件（前者性能下降，后者短期查询丢失）

### 业主投票匿名 vs 实名审计张力

**投票数据分级可见性 (Tiered Voting Data Visibility)**:
"业主 X 投了赞成 / 反对"的可见性按角色分级：
- **业主本人**：可看自己的全部投票记录（与 [投票数据永久保留与分级查询] 一致）；
- **C 端其他业主**：仅可看议案聚合结果（如赞成 1234 票 / 反对 567 票），**不能**看具体业主投了什么；
- **业委会主任 / 委员（B 端）**：可看议案聚合结果 + 分楼栋 / 分单元的投票分布（如"3 号楼赞成率 87%"），**不能**直接看具体业主标识；
- **楼代（B 端）**：仅可看自己负责楼栋的"未投人员名单 + 该楼栋聚合结果"（与 [楼代/主任未投名单可见 + 选项屏蔽] 同源），**不能**看具体业主选项；
- **街道办 / 党组（G 端）**：聚合结果 + 分楼栋分布，**不能**直接看具体业主标识；
- **法院 / 纪检（凭公文调取）**：可解封看具体业主标识 + 选项 + 时点 + 设备指纹（与 [Tier 3 个人敏感字段密封档案凭证] 同源——凭法院公文向街道办申请解封）；
- 不走 [选项 A 完全公开]（业主投反对可能被打击报复）；不走 [选项 C 完全匿名连业主自己都看不到]（双投防御 / 选举结果异议链路无法走通）。
_Avoid_: 投票完全公开、完全匿名连业主自己都看不到（前者打击报复风险，后者救济通路被堵）

**投票数据双表分离 + 加盐 hash + SM4 加密 (Aggregate vs Audit Table Split)**:
数据库层面投票数据物理隔离实现匿名 vs 可追溯双轨：
- **聚合表** `t_proposal_vote_aggregate(proposal_id, agenda_id, vote_option, count, total_area)` —— 仅存聚合结果，无业主标识；
- **审计表** `t_proposal_vote_audit(audit_id, proposal_id, anonymized_voter_id VARCHAR(64), vote_option, voted_at, ip_hash, device_fingerprint_hash, real_voter_user_id_encrypted SM4)`：
  - `anonymized_voter_id = sha256(real_voter_user_id || proposal_salt)` —— 不同议案 salt 不同，跨议案无法关联同一业主；
  - `real_voter_user_id_encrypted` 是 SM4 加密的真实 user_id（密钥与 [Tier 3 密封档案] 同一 KMS 托管）；
- **应用层查询路径**：
  - 聚合查询走 `t_proposal_vote_aggregate`（无业主标识、本质匿名）；
  - 业主自己查走 `t_proposal_vote_audit WHERE anonymized_voter_id = sha256(currentUserId || proposal_salt)`（仅自己能查）；
  - 计票（去重 + 双投防御）走 `t_proposal_vote_audit` 的 `real_voter_user_id_encrypted` 解密对比（service 层临时解密内存中比对，不持久化）；
- **凭公文解封**：法院申请凭 SM4 密钥解密 `real_voter_user_id_encrypted` 得到真实 user_id（与 [Tier 3 密封档案] 解封链路一致）；
- DBA 直接查表只能拿到加密 / 加盐数据，无法关联到具体业主；
- 不走 [选项 A 直接存单表]（DBA 可查任何业主选项）；不走 [选项 C 完全无审计表]（settle 计票去重 / 双投防御无法实现）。
_Avoid_: 投票数据存单表无加密、完全无审计表（前者 DBA 滥权风险，后者计票去重无法实现）

**投票截止前可改 + 计票采用最后版本 (Editable Until Deadline, Last-Vote-Wins)**:
业主投票可在投票截止前修改，承接误投救济需求：
- 业主在 C 端 [我的投票 - 修改] 修改时新写入 `t_proposal_vote_audit` 行（旧行不删，留审计）；
- **计票仅采用最后一次投票**（按 `voted_at` 最大值）—— 与 [双投防御 + 业主自投优先] 同源——业主自主选择以最新意愿为准；
- 修改次数无硬上限但 **1 议案修改 ≥ 5 次** 自动打 `frequent_vote_change_risk_tag = TRUE`，楼代 / 街道办可见（防止业委会通过反复施压让业主改票）；
- 修改风险标签触发后街道办可主动联系业主核实是否被胁迫（与 [线下票造假离线监控] 同源——异常行为模式触发人工核查）；
- 投票截止瞬间的"在飞票"按 [投票截止以业主签字时间为准] 处理；
- 不走 [选项 A 投了不可改]（误投无救济）；不走 [选项 C 可改无次数限制无监控]（频繁施压改票场景无防御）。
_Avoid_: 投了不可改、可改无次数监控（前者误投无救济，后者反复施压改票无防御）

**投票时点锚定 + 双计数（分子含失效业主 / 分母按截止时点）(Vote Timestamp Anchoring + Dual Counting)**:
业主投票期间资格变化（卖房 / 死亡 / 失格）的处理与 [voting engine] 双计数集成：
- 投票动作的法律效力时点锚定在投票当下（与 [投票截止以业主签字时间为准] / [证书撤销时点效力] / [失格后历史不追溯] 同源）；
- 业主投票后失效 → 已投票**保留**，作为历史动作不可撤销；
- **计票分母**按投票截止时点的 `t_property_owner_history.valid_from <= 截止时点 < valid_to` 取业主名册——失效业主**不计入分母**（投票截止时点已不是业主）；
- **但失效业主的投票若在截止时点前已投出 → 计入"参与计数"分子**——出现"分子有此业主、分母无此业主"看似不平衡，实际是**历史动作合法保留 + 当下分母客观事实**双轨：投票时该业主合法持业主资格，投出的票是历史合法动作；
- [AbstractVotingEngine.settle] 通过 `participating_owner_count`（分子，参与计数，含失效业主）vs `valid_owner_count_at_deadline`（分母，按截止时点 valid 业主）双计数承接；
- 双 2/3 quorum 按 `valid_owner_count_at_deadline` 计算（分母不含失效业主），但 quorum 检查时 `participating_owner_count / valid_owner_count_at_deadline` 可能 ≥ 1.0（分子大于分母）—— 数值上看是 100%+ 参与率，业务语义是"超过当前 valid 业主数都参与了，因为有部分投票时合法但截止时已失效的业主"，**仍视为达 quorum**；
- 不走 [选项 A 失效业主投票作废重新计票]（违背历史动作不追溯）；不走 [选项 C 失效后重新计算分母把分子也修正]（破坏 [投票截止以业主签字时间为准] 时点稳定性）。
_Avoid_: 失效业主投票作废、失效后修正分子（前者违背时点锚定，后者破坏稳定性）

**投票事件上链 + Q28 证据包集成 (Voting Events on Judicial Chain)**:
投票数据法庭可采性通过上链 + 证据包双轨保证：
- 投票截止 settle 完成后平台自动上链 `event_kind = SETTLEMENT_DOWNSTREAM` 事件，payload 含 `proposal_id` + `aggregate_count` + `anonymized_voter_id_hash_list`（哈希列表，不含具体业主标识）+ `total_area` + `participating_owner_count`；
- 司法链返回 `tx_hash` 落 `t_proposal.settlement_tx_hash`；
- 业主或法院通过 [Q28 证据包] 一键导出含投票相关证据时：
  - **公开层（业主端 ZIP）**：含聚合结果 + `settlement_tx_hash` + 业主自己的投票记录（`anonymized_voter_id` 自匹配）；
  - **密封档案（凭法院公文解封）**：含具体业主标识（解密 `real_voter_user_id_encrypted`）+ 选项 + 时点 + 设备指纹哈希；
- 法院凭判决书 / 调取证据通知书向街道办申请解封 → 街道办从 KMS 拉密钥解密 `real_voter_user_id_encrypted` → 提供给法院；
- 与 [真链 voucher 享受司法链证据规则] 同源——投票事件上链后享受同等司法链证据规则待遇（[最高人民法院关于互联网法院审理案件若干问题的规定] 第 11 条）；
- **mock 期投票事件**（M3 内测期）按 [Mock vs 真链证据等级差异] 处理——mock 期 tx_hash 是普通电子证据级别，不享受司法链等级"举证倒置 / 自动采信"待遇；
- 不走 [选项 A 出具 t_proposal_vote_audit 截图]（无司法链锚定证据强度弱）；不走 [选项 C 投票数据不上链]（业委会可主张数据被平台篡改）。
_Avoid_: 投票数据不上链、出具数据库截图作证（前者业委会可主张被篡改，后者证据强度弱）

### 议案通过到工程开工的前置准备期

**APPROVED_PREPARING 中间态 (Approved-Preparing State)**:
议案 settle 通过后到工程实际开工之间的"前置准备期"通过新增 `APPROVED_PREPARING` 状态承接：
- 议案状态机扩展：`DRAFT → VOTING → SETTLED → APPROVED_PREPARING → EXECUTING → CLOSED_*`；
- `APPROVED_PREPARING` 期间业委会负责协调招投标 / 合同签署 / 现场勘察；
- 期间动作落 `t_proposal_preparation_action(action_id, proposal_id, action_kind ENUM(BIDDING_LAUNCHED/BID_AWARDED/CONTRACT_SIGNED/SITE_SURVEY_DONE/FUND_INITIAL_DISBURSEMENT/SAFETY_HANDOVER_DONE/...), action_taken_by_user_id, action_taken_at, attachment_url)`；
- 业主在 C 端可看到"准备期进度"（招投标已启动 / 合同已签订 / 等待开工），与 [Q23.1 业主中间可见性分级] 一致；
- **状态推进规则**：`APPROVED_PREPARING → EXECUTING` 由业委会主任手工推进 + 必须满足三个硬条件，缺一不能推进：
  - [Bidding 已结束]（`t_bidding.status = CONCLUDED`）；
  - [合同已双方签署]（`t_construction_contract.signed_by_both_parties = TRUE`）；
  - [首期资金已划拨]（`t_proposal_payment_installment` 第一行已 paid）；
- **超期机制**：默认 `APPROVED_PREPARING` 上限 90 天，超期未推进 EXECUTING → 自动 `preparation_overdue_tag = TRUE` + 业主联署 ≥ 1/5 可触发 [议案变更 / 终止] 走 [变更议案分级处置]；
- 不走 [选项 A 直接 EXECUTING]（工程未开工状态语义不准）；不走 [选项 C settle 立即触发开工]（跳过准备期工程交付质量得不到保证）。
_Avoid_: 议案通过即进 EXECUTING、议案通过立即触发开工指令（前者状态语义不准，后者跳过准备期工程质量无保证）

**中标公告 5 天异议期 + 街道办一审 (Bidding Award Dispute Period)**:
业主对中标本身（价格偏高 / 工程商资质不符 / 程序违法）的二次异议期：
- 中标公告发布后 `t_bidding_award_announcement.dispute_period_end_at = announce_at + 5 days`（与 [选举结果 5 天异议期] / [线下票异议期] 5 天对齐）；
- 业主 5 天内可起 `dispute_kind = BIDDING_AWARD_DISPUTE` 走 [异议主表 + 业务附属]；
- **直接街道办一审**（与 [选举结果 5 天异议期 + 街道办一审] / [THIRD_PARTY_REFERENCE_DISPUTE 直接街道办一审] 同源——业委会 / 物业经理可能与中标方有利益关联，业委会一审回避缺失）；
- 街道办 **7 日内**出结论（比 14 日短，避免影响工程进度）：
  - **UPHELD**（异议成立，如发现工程商资质造假）→ 中标作废，重新招投标，原工程商落 [工程商黑名单触发]；
  - **REJECTED** → 中标继续走 `APPROVED_PREPARING`；
  - **PARTIAL_UPHELD**（如发现合同条款有问题但中标本身合法）→ 街道办指导业委会修订合同后继续；
- 5 天异议期内业委会**不能**签署合同（`t_construction_contract.signing_blocked_by_dispute = TRUE`），异议处理完后才能继续；
- 异议期满无异议 → 自动可签合同；
- 不走 [选项 A 业主无异议权]（中标方与业委会合谋利益输送无救济）；不走 [选项 C 异议期 30 天]（工程开工时间被过度推迟）。
_Avoid_: 业主无中标异议权、异议期 30 天（前者无救济通路，后者工程进度被砸）

**资金分期划拨与议案分期付款双字段绑定 (Tiered Disbursement Tied to Installments)**:
议案通过后资金的实际划拨与 [议案分期付款双字段] 完全集成：
- **首期划拨在 APPROVED_PREPARING 期间**根据合同约定（如签约后 30% 预付款）：
  - 满足 [合同已双方签署] 前提；
  - 业委会主任 + 物业经理双签（与 [TRUST 模式双密码核销] 同源，TRUST 模式下还需司法链上链）；
  - 校验 `frozen_balance >= 本期金额`；
- **后续分期划拨**在 `EXECUTING` 状态期间触发（工程进度 30% / 60% / 验收合格 100% 等）：
  - 校验工程进度证据（监理报告 / 验收证书 / 现场照片）；
  - 双签 + 校验后 `frozen_balance -= 本期金额` + `paid_amount += 本期金额`；
- 每次划拨落 `t_proposal_payment_installment(installment_id, proposal_id, installment_index, planned_amount, actual_paid_amount, paid_at, paid_by_signers, attachment_url, payment_progress_percentage)`；
- 与 [Q26 双签作废方案-甲] 一致——任一未完成签名的划拨可单方撤回；
- 与 [TRUST 模式 voucher 永久保留] 一致——每期划拨独立上链 + 永久保留；
- 不走 [选项 A 议案 settle 时一次性划拨]（资金提前给乙方违约风险高）；不走 [选项 C 业主每期投票同意]（已授权工程再次投票多余且影响进度）。
_Avoid_: 议案通过即一次性划拨、每期划拨业主重投票（前者乙方违约风险，后者重复审议影响进度）

**合同变更分级处置 (Tiered Contract Change Handling)**:
`APPROVED_PREPARING` 或 `EXECUTING` 期间合同变更按金额 / 工期 / 工程范围分级：
- **轻微变更**（金额变化 < 5% **且** 不涉及工程范围 **且** 工期 ≤ 7 天）：
  - 业委会内部 ≥ 2/3 同意 + 物业经理签字 + 街道办备案；
  - **不走业主重投票**；
- **中度变更**（金额变化 5%-20% **或** 工期 7-30 天 **或** 增加非主体工程范围）：
  - 业委会议决议 + 业主联署 ≥ 1/10 触发"轻量级业主大会"（简单多数表决）；
  - 街道办备案；
- **重大变更**（金额变化 > 20% **或** 工期 > 30 天 **或** 增加主体工程范围）：
  - 完整走 [变更议案分级处置] 双 2/3 重投票（与 [Q18 变更议案] 一致）；
- 落 `t_contract_change(change_id, proposal_id, change_kind ENUM(MINOR/MEDIUM/MAJOR), original_value, new_value, justification, decided_by, decided_by_meeting_id, decided_at, frozen_balance_delta)`；
- **frozen_balance 联动**：
  - 金额增加 → 触发 [变更议案追加扣减 + frozen 补冻]（与 [Q18 变更议案] 一致）；
  - 金额减少 → 解冻差额，差额返回 `available_balance`；
- 不走 [选项 A 所有变更走完整 Q18 重投票]（轻微变更成本过高影响进度）；不走 [选项 C 所有合同变更不需审批]（业委会主任借此擅自抬合同金额）。
_Avoid_: 所有合同变更走重投票、所有变更不需审批（前者成本过高，后者主任可滥权）

**工程暂停分级 + 期限受限 (Tiered Suspension with Bounded Duration)**:
工程在 `APPROVED_PREPARING` / `EXECUTING` 期间因外部原因暂停的分级处置：
- **轻度暂停**（雨季 / 寒冷停工 ≤ 30 天）：
  - 业委会主任 + 物业经理双签即可，不走业主大会；
  - 落 `t_proposal_suspension(suspension_id, proposal_id, suspension_kind ENUM(WEATHER/HOLIDAY/MINOR_DISPUTE_PENDING/...), planned_resume_at, actual_resume_at, decided_by_signers, suspension_kind_tier ENUM(MINOR/MEDIUM/MAJOR))`；
- **中度暂停**（30-90 天 **或** 涉及法律纠纷 **或** 工程商资质审查）：
  - 业委会议决议 + 街道办备案；
- **重度暂停**（> 90 天 **或** 工程商破产 **或** 政府征用 **或** 自然灾害严重影响）：
  - 街道办 / 党组介入 + 业主大会简单多数批准 + 决定后续路径（继续 / 终止 / 替换工程商）；
- **`frozen_balance` 暂停期间保留不解冻**（与 [换届期分级冻结 - 已通过议案的执行] 同源——业主已表决的财务安排不因外部暂停瓦解）；
- **暂停超 180 天自动触发 [议案变更] 强制重审**——业主通过新议案决定继续 / 终止 / 替换工程商；
- 重新启动从 `SUSPENDED` 切回 `EXECUTING` 走对应级别的反向流程（轻度双签 / 中度议决 / 重度业主大会）；
- 不走 [选项 A 主任直接点暂停]（主任可借此拖延工程）；不走 [选项 C 必须业主大会决议]（紧急情况如台风强制停工业主大会召集来不及）。
_Avoid_: 主任直接点暂停、所有暂停必须业主大会决议（前者主任可拖延，后者紧急情况来不及）

### 工程 EXECUTING 期间的现场治理

**独立监理账号体系 (Independent Supervision Account)**:
工程监理人作为住建部强制要求的独立第三方在系统中独立入驻：
- 监理获得独立 G/B 端账号，独立提交 `t_construction_supervision_report(report_id, proposal_id, supervisor_user_id, report_kind ENUM(WEEKLY/HIDDEN_CHECKPOINT/MILESTONE_INSPECTION/INCIDENT/ACCEPTANCE), reported_at, attachment_urls JSONB, hash_chain_tx, status)`；
- 监理报告与业委会双签**并列**（不可替代）——监理签字是 [隐蔽工程 checkpoint] / [进度款划拨] / [验收] 的硬条件；
- 监理资质由街道办在监理上线时审核（必须不是工程商关联方，与 [街道办前置审查] / [第三方反向异议直接街道办一审] 同源）；
- 监理失格 / 弃权 → 议案进 `SUSPENDED_INVESTIGATING` 走中度暂停（与 [工程暂停分级 - 中度] 同源）；
- 不走 [选项 A 监理只是工程商乙方不进系统]（监理纸件易篡改无链路审计）；不走 [选项 C 物业经理代上传]（监理独立性瓦解）；不走 [选项 D 业主大会选定监理]（住建部强制资质要求，业主大会无技术裁决能力）。
_Avoid_: 监理报告由物业经理代传、监理只签后期纸件、业主大会选定监理（前两者瓦解监理独立性，后者超出业主大会能力）

**隐蔽工程 checkpoint 不可逆 (Hidden Engineering Checkpoint Irreversibility)**:
地基 / 防水 / 管线等覆盖后无法回查的工程节点强制 3 方在场签字：
- 落 `t_hidden_engineering_checkpoint(checkpoint_id, proposal_id, checkpoint_kind ENUM(FOUNDATION/WATERPROOF/PIPE_BURIED/STRUCTURE/...), planned_inspect_at, actual_inspect_at, supervision_report_id, photo_urls JSONB, video_url, hash_chain_tx, signed_by_supervisor, signed_by_committee_user_ids ARRAY≥2, signed_by_property_manager, status ENUM(PENDING/PASSED/FAILED))`；
- 强制条件：监理 + 业委会 ≥ 2 人 + 物业经理同时在场签字 + 现场照片 / 视频上传 + [上司法链 mock+real 双轨] hash 永久保留；
- 任一签字缺失即 FAILED → 工程不能进下一阶段（混凝土浇注 / 回填 / 装修门禁锁死）；
- PASSED 的 checkpoint 永久保留 + 哈希上链，是后期发生漏水 / 沉降时工程商追责凭证；
- 不走 [选项 C 监理一人签即可]（监理误判 / 收买后业委会无救济）；不走 [选项 D 4 方加楼栋长]（业主代表无技术能力，协调成本过高）。
_Avoid_: 隐蔽工程仅监理签字、4 方签字加楼栋长（前者监理失误无救济，后者协调成本过高且业主代表无技术裁决能力）

**进度自动比对与违约金扣减 (Milestone-Based Auto-Penalty)**:
工程进度滞后违约金扣减自动化：
- 合同签订时录入 `t_construction_contract.milestone_schedule JSONB`（分段工期 + 分段违约金率，如"30 天进度 30%、滞后 5% 触发 0.1% 日违约金、滞后 20% 触发解约"）；
- 系统每周自动跑 `MilestoneCheckJob`：以监理报告 / 业委会现场照片为进度证据，比对 `expected_progress vs actual_progress`；
- 滞后超阈值 → 自动生成 `t_milestone_breach_event(event_id, proposal_id, milestone_index, expected_progress, actual_progress, breach_tier ENUM(MINOR/MEDIUM/MAJOR), penalty_amount, decided_at, contested_by_contractor, dispute_id)`；
- 违约金从 `frozen_balance` 直接扣减（业委会双签确认后才执行）+ 落 [Outbox + 司法链] 上链；
- 工程商 **7 日异议期**可起 `dispute_kind = MILESTONE_BREACH_DISPUTE` → 街道办仲裁（与 [中标公告 5 天异议期 + 街道办一审] 同源 7 日时间窗）；
- 街道办仲裁结论：UPHELD（业委会胜）→ 扣减执行；REJECTED（工程商胜）→ 退还违约金 + 工程商保留追究业委会刁难权；PARTIAL → 减少扣减比例；
- 不走 [选项 A 业委会自行协商]（系统无审计源）；不走 [选项 B 主任手工触发]（主任与工程商勾结风险）；不走 [选项 D 月人工核查]（响应慢且漏检）。
_Avoid_: 业委会自行协商、主任手工触发违约金、月度人工核查（前两者勾结风险，后者响应慢漏检）

**安全事故分级处置 (Tiered Construction Accident Handling)**:
工程期间安全事故按损失 / 伤亡严重度分级（对接《建设工程质量管理条例》分级）：
- **轻度**（财产损失 < 5 万 **且** 无人员受伤）：物业经理 + 监理 + 业委会现场处置 + 落 `t_construction_accident(accident_id, proposal_id, accident_kind, severity_tier ENUM(MINOR/MEDIUM/MAJOR), occurred_at, casualty_count, property_loss_amount, on_site_handled_by, attachment_urls, hash_chain_tx, status)` + 工程不停；
- **中度**（5-100 万 **或** 轻伤）：自动 `EXECUTING → SUSPENDED_INVESTIGATING` + 业委会议决议 + 街道办备案 + 邀第三方鉴定 + 工程商赔偿（与 [工程暂停分级 - 中度] 同源）；
- **重度**（> 100 万 **或** 重伤 **或** 死亡）：自动 `EXECUTING → SUSPENDED_LEGAL` + **强制报警**（系统自动发 webhook 至 110 / 派出所对接 API）+ 党组 / 政府介入 + 业主大会决定后续（继续 / 替换工程商 / 终止）；工程款 `frozen_balance` 锁定不解冻直至司法定责完成；
- 全部上司法链 + 关联工程商和监理 user_id（后续追责凭证）；
- 不走 [选项 A 业主大会决定继续]（轻微事故业主大会成本过高）；不走 [选项 C 工程商保险私下处理]（业委会未尽到注意义务承担连带责任）；不走 [选项 D 所有事故业主大会表决]（响应过慢）。
_Avoid_: 所有事故业主大会表决、工程商保险私下处理、轻微事故业主大会决定（前者响应慢轻微事故成本过高，中者业委会承担连带责任，后者成本过高）

**验收质量纠纷的对抗机制 (Acceptance Dispute Resolution)**:
工程完工时业委会拒绝验收 vs 工程商主张完工的对抗处置：
- 落 `t_acceptance_dispute(dispute_id, proposal_id, contractor_claim TEXT, committee_claim TEXT, opened_at, status ENUM(MEDIATION/EXPERT_REVIEW/CONCLUDED), expert_review_id, final_verdict, frozen_balance_locked)`；
- 默认 **30 天调解期**：街道办指派调解员（与 [纠纷异议升级 5 级] 同源）；
- 调解失败 → 业主大会简单多数决定**走第三方独立鉴定**——鉴定费用从 `frozen_balance` 中预留 5% 工程款支付，鉴定结果出来后败诉方承担；
- 鉴定结论**不可推翻**：合格 → 工程商收尾款 + 业委会必须签验收；不合格 → 工程商整改 / 减款 / 解约（按 [合同变更分级处置] 中度 / 重度走）；
- 整个对抗期间：`frozen_balance` 锁死不释放、`quality_acceptance_status = DISPUTED`、议案状态 `EXECUTING_DISPUTE`；
- 鉴定结论作为后续 [Outbox + 司法链] 上链证据；
- 不走 [选项 A 业委会单方拒收]（工程商可起诉业委会无理拒收）；不走 [选项 B 工程商单方主张无异议则通过]（业委会被绑架）；不走 [选项 D 直接民事诉讼]（工程款被无限冻结 12-24 月双方都伤）。
_Avoid_: 业委会单方拒收、工程商单方主张完工、直接走民事诉讼（前两者一方被绑架，后者周期过长双方都伤）

### 工程验收后的保修期与缺陷责任

**留存款分阶段释放 (Tiered Retention Release)**:
工程验收通过时合同款不一次付清，按住建部标准合同范本约定 5%-10% 留存款分阶段释放：
- 落 `t_retention_balance(retention_id, proposal_id, retention_amount, retention_rate, release_schedule JSONB, current_balance, status)`；
- 验收通过 → 释放 `(1 - retention_rate)`，剩余进 retention pool 锁定；
- 释放节奏对接住建部 [建设工程质量管理条例] 保修期分段：
  - **1 年期满 → 释放 30%**（覆盖最常见的设备 / 装修保修）；
  - **2 年期满 → 释放 30%**（覆盖电气 / 管线 / 设备安装）；
  - **5 年期满 → 释放 40%**（覆盖屋面防水 / 外墙 / 主体结构）；
- 每次释放需业委会双签 + 监理出"无质量问题"声明 + [上司法链 mock+real 双轨]；
- 工程款现金流 vs 业主救济权的住建部标准平衡点；
- 不走 [选项 A 验收即付清]（保修靠工程商信誉无救济）；不走 [选项 C 5%一次性 5 年释放]（业主吃亏，无分段调整空间）；不走 [选项 D 业主大会逐期表决]（业主疲劳）。
_Avoid_: 验收即付清、留存款一次性 5 年后释放、业主大会逐期表决释放（前者无救济，中者业主吃亏，后者业主疲劳）

**保修期内业主报修通路 (Warranty Claim Pipeline)**:
业主在保修期内发现质量问题直接对工程商起请求，去掉物业经理 / 业委会的中间环节：
- 落 `t_warranty_claim(claim_id, proposal_id, claimed_by_user_id, claim_kind ENUM(LEAK/SUBSIDENCE/EQUIPMENT_FAILURE/STRUCTURAL/OTHER), claimed_at, evidence_urls JSONB, severity ENUM(MINOR/MEDIUM/MAJOR), status ENUM(SUBMITTED/CONTRACTOR_NOTIFIED/UNDER_REPAIR/REPAIRED/DISPUTED/ESCALATED), claim_kind_in_warranty_window BOOL, repair_start_at, repair_completed_at, signed_by_owner, signed_by_supervisor, hash_chain_tx)`；
- 系统**自动校验**保修类别 + 时间窗（屋面 5 年 / 设备 2 年等）→ `claim_kind_in_warranty_window`；
- 在窗内 → 自动通知工程商 + 业委会 + [独立监理账号体系]（保修期内监理保留账号）；
- 工程商响应时限分级（与 [安全事故分级] 同节奏）：
  - **轻度 7 日**完成整改；
  - **中度 30 日**；
  - **重度 90 日**；
  - 必须 3 日内**响应**确认收悉；
- 整改完成 → 业主 + 监理双方现场签字 + 上司法链；
- 不走 [选项 A 业主自找工程商]（业委会无审计源）；不走 [选项 C 物业经理代收]（中间环节传递失真）；不走 [选项 D 每个保修类别独立流程]（用户体验割裂）。
_Avoid_: 业主自找工程商、物业经理代转报修、每类别独立流程（前者无审计，中者传递失真，后者体验割裂）

**工程商失联倒闭兜底 (Contractor Default Fallback)**:
工程商在保修期内失联 / 倒闭 / 拒不整改时业主救济通路：
- **第一步**：工程商 3 日不响应 / 整改超过分级时限（轻度 7 日 / 中度 30 日 / 重度 90 日）→ 自动 `t_warranty_claim.status = DISPUTED` + 业委会议决议 + 街道办仲裁（与 [纠纷异议升级 5 级 - 街道办一审] 同源）；
- **第二步**：街道办仲裁认定工程商违约 → **直接从 retention_balance 扣减整改费用**（合同范本已写明，不需法院判决，retention 持有人是业委会，扣减权法定）；retention 不足 → 走 [应急维修资金] / 业主大会简单多数批准追加扣减 [年度预算]；
- **第三步**：retention 扣减发生 → 工程商落 [工程商黑名单触发]（与 [中标公告异议成立] / [重大缺陷升级] 同源）+ 街道办全街道公告 + [上司法链]（后续工程商起诉业委会"扣留款无理"也有完整证据链）；
- 不走 [选项 A 业主自费整改]（无救济）；不走 [选项 B 法院追偿]（追偿成功率低周期长）；不走 [选项 D 强制工程保险]（保险产品不成熟成本过高）。
_Avoid_: 业主自费整改、走法院追偿、强制工程保险（前者无救济，中者效率低，后者保险产品不成熟）

**保修期满公示与最终释放 (Warranty End Public Announcement)**:
5 年保修期满 retention 剩余部分释放前的最后异议机制：
- 期满前 **30 天**自动 `WarrantyExpiryNoticeJob` 推送至全体业主 + 张贴公告 + 落 `t_warranty_expiry_notice(notice_id, proposal_id, contractor_id, total_released, remaining_retention, total_claims, completed_claims, pending_claims, scheduled_release_at, dispute_period_end_at)`；
- 业主 **30 天异议期**（与 [中标公告 5 天异议期] / [选举 5 天异议期] 时间窗体系延展）可起：
  - **dispute_kind = WARRANTY_PERIOD_END_DISPUTE_PENDING_REPAIR**（"还有未整改项"）→ 走 Q39.2 / Q39.3 流程，retention 锁定；
  - **dispute_kind = WARRANTY_PERIOD_END_DISPUTE_HISTORICAL_FRAUD**（"工程质量历史造假"）→ 走 [街道办一审]；
- 30 天无异议 / 异议处理完毕 → 业委会双签 + [上司法链] + retention 全部释放给工程商；
- 不走 [选项 A 直接释放无通知]（业主无救济）；不走 [选项 C 业委会自行决定]（与工程商勾结风险）；不走 [选项 D 业主大会批准]（动员成本过高 + 业主无技术能力评估）。
_Avoid_: 直接释放、业委会自行决定释放、业主大会批准释放（前者无救济，中者勾结风险，后者动员成本过高）

**重大缺陷责任自动升级 (Major Defect Auto-Escalation)**:
保修期内发现重大缺陷（主体结构开裂 / 大面积渗漏 / 危及生命安全）时基于 `severity = MAJOR` 自动升级机制：
- **自动停止该工程商所有其他在保工程的留存款释放**（防止工程商失血逃逸，跨议案统一锁定 retention）；
- 自动 `EXECUTING_WARRANTY → EMERGENCY_REPAIR` + 业委会议决议 + 街道办通报；
- 强制邀第三方鉴定（费用先从 retention 预留 5%，败诉方承担，与 [验收质量纠纷第三方鉴定] 同源）；
- 鉴定确认重大缺陷 → 工程商**全额赔偿** + retention 全部扣减 + 进 [工程商黑名单触发] **永久**；
- 涉及人员伤亡 → 自动报警 / 党组介入（与 [安全事故重度] 同源——重度事故路径完全复用）；
- 全链路 [上司法链 mock+real 双轨]；
- severity 双锁：业主报修时填写 + 监理鉴定确认（防止业主单方升级造成工程商滥诉风险）；
- 不走 [选项 A 走普通报修]（响应过慢）；不走 [选项 C 业主大会决定升级]（响应过慢）；不走 [选项 D 业委会全权决定]（被收买风险）。
_Avoid_: 重大缺陷走普通报修、业主大会决定是否升级、业委会全权决定升级（前两者响应慢，后者被收买风险）

### 公共收益经营治理

**经营合同分级审批 (Tiered Public Revenue Contract Approval)**:
公共收益经营合同（车位 / 广告 / 场地 / 储物间）按金额 / 期限 / 空间类型分级审批（与 [合同变更分级处置] 同源）：
- 落 `t_public_revenue_contract(contract_id, tenant_id, contract_kind ENUM(PARKING/ELEVATOR_AD/SPACE_LEASE/STORAGE_RENT/...), counterparty_org_id, contract_tier ENUM(MINOR/MEDIUM/MAJOR), annual_amount, contract_period_months, space_type, decided_by, decided_by_meeting_id, signed_at, expires_at, status ENUM(ACTIVE/EXPIRED/TERMINATED/DISPUTED))`；
- **轻度**（年金额 < 5 万 **且** 期限 < 1 年 **且** 非主要公共空间）：业委会 ≥ 2/3 同意 + 街道办备案；
- **中度**（5-50 万 **或** 1-3 年 **或** 主要公共空间如电梯 / 楼道广告）：业主大会**简单多数**（[Q32 业主大会议事规则] 简单多数路径）+ 街道办备案；
- **重度**（> 50 万 **或** > 3 年 **或** 改变公共空间用途）：完整双 2/3 议案（与 [变更议案分级处置 - 重大] 一致）+ 党组前置审查（与 [Q23.4 街道办前置审查] 同源）；
- 不走 [选项 A 物业全权决定]（暗箱抽水风险）；不走 [选项 C 所有合同走业主大会]（业主疲劳）；不走 [选项 D 主任单签]（单点失败）。
_Avoid_: 物业全权决定、所有合同走完整业主大会、主任单签（前者暗箱抽水，中者业主疲劳，后者单点失败）

**收入归集与三种物业模式差异化代理 (Public Revenue Account Across Property Modes)**:
公共收益归属法定属于全体业主共有（《民法典》§282），独立账户在物业模式三套账之上：
- 落 `t_public_revenue_account(account_id, tenant_id, account_kind = PUBLIC_REVENUE_OWNER_OWNED, balance, frozen_balance, available_balance)`——**法定属于全体业主共有**；
- **LUMP_SUM 包干制**：物业代收 → **必须**当日 / 当周转入 `t_public_revenue_account`，物业账户仅"过路户"+ 系统强制 `t_public_revenue_reconciliation` 日终对账 + 偏差报警；C 端业主能看 account 余额但不看物业内账（与 [包干制数据范围严格锁死本组织] 一致）；
- **FUND_RAISING 筹金制**：物业代理收支，每笔进 / 出账业委会主任 [双签]，C 端业主默认季度 / 年度公示（与 [Q23.1 业主中间可见性分级] 一致）；
- **TRUST 信托制**：直接进信托账户，每笔实时 [上司法链 mock+real 双轨] + C 端业主穿透展示（与 [TRUST 模式双密码核销] / [voucher 永久保留] 一致）；
- 不走 [选项 A 物业自有账户混账]（违法）；不走 [选项 C 业委会管账户物业不接触]（业委会非全职无经营对接能力）；不走 [选项 D 街道办设统一账户]（越级介入）。
_Avoid_: 物业自有账户混账代收、业委会管账户物业不接触、街道办设统一账户（前者违法混账，中者业委会无经营能力，后者越级介入）

**经营成本明细分级签字 + 自动税务 (Cost Tiered Signing and Auto Taxation)**:
公共收益对应可关联成本 + 经营服务费 + 自动税务处理：
- 每笔成本落 `t_public_revenue_cost(cost_id, contract_id, cost_kind ENUM(MAINTENANCE/UTILITY/INSURANCE/TAX/PROPERTY_MGMT_FEE/AGENCY_COMMISSION/OTHER), amount, paid_to_org_id, paid_at, supporting_doc_url, signed_by, hash_chain_tx)`；
- **成本支出分级签字**：
  - 单笔 < 5000 元 → 物业经理签即可；
  - 5000-5 万 → 业委会双签；
  - 大于 5 万 → 业委会议决议；
- **税务自动处理**：物业代收的公共收益**默认 6% 增值税 + 25% 企业所得税**（按地方税务局口径），落 `t_public_revenue_tax_record`，季度 / 年度报税材料系统自动生成；
- **物业经营服务费**（物业为业主代理经营的合理报酬，通常 5%-15% 净收益）必须在 [经营合同分级审批] 中事先约定，不能事后偷扣；
- 不走 [选项 A 不区分成本只记总收入]（无法监督是否中饱私囊）；不走 [选项 C 成本由物业全权处理]（虚报成本风险）；不走 [选项 D 所有成本走业委会议决议]（业委会被淹没）。
_Avoid_: 不记成本明细、成本由物业全权处理、所有成本走业委会议决议（前者无监督，中者虚报风险，后者业委会被淹没）

**净收益三去向年度分配 (Annual Net Revenue Allocation)**:
扣除成本后的净收益由业主大会逐年决定按比例分配（[Q32 业主大会议事规则] 标准议程之一）：
- **维修资金补充**（推荐 ≥ 50%，对接 [t_maintenance_fund_account] 总账，与 [V2.7 财务公示快照] 衔接）；
- **社区公益**（儿童活动室 / 老年食堂 / 安保升级，走 [Q32 简单多数议案]）；
- **业主分红**（按 [专有面积比例] 分到每户业主账户，分红达个税起征点（500 元 / 户 / 年）需代扣个税）；
- 落 `t_public_revenue_allocation(allocation_id, tenant_id, fiscal_year, total_net_revenue, to_maintenance_fund, to_community_welfare, to_owner_dividend, decided_by_meeting_id, allocated_at, status)`；
- **业主大会未表决兜底 → 默认 100% 进维修资金**（防止资金沉睡僵局）；
- 不走 [选项 A 直接现金分红到业主]（削弱维修资金，5 年后大修无钱）；不走 [选项 B 全部归入维修资金]（一刀切忽略业主权利）；不走 [选项 D 业委会全权决定]（越权违背 [民法典 §282] 共有规则）。
_Avoid_: 直接现金分红、强制 100% 维修资金、业委会全权决定分配（前者削弱长期维修，中者忽略业主权利，后者越权）

**经营对手方违约分级处置 (Public Revenue Counterparty Breach Tiered Handling)**:
广告商 / 停车承租 / 场地承租等对手方违约处置（与 [进度滞后违约金扣减] / [工程商失联兜底] 同源）：
- **轻度违约**（拖欠 < 30 天）：系统自动催收（短信 / 邮件 / 推送）+ 累计滞纳金 0.05% / 日；
- **中度违约**（30-90 天）：业委会议决议起催收函 + 落 `t_public_revenue_breach(breach_id, contract_id, breach_kind ENUM(LATE_PAYMENT/DEFAULT/UNAUTHORIZED_USE/...), severity, accrued_penalty, claimed_at, status)` + 街道办备案；
- **重度违约**（> 90 天 **或** 已跑路）：自动 `t_public_revenue_contract.status = TERMINATED` + 起诉立案（业委会 / 物业代为起诉，诉讼费从下笔预付保证金扣减）+ 对手方落 [运营商黑名单]（与 [工程商黑名单触发] 同源 cross-reference）+ [上司法链 mock+real 双轨]；
- **预付保证金机制**：[经营合同分级审批] 必须约定 1-3 个月预付保证金（落 `t_public_revenue_security_deposit`），违约时直接扣减（与 [retention_balance 直接扣减] 同源法理）；
- 不走 [选项 A 业委会自行催收]（无系统审计）；不走 [选项 C 物业全权处置]（瞒报风险）；不走 [选项 D 每次违约走业主大会]（成本过高）。
_Avoid_: 业委会自行催收、物业全权处置、每次违约走业主大会（前者无审计，中者瞒报风险，后者成本过高）

### 三种物业模式之间的切换

**模式切换议案的法理门槛 (Property Mode Switch Threshold)**:
LUMP_SUM ↔ FUND_RAISING ↔ TRUST 三种物业模式的切换走完整双 2/3 议案 + 街道办前置审查 + 90 天公示期：
- 落 `t_property_mode_switch_proposal(switch_id, proposal_id, tenant_id, from_mode ENUM(LUMP_SUM/FUND_RAISING/TRUST), to_mode, switch_reason, planned_switch_at, transition_period_days, status ENUM(VOTING/APPROVED/IN_TRANSITION/COMPLETED/ROLLBACK), rollback_reason)`；
- 走 [Q18 选聘 / 解聘物业议案] 同级别（双 2/3）—— 切换模式本质就是"重新定义物业角色边界"；
- 党组前置审查（与 [Q23.4 重大资金党组审查] 同源）—— 政府关心模式切换可能引发物业公司挤兑维修资金跑路；
- 90 天公示期：让业主、物业、外部审计 / 担保方都有充足时间提异议或退出（与 [Q23.1 业主中间可见性分级] 一脉相承）；
- 不走 [选项 A 业委会议决议]（业委会越权代表业主决定模式）；不走 [选项 C 街道办指定]（越级）；不走 [选项 D 物业自决]（违反业主主权）。
_Avoid_: 业委会议决议切换、街道办指定模式、物业自决切换（前者业委会越权，中者越级，后者违反业主主权）

**历史数据保留 + 语义边界标记 (Historical Data Preserve with Semantic Boundary)**:
模式切换后，旧模式的所有数据（账单 / 合同 / 议案 / 投票 / 维修记录）保留 + 历史不可改 + 新数据走新模式语义：
- `tenant.property_mode_history JSONB`（含切换时间点、from_mode、to_mode、switch_proposal_id），所有历史议案 / 账单 / 合同的 `property_mode_at_creation` 字段标识当时模式；
- 历史数据**永久保留**（与 [TRUST 模式 voucher 永久保留] / [V2.7 财务公示快照] 同源不可篡改原则）；
- 切换后新创建的议案 / 账单 / 合同走新模式的字段 / 校验规则；
- C 端业主能查"切换前"和"切换后"两段历史（业主主权 + 透明性）；
- 切换不删任何旧 row，仅做"语义边界标记"（软件演化中的"不破坏既有不变量"原则）；
- 不走 [选项 A 全部归档冷存储新模式空白起步]（业主无法回查历史财务）；不走 [选项 C 全部迁移到新模式语义]（违反"已发生事实不可篡改"）；不走 [选项 D 旧数据由旧物业带走]（违反业主主权）。
_Avoid_: 历史数据冷归档新模式空白起步、强行迁移到新模式语义、旧物业带走旧数据（前者业主无法回查，中者违反不可篡改，后者违反业主主权）

**模式切换资金清算 + 第三方审计 (Property Switch Fund Clearing with Third-Party Audit)**:
切换时旧物业账户里的钱与在执行合同的交接走 60-120 天 IN_TRANSITION + 第三方审计 + 旧物业黑名单兜底（与 [业委会换届交接] 同源——模式切换本质就是"对物业的换届"）：
- 议案 APPROVED 后进 `IN_TRANSITION`（默认 60 天，最长 120 天）；
- 旧物业必须在 30 天内**全口径披露**：物业费应收 / 已收 / 应付 / 已付 + 公共收益代收 / 已转 / 未转 + 维修资金代管 + 在执行的合同清单；
- 强制邀请第三方审计（费用从公共收益预留 1% 兜底）：审计师独立核账输出 `t_property_mode_switch_audit` 审计报告 + [上司法链 mock+real 双轨]；
- 业委会双签 + 街道办盖章后 → 资金物理转账（公共收益账户 / 维修资金账户的合规权属变更）；
- **旧物业未配合 / 拖延 → 强制扣减保证金 + 街道办强制接管 + 旧物业落 [物业公司黑名单]**（与 [工程商黑名单触发] / [运营商黑名单] 同源 cross-reference）；
- 在执行的工程合同 / 经营合同**保持原合同**（履行中 → 不因模式切换而中断），但代理方变更（旧物业不再签字 / 新物业接管签字）；
- 不走 [选项 A 旧物业自行交接]（偷藏 / 隐匿风险）；不走 [选项 C 锁死旧物业账户]（瓦解日常运营）；不走 [选项 D 业主大会监督交接]（无审计技术能力）。
_Avoid_: 旧物业自行交接、锁死旧物业账户、业主大会监督交接（前者偷藏风险，中者运营瓦解，后者无审计能力）

**模式切换 IN_TRANSITION 分级冻结 (Switch Transition Tiered Freeze)**:
`IN_TRANSITION` 期间分级冻结业务操作（与 [换届期分级冻结] / [HANDOVER_LOCK] 同源）：
- **冻结**：起新议案（除"模式回滚议案"外）、起新经营合同、变更现有合同金额、动用维修资金 / 公共收益作非紧急支出；
- **开放**：在执行的工程合同（[Q39 保修期] / [Q38 EXECUTING] 流程不中断）、紧急维修（如水管爆裂）、业主日常报修、催收已发生欠费；
- **保留单一通路 → "模式回滚议案"**：若 IN_TRANSITION 期间发现切换不可行（如新物业无法接收 / 第三方审计发现重大问题），可由业委会或业主联署 ≥ 1/5 起 [回滚议案] 走简单多数，回滚至 from_mode；
- 已生效的法律义务（保修 / 合同履行）不能因切换瓦解，但新增风险敞口必须冻结；
- 不走 [选项 A 全冻结]（小区运营停摆）；不走 [选项 B 全开放]（旧物业可能在切换期偷塞合同）；不走 [选项 D 街道办决定冻结哪些]（越权且响应不及时）。
_Avoid_: IN_TRANSITION 全冻结、IN_TRANSITION 全开放、街道办决定冻结哪些（前者运营停摆，中者旧物业偷塞合同，后者越权）

**切换失败 4 种回滚触发 (Property Switch Rollback Triggers)**:
切换失败的回滚机制覆盖 4 种核心失败路径：
- **AUDIT_FRAUD（审计触发回滚）**：第三方审计发现旧物业账目造假 → 街道办接管旧物业账户 + 重新走 [模式切换议案的法理门槛]（暂停回到 from_mode）+ 旧物业进 [物业公司黑名单] + 司法移交；
- **NEW_PROPERTY_QUIT（新物业退出）**：新物业 IN_TRANSITION 期内主动放弃 → 自动回滚至 from_mode + 业委会重新启动招聘 + 新物业违约 → 扣减投标保证金；
- **OWNER_ROLLBACK_PROPOSAL（业主回滚议案）**：业主联署 ≥ 1/5 起 [回滚议案] 简单多数通过 → 回滚至 from_mode + 切换议案进 `ROLLBACK` 状态；
- **TIMEOUT_EXPIRED（超期未完成）**：120 天硬性上限超期 → 自动回滚（防止 IN_TRANSITION 永远拖着）；
- 落 `t_property_mode_switch_proposal.rollback_reason ENUM(AUDIT_FRAUD/NEW_PROPERTY_QUIT/OWNER_ROLLBACK_PROPOSAL/TIMEOUT_EXPIRED)`；
- 回滚后所有 IN_TRANSITION 期间的 [模式切换 IN_TRANSITION 分级冻结] 解除，恢复 from_mode 正常运行；
- 不走 [选项 A 强制完成切换]（埋下后续纠纷）；不走 [选项 C 法院判决决定]（成本过高）；不走 [选项 D 街道办决定回滚]（越权代表业主决定）。
_Avoid_: 强制完成切换、法院判决决定回滚、街道办决定回滚（前者埋纠纷，中者成本过高，后者越权）
