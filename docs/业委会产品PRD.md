# **智慧社区治理与业主自治数字化平台产品需求文档 (PRD)**

## **业务背景与系统架构**

现代城市基层治理体系中，业主委员会（简称业委会）作为业主自治的核心组织，在公共资产管理、物业监督及社区重大决策中发挥着不可替代的作用。传统线下自治模式长期面临投票效率低、业主身份核实难、财务不透明及换届选举程序不合规等痛点。伴随《中华人民共和国民法典》对业主共同决定事项投票权计算规则的重构，建设一套合规、高效、透明的数字化业主自治平台成为促进行政监管与基层自治良性互动的必然选择。  
本平台采用多租户（SaaS）架构设计，构建覆盖 C 端（业主）、B 端（业委会、物业服务企业）以及 G 端（街道办事处、居委会监管端）的数字化协同生态，实现数据互联、合规互通。

| 用户端 (Portals) | 核心用户群体 | 业务权限与安全级别 |
| :---- | :---- | :---- |
| **C端业主端 (App / 小程序)** | 物业专有部分产权人、共有产权代表、受托投票人 | 经过 L3/L4 级实名核验，支持人脸识别与数字证书签名。 |
| **B端业委会工作台** | 业委会委员、候补委员、业主大会筹备组成员 | 基于角色访问控制（RBAC），具备议题发起、财务记账及公告发布权限。 |
| **B端物业管理系统** | 物业客服、工程、财务管理人员 | 负责日常报修响应、工单反馈、公共收益录入及账单推送。 |
| **G端政府监管端** | 街道办事处、社区党委、居委会操作员 | 拥有选举系统审批、候选人资格联审、投诉干预等终审与监督权限。 |

## **1\. 用户管理 (User Management)**

### **1.1 业主身份认证与实名核验**

平台对业主身份的核验直接关系到表决的司法效力，必须建立严密的校验通道。系统支持通过身份证、港澳居民来往内地通行证等多种登记类型进行实名绑定 。

#### **1.1.1 线上多级核验工作流**

系统默认引导业主通过“姓名 \+ 身份证号 \+ 手机号 \+ 房产证号/不动产登记信息”进行四要素比对，并对接国家不动产登记数据中心或本地住建部门数据库。电子投票前强制唤起 L3 级实名认证，引入人脸识别机制，以确认为产权人本人操作。

#### **1.1.2 线下兜底绑定流程**

为保障不愿使用人脸识别或无法线上认证的业主权利，系统保留线下审核通道 。业主可携带产权证明及身份证件前往居委会进行线下核验登记，居委会操作员通过 G 端录入信息并激活 C 端账户 。

| 认证等级 | 核验要素 | 适用场景 | 替代路径与合规逻辑 |
| :---- | :---- | :---- | :---- |
| **L1 (基础认证)** | 手机号 \+ 短信验证码 | 日常公告查看、社群交流 | 无需高级信息，通过手机运营商实名制进行初步筛选。 |
| **L3 (实名认证)** | 姓名 \+ 身份证号 \+ 人脸识别 | 业主大会线上电子投票 | 提供人脸识别授权协议，不同意者可转为线下身份证与房产证原件人工核销。 |
| **L4 (司法级认证)** | L3认证 \+ 电子营业执照 / 涉外证件人工审核 | 法人业主投票授权、共有产权变更 | 企业法人需上传统一社会信用代码证、法人身份证及授权委托书原件，由居委会进行终审。 |

### **1.2 多套房产管理**

多套房产管理机制旨在解决单业主在同小区或跨小区拥有多处资产时的身份识别与计票冲突。

#### **1.2.1 同小区内一户多房计算规则**

在单一物业管理区域内，当同一业主拥有两个或以上专有部分物理单元时，其在计算“业主人数”和“投票权数”时，系统自动执行合并去重与面积累加逻辑。

* **人数投票权**：无论该业主在该小区拥有多少套房产，其业主人数仅按一人（![][image1]）计算。开发商尚未出售或虽已出售但尚未交付的部分，以及同一买受人拥有多个专有部分的，亦均合并按一人计算。  
* **专有面积投票权**：其拥有的专有部分面积按各套房产登记建筑面积之和进行累加。

#### **1.2.2 跨小区多房关联模型**

当业主通过 L3 级实名认证登录 C 端后，其统一自然人账户（UID）会关联多张由不同小区 SaaS 租户写入的社区业主身份实体（OPID）关系表。用户可在前端界面通过租户切换组件无缝切换不同小区，系统根据当前激活的小区 ID 动态加载该用户在该小区的房产、投票议题及账单。

### **1.3 共有产权与投票委托管理**

对于一个专有部分物理单元登记有两个以上所有权人的情形，系统强制推选一人行使表决权，所代表的业主人数和专有面积不进行重复拆分。其他共有产权人需在系统内提交线上确认书，指定单一代表账号绑定该房产。  
系统支持线上委托投票。业主需要在投票系统中完成身份认证和房屋绑定，并提交包含受托人姓名、身份证号、委托期限及手机号在内的委托申请。被委托人亦须完成实名认证。为防止恶意刷票，系统设定代理限额：一个受托人最多只能接受三名业主的委托进行投票。

### **1.4 欠费限制配置**

系统提供针对未按管理规约交纳维修资金或物业服务费的业主限制其参选权及表决权的配置工具，具体限制规则支持在《业主大会议事规则》框架下由小区按以下方案进行自定义：

| 限制配置方案 | 投票权计算逻辑 | 被选举权限制逻辑 | 适用场景说明 |
| :---- | :---- | :---- | :---- |
| **方案 A (完全限制)** | 其投票权数不计入总人数及建筑物总面积 | 禁止参选业委会委员 | 适用于极高违约比例小区，通过剥夺两权倒逼履约。 |
| **方案 B (完全不限制)** | 正常计入总人数及总面积 | 允许参选业委会委员 | 默认合规方案，保障基本成员权不受财务争议阻碍。 |
| **方案 C (限制被选举权)** | 正常计入总人数及总面积，保留投票权 | 禁止参选业委会委员 | 业界主流折中方案，限制欠费业主进入决策核心。 |

## **2\. 物业管理 (Property Management)**

### **2.1 日常维护与报修工单**

物业管理模块侧重于物业服务企业对小区的维护、修缮与资金联动治理。报修流程不能被设计成单纯的“推进状态”，而必须形成“前端傻瓜化输入、后台隐式确权、空间拓扑全绑定、网格员异步纠偏、治理模式自动变轨、财务结算与公示评价闭环”的端到端业务链路。系统以 RBAC + ABAC 统一权限底座控制每个动作的主体、空间范围、资金权限和数据可见范围。

#### **2.1.1 多模态前端报修与智能分流**

1. **私有空间报修：报修我家**
   * 业主在 C 端点击“报修我家”后，前端只展示其当前 Session 已激活小区下、已通过实名认证和网格员线下核销的房产。
   * 门牌号、楼栋、单元等物理信息默认置灰不可编辑。后端根据 `room_id` 调用资产服务向上追溯 `unit_id`、`building_id`、`community_id`，形成刚性物理绑定。
   * 若业主拥有多套房，必须先切换当前房产上下文；系统不得允许在提交表单时手工篡改 `room_id`。
   * 后端校验通过后创建工单，记录 `reporter_account_id`、`room_id`、`building_id`、`source=C_OWNER_APP`、`space_scope=PRIVATE`。

2. **公共/邻里区域报修：图片、视频、语音、文字快速提交**
   * 业主在小程序选择“公共区域报修”后，可上传图片、短视频、短语音或简短文字，例如“2栋大堂门禁一直在滴水”。
   * AI 多模态服务对语音执行 STT，对图片/视频执行 OCR、目标识别和场景分类，并从文本中抽取楼栋、单元、设施类型、风险词。
   * AI 命中空间节点时，系统自动回显候选位置和置信度，业主只需确认提交；后端绑定 `t_community_space_node` 中的具体节点。
   * AI 未命中或置信度不足时，系统不得要求业主反复补充位置。工单自动降级绑定到“小区公共区域（全局空节点）”，标记 `NEED_MANUAL_LOCATION`，进入公共网格抢单池。

3. **提交鉴权与防污染规则**
   * RBAC 校验用户是否为当前小区业主、租户、授权代报人或物业工作人员。
   * ABAC 校验报修空间与主体关系：私有空间必须绑定本人或授权房产；公共空间只允许在当前小区范围内提交。
   * 系统执行幂等去重：同一报修人、同一空间节点、相近标题、相近时间窗内的重复报修，返回已有工单并允许追加图片、视频、语音等证据。

#### **2.1.2 异步网格抢单与物理纠偏**

公共区域、跨栋、AI 低置信度或存在位置争议的工单，必须经历人工二次核验，避免后续表决分母、维修资金分摊和公示范围错误。

* **抢单/派单池**：系统按 `building_id`、`grid_id`、技能标签、值班状态、SLA 时限生成候选处理人。`GRID_OPERATOR`、物业客服或物业工程人员可在移动端抢单或被派单。
* **信息不足处理原则**：业主提交信息不足时，物业客服可以电话询问业主获取线索，但不得把工单退回业主补充材料。客服应指派网格员、志愿者或物业人员线下抵达现场，补充照片、GPS、设施编号、楼栋单元、文字说明等证据。
* **临门一脚纠偏**：网格员现场发现 AI 或业主误选空间节点时，可点击“变更物理节点”，凭 `GRID_OPERATOR` 或物业授权角色的写权限，将工单物理指针修正为准确的 `space_node_id`、`building_id` 或 `room_id`。
* **纠偏留痕**：每次纠偏必须记录纠偏前节点、纠偏后节点、纠偏人、时间、定位坐标、现场照片和原因说明，写入审计日志和通知 Outbox。
* **资金闸门**：只有状态达到 `VERIFIED` 且物理节点被锁定的工单，才允许进入预算、表决、备用金、信托放款、维修资金划拨等接口。模糊节点或 `NEED_MANUAL_LOCATION` 状态下，API 网关必须直接拦截资金和表决动作。

#### **2.1.3 工单状态机与角色动作**

工单状态机必须由后端领域服务强约束，前端只展示当前用户可执行动作，不得自行拼接越权状态。

| 状态 | 含义 | 允许主动作 | 主要角色 |
| :---- | :---- | :---- | :---- |
| `SUBMITTED` | 业主已提交，等待物业响应 | 受理、合并重复、撤销 | 物业客服、报修人 |
| `PENDING_VERIFY` | 已受理，待现场核验 | 现场核验、要求现场补充 | 物业客服、网格员、物业工程 |
| `NEED_MANUAL_LOCATION` | 信息不足或位置模糊，待物业侧现场补充 | 电话询问、现场补充、物理纠偏、核验 | 物业客服、网格员、志愿者、物业工程 |
| `VERIFIED` | 位置与责任范围已核实 | 派单、进入预算判断 | 物业经理、调度员 |
| `ASSIGNED` | 已派给内部班组或专项服务商 | 接单、开始初勘 | 物业工程、服务商 |
| `SURVEYING` | 初勘中 | 提交初勘记录、上传现场证据 | 物业工程、服务商 |
| `PLAN_SUBMITTED` | 方案与预算已提交 | 治理模式判定、方案退回、发起审批/表决 | 物业经理、业委会 |
| `GOVERNANCE_PENDING` | 正在走业主表决、财务联审或监管审批 | 审批、表决、补充材料、驳回 | 业委会、街道/居委会 |
| `APPROVED` | 方案已通过，可施工 | 开工 | 物业工程、服务商 |
| `IN_PROGRESS` | 施工中 | 过程记录、变更申请、提交验收 | 物业工程、服务商 |
| `PENDING_ACCEPTANCE` | 待验收 | 验收通过、要求整改 | 业委会、物业经理、相关业主代表 |
| `RECTIFICATION_REQUIRED` | 验收不通过，需整改 | 重新施工、重新提交验收 | 物业工程、服务商 |
| `COMPLETED` | 维修完成 | 评价、结算、公示、归档 | 报修人、业委会、财务 |
| `EVALUATED` | 已评价 | 归档 | 业委会、监管端 |
| `ARCHIVED` | 已归档 | 查询、审计、统计 | 授权用户 |

异常状态包括 `REJECTED`、`CANCELLED`、`SUSPENDED`、`ESCALATED`、`REASSIGN_REQUIRED`、`PLAN_REVISION_REQUIRED`、`CHANGE_REVIEW_PENDING`、`PAYMENT_EXCEPTION`、`HANDOVER_LOCK`。其中 `HANDOVER_LOCK` 用于换届争议、印章交接异常或资金安全事件期间锁死大额资金动作。

#### **2.1.4 多端专项方案设计与造价录入**

工单完成物理纠偏和现场核验后，进入工程立项阶段。

* **现场初勘**：物业工程人员或专项服务商在移动端录入故障类型、风险等级、维修建议、现场照片/视频、材料预估、是否影响公共安全。
* **方案预算**：需要动用维修资金、公共收益或信托共有基金的工单，必须在 PC 管理端上传结构化方案，包括《工程测绘报告》《造价预算书》《施工计划》《合同草案》及 PDF 附件。
* **资金科目建议**：服务商或物业经理可选择建议资金来源，例如物业包干内部成本、酬金制日常备用金、公共收益账户、楼栋专项维修资金专户、信托共有基金。
* **方案版本管理**：每次预算修改生成独立版本号，保留发起人、修改原因、差异金额和审批结果。被退回方案不得覆盖历史文件。

#### **2.1.5 三种治理模式下的审批/表决变轨**

方案提交后，策略拦截器读取小区治理模式、工单分类、金额、空间节点、是否公共部位、是否紧急抢修，决定后续路径。

1. **包干制模式**
   * 日常小修、小额耗材更换、合同内服务事项，由物业在包干成本内核销，系统仅要求上传完工凭证和评价结果。
   * 共有部位大修或超出合同范围的维修，触发局部 ABAC 计票引擎。系统根据已锁定 `space_node_id` 提取对应楼栋、单元或受影响区域的 `total_certified_area` 和业主人数作为表决分母。
   * 表决对象只定向推送给受影响空间范围内业主，避免全小区错误参与或分母膨胀。表决通过后，推送 G 端社区书记/居委会进行合规前置审查。

2. **酬金制模式**
   * 小修小补可调用酬金制日常备用金先行核销，不触发业主大会投票。
   * 完工后，物业经理必须录入发票切片、支出单、合同或维修清单，工单进入 `PENDING_COMMITTEE_AUDIT`。
   * 系统自动纳入业委会月度财务内账联审，对账任务推送至业委会主任、财务委员和监事角色。逾期未审自动生成催办和监管提示。

3. **信托制模式**
   * 涉及共有基金的维修，在方案审批、业主表决和完工验收后，触发双密码共管状态机。
   * 物业经理在 PC 端插入机构证书并输入密码 A，确认工程完工、金额和收款方。
   * 业委会主任在移动端核对服务商上传的闭水试验、施工前后照片、合同和电子回单后，输入密码 B 或生物动态认证确认放款。
   * 双签会师后，系统通过 Outbox 生成司法链存证任务和银行三方支付任务，返回 TxHash 与银行流水号。

#### **2.1.6 分布式事务落库与行级资金结算**

资金动作必须遵守“先校验、后落库、再异步外呼”的金融级处理规则。

* **本地事务**：工单状态、审批记录、资金流水、分类账变动、审计日志和 Outbox 事件在同一数据库事务内提交。
* **司法链存证**：信托制双签、业主大会表决结果、维修资金划拨审批等关键事件生成哈希摘要，通过司法链或可信时间戳服务固化。
* **三级虚拟分类账**：动用专项维修资金时，系统按 `COMMUNITY -> BUILDING -> ROOM` 三级账本核销。楼栋账扣减后，按各房屋产权面积占该楼栋 `total_certified_area` 的比例异步更新房屋子账户。
* **最终一致性**：银行放款、司法链、短信通知、财务看板更新通过 Outbox + 重试机制异步执行。若外部接口失败，工单进入 `PAYMENT_EXCEPTION`，不得静默吞错。

#### **2.1.7 穿透公示、评价与信用沉淀**

维修完结后，系统必须将工程结果转化为业主可监督、监管可审计、物业可考核的数据资产。

* **穿透公示**：酬金制、信托制、公共收益或维修资金支出的工单，必须将合同 PDF、预算书、施工前后照片、验收记录、银行电子回单、发票原图切片和分类账扣减结果发布到 C 端财务监督看板。
* **分级可见**：普通业主可查看与其小区或受影响空间范围相关的脱敏材料；业委会和 G 端在授权范围内查看完整审计包。涉及个人住址、手机号、身份证号、银行卡号的字段必须脱敏。
* **后评价**：报修人、受影响楼栋常住居民或业主代表可对响应速度、施工质量、价格透明度、现场恢复情况进行星级评价和文字反馈。
* **信用分联动**：评价结果与超时率、返修率、整改次数、投诉结案率一起计入物业公司和专项服务商信用分，作为物业合同续签、服务商准入和 G 端监管报告的硬指标。

#### **2.1.8 应急维护抢修**

对于重大自然灾害、突发安全事故、消防/电梯/外墙脱落等紧急情形，物业可发起“紧急抢修申请”。系统允许先施工排险，但必须满足以下约束：

* 发起时强制选择紧急类型、风险等级、影响范围和临时预算上限。
* 自动抄送业委会、居委会、街道监管端和受影响业主。
* 抢修阶段仍需补齐现场证据、施工记录、费用清单和后置审批。
* 若后续涉及维修资金或公共收益核销，仍必须回到对应治理模式下完成表决、联审、双签或监管备案。

#### **2.1.9 权限、安全与隐私控制**

* **RBAC**：报修人、物业客服、网格员、志愿者、物业工程、物业经理、专项服务商、业委会主任、财务委员、街道/居委会监管员分别拥有不同动作权限。
* **ABAC**：动作权限同时受租户、小区、楼栋、网格、服务企业、工单空间节点、资金金额、治理模式、换届状态约束。
* **行级数据隔离**：业主只能查看本人提交或其房产/楼栋相关的工单；物业只能查看本服务合同和组织范围内工单；网格员只能查看本网格或被派发工单；G 端按行政辖区查看。
* **隐私最小化**：现场照片、语音、视频可能包含人脸、车牌、门牌号等敏感信息，系统应支持自动打码、权限水印、下载审计和到期归档策略。

#### **2.1.10 核心验收标准**

* 业主可在 30 秒内完成私有空间或公共空间报修提交。
* AI 未命中位置时，工单进入物业侧现场补充，不退回业主补充材料。
* 未达到 `VERIFIED` 的工单无法触发预算、表决、资金、放款接口。
* 任何物理纠偏、方案版本、审批、表决、验收、结算、评价均有审计日志和通知 Outbox 记录。
* 包干制、酬金制、信托制三种模式下同一工单可自动进入不同审批和资金路径。
* 换届或交接争议触发 `HANDOVER_LOCK` 后，大额资金划拨接口必须被后端强制拦截。

### **2.2 日常报告与服务披露**

物业服务企业应定期在平台上传履约情况报告，确保服务过程留痕。

* **维护报告提交**：物业公司必须按月、按季发布工程维保、绿化修剪、消防演练等工作报告。  
* **公示管理**：报告由业委会审核后，一键推送到 C 端业主的信息查看板块，业主可对维护报告进行在线评分和留言反馈，评分结果直接关联物业满意度指数。

### **2.3 物业缴费与账单推送**

系统内置物业费财务引擎，支持物业公司批量导入收费底册、设置多套计费模板（按建筑面积计费、按固定户收费、车位费收费等）。账单通过 C 端服务号、短信或系统通知实时定向推送到业主手机。业主可在 C 端在线调用第三方支付通道完成物业费及公共能耗分摊费缴纳，支付完成后系统自动生成电子收据并冲销欠费状态。

## **3\. 委员会操作 (Committee Operations)**

### **3.1 会议管理**

业委会履职活动应通过系统进行全过程线上留痕，防止“口袋会议”或未经授权发表决议。会议分为定期会议和临时会议，其召开规则由系统进行业务逻辑硬管控：

| 会议类型 | 触发条件 | 参会与表决有效性控制 (Quorum & Voting) | 会议决定公示与归档要求 |
| :---- | :---- | :---- | :---- |
| **定期会议** | 按议事规则约定定期召开 | 必须有过半数（![][image2]）委员出席方可开会；委员不得委托他人参会。决定须经全体委员半数以上（非参会委员半数，而是总编制的 ![][image3] 以上）同意方为有效。 | 形成书面会议记录，出席委员必须进行电子手写签名，加盖业委会电子印章后自动归档，并在小区内公告。 |
| **临时会议** | 三分之一以上委员提议，或街道办、居委会认为有必要并要求召开时触发。 | 遵循与定期会议相同的半数出席与全体过半数通过规则。 | 必须在会议召开前及决定作出后及时通过沟通公告模块向全体业主发布公示。 |

### **3.2 业主小组与代表管理**

系统支持建立“业主代表-业主小组-全体业主”的三级治理架构，以降低超大型小区集体投票决策的难度。

* **代表名额与产生方式**：由筹备组或业委会根据建筑面积和业主人数，按【幢】、【单元】、【楼层】或划定的【推荐区域】分配代表名额，并由各小组推选产生业主代表。  
* **决策传递与意见征询**：业主代表在参加业主大会表决前 日，必须通过系统内置的“代表征询工具”向其代表的业主小组收集表决意见，系统汇总各幢、单元业主意见后，作为代表在线上投票时的决策依据。  
* **代表履职约束**：代表每届任期与业委会相同。若代表不依法履行职责，在占其代表专有部分面积及人数过半数的业主提出异议时，或经街道办事处责令改正后仍未履职的，系统将冻结其代表权限，由居委会指导小组重新推选代表。

### **3.3 备案与刻章管理**

新一届业委会选举产生之日起 30 日内，系统将自动汇总其选举方案、当选人信息、管理规约及业主大会议事规则等法定资料，生成标准备案压缩包。业委会线上提交备案申请，流转至街道办事处和区房地产行政主管部门办理备案登记。备案通过后，系统支持上传备案回执，并辅助申请开立银行账户、刻制印章，将印章电子化存盘以实现印章调用线上审核、日常备案变更信息跟踪。

## **4\. 选举管理 (Election Management)**

### **4.1 5年换届选举全生命周期工作流**

换届选举是业主自治中最易发生纠纷的场景。平台强制将整个选举流程切分为不可逆的串行控制链条，并在管理端对业委会的历史操作权限实施“只读锁死”。

#### **4.1.1 筹备与登记**

在业委会任期届满前 6 个月（最迟前 2 个月），系统后台触发换届警报并抄送居委会。由居委会牵头，在线组建由业主代表、旧业委会代表、居委会代表和街道代表组成的换届改选筹备组（5 至 11 人单数），公示 7 天后锁死成员名单。

#### **4.1.2 候选人提名与资格审查**

系统开启 C 端自荐及 人以上业主联名推荐通道，候选人必须上传身份证、产权证等材料。资格联审阶段，由居委会和社区党委对候选人建议人选进行合规审查并签署书面意见 9。

#### **4.1.3 候选人筛选算法**

当审查合格人数超过法定候选人差额比例（差额不低于 20%）时，系统自动启动多阶排序筛选引擎，保证中共党员候选人占比原则上不低于 50%：  
![][image4]

#### **4.1.4 会前公示与投票表决**

筹备组在投票召开 15 日前，将候选人简历及修改后的管理规约等材料在系统及线下同步张贴公告。电子表决不设置延期；纸质表决如需延期，须经居委会在线审批，延期最长不得超过 15 日，且仅限延期一次。

#### **4.1.5 计票结算与候选人当选判定**

投票截止后，系统实时核算是否达到“双参与”门槛（2/3 专有面积及 2/3 业主人数参与）。

* **当选门槛**：候选人首轮选举当选，所得赞成票对应的专有面积和人数必须过参会业主的半数。  
* **差额排序计算**：若多名候选人均过半数，系统按照“所得投票权面积占比与所得投票人数占比之和”由大到小排序：

![][image5]

* **平票仲裁逻辑**：若两分值相等，则所得专有面积票数较多者排名靠前；若面积票数也相等，则自动触发系统在线抽签模块确定排名。

## **5\. 财务管理 (Financial Management)**

### **5.1 共有资金与公共收益管理**

小区共有资金及公共收益必须单独列账、结算，并在系统后台开立公共收益专门账户。

* **收益结算录入**：物业或业委会出纳必须按季对电梯广告、地面车位租赁、共有场地出租等公共收益予以结算。  
* **限时强行公开机制**：系统强制要求在每季度第一个月 15 日前，将上一季度公共收益的收支公示表（含发票/凭证照片、资金流向明细）推送至 C 端醒目位置并公示，拒绝公示则自动锁死物业服务评价接口并启动居委会督办通知。

### **5.2 专项维修资金申请与表决**

专项维修资金（维修基金）的提取与动用，属于民法典第二百七十八条约定的重大表决事项，系统必须对流程进行硬性安全约束。

维修资金申请原则上必须关联一个已完成空间核验的维修工单。系统只接受 `VERIFIED`、`PLAN_SUBMITTED`、`GOVERNANCE_PENDING`、`APPROVED`、`IN_PROGRESS`、`PENDING_ACCEPTANCE`、`COMPLETED` 等已锁定物理空间的工单进入资金链路；`SUBMITTED`、`PENDING_VERIFY`、`NEED_MANUAL_LOCATION` 等位置未核实状态一律不得发起维修资金申请。资金申请的表决分母必须由工单锁定的 `space_node_id` 反推，不允许人工在资金模块重新选择楼栋或户范围。

\[ 动用专项维修资金申请 \]  
       |  
       v  
\[ 制定初步方案与预算公示 \] \---\> 征求业主意见并修改方案 \[13\]  
       |  
       v  
\[ 业主大会大会表决 \] \---\> 判定：双 2/3 参与 \+ 参与者双 3/4 同意   
       |  
       \+---\> \[ 表决通过 \] \---\> 物业组织实施，分阶段上传工程凭证 \[13\]  
       |  
       \+---\> \[ 表决未通过 \] \---\> 系统锁死资金划转审批节点 

## **6\. 沟通 (Communication)**

### **6.1 多级公告与消息发布系统**

平台建立三级公告发布审核系统，公告覆盖范围包括全体业主、楼栋小组、以及业委会内部。

* **发布权限**：业委会、物业、居委会均可发起对应权限内的信息、公告发布。  
* **预警推送**：发生停水停电、安全隐患整治等紧急事件时，系统直接调用运营商短信接口与 App Push 通道，进行全覆盖高强度触达。

### **6.2 电子送达确认**

系统需要确保相关征求意见书及选票精准送达每一位业主，并建立司法认可的“推定送达”时效追踪引擎。

| 送达方式 | 操作逻辑与系统凭证生成 | 送达效力认定规则 |
| :---- | :---- | :---- |
| **App / 微信服务号在线送达** | 系统向已绑定且实名的业主账号推送电子版征求意见书，读取日志记录其首次打开界面的时间戳。 | 业主点击查阅即视为在线送达，系统生成不可篡改的查阅存证日志。 |
| **线下当面派发** | 由业委会两两一组，佩戴工作证上门派发纸质表决票，拍摄业主手写签收单或人脸同框照上传系统后台。 | 签收单上传并经系统确认后，即时视为成功送达。 |
| **邮寄/快递送达** | 无法直接送达时，出纳人员在后台输入寄送地址和特快专递单号。系统对接第三方物流接口，跟踪物流投递轨迹。 | 自邮寄挂号信或快递寄出后的第二天开始计算，寄出十天后，系统自动判定为已经合法送达并产生法律公示效力。 |

### **6.3 业主留言与协同互动**

系统在 C 端建立业主反馈板块，业主可就小区日常管理、环境治理发表意见。系统支持业主创建“业主联名请愿”，当某一合理诉求在 30 日内集齐专有部分占建筑物总面积 20% 以上且占总人数 20% 以上业主提议时，系统会自动生成“业主大会临时会议召开申请单”，直接触发业委会或街道办必须在 45 日内组织召开临时会议的法定期限工作流。

## **7\. 报告 (Reporting)**

### **7.1 业主大会投票表决报告**

投票结算引擎在表决截止时自动锁定数据库，多节点联合签名后计算并输出详细的表决报告。报告必须严密包含：小区总面积/人数、实际参与表决业主名单、专有面积总和、未投票业主从众规则计算结果、赞成/反对/弃权票数。系统自动生成包含防伪加密二维码的 PDF 电子报告并盖上筹备组/业委会印章，接受业主的查询和监督。

### **7.2 年度履职与质量监督报告**

* **业委会年度履职报告**：系统要求业委会每年通过平台向业主大会报告一次年度物业实施情况、履职考评得分，并以书面图表形式在公告栏展示。  
* **物业服务履约评级报告**：系统按季度拉取报修工单及时率、投诉结案率、业主评分，合并财务公共收益公示合规度，自动形成《物业季度服务考评报告》，报告同步推送给街道办，作为行业信用评级的原始数据。

## **8\. 资产管理 (Asset Management)**

### **8.1 房产物理单元与权属名册**

资产管理模块是整个表决系统的数据底座，必须对小区的物理空间进行精确建账。

* **车位/车棚/特定空间排除规则**：系统在导入小区基础户型及测绘数据时，车位、车棚、摊位等特定空间数据可以录入资产台账，但系统底层的面积计票引擎必须设定限制规则：此类特定空间绝不计入确定业主投票权数的专有部分面积及投票人数计算体系。  
* **开发商存量房产管控**：建设单位尚未出售和虽已出售但尚未交付的部分，系统自动归集到开发商法人（UID）名下，计票时按开发商一人合并计票。

### **8.2 共有产权资产与租约台账**

* **共有部分空间建档**：系统建立小区电梯、外墙、公共通道、地面车位、设备用房等共有部分的电子名册。  
* **广告与租赁租约登记**：每一笔在共有部分开展的商业活动（如梯媒广告、快递柜入驻、临时摊位摊租），系统必须要求录入对应的租用合同、到期时间、合同金额及付款批次，由系统自动核算并生成预应收款提醒，同步对接公共收益列账模块。

### **8.3 换届财务、档案与印章移交**

当换届选举落幕、新业委会依法备案通过后，系统开启 10 天强行交接倒计时。原业委会必须在线核对资产、档案、财务清单，并由新旧业委会主任、居委会监交人共同通过 App 扫描交接实物确认码、手写电子签名完成线上移交确认。原业委会账号在到期日当日自动注销，系统后台移交确认模块生成最终的交接报告存档备查。

## **9\. 法律合规、司法存证与非功能性需求**

### **9.1 区块链与司法链电子存证**

为保证业主大会电子投票结果在司法诉讼中具备无可争议的证明力，系统底层引入联盟链（如最高人民法院司法链、地方公证机构联盟链）存证技术。

\[ 业主端 (App/小程序) 确认投票 \]  
       |  
       \+---\> L3/L4 活体身份核验 \+ 非对称私钥签名  
       |  
       v  
\[ 生成包含投票元数据的打包文件 \] (内容包括: 房产ID, 投票时间戳, 表决项哈希值)  
       |  
       v  
 \---\> \[ 司法联盟链节点广播 \] (写入智能合约区块)   
       |  
       v  
\[ 链上生成唯一哈希存证凭证回显 \] (提供司法链一键核验二维码与出证通道) 

1. **上链前真实性保护**：在电子数据生成的源头，系统对投票业主进行人脸核验，运用非对称加密技术，生成包含“业主数字签名、投票时间、表决项目、操作设备 IP、地理定位、房产证 SHA256 值”的原始电子数据包。  
2. **上链后完整性固化**：将该数据包进行哈希摘要运算后，实时广播写入对接的人民法院诉讼服务平台司法链及可信时间戳（TSA）服务器，由各节点共识写入区块，确立“推定上链后未经篡改”的法效力。若日后发生投票效力诉讼，法官可扫描表决报告上的防伪码，一键调取链上哈希对数据源进行实质性对比核验。

### **9.2 敏感个人信息与数据安全保护**

1. **告知同意与隐私授权**：对于人脸信息、不动产信息、业主身份证号等生物识别及重要个人敏感信息，系统必须在业主首次登录及人脸比对前，弹出单独的、不可预先勾选的《个人敏感信息处理单独同意协议》，保障业主的知情权与自愿权。  
2. **数据脱敏机制**：  
   * **传输与存储加密**：数据在网络传输中采用 HTTPS 及 TLS1.3 进行通道加密，数据库中核心敏感数据（身份证号、手机号、产权人真实姓名）执行国密 SM4 进行落盘加密，防止黑客撞库泄露隐私。  
   * **界面脱敏展示**：对非本人的姓名、电话、具体门牌号进行脱敏（例如：张\*、138\*\*\*\*8888、1幢\*单元\*\*\*室）。唯有在根据法律程序生成大会临时表决权公示名单时，方可在 G 端街道办审核后，在限定的公示期内对本区域内业主合规脱敏展示，防止信息公示导致隐私外泄。

### **9.3 性能与可用性指标**

* **投票并发支持**：在大中型社区举行表决期间，投票首日上午及截止日前两小时通常出现流量波峰。投票计票引擎支持通过负载均衡（Nginx）进行流量分发，核心投票写入接口（TPS）设计指标不得低于 ![][image6]，高负载下平均接口响应延迟控制在 ![][image7] 以内，防止投票卡顿。  
* **可用性与灾备**：系统架构要求实现全天候 365×24 小时高可用运行，SaaS 核心服务可用性不低于 ![][image8]。表决账本数据库执行本地与异地双活冗余灾备方案，每小时同步一次增量数据，每天凌晨执行全量冷备份，保障在发生区域性网络或物理电力故障时数据零丢失。

#### **引用的著作**

1. 未经批准的业委会选举是否违法--法治网, 访问时间为 五月 20, 2026， [http://www.legaldaily.com.cn/zt/content/2022-10/31/content\_8795111.htm](http://www.legaldaily.com.cn/zt/content/2022-10/31/content_8795111.htm)  
2. 业主大会议事规则, 访问时间为 五月 20, 2026， [http://zfjsj.quanzhou.gov.cn/xygl/wygl\_48392/202201/P020220106367833893435.doc](http://zfjsj.quanzhou.gov.cn/xygl/wygl_48392/202201/P020220106367833893435.doc)  
3. 民法典中小区业主共同决定事项的表决规则- 河南省高级人民法院, 访问时间为 五月 20, 2026， [https://www.hncourt.gov.cn/public/detail.php?id=181650](https://www.hncourt.gov.cn/public/detail.php?id=181650)  
4. 朋说丨关于业主大会投票表决问题的探讨\_专业研究\_上海道朋律师 ..., 访问时间为 五月 20, 2026， [https://www.daopenglawyer.com/zhuanyeyanjiu/323.html](https://www.daopenglawyer.com/zhuanyeyanjiu/323.html)  
5. 本白皮书版权属于可信区块链推进计划, 访问时间为 五月 20, 2026， [http://www.caict.ac.cn/kxyj/qwfb/bps/201906/P020190614499397999292.pdf](http://www.caict.ac.cn/kxyj/qwfb/bps/201906/P020190614499397999292.pdf)  
6. 告别“扫楼投票”，业主决策“线上见” 智慧物业服务平台投票指南来了 \- 福建省住房和城乡建设厅, 访问时间为 五月 20, 2026， [https://zjt.fujian.gov.cn/xxgk/gzdt/bmdt/202509/t20250915\_7009694.htm](https://zjt.fujian.gov.cn/xxgk/gzdt/bmdt/202509/t20250915_7009694.htm)  
7. 业主投票权数和投票人数如何确定？ \- 深圳政府在线, 访问时间为 五月 20, 2026， [https://www.sz.gov.cn/ztfw/zfly/wyw\_184911/ywzsk\_184570/content/mpost\_8540324.html](https://www.sz.gov.cn/ztfw/zfly/wyw_184911/ywzsk_184570/content/mpost_8540324.html)  
8. 福州居民，智慧物业服务平台投票指南来了！\_物业管理 \- 福州市住房和城乡建设局, 访问时间为 五月 20, 2026， [https://zjj.fuzhou.gov.cn/zwgk/wuyegl/202509/t20250910\_5076179.htm](https://zjj.fuzhou.gov.cn/zwgk/wuyegl/202509/t20250910_5076179.htm)  
9. 附件：业主委员会选举办法（示范文本）.docx \- 深圳市住房和建设局, 访问时间为 五月 20, 2026， [https://zjj.sz.gov.cn/attachment/0/774/774445/8739225.docx](https://zjj.sz.gov.cn/attachment/0/774/774445/8739225.docx)  
10. 业主大会议事规则, 访问时间为 五月 20, 2026， [https://zjjcmspublic.oss-cn-hangzhou-zwynet-d01-a.internet.cloud.zj.gov.cn/jcms\_files/jcms1/web2247/site/attach/0/281f1a2eb14b4f8192b04784ddeaea9d.doc](https://zjjcmspublic.oss-cn-hangzhou-zwynet-d01-a.internet.cloud.zj.gov.cn/jcms_files/jcms1/web2247/site/attach/0/281f1a2eb14b4f8192b04784ddeaea9d.doc)  
11. 成立业主大会、选举业主委员会流程图 \- 苏州市人民政府, 访问时间为 五月 20, 2026， [https://www.suzhou.gov.cn/szsrmzf/bmwj/202403/fc32007301874c8b95c9828576dfb8c3/files/8f93c9d37e1c4b04ac5fd8eabfdd1a15.pdf](https://www.suzhou.gov.cn/szsrmzf/bmwj/202403/fc32007301874c8b95c9828576dfb8c3/files/8f93c9d37e1c4b04ac5fd8eabfdd1a15.pdf)  
12. 关于进一步规范本市住宅小区公共收益使用管理相关工作的通知的解读( 2020年12月23日), 访问时间为 五月 20, 2026， [https://fgj.sh.gov.cn/zcjd/20201229/a12c06f25f484fc2a8f6777516872901.html](https://fgj.sh.gov.cn/zcjd/20201229/a12c06f25f484fc2a8f6777516872901.html)  
13. 夏阳街道业主委员会工作操作办法, 访问时间为 五月 20, 2026， [https://www.shqp.gov.cn/xiay/xyzwgk/ml/yw/20210628/880458.html](https://www.shqp.gov.cn/xiay/xyzwgk/ml/yw/20210628/880458.html)  
14. 重新诠释区块链存证技术下的最佳证据规则 \- 人民检察院, 访问时间为 五月 20, 2026， [https://www.spp.gov.cn/spp/llyj/202306/t20230616\_617747.shtml](https://www.spp.gov.cn/spp/llyj/202306/t20230616_617747.shtml)  
15. 【问答】电子投票系统是什么？主要有什么用途？如何绑定电子投票系统？, 访问时间为 五月 20, 2026， [http://www.zc.gov.cn/fw/ztfw/grfw/zffw/content/post\_10441774.html](http://www.zc.gov.cn/fw/ztfw/grfw/zffw/content/post_10441774.html)  
16. 以案说法|不刷脸不让进小区？业主可要求其他验证方式 \- 社会·法治- 人民网, 访问时间为 五月 20, 2026， [http://society.people.com.cn/n1/2021/0830/c1008-32212411.html](http://society.people.com.cn/n1/2021/0830/c1008-32212411.html)  
17. 人脸识别门禁系统泄露业主信息，物业是否承担赔偿责任？｜劳动、物业和侵权法律服务月刊（2025年第十期，总第七十四期） \- 海南昌宇律师事务所, 访问时间为 五月 20, 2026， [https://www.changyulawyer.com/nd.jsp?fromColId=-1\&id=1590&\_ngc=-1](https://www.changyulawyer.com/nd.jsp?fromColId=-1&id=1590&_ngc=-1)  
18. 武汉市业主决策电子投票系统操作规则（试行）, 访问时间为 五月 20, 2026， [https://zgj.wuhan.gov.cn/IGI/upload/file/2025/08/19/20250819105425532HMW.pdf](https://zgj.wuhan.gov.cn/IGI/upload/file/2025/08/19/20250819105425532HMW.pdf)  
19. 《深圳经济特区物业管理条例》实施若干规定 \- 中华人民共和国司法部, 访问时间为 五月 20, 2026， [https://www.moj.gov.cn/pub/sfbgw/flfggz/flfggzdfzwgz/201506/t20150624\_140526.html](https://www.moj.gov.cn/pub/sfbgw/flfggz/flfggzdfzwgz/201506/t20150624_140526.html)  
20. 业主自治中的纠纷与法律机制研究 \- 上海市司法局, 访问时间为 五月 20, 2026， [https://sfj.sh.gov.cn/ztzl\_xsqk/20201126/5bdc8d62641d46ba94e44a5cc094f1b7.html](https://sfj.sh.gov.cn/ztzl_xsqk/20201126/5bdc8d62641d46ba94e44a5cc094f1b7.html)  
21. 观韬视点| 区块链技术存证方式的证明力问题, 访问时间为 五月 20, 2026， [https://www.guantao.com/page2232](https://www.guantao.com/page2232)  
22. 业主大会议事规则, 访问时间为 五月 20, 2026， [https://www.shhuangpu.gov.cn/uploadfile/645479fd-0999-47bc-89b0-5e37352645be/%E4%B8%9A%E4%B8%BB%E5%A4%A7%E4%BC%9A%E8%AE%AE%E4%BA%8B%E8%A7%84%E5%88%99%EF%BC%88%E4%B8%AD%E7%A6%8F%EF%BC%89.doc](https://www.shhuangpu.gov.cn/uploadfile/645479fd-0999-47bc-89b0-5e37352645be/%E4%B8%9A%E4%B8%BB%E5%A4%A7%E4%BC%9A%E8%AE%AE%E4%BA%8B%E8%A7%84%E5%88%99%EF%BC%88%E4%B8%AD%E7%A6%8F%EF%BC%89.doc)  
23. 广州市住房和城乡建设局关于印发《成立业主大会和选举业主委员会工作指引》的通知, 访问时间为 五月 20, 2026， [https://zfcj.gz.gov.cn/attachment/7/7333/7333461/6441015.pdf](https://zfcj.gz.gov.cn/attachment/7/7333/7333461/6441015.pdf)  
24. 关于印发《关于进一步贯彻实施〈上海市住宅物业管理规定〉的若干意见》的通知, 访问时间为 五月 20, 2026， [https://fgj.sh.gov.cn/gfxwj/20210622/c01bf90254bb4429826e5ee4771e580b.html](https://fgj.sh.gov.cn/gfxwj/20210622/c01bf90254bb4429826e5ee4771e580b.html)  
25. 关于印发《业主大会和业主委员会指导规则》的通知 \- 北京住房公积金网, 访问时间为 五月 20, 2026， [https://gjj.beijing.gov.cn/web/zwgk61/\_300587/\_300688/318051/index.html](https://gjj.beijing.gov.cn/web/zwgk61/_300587/_300688/318051/index.html)  
26. 区块链司法存证的应用及其规制, 访问时间为 五月 20, 2026， [https://qks.swupl.edu.cn/docs//2022-10/35b088a2a8984e3ba3f30d39666c9faf.pdf](https://qks.swupl.edu.cn/docs//2022-10/35b088a2a8984e3ba3f30d39666c9faf.pdf)  
27. 最高法：物业不得强制将人脸识别作为出入小区的唯一验证方式 \- 社会·法治, 访问时间为 五月 20, 2026， [http://society.people.com.cn/n1/2021/0728/c1008-32172943.html](http://society.people.com.cn/n1/2021/0728/c1008-32172943.html)

[image1]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAADwAAAAaCAYAAADrCT9ZAAABk0lEQVR4Xu2Xu0rEQBSGj5dGEEGwshYbCwst7SzFxs5HUPEGPoHYeAEfQBRFUNBCxMJCtPcNtNFCm8ULiqAgiv5nZ5ad/E7WZJFNWOeDj838ZxNmkpnMrkggEKgnNuAL/LJuRqqGTynX1cFoOVPm4BiHSXAH5OMMdnGYEbvwXcr9HY+Wf6cBHsMDMRcYiZaLxN2IrKlqwNOw3x7HPeUPDnJCVQN+cI6fxFykzcm64aLTzhNVDdh9orpOtX3pZDuw1WnnidQD1vV7RBlPa98UT8oa3I5xS8yuoDvFuv3uUPGs5GjfJjishLt+3UwvtGzb+kZkBuAhhxmg/ZzksBKPHFhKT7kHLlBN6YOdHGaA9nGKw0rETdcTMbUr2EK1NMzDpRQOm9MSo32c4TCOZnjKoaVRfq7lEveSn21K+zfLoY8mMR0/54LDK3yjbNV++m5ErekQ048VLjB78FnM/qv7rv5W9tEr/jdgu8SfUwv24R28hTf2syD+l+ufoLNilMN6pjSd9Q/Fv+ACXnMYCAQCgUB6vgH6mmcsqbn4qAAAAABJRU5ErkJggg==>

[image2]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAADQAAAAWCAYAAACPHL/WAAAAkUlEQVR4Xu3WoQ3CQByF8QNBygAkXaECi6xgC2ZgDRij6RgwQGUNAzSwAQmWJvRrqngS98r/l3zmnjp1l1IIIfyDnR64W9GDGlp8T96WdKM7rWWzd6EX5Tq4q6mnrQ7uzvShUgdXxzRd6KCDm1OaLrLXwU1Fbyp0cHOlJ210cDI+pi11lMlmafz6zOqHEEL4zQC6vBHWkPur4gAAAABJRU5ErkJggg==>

[image3]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAACEAAAAWCAYAAABOm/V6AAABxklEQVR4Xu2UyytFURTGl8gjJYqBKeU5UfwBJCNJJgamUoYeGXilPEoxQBmIkjIxlYnEzIAJSkRGkgyYUEjEt+y9zl1nnXMzcWf3V19nf9/ad+997t5nE6VJPW02iKHOBv9FLfTun59QQ7gccA116KAf2oJqvK+CNqG+oEeCcegZeoW6TY35hsp9uwh6g76gQagFWvV9HnyfgGlf0DoJ9XBcQHvKn0OHyjP822S+1D/5n4owAS1BG9AYlBku/1JA0QkYzgp9u9h7jfUjZLZB4ImbbGg4peiADGdrxmu0z6KYbRBG6e9FyDZZbP4Bdfl2M9RjakkZhmbIDbbunyuhHtHJhLicPR/sR5VNQu3KRxiAdk3GA00ZbydjkuWabOhe+WpyX9eBymKxg1svJMs1fF8I+oDnQ5dSyJCGgr/t/1jELNSq/C20rfyNNHiQJ1WQTA/+YrzAWfA2hlzozmTcf1n5BV0YUgXJ9KSdxguc1dvQw/+mxS5iURp8SEpUoZFc50qVMZz1Kj/nszjmyX2iliNoR/lgOxjeDnl7VpkuevLI1Y6hM3LXb9x54m0IDa7IocTC+WDy1Z8SrmxgqCB3ce3bQpo0zA+X9oHy1N0OmQAAAABJRU5ErkJggg==>

[image4]: <data:image/png;base64,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>

[image5]: <data:image/png;base64,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>

[image6]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAACkAAAAZCAYAAACsGgdbAAAB60lEQVR4Xu2VvUsdURDFJ0SRNCIWNim0SBdQ0TJCilhZiYFUdjbxXxC0sgiohRKTIhEbsQgpLERIoo0gJFYqooWdaBfFj0TznczJvbPv7OzuyytT7A8OzjmzO/e+9+6uIiUl/y8dqhPVb9V7VVO6nTCqulBdqYZcz7ij+iBh1qrrMVOqbxLW7XG9DI9VM+QXJCzQRRnYU70jv6vaIA/uS7jX6HTeOFeNk79WPSGfAUP8IJ81Om8g428dfpg8wLeFX8d4INlZzTlZiiPJXuA3ueW8gexlrFuix1/mbcwNbLpo1qAPixiRcEMfZX7TBudjVDPzks5RfydvIN/xYR79Ei6ednktm1yimpmV7Cbx8HmQ42xWZVL1SvVTwrlhatnkOtUMPjDy29GjPq20E4rWyAXDcPEyZUUDOF+kmnkqIa+LHvVZpZ2A/JcPq+E35b3BedGZnJPsrK/kDeQHPjTw875wmS1uL9nL6D3I9mN9L/p/Pd3VPvBzH4KHkn+TZTejfxS9B1m38wPkwSdJn8Fnkp11I2b1Lk9As4F8e8xWKAPI8N/JmIgZ80b1g7wt3kYZQHaX/KbkP/EJeNvjwEIfJQzAa8NzS0IPA7dVXyRswoPeZ9VrCdf3ptt/aZXQW1Mdqo7T7ZKSkpKSWvkDmWSmxKrJe7IAAAAASUVORK5CYII=>

[image7]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAACIAAAAZCAYAAABU+vysAAABVklEQVR4Xu2Uu0oEQRBFyxfmIhiIfoaIseAHiJkmgoiK4oKCRg5qaCSYmRgpmCgY+Q8GpqYa+EjURHyA3mt3Y03Nzgy9btgHDrt9q7qnWHZaJJGIZw0u2LCCZ7gDh2A3HIVXuY4IjuEH/PYu5suVhD3aRq6jRVoZZBMewXlT+xexg3zZoF3EDvJpgyZ0wkN4C3fF/Q9riR3kHR7AF3gibv+Yqnf5rGxdCpuWbFjBK5xQ6xFxZ/T5Nc+yDz4366Zw07INI+EZHJBwoPA27cPh0FQHN6zYsIIeG8jfgwPTKqO8e2ph46oNS5gR179ucj1Iry6AbXG1zOQF2FR2IU3BAbWeFdc/qDLC7MZ/34Ibqkau4aXJcvSLO2TPFkCHFH9yYtcXJsvMmtzDcZP9cgqf4J24d52fD+Kufc2ZFO+AMDxfX36+iRs6kMFJ+OjrdE7VE4lEoi38ADxCWxZ1cnvaAAAAAElFTkSuQmCC>

[image8]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAEAAAAAZCAYAAACB6CjhAAAC/UlEQVR4Xu2XS6hOURTHF3nL43q7BpcRKYnCACmPgXRDQhmQkgkDE5eRIiUkJANCCSMTjGQgyUTIm5IIEdENV96v9W+t9Vlntc853ze8Or/6933//15nn+/ss/c+5yOqqOisLGR1iWFgdgz+B/qx/rAmsd6yVmSba5xj7Y9hET1Yl0k6vxDaPG2s7yR1s0JbGWdJjnvI6hbajOWsjyR1K0Mb+MZa7TzqoB2seazt6n+7mlImkxw0QP0U9ZFPrOvOt7N2OZ/HQJL+xqkfqR6D7nnA6nD+GuuK8wDH9Q3eaNbPR6w+Li8FndxOZHedP6iZZ2giS2F3yXOP9cv5pSQ1TS4DyIYF7/F9gBnU4NS3izgc8huaG6mLAMg2xzCAGtwVzx7NjfvBG8jOB4/f7L0nDkgpq0g62Rfyi5obRQPwOoaOFpKaWyHforkti6L+ff6c/t2sIaxTru0JNTj1gf3AOAOeaT5YffwhRl7uQXucAcc1X6L+qfpIqn9scKcpe7fnsPY63xA4wZ1EBmFDBFvVe5ZpFvMI2uPUxEUg36gez2x43FVjhGZl/QPf/yCSWRn3tVwwejiJ7cqbSDZAZF2tiOQpgEel8YakBo/FIkaR1OGCADaqS5rN1Aw8Zr13/gvVNwBYFr2d9/U/3PdChpM8/7EZTSBZT6kTY+1+YB1Qjxo/KHl0J5m2L1kLWMdIjo3vA+tZr1gn1admj2c+a7fzJ0jeI4wjVP7GmAQnLnuZwKijbmxsqIOrlB7gCGrWxtDxM/ivJDfRWMSa6nyS1DSDX+x8i2bTXHZGM08v1rqQpWYTfHxeI8PyMzZolgdmU8+QYVn6AcA12D6WC07y2Xm87eEtzzOdpA7LA9g7+ZhahWCDOd5ltpaNQ8EDLAVka1wWb4KnlbUthsxOkr3KOEp1LIGJJCd7oZ95a9o2PYw8PkdnWgX8S8MrrKc/Sf07kvWMGZECx6HOZszcbHMG/CfIA8faRZdt0J0SzCD/dIpgwLER3owNFRUVFRUVFTX+Angl4iiwlgiRAAAAAElFTkSuQmCC>
