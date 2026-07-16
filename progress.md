# Progress: 维修工程项目询价、比价与定商闭环

- 已确认缺口不是页面入口隐藏：新项目后端没有项目级邀请、报价或推荐实体，合同只校验供应商已核验和金额上限。
- 已确认旧工单的完整供应商链路仍在，但共有部分新工单勘验后已切换到项目台账，不能继续写旧状态机。
- 初步确定采用项目级采购聚合并复用既有供应商主体、选择方式枚举和竞争性询价规则；不修改旧工单历史报价表的聚合归属。
- 已新增 `V3.88` 项目级邀价、报价版本和中选快照表，以及对应领域模型、持久化端口、MyBatis 实现和管理端/供应商接口。
- 锁定方案现要求存在有效中选报价并把中选快照纳入哈希；合同现强制使用同一供应商且金额不超过中选报价。
- 业主披露模型已增加中选供应商、金额、报价说明和原件，并继续通过项目附件短时效权限链路提供查看。
- 项目级采购闭环已贯通：竞争性询价邀请、供应商在线报价、物业核验纸质报价、报价修订、比价推荐、中选快照和方案版本修订均使用同一项目/方案边界。
- 方案锁定后仍可只读查看中选结果；新方案版本存在草稿时可重新询价，但不能修改已锁定方案的历史中选记录。
- 合同测试已覆盖“不得更换中选供应商”和“不得超过中选报价/方案/审价上限”；非询价主题的工程测试统一构造真实三家竞争性报价前置条件。
- 后端聚焦回归通过：`RepairProjectSourcingFlowTest` 等 5 个测试类共 11 项，0 failures、0 errors。

## Historical Progress

# Progress: 维修实施方案编辑与业主端披露

- 已确认本轮目标：Yaochi 独立全宽方案编辑页、说明性字段受限富文本、后端可信清洗与快照、Shennong 业主侧锁定方案展示。
- 已确认不采用“仅把弹窗拉宽”或“把所有方案字段塞进一段富文本”的过度方案；工程金额、工程量、资金、分摊、验收和付款规则继续保持结构化。
- 后端提交 `aded4d4`：四个说明字段经 jsoup 白名单清洗后进入方案快照；锁定方案、工程项、分摊汇总和方案附件按关联报修向有权业主投影。
- Yaochi 提交 `4e2381d`：新建项目从通用弹窗迁到独立全宽编辑页，四个说明字段使用受限富文本工具栏，管理端项目详情同步按富文本渲染。
- Shennong 提交 `7f848f6`：报修详情按问题与范围、工程项、资金分摊、实施要求、验收质保、结算付款和附件分章节展示锁定方案；草稿继续只显示“方案编制中”。
- Pangu 全量测试通过（629 tests，0 failures，0 errors，1 skipped）；Yaochi 生产构建、Shennong TypeScript 检查和正式微信小程序构建通过。
- Playwright 已完成 Yaochi 桌面与 390x844 视口验收，页面无横向溢出或控制台错误；微信开发者工具重新编译后原生模拟器正常渲染生产包。
- 三端代码已经分仓提交并完成生产发布：Pangu 新 JAR 的本地/现网 SHA-256 一致，Flyway 102 个迁移校验通过且版本保持 `3.86`；Yaochi 现网已切换到 `assets/index-BVQ2fsdT.js`；Shennong 体验版 `0.1.19` 上传成功。
- 线上验收通过：`nginx`、`pangu` 均为 `active`，生产登录返回 HTTP/业务 `code=200`，新增业主维修方案路由经过身份校验后返回预期的业务错误语义而非路由缺失。

## Historical Progress

# Progress: 报修勘验统一提交入口

- 后端新增 `submit-inspection` 统一用例：物业现场人员从已提交、待定位、待核验、已核验、已派单或勘验中任一内部状态提交时，在同一事务内补齐受理、位置核验、指派和开始勘验等审计事件，最终进入 `SURVEY_COMPLETED`。
- 勘验图片和视频允许在勘验完成前上传，最终提交仍校验附件归属、类型和 READY 状态，并绑定到 `SUBMIT_SURVEY`。
- Yaochi 管理后台已移除“受理/核验通过/派单/开始初勘”前置按钮，改为统一勘验表单和真实附件上传；Shennong 物业端改用同一接口，支持现场拍摄或相册选择照片、短视频。
- `RepairWorkOrderFlowTest` 13 项通过；Yaochi 生产构建、Shennong TypeScript 检查及微信小程序开发构建通过。Taro 构建通过 `CI=1` 关闭交互式更新检查，避免本机 `system-configuration` 原生模块异常。
- Chrome 已验证工单 484 首节点为“勘验”，当前动作直接展示结论、风险、照片、可选视频和“提交勘验记录”，页面无重叠；本地 8080 后端已重启并加载新接口。

## Historical Progress

# Progress: 公共报修位置范围与维修专业拆分

- 后端新增 `BUILDING / COMMUNITY` 公共区域范围：楼栋公共部位必须绑定楼栋，小区公共区域不绑定楼栋但必须填写明确位置，空范围继续进入待现场定位。
- 工单、位置纠偏、业主可见范围和响应 DTO 已贯通 `publicAreaScope`；楼栋与小区公共维修资金增加交叉使用校验。
- 维修专业统一为规范代码，兼容 `FIRE`、`ELECTRIC`、`PUBLIC_PIPE`、`WALL_LEAK`、`PUBLIC_FACILITY` 等旧客户端别名并统一落库。
- Yaochi 登记页已拆分为“楼栋公共部位 / 小区公共区域 / 待现场定位”三个范围，维修专业独立选择且不再默认“公共设施”。
- Shennong 业主报修同步提交公共区域范围，并改用规范维修专业代码。
- `RepairWorkOrderFlowTest` 12 项通过；Yaochi 生产构建和 Shennong TypeScript 检查通过。Shennong Taro 生产构建仍受本机 `system-configuration` 原生模块 NULL panic 影响，与本次 TypeScript 变更无关。
- 浏览器已验证楼栋、小区两种登记交互和页面排版；更新后的后端已使用 Flyway `V3.65` 启动在 `http://localhost:8080/pangu`。

## Historical Progress

# Progress: 楼栋维修表决渠道二选一

- 已完成现状核对：确认表决渠道需要落在楼栋决策快照上，C 端资格由实名业主房产与表决范围共同判定，微信截图需要接入既有 OSS 附件而不是继续手填文件标识。
- 后端已初步增加 ONLINE / WECHAT 渠道、C 端待表决查询和逐户在线选择写入；正在补齐渠道专属校验、业主报价预览及微信截图真实上传。
- 后端已完成渠道锁定、C 端实名投票代表资格校验、在线选择修改审计、推荐报价短时效预览、在线/微信专属完成规则和微信截图 OSS 绑定。
- Yaochi 已在推荐供应商后提供“C 端在线表决 / 微信接龙”二选一；在线模式仅显示结束表决动作，微信模式显示逐户核验与真实截图上传，报审阶段按渠道决定是否必须附截图打印件。
- Shennong 业主报修页已增加“楼栋维修待表决”，展示维修方案、推荐供应商、报价摘要及附件，按每套房屋提交同意、不同意或弃权并允许截止前修改。
- 业务方案文档已同步为楼栋表决双渠道，明确同一决策周期不可混用，业主大会仍保持独立纸质/线上流程。
- 聚焦楼栋维修全流程测试通过；Pangu 全量测试通过（586 tests，0 failures，0 errors，1 skipped）。
- Yaochi 生产构建、Shennong TypeScript 检查及微信小程序开发构建通过；三仓 `git diff --check` 通过。
- 本地后端已重新打包并运行在 `http://localhost:8080/pangu`，Flyway 为 `V3.63`。浏览器已验证物业端微信渠道显示逐户核验与截图上传，桌面和 390x844 移动视口无横向溢出，控制台无错误。

- 已确认二选一业务边界：ONLINE 由范围内业主在 C 端查看推荐方案和报价后表决；WECHAT 由楼栋长线下发起并把结果交物业上传。
- 已完成现状盘点：当前后端、Yaochi 和 Shennong 只有微信接龙式物业代录路径，尚无 C 端在线楼栋表决接口。

## Historical Progress

# Progress: Excel 报价附件转 PDF 预览

- 已核对当前附件链路：原件上传 OSS，预览接口生成短时效 URL；现有前端已经能在弹窗内预览 PDF。
- 已确认转换采用 Java 管理 LibreOffice headless 子进程，而不是前端解析 Excel；原 Excel 保留为审计原件，PDF 仅作为派生预览对象。
- 已验证本机 `soffice` 可执行，版本为 LibreOfficeDev 26.8；官方命令行参数支持无界面转换和独立输出目录。
- 后端已增加文档转换端口和 LibreOffice 实现：独立临时目录/profile、45 秒默认超时、PDF 头与 50MB 大小校验、进程输出日志和可靠清理。
- OSS 端口已增加受控读取与对象存在性检查；Excel 首次预览生成版本化派生 PDF，后续复用，删除未绑定原件时同步清理派生对象。
- 预览响应新增 `converted`，前端会显示“Excel 已转换为 PDF 预览”；转换失败时弹窗保留错误说明和“下载原件”，不影响原 Excel。
- 中文字体回退已接入独立 Fontconfig；使用真实 27KB 中文 Excel 端到端生成 414,569 字节、12 页 PDF，OSS 返回 `application/pdf`，首屏视觉检查通过，测试附件及派生对象已清理。
- 后端聚焦测试 11 项及全量测试 586 项全部通过（0 failures、0 errors、1 skipped），包含真实 LibreOffice 转换、Excel 预览接口和派生缓存复用；后端打包、Yaochi 构建、两仓 `git diff --check` 和浏览器验收通过。
- 部署依赖与环境变量已记录在 `docs/repair-excel-preview-deployment.md`；最终后端已使用最新 jar 运行于本地 8080 端口。

## Historical Progress

# Progress: 供应商报价附件弹窗预览

- 已核对现有链路：前端直接打开后端生成的 OSS 下载地址；后端已具备附件可见性和供应商范围校验，但对象存储接口只有下载 URL。
- 已确认采用独立预览凭证：后端生成 `inline` 短时效地址并返回文件元数据，下载仍保留为用户明确动作。
- 后端已新增附件预览凭证接口，复用工单可见性、附件归属、供应商访问范围和附件状态校验；OSS 地址有效期为 10 分钟。
- 报价附件入口已改为“预览附件”：图片和 PDF 在站内弹窗展示，Word/Excel 等格式展示文件元数据，只有点击“下载原件”才执行下载。
- 已修正 OSS `response-content-type` 覆盖导致的 `InvalidRequest`；真实 OSS 图片响应为 200，Chrome 弹窗成功加载，控制台 0 errors。
- 后端 `RepairWorkOrderFlowTest` 通过（10 tests，0 failures，0 errors）；后端打包和 Yaochi `npm run build` 通过。
- Chrome 桌面端与 Playwright 390x844 移动端验收通过；移动端弹窗全屏、无横向溢出，且未自动创建标签页或下载附件。

## Historical Progress

# Progress: 维修工单侧边详情页

- 已核对当前实现和通用 Sheet：详情提前返回导致列表消失，默认 Sheet 过窄且遮罩会阻断列表交互。
- 已确定使用非模态无背景遮罩的宽侧页，桌面保留列表上下文，移动端全屏，详情内容独立滚动。
- 详情已改为右侧宽侧页，主页面始终保留工单列表；侧页顶部固定显示工单标题、编号、时间、刷新和关闭操作。
- 主面包屑恢复为“物业管理 > 维修工单”，详情层级在侧页标题内表达；“办理 / 详情与记录”及原有业务动作保持不变。
- 已验证点击左侧另一张工单时，侧页可直接从 214 号工单切换到 99 号工单，不关闭列表或丢失列表状态。
- 通用 Sheet 增加可选 `showOverlay`，默认行为不变；维修侧页使用非模态无遮罩模式，其他页面不受影响。
- Yaochi 两次 `npm run build`、两仓 `git diff --check` 通过；Chrome 桌面端验收通过，390x844 移动端侧页全屏且无横向溢出，控制台 0 errors。

## Historical Progress

# Progress: 维修工单详情信息架构优化

- 已完成现状核对：确认面包屑只支持两级，报价查看与供应商推荐被拆到不同页签，推荐表单仍内联堆在当前动作中。
- 已确定收敛方案：两级工作面“办理 / 详情与记录”，报价行发起推荐并在弹窗中填写动作参数。
- 面包屑已补齐“物业管理 > 维修工单 > 工单详情”，返回列表时自动清除详情层级。
- 四个页签已收敛为“办理”和“详情与记录”；办理页同时展示当前动作和供应商报价，只读信息与审计流程连续展示。
- 报价行已增加“推荐此供应商”，推荐方式、推荐理由、响应不足三家说明和长期合作关系集中在确认弹窗填写；旧的报价查看弹窗和内联推荐表单已删除。
- Yaochi `npm run build`、`git diff --check` 通过；Chrome 桌面端与 Playwright 390x844 移动端验收通过，移动端无横向溢出，控制台无业务错误。

## Historical Progress

# Progress: 发出邀价后的按钮状态

- `doAction` 现在返回布尔成功结果；邀价成功后清空供应商选择，失败时保留选择。
- 首次待邀价阶段显示“发出邀价”；进入询价中且无新选择时显示禁用的“邀价已发出”；重新选择供应商后显示“补充发出邀价”。
- Yaochi `npm run build` 与 `git diff --check` 通过；Chrome 已确认当前询价中工单的按钮为禁用完成态。

## Historical Progress

# Progress: 报修业务文案校正

- 已将“已定供应商”改为“物业已推荐供应商”，进度节点改为“推荐供应商”“接龙/业主大会”“主任/副主任”“盖章”。
- 风险等级、资金来源和审计动作已使用中文业务名称；资金名称统一为“楼栋维修资金”“小区公共维修资金”“小区公共收益”。
- 资金说明按来源动态展示真实流程，楼栋维修明确接龙截图随正式报审文件送审，主任或副主任任一人确认后再由业委会盖章。
- Yaochi `npm run build` 与 `git diff --check` 通过；Chrome 已确认进度节点、方案与资金、楼栋资金流程说明和审计流水均显示校正后的业务文案。

## Historical Progress

# Progress: 供应商激活后待报价为空

- 已核对真实数据库：93 号邀请为 `ACTIVATED`，但供应商组织 `4684` 的维修邀价记录为 0，214 号工单仍停留在 `PLAN_SUBMITTED`。
- 保持账号激活与维修邀价两个业务事实独立，不增加错误的自动授权或工单兜底展示。
- Yaochi 供应商工作台空状态改为“尚未收到维修邀价”，并明确账号已经开通；物业端账号激活邀请增加“不发送维修邀价”的悬浮提示。
- Yaochi `npm run build` 与相关文件 `git diff --check` 通过；Chrome 已验证供应商工作台正确显示新的空状态且无布局问题。

## Historical Progress

# Progress: 供应商企业与账号状态优化

## Session: 2026-07-11

### Phase 1: 状态与真实账号核验 — complete
- 已确认“待核验”是企业主体状态，不是账号或报价状态。
- 已查询本地数据库确认三家供应商当前联系人、登录身份和激活邀请状态。
- 决定新增独立账号状态及邀请信息，不开放物业自行核验企业主体。
- 后端供应商组织查询已聚合账号与有效邀请，返回 `CONTACT_MISSING / NOT_INVITED / PENDING_ACTIVATION / ACTIVATED`、账号数、登录手机号、邀请编号和有效期。
- Yaochi 已拆分“企业核验”和“账号激活”双状态；待激活显示邀请编号与登录手机号，已激活显示实际登录手机号，已激活账号不再展示重复邀请按钮。
- 发出邀价后页面随工单刷新重新查询供应商状态，可直接回显后端自动创建的激活邀请。
- 聚焦后端流程测试通过；全量 `mvn test` 通过（585 tests，0 failures，0 errors，1 skipped）；Yaochi `npm run build` 与两仓 `git diff --check` 通过。
- Chrome 本地页面验收通过：三家供应商分别正确显示“账号未邀请”“联系人待补充”“账号已激活”，已激活企业回显登录手机号 `13800000031`，双状态和操作按钮无重叠。
- 最新 Pangu 后端已重新打包并运行于 `http://localhost:8080/pangu`。

### Errors Encountered
- 首次查询供应商账号时把 `sys_user.status` 当作整数比较，数据库实际为字符状态；删除错误类型条件后查询成功。

## Historical Progress

# Progress: 供应商最小登记

## Session: 2026-07-11

### Phase 1: 当前约束核验 — complete
- 已确认前端按钮、DTO Bean Validation、应用服务和数据库四层都要求三个字段必填。
- 已确认自动账号邀请依赖联系人姓名与手机号，若直接放宽登记会让邀价事务失败。
- 决定缺少联系人时只跳过账号激活邀请，保留维修邀价与物业代录报价路径。

### Phase 2: 后端与数据模型 — complete
- 新增 `V3.60`，供应商统一社会信用代码、联系人和手机号允许为空；已填写社会信用代码仍受原唯一约束。
- 登记接口仅强制企业名称；可选字段有值时仍执行格式与长度校验。
- 同一租户按企业名称重复登记未补社会信用代码的供应商时复用原组织，并允许补齐缺失资料。
- 邀价遇到无联系人供应商时跳过账号激活邀请，不回滚维修邀价事务。

### Phase 3: Yaochi — complete
- 三个字段已明确标注“选填”，登记按钮只依赖企业名称。
- 请求不会提交空字符串；登记成功后清空表单并自动选中供应商。
- 供应商列表对缺失联系人显示“联系人未补充”，且只在联系人姓名和手机号齐全时显示账号激活按钮。

### Phase 4: 验证与文档 — complete
- 报修聚焦测试：10 tests，0 failures，0 errors；覆盖仅企业名称登记、临时记录复用、无联系人邀价和物业代录报价。
- Pangu 全量测试：585 tests，0 failures，0 errors，1 skipped；Flyway 已升级到 `V3.60`。
- Yaochi `npm run build` 通过，仅保留既有 chunk-size warning。
- Chrome 实测三个字段均显示“选填”，只填写企业名称时登记按钮可用，控制台无业务错误。
- 业务方案已补充最小登记、账号邀请和签约核验边界。
- 最新后端可执行 jar 已重新打包并启动，Tomcat 正常监听 `8080`，数据库版本为 `V3.60`。

### Errors Encountered
- 浏览器验证后清理临时输入时，旧 locator 在页面重渲染后超时；重新加载页面清除了未提交内容，没有产生供应商登记或其他业务副作用。

## Historical Progress

# Progress: 报修治理路径自动分流

## Session: 2026-07-11

### Phase 1: 当前状态核验 — complete
- 已确认“发出邀价”和“治理路径判定”同时出现是前端按同一 `PLAN_SUBMITTED` 状态分别渲染造成。
- 已确认后端 `routePlan` 只按 5 万阈值和 `PROPERTY_INTERNAL` 资金来源判断，能让楼栋/公共维修绕过供应商与正式治理流程。

### Phase 2: 后端自动分流 — complete
- 物业包干维修在提交维修范围后由后端直接进入 `APPROVED`，不再经过人工路径按钮。
- 楼栋维修资金、公共维修资金和公共收益维修保留在供应商选择路径，必须经过邀价/报价/定商。
- 已删除 `route-plan` 应用服务与管理端接口，关闭绕过供应商和表决流程的后门。
- 楼栋接龙和业主大会关联增加资金来源守卫，避免定商后选择错误治理分支。

### Phase 3: Yaochi 当前动作 — complete
- 已移除“治理路径判定”按钮；`PLAN_SUBMITTED` 显示为“待邀价”。
- 物业包干维修不再展示供应商公开限价，确认后直接进入开工路径。
- 定商后根据资金来源只显示楼栋接龙或业主大会入口，不再同时显示两个互斥动作。

### Phase 4: 验证与文档 — complete
- 报修聚焦测试：10 tests，0 failures，0 errors。
- Pangu 全量测试：585 tests，0 failures，0 errors，1 skipped。
- Yaochi `npm run build` 通过，仅保留既有 chunk-size warning。
- Chrome 实测“治理路径判定”按钮数量为 0，“发出邀价”按钮数量为 1；新分流说明可见，控制台无业务错误。
- 业务方案已补充方案提交后的自动分流规则和人工路径接口删除说明。
- 最新后端可执行 jar 已重新打包并启动，Tomcat 正常监听 `8080`，上下文为 `/pangu`。

### Errors Encountered
- Chrome 扩展初始化时 Statsig 遥测请求超时，但本地页面连接、DOM 检查和后续操作均正常；该错误与业务页面无关，未重试遥测请求。
- 首次在沙箱内停止旧后端进程时被系统拒绝；确认是进程权限边界后改用已授权的提升权限命令，旧进程已正常停止。
- 按仓库指南从聚合根执行 `spring-boot:run` 时 Maven 无法解析仅声明在 bootstrap 子模块中的插件前缀；改为先完成 reactor package，再直接运行可执行 jar，避免重复同一失败命令。
- 沙箱内首次访问本机健康检查被网络隔离拒绝；提升权限后服务可达，`/actuator/health` 返回 403 是现有安全策略保护该端点，启动日志已确认 Tomcat 正常启动。

## Historical Progress

# Progress: 供应商账号激活闭环

## Session: 2026-07-11

### Phase 1: 当前缺口核验 — complete
- 确认本地供应商角色账号为 0 条。
- 确认 `t_supplier_activation_invitation` 仅有迁移表结构，尚无应用代码。
- 确认供应商组织登记与供应商工作台已存在，但登录身份链路缺失。
- 确认统一登录为手机号 + 短信验证码，不使用默认密码；账号激活应接入既有 `t_account/sys_user/sys_user_role`。
- 确认供应商组织使用跨租户 S 端部门，报价访问由邀价记录收口。
- 决定采用“受邀手机号 + 短信验证码”激活，不增加密码；公开激活接口只消费有效邀请。
- 决定复用现有账号/工作身份仓储能力，激活后绑定 `SERVICE_PROVIDER_STAFF` 并记录邀请人为授权人。
- Phase 1 模型核验完成：不新增供应商账号表，不引入密码，以现有自然人账号和供应商工作身份组成闭环。

### Phase 2: 后端激活闭环 — complete
- 新增供应商账号激活应用服务：创建/自动确保邀请、手机号与有效期校验、短信验证码校验、一次性消费、账号创建/复用、供应商工作身份和角色绑定、授权审计回填。
- 新增公开激活接口与物业经理邀请接口，并将公开激活加入 Security 白名单。
- 邀价动作会为未激活的企业联系人自动创建有效邀请，物业代录报价路径保持不变。
- 新增 `V3.54` 待激活邀请唯一约束与手机号状态索引。
- 聚焦测试已贯通“邀价 -> 联系人激活 -> 统一登录 -> 查看待报价 -> 供应商本人在线报价”，并验证同一企业两个独立经办人和邀请不可重复消费。
- 首轮聚焦测试暴露供应商工单 SQL 只有首列带表别名，JOIN 后 `tenant_id` 歧义；已改为明确读取 `wo.*`，复跑 9 tests 全部通过。

### Phase 3: Yaochi 与本地验收账号 — complete
- Yaochi 登录页新增“供应商激活”模式，支持邀请编号、经办人姓名、受邀手机号和短信验证码激活。
- 物业工单的供应商列表新增激活邀请动作，页面展示生成的邀请编号。
- 前端补齐 S 端供应商角色映射、供应商工作台顶栏与“本服务组织”范围语义。
- 本地已通过真实 API 创建并激活验收账号 `13800000031`，角色为 `SERVICE_PROVIDER_STAFF`；开发验证码为 `123456`，账号已收到工单 `RO-20260708-000099` 的邀价。

### Phase 4: 导航隔离与全量验证 — complete
- 浏览器验收发现供应商仍继承内部通用菜单；根因是菜单查询把 `required_*` 全空菜单对所有角色开放，单纯清理 `sys_role_menu` 无法隔离外部角色。
- 新增通用 `navigation_isolated` 角色策略；供应商角色启用后只下发显式绑定的供应商工作台菜单。
- 后端全量 `mvn clean test`：583 tests，0 failures，0 errors，1 skipped。
- Yaochi `npm run build` 通过，仅保留既有 chunk-size warning。
- Playwright 已验证桌面与 390x844 移动视口：供应商账号登录后只显示供应商工作台，可见待报价工单，无控制台错误。

### Phase 5: 文档与缺口审计 — complete
- 业务方案已补充限时邀请、短信校验、账号/工作身份复用、多经办人、一次性消费与供应商数据隔离规则。
- 生产短信发送仍属于已明确的外部可信服务集成边界；本轮没有用默认密码、共享账号或前端自报组织替代。

## Historical Progress

# Progress: 选举闭环对齐推进

## Session: 2026-06-27

### Phase 1: 当前状态核验 — complete
- 已读取 `docs/选举闭环对齐路线图.md`。
- 已确认路线图当前状态：A 阶段后端已基本实现；B 阶段方案已决策但未落代码。
- 已检查工作树：存在 A 阶段相关后端改动和路线图改动。
- 已尝试仓库指令 `~/.codex/superpowers/.codex/superpowers-codex bootstrap`，当前环境路径不存在。

### Phase 2: A 阶段聚焦验证 — complete
- 首次聚焦测试未带 `-am`，运行时出现旧字节码相关失败。
- 改用：
  `mvn -pl pangu-bootstrap -am -Dtest=ProposalLifecycleServiceTest,ProposalHandoverGuardTest,ElectionProposeAndRouterTest -Dsurefire.failIfNoSpecifiedTests=false test`
- 结果：27 tests，0 failures，0 errors。

### Phase 3: 规划文件更新 — complete
- 已将 `task_plan.md` / `findings.md` / `progress.md` 从旧的 M3-1 异议升级任务切换到当前选举闭环任务。

### Phase 4: A 阶段全量验证 — complete
- 执行 `mvn clean test`。
- 结果：失败，`pangu-bootstrap` 404 tests 中 289 errors。
- 主要根因不是业务断言：
  - SpringBootTest 首个上下文初始化时 PostgreSQL 连接失败，后续同上下文测试因 failure threshold 被跳过。
  - Mockito inline ByteBuddy mock maker 无法 self-attach，当前沙箱也出现 `/Users/juchen/.rvm/scripts/rvm:29: operation not permitted: ps`。
- 用提升权限重跑后，环境问题消失，暴露 2 个真实测试隔离回归：
  - `DisclosureHandoverEndToEndTest.publishFrozenDuringHandover_thenAutoRecoversAfterSettlement`
  - `ProposalHandoverEndToEndTest.generalProposeFrozenDuringHandover_thenAutoRecoversAfterSettlement`
- 根因：租户 `10001` 有既有 seed 在途选举 `subject_id=360 / 开始换届选举 / status=3`，两个 E2E 只结算自身插入的选举，恢复步骤仍被共享 seed 熔断。
- 修复：两个 E2E 在用例期间临时 suppress 非本测试在途 ELECTION，`@AfterEach` 恢复原状态。
- 聚焦验证：
  `mvn -pl pangu-bootstrap -am -Dtest=DisclosureHandoverEndToEndTest,ProposalHandoverEndToEndTest -Dsurefire.failIfNoSpecifiedTests=false test`
  结果：2 tests，0 failures，0 errors。
- 全量验证：
  `mvn clean test`
  结果：404 tests，0 failures，0 errors，1 skipped。

### Phase 5: B 阶段后端切片 — complete
- 新增 `V3.6__voting_subject_dual_review.sql`：
  - `t_voting_subject.status` 放开到 1-8。
  - 新增 `review_history JSONB NOT NULL DEFAULT '[]'::jsonb`。
  - 新增 `voting:subject:review:committee` / `voting:subject:review:street` 权限并授权。
- `SubjectStatus` 新增 `PENDING_COMMITTEE(7)`、`PENDING_STREET(8)`。
- `VotingSubjectActions` 新增 ELECTION 双签动作：
  `submitForCommitteeReview`、`committeeApprove`、`committeeReject`、`streetApprove`、`streetReject`。
- 新增 `ProposalReviewService`，承载议题双签流转；街道办终审通过前校验候选人池至少 1 名 APPROVED。
- `SubjectAdminController` 新增：
  - `POST /api/v1/voting-subjects/{subjectId}/submit-for-review`
  - `POST /api/v1/voting-subjects/{subjectId}/committee-review`
  - `POST /api/v1/voting-subjects/{subjectId}/street-review`
- `ProposalLifecycleService.publish` 已收紧：ELECTION 只能从 `PENDING_STREET` 进入 `PUBLISHED`；GENERAL/MAJOR 仍保留 `DRAFT -> PUBLISHED`。
- 新增/更新测试：
  - `ProposalReviewServiceTest`
  - `ElectionWorkflowEndToEndTest`
  - `ProposalLifecycleServiceTest`
  - `VotingLifecycleTriggerTest`

### Phase 6: B 后端验证 — complete
- 聚焦验证：
  `mvn -pl pangu-bootstrap -am -Dtest=VotingLifecycleTriggerTest,ProposalLifecycleServiceTest,ProposalReviewServiceTest,ElectionWorkflowEndToEndTest -Dsurefire.failIfNoSpecifiedTests=false test`
  结果：34 tests，0 failures，0 errors。
- 全量验证：
  `mvn clean test`
  结果：414 tests，0 failures，0 errors，1 skipped。

### Phase 7: 源文件再对齐 — complete
- 已读取 `/Users/juchen/Documents/Notes/选举闭环.md`。
- 发现源文件对 ELECTION 立项约束更严格：必须由 G 端基层经办员 `GOV_OPERATOR` 执行，居委会 / 街道办分别承担初审 / 终审，不作为新建立项执行人。
- 已将 `ProposalLifecycleService.propose` 从三角色白名单收紧为 G 端 `GOV_OPERATOR` 独占，并补充街道办 / 居委会立项拒绝测试。
- 已更新 `ElectionWorkflowEndToEndTest`：经办员立项并提交初审，居委会初审，街道办终审。
- 聚焦验证：
  `mvn -pl pangu-bootstrap -am -Dtest=ProposalLifecycleServiceTest,ElectionProposeAndRouterTest,ElectionWorkflowEndToEndTest -Dsurefire.failIfNoSpecifiedTests=false test`
  结果：26 tests，0 failures，0 errors。
- 全量验证：
  `mvn clean test`
  结果：416 tests，0 failures，0 errors，1 skipped。

### Phase 8: review_history 审批轨迹落库 — complete
- 新增 `VotingSubjectRepository.updateStatusWithReviewHistory(...)`，状态翻转与 `review_history` JSONB 追加在同一条 SQL 内完成。
- `ProposalReviewService` 已为 5 个双签动作追加审计元数据：
  `action`、`decision`、`reviewerUserId`、`reason`、`reviewedAt`、`fromStatus`、`toStatus`。
- `ProposalReviewServiceTest` 已断言审计 JSON 包含动作、决策、审核人和状态变更。
- `ElectionWorkflowEndToEndTest` 已断言三步链路后 `review_history` 数组长度为 3，且审核人分别为经办员、居委会、街道办。
- 聚焦验证：
  `mvn -pl pangu-bootstrap -am -Dtest=ProposalReviewServiceTest,ElectionWorkflowEndToEndTest -Dsurefire.failIfNoSpecifiedTests=false test`
  结果：11 tests，0 failures，0 errors。
- 全量验证：
  `mvn clean test`
  结果：416 tests，0 failures，0 errors，1 skipped。

### Phase 9: 双签 controller 权限矩阵测试 — complete
- `VotingEndpointMatrixTest` 已补齐双签端点 web 层覆盖：
  - `submit-for-review`：经办员通过 `@PreAuthorize` 后命中不存在资源 404，网格员无创建权限 403。
  - `committee-review`：居委会通过权限后命中不存在资源 404，网格员 403，缺少 `decision` 请求体返回 400。
  - `street-review`：街道办通过权限后命中不存在资源 404，居委会 403，缺少 `decision` 请求体返回 400。
- 聚焦验证：
  `mvn -pl pangu-bootstrap -am -Dtest=VotingEndpointMatrixTest -Dsurefire.failIfNoSpecifiedTests=false test`
  结果：21 tests，0 failures，0 errors。
- 全量验证：
  `mvn clean test`
  结果：424 tests，0 failures，0 errors，1 skipped。

### Next（当时记录，已由 Phase 12 部分完成）
- 梯度 B 三仓同步已完成；下一步进入梯度 C：HANDOVER_LOCK 财务熔断与换届备案通过。
- 梯度 C 开工前需明确大额阈值，以及维修资金支取 / 信托动账 / 公共收益划拨的首批熔断接口范围。

### yaochi 前端预检 — blocked-by-permission
- 已确认 `/Users/juchen/Documents/workspace/yaochi` 存在，且已有 A 阶段前端改动痕迹。
- 已定位 B.7 需要修改的文件：
  - `src/app/lib/voting.ts`
  - `src/app/components/pages/SubjectProposal.tsx`
- 已确认当前会话可写根仅包含 pangu；尝试对 yaochi 打补丁被权限策略拒绝，因此未改动前端文件。
- 下一步需要在包含 yaochi 的可写上下文中执行前端改动，或显式授权修改该仓库。

### Phase 10: ELECTION 立项 dept_type 精确护栏 — complete
- 已把 `sys_dept.dept_type` 装配进 `UserContext`：
  - `UserContext` 新增 `deptType`。
  - `UserContextMapper.xml` 查询 `d.dept_type AS deptType`。
  - `DefaultUserContextLoader` 传递 `deptType`。
- `AuthService.login` 的 `user_info` 已返回 `dept_type`，供 yaochi / shennong-app 同步类型使用；JWT 仍不嵌角色/权限/部门信息。
- `ProposalLifecycleService.propose` 已从 `deptCategory=G` 近似护栏收紧为 `roleKey=GOV_OPERATOR + deptType IN (2,5)`。
- 已补 `ProposalLifecycleServiceTest.propose_electionByGovOperatorOnPartyDept_rejected`，防止 G 端党组织 `dept_type=6` 被误放行。
- 聚焦验证：
  `mvn -pl pangu-bootstrap -am -Dtest=ProposalLifecycleServiceTest,ControllerIntegrationTest,ElectionProposeAndRouterTest,ProposalHandoverGuardTest,DataScopeTest,VoteSubmissionServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`
  结果：50 tests，0 failures，0 errors，1 skipped。
- 全量验证：
  `mvn clean test`
  结果：425 tests，0 failures，0 errors，1 skipped。

### yaochi / shennong-app 同步预检 — blocked-by-permission
- 用户确认：
  - `/Users/juchen/Documents/workspace/yaochi` 是管理端。
  - `/Users/juchen/Documents/workspace/shennong-app` 是 C 端。
- yaochi 需要同步：
  - `src/app/lib/auth.ts`：`UserInfo` 增加 `dept_type`。
  - `src/app/lib/voting.ts`：补 `PENDING_COMMITTEE / PENDING_STREET` 与三步双签 API。
  - `src/app/components/pages/SubjectProposal.tsx`：列表纳入双签中状态；ELECTION 立项 UI 收紧为 `GOV_OPERATOR + dept_type IN (2,5)`；直接公示替换为提交初审 / 居委会初审 / 街道办终审。
- shennong-app 需要同步：
  - `src/lib/auth.ts`：`UserInfo` 增加 `dept_type`。
  - `src/lib/owner-voting.ts`：`SubjectStatus` 增加 `PENDING_COMMITTEE / PENDING_STREET`。
  - C 端页面不展示双签中状态，继续只显示业主可见的 `PUBLISHED / VOTING / CLOSED / SETTLED`。
- 首次尝试提权写入 yaochi / shennong-app 时被系统拒绝（额度限制），当时未改动两个外部仓库。

### Phase 11: yaochi / shennong-app 同步 — complete
- yaochi 管理端已同步 B 阶段双签：
  - `src/app/lib/auth.ts`：`UserInfo` 增加 `dept_type`。
  - `src/app/lib/voting.ts`：`SubjectStatus` 增加 `PENDING_COMMITTEE / PENDING_STREET`，新增 `submitSubjectForReview`、`committeeReviewSubject`、`streetReviewSubject`。
  - `src/app/components/pages/SubjectProposal.tsx`：筹备列表纳入 `DRAFT / PENDING_COMMITTEE / PENDING_STREET / PUBLISHED`；ELECTION 立项 UI 收紧为 `GOV_OPERATOR + dept_type IN (2,5)`；ELECTION 直接公示替换为提交初审 / 居委会初审 / 街道办终审。
- shennong-app C 端已同步类型兼容：
  - `src/lib/auth.ts`：`UserInfo` 增加 `dept_type`。
  - `src/lib/owner-voting.ts`：`SubjectStatus` 增加 `PENDING_COMMITTEE / PENDING_STREET`。
  - `src/pages/owner/voting-list/index.tsx`：补齐新状态的空态 label；页面仍只展示业主可见状态，不暴露双签中状态。
- 验证：
  - yaochi：`npm run build` 通过。
  - shennong-app：`npm run type-check` 通过。
  - pangu：`mvn clean test` 通过，425 tests，0 failures，0 errors，1 skipped。

### Phase 12: C-mini 租户任期锁闭环 — complete
- 后端新增 `t_tenant_term_state`（V3.7），由于当前迁移没有稳定 `sys_tenant` 表，先用专用表承载租户任期状态。
- 新增领域模型与端口：
  - `TenantTermStatus`
  - `TenantTermState`
  - `TenantTermStateRepository`
- 新增基础设施 mapper / repository：
  - `TenantTermStateMapper`
  - `TenantTermStateMapper.xml`
  - `TenantTermStateRepositoryImpl`
- `HandoverCircuitBreaker` 已优先读取持久 `HANDOVER_LOCK`；无持久锁时继续回退到在途 ELECTION 查询派生。
- `VotingApplicationService.settle` 已在 ELECTION 结算成功后写入 `HANDOVER_LOCK`。
- 新增 `TenantTermLockService.confirmHandover` 与 `POST /api/v1/handover/confirm`，街道办权限 `voting:subject:review:street` 可恢复 `NORMAL`。
- yaochi 管理端同步：
  - `src/app/lib/voting.ts` 新增 `confirmHandover()`。
  - `SubjectProposal.tsx` 将 SETTLED ELECTION 保留为换届收尾入口，并给街道办显示“备案通过”按钮。
- 验证：
  - 首次 C-mini 聚焦测试因 `ElectionProposeAndRouterTest` 缺 `Map` import 编译失败，已修复。
  - 聚焦验证：`mvn -pl pangu-bootstrap -am -Dtest=HandoverCircuitBreakerTest,ElectionProposeAndRouterTest,VotingEndpointMatrixTest -Dsurefire.failIfNoSpecifiedTests=false test`，30 tests，0 failures，0 errors。
  - 首次全量 `mvn clean test` 因新增任期锁 FK 阻挡 `ElectionWorkflowEndToEndTest` 清理，随后污染 `DataScopeTest`；已补清理顺序。
  - 污染链聚焦验证：`mvn -pl pangu-bootstrap -am -Dtest=ElectionWorkflowEndToEndTest,DataScopeTest -Dsurefire.failIfNoSpecifiedTests=false test`，4 tests，0 failures，0 errors。
  - 后端全量：`mvn clean test`，428 tests，0 failures，0 errors，1 skipped。
  - yaochi：`npm run build` 通过，Vite chunk-size warning 仍存在。

### Phase 13: C 维修资金支取熔断第一切片 — complete
- 现状盘点：
  - 仓库没有 `FundExpenseService / TrustFundService / MaintenanceFundService` 三个写服务。
  - V2.2 已有 `t_maintenance_fund_account` / `t_fund_ledger_entry`，但此前只有只读聚合 gateway。
- 新增 `TenantTermLockGuard`：
  - 读取 `platform.handover.large-amount-threshold`，默认 `10000.00`。
  - HANDOVER_LOCK 下金额 `>= 阈值` 熔断，`< 阈值` 放行。
- 新增维修资金支取真实写路径：
  - `MaintenanceFundApplicationService`
  - `MaintenanceFundExpenseCommand`
  - `MaintenanceFundApplicationException`
  - `MaintenanceFundAccountRepository`
  - `MaintenanceFundAccountMapper.xml`
  - `MaintenanceFundAccountRepositoryImpl`
- 新增集成测试 `MaintenanceFundHandoverGuardTest`：
  - HANDOVER_LOCK + `10000.00` 拒绝，余额/流水不变。
  - HANDOVER_LOCK + `9999.99` 放行，扣余额并写流水。
  - 无 HANDOVER_LOCK + `10000.00` 放行。
- 验证：
  - 聚焦：`mvn -pl pangu-bootstrap -am -Dtest=MaintenanceFundHandoverGuardTest,HandoverCircuitBreakerTest -Dsurefire.failIfNoSpecifiedTests=false test`，6 tests，0 failures，0 errors。
  - 全量：`mvn clean test`，431 tests，0 failures，0 errors，1 skipped。
  - 显式配置 `platform.handover.large-amount-threshold` 后复验：`mvn -pl pangu-bootstrap -am -Dtest=MaintenanceFundHandoverGuardTest -Dsurefire.failIfNoSpecifiedTests=false test`，3 tests，0 failures，0 errors。

### Phase 14: C 公共收益划拨熔断第一切片 — complete
- 现状判断：
  - V2.2 已在 `t_fund_ledger_entry.business_type=3` 预留 `PUBLIC_INCOME_TRANSFER`。
  - 当前没有独立公共收益账户模型；第一切片按“公共收益划拨入维修资金账户”落在既有账户/流水模型。
- 新增公共收益划拨真实写路径：
  - `PublicRevenueTransferCommand`
  - `MaintenanceFundApplicationService.recordPublicIncomeTransfer`
  - `MaintenanceFundAccountRepository.credit`
  - `MaintenanceFundAccountMapper.credit`
- 写路径行为：
  - 写入前执行 `HANDOVER_LOCK + 大额阈值` 守卫；
  - `SELECT ... FOR UPDATE` 锁定维修资金账户；
  - 校验 tenant 与金额；
  - 增加 `total_balance`、递增 `version`；
  - 写 `t_fund_ledger_entry`，`business_type=3`，`direction=1`。
- 扩展 `MaintenanceFundHandoverGuardTest`：
  - HANDOVER_LOCK + `10000.00` 公共收益划拨拒绝，余额/流水不变。
  - HANDOVER_LOCK + `9999.99` 公共收益划拨放行，余额增加并写流水。
- 验证：
  - 聚焦：`mvn -pl pangu-bootstrap -am -Dtest=MaintenanceFundHandoverGuardTest -Dsurefire.failIfNoSpecifiedTests=false test`，5 tests，0 failures，0 errors。
  - 全量：`mvn clean test`，433 tests，0 failures，0 errors，1 skipped。

### Phase 15: C 信托制双签动账熔断第一切片 — complete
- 现状判断：
  - 仓库没有独立 `TrustFundService` / 信托账户 / 分期付款写模型。
  - 已有 `GovernanceLock` 通用双签机制，可承载“信托付款指令已由业委会主任 + 街道办双签解锁”的证明。
- 新增 Flyway `V3.8__trust_fund_payment_lock_entity.sql`：
  - 扩展 `t_governance_lock.chk_lock_entity_type`，新增 `TRUST_FUND_PAYMENT`。
  - 更新 `t_governance_lock.entity_type` 注释。
- 新增信托制双签动账真实写路径：
  - `TrustFundDisbursementCommand`
  - `LockEntityType.TRUST_FUND_PAYMENT`
  - `MaintenanceFundApplicationService.recordTrustFundDisbursement`
- 写路径行为：
  - 写入前执行 `HANDOVER_LOCK + 大额阈值` 守卫；
  - 校验对应 `TRUST_FUND_PAYMENT` 治理锁已 `FULLY_UNLOCKED`；
  - `SELECT ... FOR UPDATE` 锁定维修资金账户；
  - 校验 tenant、金额、可用余额；
  - 扣减 `total_balance`、递增 `version`；
  - 写 `t_fund_ledger_entry`，`business_type=7`，`direction=2`。
- 扩展测试：
  - `MaintenanceFundHandoverGuardTest`：未完成双签拒绝、双签完成后小额出账放行、HANDOVER_LOCK 下已双签大额仍熔断。
  - `LockMatrixIntegrationTest`：`TRUST_FUND_PAYMENT` 全治理锁链路。
- 验证：
  - 聚焦：`mvn -pl pangu-bootstrap -am -Dtest=MaintenanceFundHandoverGuardTest,LockMatrixIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`，15 tests，0 failures，0 errors。
  - 全量：`mvn clean test`，437 tests，0 failures，0 errors，1 skipped。

### Phase 16: C 老主任密钥回收 mock 钩子 — complete
- 现状判断：
  - 当前仓库没有真实密钥/证书系统，也没有老主任密钥表。
  - 按 C 阶段“mock 钩子”要求，先补端口和日志型实现，不硬造真实密钥模型。
- 新增端口与实现：
  - `CommitteeKeyRevocationGateway`
  - `MockCommitteeKeyRevocationGateway`
- `TenantTermLockService.confirmHandover` 已在 `releaseHandoverLock` 成功后调用 `revokeOutgoingDirectorKeys(tenantId, confirmedByUserId)`。
- 新增 `TenantTermLockServiceTest`：
  - ELECTION settle 后只进入 HANDOVER_LOCK，不回收密钥；
  - GENERAL 不进入 HANDOVER_LOCK；
  - confirmHandover 按顺序先 release 任期锁，再触发密钥回收 mock。
- 验证：
  - 首次聚焦测试因测试里调用不存在的 `VotingSubject.createDraft(...)` 工厂编译失败；已改为 builder 构造。
  - 聚焦：`mvn -pl pangu-bootstrap -am -Dtest=TenantTermLockServiceTest,HandoverCircuitBreakerTest,VotingEndpointMatrixTest -Dsurefire.failIfNoSpecifiedTests=false test`，29 tests，0 failures，0 errors。
  - 全量：`mvn clean test`，440 tests，0 failures，0 errors，1 skipped。

### Next（当时记录，已过期）
- 梯度 D-mini 已完成；下一步进入梯度 E 前，先细化监控基线 / Merkle / Clock Suspend 的最小切片。
- 当时 V3.9/V3.10 已被 D-mini 分身 seed 与 seed 调整使用；后续迁移现已推进到 V3.14。
- shennong-app 本轮没有新增 C 端可见行为，暂不需要新增页面能力。

### Phase 17: D-mini 后端 SYS_USER 分身切卡 — complete
- 现状判断：
  - `sys_user.uk_account_dept` 已支持同一自然人在不同部门下有多个 SYS_USER 分身。
  - `sys_user_role` 是 `user_id -> role_id` 一分身一角色，D-mini 不需要改主键。
  - 原 `UserContextLoader` 查询只按 `user_id / uid` 装配，D-mini 必须收紧 account 归属校验。
- 后端新增：
  - `V3.9__seed_identity_shadow_switch.sql`：给刘主任 `account_id=999803` 新增网格员分身 `user_id=800006`，角色 `GRID_OPERATOR`，并绑定楼栋。
  - `V3.10__adjust_identity_shadow_seed_assignment.sql`：将 800006 的楼栋占用迁到隔离楼栋 `39999`，避免干扰责任田基线楼栋。
  - `IdentityShadowMapper` / `IdentityShadowMapper.xml`：查询当前自然人名下 SYS_USER 分身。
  - `SwitchShadowRequest`。
  - `GET /api/v1/auth/shadows`：返回当前自然人名下所有 SYS_USER 分身。
  - `POST /api/v1/auth/switch-shadow`：校验 `account_id + targetUserId` 后刷新 JWT，并回填 `t_account.last_active_identity_id`。
- 安全收紧：
  - `UserContextMapper.loadSysUserContext` 改为 `account_id + user_id` 查询。
  - `UserContextMapper.loadCUserContext` 改为 `account_id + uid` 查询。
- 新增测试：
  - `SwitchShadowMatrixTest` 覆盖分身列表、刘主任切到网格员分身、切到其他账号分身被拒。
- 验证：
  - 聚焦：`mvn -pl pangu-bootstrap -am -Dtest=SwitchShadowMatrixTest,SwitchTenantMatrixTest,ControllerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`，13 tests，0 failures，0 errors，1 skipped。
  - 首次全量 `mvn clean test` 失败：新增 800006 网格分身占用 `30001/30002`，触发 `BuildingAssignmentTest` 责任田同角色互斥断言变化。
  - 修复：追加 V3.10 将 800006 楼栋占用迁到隔离楼栋；`BuildingAssignmentTest.assign_buildingOccupiedBySameRole_409_42407` 改为验证第二个 GRID_OPERATOR 分身的真实冲突路径。
  - 责任田联动聚焦：`mvn -pl pangu-bootstrap -am -Dtest=BuildingAssignmentTest,SwitchShadowMatrixTest -Dsurefire.failIfNoSpecifiedTests=false test`，21 tests，0 failures，0 errors。
  - 后端全量：`mvn clean test`，443 tests，0 failures，0 errors，1 skipped。

### Phase 18: yaochi Topbar 分身切卡同步 — complete
- yaochi 管理端新增真实分身切卡：
  - `src/app/lib/auth.ts` 新增 `SysUserShadow`、`listSysUserShadows()`、`switchSysUserShadow()`，并复用 session 派生逻辑保存新 token。
  - `src/app/lib/store.tsx` 新增 `switchShadow(targetUserId)`，切换后重算 `roleId / communityId / permissions / roleKey`。
  - `src/app/components/shell/Topbar.tsx` 在头像下拉中展示真实工作分身列表；点击非当前分身后调用后端切卡并刷新菜单权限。
- 旧的演示角色切换仍保留在下拉下半区，避免影响现有演示路径。
- 验证：yaochi `npm run build` 通过，仍有既有 Vite chunk-size warning。

### Phase 19: E1 ELECTION 立项分母冻结 + Merkle 进度存证 — complete
- 现状判断：
  - 源文件要求“创建选举时立即触发房产清洗并冻结分母 Merkle root，之后产权转移/拆分不改变本次分母”。
  - V2.3 已有 `t_voting_denominator_snapshot.aggregate_hash`，可承载行级分母 Merkle root；E1 不新增迁移。
- 后端新增/调整：
  - 新增 `DenominatorSnapshotRow`。
  - `VotingDenominatorSnapshotMapper` / XML 新增 `selectSnapshotBySubjectId` 与 `insertSnapshotIfAbsent`，同议题已存在快照时不刷新。
  - `DefaultVotingDenominatorResolver` 先查冻结快照；首次冻结后写快照和 item rows，后续直接复用。
  - `ProposalLifecycleService.propose` 对 ELECTION 立项后立即解析并冻结分母；GENERAL/MAJOR 不强制冻结。
  - `VotingDenominatorReader` 新增 `findFrozenSnapshot`，`DenominatorTotals` 增加快照 ID / Merkle root。
  - `VotingProgress` 与 `SubjectProgressResponse` 新增 `denominatorSnapshotId / denominatorMerkleRoot`。
  - `VotingProgressQueryService` 进行中进度优先使用冻结分母；结算态补充冻结 Merkle 证据。
- 测试更新：
  - `ProposalLifecycleServiceTest` 覆盖 ELECTION 立项调用分母解析、GENERAL 不调用。
  - `ElectionProposeAndRouterTest` 覆盖立项即生成快照，以及立项后新增房产不改变进度分母。
  - `ElectionWorkflowEndToEndTest` 将业主/房产 seed 前移到立项前，并断言冻结快照。
- 验证：
  - 聚焦：`mvn -pl pangu-bootstrap -am -Dtest=ElectionProposeAndRouterTest,VotingProgressQueryTest,ProposalLifecycleServiceTest,VotingProgressCalculatorTest,ProposalHandoverGuardTest -Dsurefire.failIfNoSpecifiedTests=false test`，40 tests，0 failures，0 errors。
  - 首次全量 `mvn clean test` 失败：`ElectionWorkflowEndToEndTest` 在立项后才 seed 房产，新规则下立项冻结分母失败返回 500。
  - 修复：将该 E2E 的业主/房产 seed 前移到立项前，并补快照断言。
  - E2E 聚焦：`mvn -pl pangu-bootstrap -am -Dtest=ElectionWorkflowEndToEndTest -Dsurefire.failIfNoSpecifiedTests=false test`，1 test，0 failures，0 errors。
  - 后端全量：`mvn clean test`，444 tests，0 failures，0 errors，1 skipped。

### Phase 20: E1 yaochi / shennong-app 契约同步 — complete
- yaochi 管理端：
  - `src/app/lib/voting.ts` 的 `SubjectProgress` 新增 `denominatorSnapshotId / denominatorMerkleRoot` 可空字段。
  - 验证：`npm run build` 通过，仍有既有 Vite chunk-size warning。
- shennong-app C 端：
  - `src/lib/voting-progress.ts` 的 `SubjectProgress` 新增 `denominatorSnapshotId / denominatorMerkleRoot` 可空字段。
  - 验证：`npm run type-check` 通过。

### Next（当时记录，已由 Phase 21 部分完成）
- 梯度 E2：写入侧增量计数 + Redis 时序 Bloom 监控基线。
- 梯度 E3：Waiver / 候选人审查拒绝强制 C1-C5 reason_code + evidence_jsonb。
- 梯度 E4：Clock Suspend，把 HANDOVER_LOCK 期间的议案倒计时物理暂停并在 NORMAL 恢复。
- 当时 V3.9/V3.10 已用于 D-mini；后续迁移现已推进到 V3.14。

### Phase 21: E2a 投票写入侧 Redis Bloom + 增量计数基线 — complete
- 当时现状判断：
  - `VoteSubmissionService.cast` 是 C 端投票唯一应用写入口；
  - 当时 `t_vote_item` / `CastVoteRequest` 没有显式 vote_channel，暂以 `signatureHash` 为空统计 `unsigned`，作为纸票/线下票候选基线。
- 后端新增：
  - `VoteCastMonitorGateway`：领域端口与 `VoteCastEvent`。
  - `RedissonVoteCastMonitorGateway`：Redis 监控基线实现。
- 写入行为：
  - 成功写入 `t_vote_item` 后注册 `TransactionSynchronization.afterCommit`，主事务提交后再写监控基线；
  - 非事务场景立即写入，便于单元测试；
  - Redis/监控异常只记录 warn，不影响投票结果。
- Redis key：
  - `bf:vote-cast:{subjectId}`：`opid:targetId` Bloom 基线；
  - `counter:vote-cast:{subjectId}:total`：成功写票总数；
  - `counter:vote-cast:{subjectId}:unsigned`：无签名票数；
  - `counter:vote-cast:{subjectId}:last-at`：最近一次写票时间；
  - `counter:vote-cast:{subjectId}:rapid-interval`：相邻写票间隔小于 30 秒次数。
- 测试：
  - `VoteSubmissionServiceTest` / `ElectionVoteSubmissionTest` 验证成功写票才调用监控端口。
  - `VoteCastMonitorGatewayIntegrationTest` 验证真实 Redisson Bloom 与计数器写入。
- 验证：
  - 聚焦：`mvn -pl pangu-bootstrap -am -Dtest=VoteSubmissionServiceTest,ElectionVoteSubmissionTest,VoteCastMonitorGatewayIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`，19 tests，0 failures，0 errors。
  - 后端全量：`mvn clean test`，445 tests，0 failures，0 errors，1 skipped。

### Phase 22: E2b 纸票占比阈值判定 + 监控告警查询面 — complete
- 当时现状判断：
  - E2a 已有 Redis 计数，但没有查询侧阈值判定；
  - 当时投票写入契约没有显式 `vote_channel`，本轮继续用 `signatureHash` 为空作为纸票/线下票候选口径。
- 后端新增/调整：
  - `VoteCastMonitorSnapshot`：领域监控快照。
  - `VoteCastMonitorGateway.loadCounters(subjectId)`：读取 `total / unsigned / rapid-interval` 计数。
  - `RedissonVoteCastMonitorGateway.loadCounters`：从 Redis atomic long 读取既有监控 key。
  - `VoteMonitorQueryService`：按租户校验议题，计算 `unsignedRatio`，输出无签名票占比告警与快速连续写票告警。
  - `VoteMonitorResponse`：管理端监控响应。
  - `GET /api/v1/voting-subjects/{subjectId}/monitor`：管理端查询入口，权限 `voting:subject:audit`。
- 配置：
  - `platform.voting.monitor.unsigned-ratio-threshold: "0.30"`。
  - `platform.voting.monitor.rapid-interval-threshold: 1`。
- 测试：
  - `VoteMonitorQueryServiceTest` 覆盖超阈值、无票、跨租户隐藏。
  - `VoteCastMonitorGatewayIntegrationTest` 补 `loadCounters` 真实 Redis 读取断言。
  - `VotingEndpointMatrixTest` 覆盖监控端点 G 端通过 preAuth 后 404、C 端 403。
- 验证：
  - 聚焦：`mvn -pl pangu-bootstrap -am -Dtest=VoteMonitorQueryServiceTest,VoteCastMonitorGatewayIntegrationTest,VotingEndpointMatrixTest -Dsurefire.failIfNoSpecifiedTests=false test`，29 tests，0 failures，0 errors。
  - 后端全量：`mvn clean test`，450 tests，0 failures，0 errors，1 skipped。

### Phase 23: E2b yaochi 同步 — complete
- yaochi 管理端已同步 API 契约：
  - `src/app/lib/voting.ts` 新增 `VoteMonitor` 类型。
  - `src/app/lib/voting.ts` 新增 `getSubjectMonitor(subjectId)`，调用 `/voting-subjects/{subjectId}/monitor`。
- shennong-app 本轮没有新增 C 端可见字段或流程，未改动。
- 验证：
  - yaochi：`npm run build` 通过，仍有既有 Vite chunk-size warning。

### Next（当时记录，已由 Phase 26/27 完成）
- 当时计划推进梯度 E4：Clock Suspend，把 HANDOVER_LOCK 期间的议案倒计时物理暂停并在 NORMAL 恢复。
- 后续如需显式区分线上票 / 纸票 / 线下代录票，建议新增 `vote_channel` 写入契约并同步 shennong-app。

### Phase 24: E3 C1-C5 拒绝理由码 + JSONB 证据链后端闭环 — complete
- 现状判断：
  - 源文件要求 G 端拒绝必须挂 C1-C5 客观理由与 JSONB 证据链。
  - 当前 Waiver 拒绝、候选人党组审查拒绝、候选人居委会审查拒绝此前没有统一 reason_code / evidence_jsonb 强约束。
- 后端新增/调整：
  - 新增 `ElectionRejectReasonCode`，只允许 `C1 / C2 / C3 / C4 / C5`。
  - 新增 `RejectEvidencePolicy`，拒绝时强制校验 reason code 与非空 JSON object 证据；通过时不要求证据。
  - `VotingApplicationException.Reason`、`ElectionErrorCode`、`ElectionExceptionTranslator` 新增 `REJECT_REASON_CODE_REQUIRED(40952)` 与 `REJECT_EVIDENCE_REQUIRED(40953)`。
  - 候选人审查命令、Waiver 审查命令均新增 `rejectReasonCode / rejectEvidenceJson`，并保留旧构造重载以降低调用面破坏。
  - `t_election_candidate` 与 `t_party_ratio_waiver` 通过 `V3.11__election_reject_reason_evidence.sql` 新增拒绝理由码和 JSONB 证据字段。
  - 候选人 mapper / repository / response 已读写拒绝理由码、证据、审核人、审核阶段与拒绝时间。
  - Waiver mapper / repository / response 已读写居委会、街道两段拒绝理由码与证据。
- 接口契约：
  - `ReviewCandidateRequest` 新增 `rejectReasonCode` 与 `rejectEvidence`。
  - `ReviewWaiverRequest` 新增 `rejectReasonCode` 与 `rejectEvidence`。
  - `POST /api/v1/election-candidates/{id}/party-review`、`/review` 拒绝时必须带 C1-C5 + evidence。
  - `POST /api/v1/waivers/{id}/committee-review`、`/street-review` 拒绝时必须带 C1-C5 + evidence。
- 测试：
  - `ElectionCandidateServiceTest` 覆盖缺 reason code 拒绝。
  - `ElectionCandidateEndpointMatrixTest` 覆盖缺 reason code 返回 400/40952，以及拒绝证据落到响应。
  - `ElectionWorkflowIntegrationTest` 覆盖 Waiver 居委会拒绝 reason/evidence 持久化。
  - `ElectionWorkflowEndToEndTest` 覆盖候选人拒绝返回 `rejectReasonCode`。
- 验证：
  - 聚焦：`mvn -pl pangu-bootstrap -am -Dtest=ElectionCandidateServiceTest,ElectionCandidateEndpointMatrixTest,ElectionWorkflowIntegrationTest,ElectionWorkflowEndToEndTest -Dsurefire.failIfNoSpecifiedTests=false test`，38 tests，0 failures，0 errors。
  - 后端全量：`mvn clean test`，454 tests，0 failures，0 errors，1 skipped。

### Phase 25: E3 yaochi 同步与路线图更新 — complete
- yaochi 管理端已同步候选人拒绝契约：
  - `src/app/lib/election.ts` 新增 `RejectReasonCode`、`RejectEvidenceInput`，`partyReviewCandidate` / `reviewCandidate` 拒绝时发送 `rejectReasonCode / rejectEvidence`。
  - `Election.tsx` 与 `SubjectProposal.tsx` 在拒绝候选人时采集 C1-C5 reason code 与证据 note，再调用后端 API。
- 本轮 shennong-app 无需改动：
  - C 端只读取业主可见议题 / 候选人 / 投票流程，不执行 Waiver 或候选人管理拒绝动作。
  - 后端新增拒绝证据字段不会改变业主侧现有列表或投票提交契约。
- 验证：
  - yaochi：`npm run build` 通过，仍有既有 Vite chunk-size warning。

### Next（当时记录，已由 Phase 28 部分完成）
- 当时计划进入 E 后续精细化：显式 `vote_channel` 写入契约或业主代表 / 楼栋长催票权限事件驱动激活；其中 `vote_channel` 已完成。

### Phase 26: E4 Clock Suspend 后端闭环 — complete
- 现状判断：
  - 当前倒计时完全由 `t_voting_subject.vote_start_at / vote_end_at` 驱动。
  - `VotingOpenScheduler` 基于 `PUBLISHED + vote_start_at <= now` 开票。
  - `VotingDeadlineScheduler` 基于 `VOTING + vote_end_at < now` 截止结算。
  - 因此 E4 选择“暂停标记 + 恢复时顺延原时间字段”，复用既有 scheduler 与前端时间展示。
- 后端新增/调整：
  - 新增 `V3.12__voting_clock_suspend.sql`：`clock_suspended_at`、`clock_suspended_by_subject_id`，以及 active scheduler 部分索引。
  - 新增 `V3.13__voting_clock_suspend_delete_policy.sql`：将 `clock_suspended_by_subject_id` 自引用 FK 改为 `ON DELETE SET NULL`。
  - `VotingSubject`、`VotingSubjectRow`、mapper、repository 已读写暂停字段。
  - `VotingSubjectRepository` 新增 `suspendVotingClocksForHandover` 与 `resumeVotingClocksAfterHandover`。
  - `TenantTermLockService.engageAfterElectionSettled` 在 ELECTION 结算进入 HANDOVER_LOCK 后暂停同租户 `GENERAL/MAJOR + PUBLISHED/VOTING` 议题。
  - `TenantTermLockService.confirmHandover` 在释放任期锁前先恢复暂停议题，按暂停时长顺延 `vote_start_at / vote_end_at`。
  - `selectPublishedReadyForOpen` 与 `selectExpiredVoting` 均排除 `clock_suspended_at IS NOT NULL`。
- 接口契约：
  - `AdminSubjectResponse` 新增 `clockSuspendedAt / clockSuspendedBySubjectId`。
  - `OwnerSubjectResponse` 新增同名字段。
- 测试：
  - `TenantTermLockServiceTest` 验证暂停/恢复调用顺序。
  - `ElectionWorkflowIntegrationTest.clockSuspend_handoverLockPausesAndResumesPublishedVotingSubjects` 验证真实 DB 暂停、scheduler 排除、NORMAL 恢复后顺延。
- 验证：
  - 单测切片：`mvn -pl pangu-bootstrap -am -Dtest=TenantTermLockServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`，3 tests，0 failures，0 errors。
  - E4 聚焦：`mvn -pl pangu-bootstrap -am -Dtest=ElectionWorkflowIntegrationTest,TenantTermLockServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`，8 tests，0 failures，0 errors。

### Phase 27: E4 三仓同步与全量验证 — complete
- yaochi 管理端：
  - `src/app/lib/voting.ts` 的 `AdminSubject` 新增 `clockSuspendedAt / clockSuspendedBySubjectId`。
  - 验证：`npm run build` 通过，仍有既有 Vite chunk-size warning。
- shennong-app C 端：
  - `src/lib/owner-voting.ts` 的 `OwnerSubject` 新增 `clockSuspendedAt / clockSuspendedBySubjectId`。
  - 验证：`npm run type-check` 通过。
- 首次 pangu 全量失败：
  - `ElectionWorkflowEndToEndTest` 清理被 `clock_suspended_by_subject_id` 自引用 FK 阻挡。
  - 因清理失败残留 `c_owner_property`，后续 `DataScopeTest` 从期望 3 条变成 6 条。
- 修复：
  - 新增 V3.13 将自引用 FK 改为 `ON DELETE SET NULL`。
  - `ElectionWorkflowEndToEndTest` 与 `ElectionWorkflowIntegrationTest` 清理时先清空暂停引用。
- 复验：
  - 失败链路聚焦：`mvn -pl pangu-bootstrap -am -Dtest=ElectionWorkflowEndToEndTest,DataScopeTest,ElectionWorkflowIntegrationTest,TenantTermLockServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`，12 tests，0 failures，0 errors。
  - 后端全量：`mvn clean test`，455 tests，0 failures，0 errors，1 skipped。

### Next
- 显式 `vote_channel` 写入契约已由 Phase 28 完成；下一步进入业主代表 / 楼栋长催票权限事件驱动激活。
- 待补验证：shennong-app `npm run type-check`，本轮因自动审批使用限额被拒。
- V3.14 已用于 `vote_channel`；后续迁移从 V3.15+ 开始。

### Phase 28: 显式 vote_channel 写入契约 — complete
- 现状判断：
  - E2a/E2b 监控先用 `signatureHash == null` 推断纸票/线下票候选。
  - C 端 GENERAL/ELECTION 正常线上投票可能没有签名，继续用签名空值推断会误报纸票占比。
- 后端新增/调整：
  - 新增 `VoteChannel`：`ONLINE / PAPER / OFFLINE_PROXY`。
  - 新增 `V3.14__vote_channel.sql`：`t_vote_item.vote_channel`，默认 `ONLINE`，含 check constraint 与 subject/channel 索引。
  - `CastVoteCommand`、`CastVoteRequest`、`VoteItem`、`VoteItemRow`、MyBatis mapper、repository 已贯通 `vote_channel`。
  - 旧 `CastVoteCommand` 构造器、旧 `VoteItem` 构造器、旧请求缺省都按 `ONLINE` 处理。
  - `VoteCastEvent` 新增 `voteChannel`；`unsignedLikePaper()` 改为只对 `PAPER / OFFLINE_PROXY` 为 true。
  - `RedissonVoteCastMonitorGateway` 沿用 `counter:vote-cast:{subjectId}:unsigned` key，但语义从“无签名票”升级为“纸票/线下代录票”。
- C 端同步：
  - shennong-app `CastVoteRequest` 新增 `voteChannel` 类型。
  - 业主端正常提交固定传 `voteChannel: 'ONLINE'`。
- yaochi 同步：
  - 本轮无管理端投票提交入口；监控接口字段保持兼容，暂不改 UI 契约。
- 验证：
  - pangu 聚焦：`mvn -pl pangu-bootstrap -am -Dtest=VoteSubmissionServiceTest,ElectionVoteSubmissionTest,VoteCastMonitorGatewayIntegrationTest,VoteMonitorQueryServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`，24 tests，0 failures，0 errors。
  - shennong-app `npm run type-check` 当时被自动审批使用限额拒绝；后续已补跑通过。

### Next
- 业主代表 / 楼栋长催票权限事件驱动激活第一切片已由 Phase 29 完成。
- 下一步可继续补真实催票发送记录 / 通知 Outbox，或补线下代录写票管理端入口。

### Phase 29: 业主代表 / 楼栋长催票权限事件驱动激活第一切片 — complete
- 现状判断：
  - 设计稿要求投票期事件驱动激活动员能力，而不是把催票/线下核销长期静态挂在角色上。
  - 现有责任田模型 `sys_user_building` 已能表达楼栋长/网格员负责楼栋，适合作为动态授权来源。
- 后端新增/调整：
  - 新增 `V3.15__voting_mobilization_permission.sql`，落 `t_voting_mobilization_permission`。
  - 新增 `VotingMobilizationPermission`、`VotingMobilizationPermissionRepository`、MyBatis mapper/XML/repository 实现。
  - 新增 `VotingMobilizationService`：
    - `activateForVotingOpened`：开票后按议题 scope 与 `sys_user_building` 生成 ACTIVE 权限；
    - `deactivateForSubject`：撤回/结算后失效；
    - `listMine`：当前 sys_user 查询自己在某议题下的生效权限。
  - `ProposalLifecycleService.openVoting` 已在 PUBLISHED -> VOTING 成功后激活权限。
  - `ProposalLifecycleService.cancel` 与 `VotingApplicationService.settle` 已在终态动作后失效权限。
  - `SubjectAdminController` 新增 `GET /voting-subjects/{subjectId}/mobilization-permissions/me`。
- 覆盖范围：
  - `OWNER_REPRESENTATIVE`：楼栋长/业主代表投票期催票与线下代录；
  - `GRID_OPERATOR`：现有 seed 与责任田模型中的网格内催票/线下核销。
- yaochi 同步：
  - `src/app/lib/voting.ts` 新增 `VotingMobilizationPermission` 与 `getMyMobilizationPermissions`。
- shennong-app：
  - 本轮无 C 端可见契约变化，补跑 type-check。
- 验证：
  - pangu 聚焦：`mvn -pl pangu-bootstrap -am -Dtest=ProposalLifecycleServiceTest,VotingMobilizationServiceTest,VotingEndpointMatrixTest -Dsurefire.failIfNoSpecifiedTests=false test`，53 tests，0 failures，0 errors。
  - yaochi：`npm run build` 通过，仍有既有 Vite chunk-size warning。
  - shennong-app：`npm run type-check` 通过。

### Next
- 真实催票发送记录 / 通知 Outbox。
- 线下代录写票管理端入口，写票时使用 `voteChannel=PAPER/OFFLINE_PROXY` 并校验动态 `canOfflineProxy`。

### Phase 30: 真实催票发送记录 / 通知 Outbox — complete
- 后端新增/调整：
  - 新增 `V3.16__voting_mobilization_reminder.sql`。
  - `t_outbox_event.event_type` 扩展为支持 `4 = VOTING_REMINDER_REQUESTED`。
  - 新增 `t_voting_mobilization_reminder`，记录一次楼栋级催票请求及对应 outbox event。
  - 新增 `VotingMobilizationReminder`、`VotingMobilizationReminderRepository`、`VotingReminderOutboxGateway`。
  - 新增 MyBatis row/mapper/XML/repository 与 `OutboxVotingReminderGateway`。
  - `VotingMobilizationService.sendReminder` 校验 SYS_USER、租户、VOTING 状态、目标楼栋 ACTIVE `canRemind` 动态权限后，统计未投户数、写 outbox、写催票记录。
  - `SubjectAdminController` 新增 `POST /api/v1/voting-subjects/{subjectId}/mobilization-reminders`。
- 三端同步：
  - yaochi `src/app/lib/voting.ts` 新增催票请求/响应类型和 `sendMobilizationReminder`。
  - shennong-app `src/lib/reminder.ts` 新增同名 API；保留逐户 `markNotified` mock，因其语义不同于本轮楼栋级 outbox 催票。
- 验证：
  - pangu 聚焦：`mvn -pl pangu-bootstrap -am -Dtest=VotingMobilizationServiceTest,VotingEndpointMatrixTest -Dsurefire.failIfNoSpecifiedTests=false test`，34 tests，0 failures，0 errors。
  - yaochi：`npm run build` 通过，仍有既有 Vite chunk-size warning；沙箱内首次因 `.vite-temp` 写权限 EPERM，授权重跑通过。
  - shennong-app：`npm run type-check` 通过。

### Next
- 线下代录写票管理端入口，写票时使用 `voteChannel=PAPER/OFFLINE_PROXY` 并校验动态 `canOfflineProxy`。
- 通知 Outbox 消费器：消费 `VOTING_REMINDER_REQUESTED`，对接短信/Push/站内信，并回写 outbox attempts/status/error。
- V3.16 已用于催票记录；后续迁移从 V3.17+ 开始。

### Phase 31: 线下代录写票管理端入口 — complete
- 后端新增/调整：
  - 新增 `OfflineProxyVoteCommand` 与 `OfflineProxyVoteRequest`。
  - `SubjectAdminController` 新增 `POST /api/v1/voting-subjects/{subjectId}/offline-proxy-votes`。
  - `VotingMobilizationService.castOfflineProxyVote`：
    - 校验 SYS_USER、租户、VOTING 状态；
    - 从 opid 反查 uid/building；
    - 校验该楼栋 ACTIVE `canOfflineProxy` 动态权限；
    - 调用统一写票服务，强制 `voteChannel=OFFLINE_PROXY`。
  - `VoteSubmissionService` 将 `MAJOR` L3 face-auth 闸门限定为 `ONLINE`，线下纸面/代录通道不复用 C 端人脸上下文。
- 三端同步：
  - yaochi `src/app/lib/voting.ts` 新增 `OfflineProxyVoteInput`、`VoteAcknowledgement`、`castOfflineProxyVote`。
  - shennong-app `src/lib/reminder.ts` 新增同名类型与 API，留给 worker 工作台后续接线。
- 验证：
  - pangu 聚焦：`mvn -pl pangu-bootstrap -am -Dtest=VoteSubmissionServiceTest,VotingMobilizationServiceTest,VotingEndpointMatrixTest -Dsurefire.failIfNoSpecifiedTests=false test`，52 tests，0 failures，0 errors。
  - yaochi：`npm run build` 通过，仍有既有 Vite chunk-size warning；沙箱内首次因 `.vite-temp` 写权限 EPERM，授权重跑通过。
  - shennong-app：`npm run type-check` 通过。

### Next
- 通知 Outbox 消费器：消费 `VOTING_REMINDER_REQUESTED`，对接短信/Push/站内信，并回写 outbox attempts/status/error。
- worker 真实待催票列表接口：替换 shennong-app `listReminderTasks/listPendingOwners/markNotified` 的 mock。
- yaochi / shennong-app 页面接线：把动态权限、催票、线下代录 API 接到真实操作面。

### Phase 32: 通知 Outbox 消费器 — complete
- 后端新增/调整：
  - 新增 `VotingReminderDeliveryCommand` 与 `VotingReminderDeliveryGateway`。
  - 新增 `VotingReminderOutboxRepository` 端口。
  - 新增 `VotingReminderOutboxConsumerService`，负责 payload 解析、调用投递网关、确认/失败回写。
  - `OutboxEventMapper` 扩展：
    - `claimReminderPending(limit)`：领取 `event_type=4` 的 PENDING/FAILED 事件，`attempts < 5`，并用 `FOR UPDATE SKIP LOCKED` 防并发重复领取；
    - `markConfirmed(eventId)`；
    - `markFailed(eventId, lastError)`。
  - 新增 `VotingReminderOutboxRepositoryImpl` 与 `MockVotingReminderDeliveryGateway`。
  - 新增 `VotingReminderOutboxScheduler`，默认每分钟第 15 秒消费一批。
  - `application.yml` 新增 `platform.voting.reminder-outbox-cron` 与 `platform.voting.reminder-outbox-batch-size`。
- 三仓同步：
  - 本轮没有新增 HTTP/API 契约；yaochi / shennong-app 不需要代码同步。
- 验证：
  - 消费器聚焦：`mvn -pl pangu-bootstrap -am -Dtest=VotingReminderOutboxConsumerServiceTest,VotingMobilizationServiceTest,VotingEndpointMatrixTest -Dsurefire.failIfNoSpecifiedTests=false test`，40 tests，0 failures，0 errors。
  - DB 集成：`mvn -pl pangu-bootstrap -am -Dtest=VotingReminderOutboxConsumerServiceTest,VotingReminderOutboxRepositoryIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`，4 tests，0 failures，0 errors。

### Next
- worker 真实待催票列表接口：替换 shennong-app `listReminderTasks/listPendingOwners/markNotified` 的 mock。
- yaochi / shennong-app 页面接线：把动态权限、催票、线下代录 API 接到真实操作面。
- 真实通知 provider：替换 `VotingReminderDeliveryGateway` 的 mock 实现，对接短信/Push/站内信。

### Phase 33: worker 真实待催票列表接口 — complete
- 后端新增/调整：
  - 新增 `V3.17__voting_mobilization_owner_notice.sql`。
  - 新增 `t_voting_mobilization_owner_notice`，记录逐户通知渠道、备注、通知人和通知时间，并按 `subject_id + uid + channel` 去重 upsert。
  - 新增 `ReminderChannel`、`ReminderTask`、`ReminderPendingOwner` 与 `VotingReminderTaskRepository`。
  - 新增 `VotingReminderTaskService`，统一校验 SYS_USER、租户、VOTING 状态和 ACTIVE `canRemind` 动态授权。
  - 新增 MyBatis row/mapper/XML/repository，实现任务列表、待通知业主列表和逐户通知记录写入。
  - 新增 `ReminderTaskController`：
    - `GET /api/v1/reminder/tasks`
    - `GET /api/v1/reminder/tasks/{subjectId}/pending`
    - `POST /api/v1/reminder/tasks/{subjectId}/notify`
- 行为边界：
  - 任务列表只统计当前用户动态授权楼栋内的业主投票情况。
  - 待通知列表只返回未投票业主，并透出已通知渠道、最近通知时间和备注。
  - 标记已通知只允许在业主未投票且用户对该业主楼栋仍有 `canRemind` 时写入。
- 三仓同步：
  - shennong-app 既有 `src/lib/reminder.ts` 的真实 API 路径与类型已经匹配新增后端接口；本轮仅需要更新“后端未完成/仅前端记录”的注释与页面提示。
  - 由于 shennong-app 不在 pangu 可写根内，注释补丁和 `npm run type-check` 授权执行均被审批层使用限额拒绝；未绕过执行。
  - yaochi 未引用 worker `reminder/tasks` API，本轮无代码同步点。
- 验证：
  - pangu 聚焦：`mvn -pl pangu-bootstrap -am -Dtest=VotingReminderTaskRepositoryIntegrationTest,VotingEndpointMatrixTest -Dsurefire.failIfNoSpecifiedTests=false test`，34 tests，0 failures，0 errors。

### Next
- shennong-app：更新 worker 催票页注释/提示文案，并在可授权环境补跑 `npm run type-check`。
- shennong-app：把 worker 页面从保留 mock 的展示，推进到 `USE_MOCK=false` 真实联调验证。
- yaochi：本阶段无需代码同步；后续可继续把动态权限、催票发送、线下代录 API 接入管理端真实操作面。
- 通知链路：替换 `VotingReminderDeliveryGateway` mock provider，对接短信/Push/站内信。

### Phase 34: 催票逐户投递明细 — complete
- 后端新增/调整：
  - 新增 `V3.18__voting_reminder_delivery.sql`。
  - 新增 `t_voting_reminder_delivery`，作为催票 outbox 展开的逐户投递明细表。
  - 新增 `VotingReminderDeliveryMapper` / XML。
  - 新增 `DatabaseVotingReminderDeliveryGateway`，默认 `platform.voting.reminder-delivery-mode=database` 时启用。
  - `MockVotingReminderDeliveryGateway` 改为仅 `platform.voting.reminder-delivery-mode=mock` 时启用。
  - `application.yml` 新增 `platform.voting.reminder-delivery-mode: database`。
- 行为边界：
  - outbox 消费后按 `subjectId / tenantId / buildingId` 展开当前仍未投票的正常业主房产。
  - 逐户明细默认生成 `SMS + READY` 记录，后续真实供应商从该表接续。
  - `UNIQUE(outbox_event_id, opid, channel)` 保证 outbox 重试幂等。
- 三仓同步：
  - 本轮没有新增 HTTP/API 契约；yaochi / shennong-app 不需要代码同步。
- 验证：
  - pangu outbox DB 集成：`mvn -pl pangu-bootstrap -am -Dtest=VotingReminderOutboxConsumerServiceTest,VotingReminderOutboxRepositoryIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`，5 tests，0 failures，0 errors。
  - pangu 聚焦回归：`mvn -pl pangu-bootstrap -am -Dtest=VotingReminderOutboxConsumerServiceTest,VotingReminderOutboxRepositoryIntegrationTest,VotingMobilizationServiceTest,VotingEndpointMatrixTest -Dsurefire.failIfNoSpecifiedTests=false test`，45 tests，0 failures，0 errors。

### Next
- shennong-app：更新 worker 催票页注释/提示文案，并在可授权环境补跑 `npm run type-check`。
- shennong-app：做 `USE_MOCK=false` 的 worker 真实联调验证。
- 通知链路：新增真实短信/Push/站内信 provider，消费 `t_voting_reminder_delivery` 的 READY 明细并回写投递状态。
- V3.18 已用于催票逐户投递明细；后续迁移从 V3.19+ 开始。

### Phase 35: 催票投递状态机与 mock provider — complete
- 后端新增/调整：
  - 新增 `V3.19__voting_reminder_delivery_dispatch.sql`。
  - `t_voting_reminder_delivery` 增加 attempts、last_attempt_at、submitted_at、confirmed_at、failed_at、provider_message_id、last_error。
  - 新增 `VotingReminderDeliveryItem`、`VotingReminderDeliveryReceipt`、`VotingReminderSmsProvider`。
  - 新增 `VotingReminderDeliveryRepository`、MyBatis mapper/XML claim/confirm/fail、repository 实现。
  - 新增 `VotingReminderDeliveryDispatchService`。
  - 新增 `VotingReminderDeliveryScheduler`，默认每分钟第 30 秒调度。
  - 新增 `MockVotingReminderSmsProvider`，成功返回 `mock-sms-{deliveryId}`。
  - `application.yml` 新增 `platform.voting.reminder-delivery-cron` 与 `reminder-delivery-batch-size`。
- 行为边界：
  - 领取 `delivery_status IN (READY=1, FAILED=4)` 且 `attempts < 5` 的明细；
  - 领取时写 `SUBMITTED=2`、`attempts+1`、`submitted_at/last_attempt_at`；
  - provider 成功后写 `CONFIRMED=3 / confirmed_at / provider_message_id`；
  - provider 失败后写 `FAILED=4 / failed_at / last_error`，后续 tick 可重试。
- 三仓同步：
  - 本轮没有新增 HTTP/API 契约；yaochi / shennong-app 不需要代码同步。
- 验证：
  - pangu 投递状态机切片：`mvn -pl pangu-bootstrap -am -Dtest=VotingReminderDeliveryDispatchServiceTest,VotingReminderOutboxRepositoryIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`，6 tests，0 failures，0 errors。
  - pangu 聚焦回归：`mvn -pl pangu-bootstrap -am -Dtest=VotingReminderDeliveryDispatchServiceTest,VotingReminderOutboxConsumerServiceTest,VotingReminderOutboxRepositoryIntegrationTest,VotingMobilizationServiceTest,VotingEndpointMatrixTest -Dsurefire.failIfNoSpecifiedTests=false test`，48 tests，0 failures，0 errors。

### Next
- shennong-app：更新 worker 催票页注释/提示文案，并在可授权环境补跑 `npm run type-check`。
- shennong-app：做 `USE_MOCK=false` 的 worker 真实联调验证。
- 通知链路：替换 `VotingReminderSmsProvider` mock 实现为真实短信供应商适配器，保留现有投递状态机。
- V3.19 已用于催票投递状态机；当前 V3.23 已用于 Waiver 双签角色分离，后续迁移从 V3.24+ 开始。

### Phase 36: 催票投递明细查询接口 — complete
- 后端新增/调整：
  - 新增 `VotingReminderDeliveryStatus` 领域视图。
  - 新增 `VotingReminderDeliveryQueryRepository` 与 `VotingReminderDeliveryQueryService`。
  - `VotingReminderDeliveryMapper` / XML 新增 `listBySubject` 查询，支持按议题、楼栋和投递状态过滤。
  - 新增 `VotingReminderDeliveryQueryRepositoryImpl`。
  - 新增 `VotingReminderDeliveryStatusResponse`。
  - `SubjectAdminController` 新增 `GET /api/v1/voting-subjects/{subjectId}/reminder-deliveries`。
- 行为边界：
  - 只读查询，不新增投递状态写入口。
  - 粗权限沿用 `voting:subject:audit`；应用层校验 subject 存在且属于当前 tenant。
  - 响应手机号已脱敏，返回状态、attempts、provider message id、last error 与各阶段时间戳，供管理端展示和排障。
  - `limit` 限制在 `1..500`，默认 100。
- 三仓同步：
  - 本轮已新增 pangu 后端管理端接口；yaochi 下一步需要新增 API wrapper 和页面展示。
  - shennong-app 不消费该管理端查询接口，本轮无 C 端代码同步点。
- 验证：
  - 查询接口切片：`mvn -pl pangu-bootstrap -am -Dtest=VotingReminderOutboxRepositoryIntegrationTest,VotingEndpointMatrixTest -Dsurefire.failIfNoSpecifiedTests=false test`，39 tests，0 failures，0 errors。
  - 通知链路聚焦回归：`mvn -pl pangu-bootstrap -am -Dtest=VotingReminderDeliveryDispatchServiceTest,VotingReminderOutboxConsumerServiceTest,VotingReminderOutboxRepositoryIntegrationTest,VotingMobilizationServiceTest,VotingEndpointMatrixTest -Dsurefire.failIfNoSpecifiedTests=false test`，50 tests，0 failures，0 errors。

### Next
- yaochi：接入 `GET /api/v1/voting-subjects/{subjectId}/reminder-deliveries`，在议题详情或催票记录页展示逐户投递状态。
- shennong-app：继续保留 worker 真实催票接口联调任务；该管理端投递查询接口不需要 C 端同步。
- 通知链路：替换 `VotingReminderSmsProvider` mock 实现为真实短信供应商适配器，保留现有状态机和明细查询能力。
- V3.19 已用于催票投递状态机；当前 V3.23 已用于 Waiver 双签角色分离，后续迁移从 V3.24+ 开始。

### Phase 37: yaochi 表决看板展示催票投递明细 — complete
- 管理端新增/调整：
  - `src/app/lib/voting.ts` 新增 `VotingReminderDeliveryStatus`、`ReminderDeliveryStatusCode`、`ListReminderDeliveriesParams` 与 `listReminderDeliveries`。
  - `src/app/components/pages/Voting.tsx` 新增“催票投递明细”表，跟随当前选中议题加载最近 50 条投递记录。
  - 表格展示业主 UID/OPID、楼栋、脱敏手机号、渠道、投递状态、attempts、供应商回执、更新时间与失败原因。
  - `Voting.tsx` 补齐 `PENDING_COMMITTEE / PENDING_STREET` 的状态 label 与 step 映射，避免 ELECTION 双签中状态进入表决看板时显示缺口。
- 行为边界：
  - 本轮只做管理端只读展示，不新增投递状态写入口。
  - 暂不做楼栋 / 状态筛选控件；API wrapper 已支持参数，后续可接 UI。
  - shennong-app 不消费该管理端查询接口，本轮无需同步。
- 验证：
  - yaochi：`npm run build` 通过；仍有既有 Vite chunk-size warning。

### Next
- 通知链路：替换 `VotingReminderSmsProvider` mock 实现为真实短信供应商适配器，保留现有状态机和明细查询能力。
- yaochi：投递明细查询、筛选与单条详情查看均已完成。
- shennong-app：继续 worker 真实催票接口 `USE_MOCK=false` 联调。
- V3.19 已用于催票投递状态机；当前 V3.23 已用于 Waiver 双签角色分离，后续迁移从 V3.24+ 开始。

### Phase 38: 可配置 HTTP 短信 provider 适配器 — complete
- 后端新增/调整：
  - 新增 `HttpVotingReminderSmsProvider`，通过 JDK `HttpClient` 调用外部短信网关。
  - `MockVotingReminderSmsProvider` 增加 `@ConditionalOnProperty`，默认 `platform.voting.sms-provider-mode=mock` 时启用。
  - `application.yml` 新增：
    - `platform.voting.sms-provider-mode: mock`
    - `platform.voting.sms-provider.endpoint`
    - `platform.voting.sms-provider.bearer-token`
    - `platform.voting.sms-provider.timeout-millis: 3000`
- 行为边界：
  - 默认仍使用 mock provider，不改变本地开发和既有测试行为。
  - 设置 `sms-provider-mode=http` 后，provider 会 POST 投递明细 JSON 到配置 endpoint。
  - 2xx 响应中读取 `providerMessageId / messageId / bizId / requestId` 作为回执 ID。
  - 非 2xx、网络异常、回执缺失都会抛异常，由现有投递状态机回写 FAILED 并等待重试。
- 三仓同步：
  - 本轮没有新增管理端或 C 端 HTTP API；yaochi / shennong-app 不需要改代码。
  - yaochi 已有投递明细展示，可观察 HTTP provider 的回执 ID 与失败原因。
- 验证：
  - HTTP provider 切片：`mvn -pl pangu-bootstrap -am -Dtest=HttpVotingReminderSmsProviderTest,VotingReminderDeliveryDispatchServiceTest,VotingReminderOutboxRepositoryIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`，8 tests，0 failures，0 errors。
  - 通知链路聚焦回归：`mvn -pl pangu-bootstrap -am -Dtest=HttpVotingReminderSmsProviderTest,VotingReminderDeliveryDispatchServiceTest,VotingReminderOutboxConsumerServiceTest,VotingReminderOutboxRepositoryIntegrationTest,VotingMobilizationServiceTest,VotingEndpointMatrixTest -Dsurefire.failIfNoSpecifiedTests=false test`，52 tests，0 failures，0 errors。

### Next
- 根据具体短信供应商补充签名算法、模板参数映射或回执字段映射。
- yaochi：投递明细查询、筛选与单条详情查看均已完成。
- shennong-app：继续 worker 真实催票接口 `USE_MOCK=false` 联调。
- V3.19 已用于催票投递状态机；当前 V3.23 已用于 Waiver 双签角色分离，后续迁移从 V3.24+ 开始。

### Phase 39: shennong-app worker 催票真实分支说明与类型检查 — complete
- shennong-app 新增/调整：
  - `src/lib/reminder.ts` 顶部说明已从“后端未完成 / 本期完全 mock”改为 `USE_MOCK=true` 前端 stub、`USE_MOCK=false` 走 pangu 后端真实接口。
  - `src/pages/worker/reminder-list/index.tsx` 顶部说明和底部提示已改为 mock 本地演示 / 真实逐户通知记录双分支。
  - `README.md` 已把 PR-E 移入“已接入真实后端能力”，并列明三条真实接口：`GET /reminder/tasks`、`GET /reminder/tasks/{subjectId}/pending`、`POST /reminder/tasks/{subjectId}/notify`。
- pangu 文档同步：
  - `docs/选举闭环对齐路线图.md` 已补充 shennong-app worker 催票真实分支说明完成状态。
  - `task_plan.md` / `findings.md` / `progress.md` 已更新当前进展与下一步任务。
- 验证：
  - shennong-app：`npm run type-check` 通过。

### Next
- shennong-app：做 `USE_MOCK=false` 真实后端联调，覆盖登录态、任务列表、待通知业主和逐户通知写入。
- 通知链路：根据具体短信供应商补充签名算法、模板参数映射或回执字段映射。
- yaochi：投递明细筛选和单条详情查看均已完成。
- V3.19 已用于催票投递状态机；当前 V3.23 已用于 Waiver 双签角色分离，后续迁移从 V3.24+ 开始。

### Phase 41: yaochi 催票投递记录详情查看 — complete
- 管理端新增/调整：
  - `src/app/components/pages/Voting.tsx` 的催票投递明细表新增“查看”动作。
  - 新增投递详情 Dialog，展示 delivery id、议题、楼栋、UID/OPID、脱敏手机号、渠道、状态、尝试次数、outbox id、供应商回执。
  - 详情中展示创建、最近尝试、提交、确认、失败时间，以及 message template 和完整失败原因。
- 行为边界：
  - 本轮不新增后端接口；详情字段来自现有 `listReminderDeliveries` 响应。
  - 该能力面向管理端排障，shennong-app 不需要同步。
- 验证：
  - yaochi：`npm run build` 通过；仍有既有 Vite chunk-size warning。

### Next
- shennong-app：做 `USE_MOCK=false` 真实后端联调，覆盖登录态、任务列表、待通知业主和逐户通知写入。
- 通知链路：根据具体短信供应商补充签名算法、模板参数映射或回执字段映射。
- yaochi：投递明细查询、筛选与单条详情查看均已完成，后续暂无阻塞闭环的管理端缺口。
- V3.19 已用于催票投递状态机；当前 V3.23 已用于 Waiver 双签角色分离，后续迁移从 V3.24+ 开始。

### Phase 40: yaochi 催票投递明细楼栋 / 状态筛选 — complete
- 管理端新增/调整：
  - `src/app/components/pages/Voting.tsx` 的“催票投递明细”增加楼栋 ID 输入框、查询按钮、状态下拉和重置按钮。
  - 楼栋筛选点击查询或回车后生效；状态筛选立即生效。
  - 查询参数复用既有 `listReminderDeliveries(subjectId, { buildingId, status, limit })`。
  - 重置后恢复最近 50 条投递明细。
- 行为边界：
  - 本轮不新增后端接口，也不改变投递状态写入逻辑。
  - shennong-app 不消费管理端投递明细查询，本轮无需 C 端同步。
- 验证：
  - yaochi：`npm run build` 通过；仍有既有 Vite chunk-size warning。

### Next
- shennong-app：做 `USE_MOCK=false` 真实后端联调，覆盖登录态、任务列表、待通知业主和逐户通知写入。
- 通知链路：根据具体短信供应商补充签名算法、模板参数映射或回执字段映射。
- yaochi：投递明细查询、筛选与单条详情查看均已完成。
- V3.19 已用于催票投递状态机；当前 V3.23 已用于 Waiver 双签角色分离，后续迁移从 V3.24+ 开始。

### Phase 42: HTTP 短信 provider 通用签名 / 模板参数 / 回执字段映射 — complete
- 后端新增/调整：
  - `HttpVotingReminderSmsProvider` payload 新增 `templateCode` 与 `templateParams`。
  - `templateCode` 可通过 `platform.voting.sms-provider.template-code` 配置；为空时沿用原 `messageTemplate`。
  - `templateParams` 默认包含 `subjectId / tenantId / buildingId / opid / uid / message`。
  - 新增 `platform.voting.sms-provider.provider-message-id-fields`，支持逗号分隔字段和 `data.smsId` 这类点路径。
  - 新增 HMAC-SHA256 签名配置：`signature-secret / signature-header / signature-timestamp-header`。
  - 未配置 `signature-secret` 时不发送签名头，保持默认 HTTP provider 行为兼容。
- 测试覆盖：
  - 默认 bearer + 顶层 `providerMessageId` 兼容；
  - 嵌套回执字段 `data.smsId`；
  - 自定义模板 code；
  - HMAC 签名头；
  - 非 2xx 响应仍抛错，交由既有投递状态机回写 FAILED。
- 验证：
  - provider 切片：`mvn -pl pangu-bootstrap -am -Dtest=HttpVotingReminderSmsProviderTest,VotingReminderDeliveryDispatchServiceTest,VotingReminderOutboxRepositoryIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`，10 tests，0 failures，0 errors。
  - 通知链路聚焦回归：`mvn -pl pangu-bootstrap -am -Dtest=HttpVotingReminderSmsProviderTest,VotingReminderDeliveryDispatchServiceTest,VotingReminderOutboxConsumerServiceTest,VotingReminderOutboxRepositoryIntegrationTest,VotingMobilizationServiceTest,VotingEndpointMatrixTest -Dsurefire.failIfNoSpecifiedTests=false test`，54 tests，0 failures，0 errors。

### Next
- shennong-app：做 `USE_MOCK=false` 真实后端联调，覆盖登录态、任务列表、待通知业主和逐户通知写入。
- 通知链路：拿到具体短信供应商参数后，配置 endpoint、template-code、provider-message-id-fields、签名 header/secret 并做联调。
- yaochi：投递明细查询、筛选与单条详情查看均已完成，后续暂无阻塞闭环的管理端缺口。
- V3.19 已用于催票投递状态机；当前 V3.23 已用于 Waiver 双签角色分离，后续迁移从 V3.24+ 开始。

### Phase 43: shennong-app worker 催票真实接口 smoke 验证入口 — complete
- C 端新增/调整：
  - `scripts/smoke-reminder-real.mjs`：新增 worker 催票真实接口 smoke。
  - `package.json`：新增 `npm run smoke:reminder`。
  - `README.md`：新增 worker 催票真实接口 smoke 使用说明。
- smoke 行为：
  - 通过 `PANGU_TOKEN` 注入工作端 JWT；
  - 默认使用 `http://127.0.0.1:8080/pangu/api/v1`，可通过 `PANGU_API_BASE_URL` 覆盖；
  - 默认只读验证 `GET /reminder/tasks` 与 `GET /reminder/tasks/{subjectId}/pending`；
  - 仅在设置 `PANGU_REMINDER_NOTIFY_UID` 时执行 `POST /reminder/tasks/{subjectId}/notify`；
  - 写入参数支持 `PANGU_REMINDER_SUBJECT_ID / PANGU_REMINDER_NOTIFY_CHANNEL / PANGU_REMINDER_NOTIFY_NOTE`。
- 验证：
  - shennong-app：`npm run smoke:reminder -- --help` 通过。
  - shennong-app：`npm run type-check` 通过。
- 边界：
  - 本轮没有真实工作端 token，因此未执行 live smoke；脚本已经把真实联调步骤固定下来。

### Next
- shennong-app：启动 pangu 后端并用工作端 SYS_USER token 跑 `PANGU_TOKEN='<jwt>' npm run smoke:reminder`。
- shennong-app：如只读 smoke 通过，再指定 `PANGU_REMINDER_NOTIFY_UID` 验证逐户通知写入。
- 通知链路：拿到具体短信供应商参数后，配置 endpoint、template-code、provider-message-id-fields、签名 header/secret 并做联调。
- V3.19 已用于催票投递状态机；当前 V3.23 已用于 Waiver 双签角色分离，后续迁移从 V3.24+ 开始。

### Phase 48: shennong-app worker 催票只读 live smoke — complete
- 后端环境：
  - 本地 PostgreSQL / Redis 容器已运行。
  - 8080 端口被 2026-06-28 由 IntelliJ 启动的旧 `PanguApplication` JVM 占用，直接 smoke 时 `GET /reminder/tasks` 返回 500。
  - 为避免影响用户调试进程，本轮在 18080 启动当前代码后端：Flyway 当前 schema 版本为 3.19，Tomcat 正常启动。
- 登录与身份：
  - 使用种子账号 `13800000004 / 123456` 登录 `http://127.0.0.1:18080/pangu/api/v1/auth/login` 成功。
  - 返回身份为 `SYS_USER / GRID_OPERATOR / active_identity_id=800004 / tenant_id=10001`，权限包含 `voting:subject:audit`。
- smoke 结果：
  - 执行 `PANGU_API_BASE_URL=http://127.0.0.1:18080/pangu/api/v1 PANGU_TOKEN=<jwt> npm run smoke:reminder`。
  - `GET /reminder/tasks` 返回 200，脚本校验响应数组结构通过。
  - 当前本地库返回 `count=0`，因此 smoke 按设计跳过 `GET /reminder/tasks/{subjectId}/pending` 与 `POST /notify`。
- 当前结论：
  - shennong-app 真实接口 smoke 已证明登录态、鉴权、任务列表契约和脚本入口可用。
  - 还缺一条活跃 `VOTING` 议题 + ACTIVE `canRemind` 楼栋权限 + 未投业主数据，才能继续验证 pending owners 与逐户通知写入。

### Next
- 准备或选择一条可催票的本地/联调数据：活跃 `VOTING` 议题、`800004` 或楼栋长对应 ACTIVE `canRemind` 权限、楼栋内未投业主。
- 用同一 smoke 指定 `PANGU_REMINDER_SUBJECT_ID` 验证 pending owners。
- pending 有数据后，再显式指定 `PANGU_REMINDER_NOTIFY_UID` 验证逐户通知写入。
- 真实短信供应商参数到位后，配置 HTTP provider 并做短信网关联调。

### Phase 49: shennong-app worker 催票 pending/notify live smoke — complete
- pangu 新增本地联调脚本：
  - `scripts/prepare-reminder-smoke.sql`：准备固定催票联调数据。
  - `scripts/cleanup-reminder-smoke.sql`：清理固定联调数据。
- fixture 内容：
  - `subject_id=990480`；
  - 议题标题 `CODEx smoke 催票联调议题`；
  - `tenant_id=10001`、`subject_type=ELECTION`、`status=VOTING`、`scope=BUILDING 30001`；
  - worker `sys_user=800004 / GRID_OPERATOR`；
  - pending owner `uid=70001 / opid=1 / room=30001101`；
  - ACTIVE `canRemind/canOfflineProxy` 动员权限。
- 执行数据准备：
  - `docker exec -i pangu-postgres psql -U postgres -d pangu_db < scripts/prepare-reminder-smoke.sql` 成功。
  - 输出确认 fixture ready，`subject_id=990480`、`user_id=800004`、`uid=70001`、`opid=1`。
- live smoke：
  - 当前代码后端在 `18080` 启动成功，Flyway schema 版本 `3.19`。
  - `13800000004 / 123456` 登录成功，token 身份仍为 `SYS_USER / GRID_OPERATOR / 800004`。
  - shennong-app 执行：
    `PANGU_API_BASE_URL=http://127.0.0.1:18080/pangu/api/v1 PANGU_TOKEN=<jwt> PANGU_REMINDER_SUBJECT_ID=990480 PANGU_REMINDER_NOTIFY_UID=70001 PANGU_REMINDER_NOTIFY_CHANNEL=PHONE PANGU_REMINDER_NOTIFY_NOTE='codex smoke notify' npm run smoke:reminder`
  - 输出：
    - `GET /reminder/tasks ok, count=1`
    - `GET /reminder/tasks/990480/pending ok, count=1`
    - `POST /reminder/tasks/990480/notify ok, uid=70001, channel=PHONE`
- DB 验证：
  - `t_voting_mobilization_owner_notice` 已出现 `subject_id=990480 / uid=70001 / notified_by_user_id=800004 / channel=PHONE / note='codex smoke notify'`。
- shennong-app 文档同步：
  - README 的 worker 催票 smoke 段已补充 pangu 本地 fixture 准备命令；
  - 写入 smoke 示例已改为 `PANGU_REMINDER_SUBJECT_ID=990480 / PANGU_REMINDER_NOTIFY_UID=70001`。

### Next
- 真实短信供应商参数到位后，配置 HTTP provider 并做短信网关联调。
- 如需要复验 worker 催票真实接口，先运行 `scripts/prepare-reminder-smoke.sql`，验证完成后按需运行 `scripts/cleanup-reminder-smoke.sql`。
- yaochi 当前闭环无新增阻塞点；后续主要是供应商回执联调后的投递明细观测。

### Phase 50: HTTP provider 本地 smoke harness — complete
- pangu 新增本地 HTTP provider 联调工具：
  - `scripts/fake-sms-provider.mjs`：本地假短信网关，监听 `POST /sms`，可校验 bearer token 与 HMAC-SHA256 签名，并返回嵌套回执 `data.smsId=fake-sms-{deliveryId}`。
  - `scripts/prepare-http-sms-provider-smoke.sql`：准备固定 READY delivery。
  - `scripts/cleanup-http-sms-provider-smoke.sql`：清理固定 HTTP provider fixture。
- fixture 内容：
  - `subject_id=990481`；
  - `delivery_id=990481`；
  - `outbox_event_id=990481`，`event_type=4` 且置为 CONFIRMED，避免 outbox consumer 重复展开；
  - READY delivery 指向 `uid=70001 / opid=1 / phone=13800000012 / message_template=VOTE_REMINDER`。
- 已执行验证：
  - `docker exec -i pangu-postgres psql -U postgres -d pangu_db < scripts/prepare-http-sms-provider-smoke.sql` 成功，输出 `ready / delivery_id=990481 / delivery_status=1 / attempts=0`。
  - `FAKE_SMS_PORT=19090 FAKE_SMS_BEARER=local-token FAKE_SMS_SIGNATURE_SECRET=local-secret ... node scripts/fake-sms-provider.mjs` 启动成功，随后已停止。
  - `node --check scripts/fake-sms-provider.mjs` 通过。
  - `lsof -nP -iTCP:19090 -sTCP:LISTEN` 确认假短信服务无残留监听。
  - `docker exec -i pangu-postgres psql -U postgres -d pangu_db < scripts/cleanup-http-sms-provider-smoke.sql` 成功清理 `990481` fixture。
- 当时未完成的 live 验证：
  - 需要启动 pangu 并配置：
    `--platform.voting.sms-provider-mode=http`
    `--platform.voting.sms-provider.endpoint=http://127.0.0.1:19090/sms`
    `--platform.voting.sms-provider.bearer-token=local-token`
    `--platform.voting.sms-provider.template-code=TPL_VOTE_REMINDER`
    `--platform.voting.sms-provider.provider-message-id-fields=data.smsId`
    `--platform.voting.sms-provider.signature-secret=local-secret`
  - 当时尝试启动当前后端到 `18080` 时，提权审批因 Codex 使用限额被拒，未执行调度器 live smoke。
  - 该缺口已在 Phase 53 补跑通过：`READY -> CONFIRMED / provider_message_id=fake-sms-990481`。

### Next
- HTTP provider live smoke 已在 Phase 53 补跑通过；如需复验，执行 `CLEANUP_FIXTURE=true scripts/smoke-http-sms-provider.sh`。
- 供应商参数到位后，用同一流程替换 endpoint/token/secret/template-code/provider-message-id-fields 做真实联调。

### Phase 51: HTTP provider dispatch 组合回归测试 — complete
- 发现的覆盖缺口：
  - `HttpVotingReminderSmsProviderTest` 已覆盖 provider payload、bearer、HMAC、模板 code、嵌套回执字段与非 2xx；
  - `VotingReminderDeliveryDispatchServiceTest` 已覆盖 dispatch 成功/失败回写；
  - 但两者此前是分离测试，没有直接证明 `dispatchPending -> HttpVotingReminderSmsProvider -> markConfirmed` 组合链路。
- 本轮新增测试：
  - 在 `VotingReminderDeliveryDispatchServiceTest` 新增 `dispatchPending_withHttpProvider_postsSignedPayloadAndMarksConfirmed`。
  - 测试启动 JVM 内 `HttpServer`，让 dispatch service 使用真实 `HttpVotingReminderSmsProvider`。
  - 覆盖：
    - `Authorization: Bearer local-token`；
    - `X-Pangu-Timestamp / X-Pangu-Signature`；
    - `templateCode=TPL_VOTE_REMINDER`；
    - `templateParams.subjectId/message`；
    - 响应 `data.smsId=fake-sms-1`；
    - `deliveryRepository.markConfirmed(1L, "fake-sms-1")`。
- 验证：
  - `mvn -pl pangu-bootstrap -am -Dtest=VotingReminderDeliveryDispatchServiceTest,HttpVotingReminderSmsProviderTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - 7 tests，0 failures，0 errors。
- 边界：
  - 该测试是 JVM 内 HTTP server + mocked repository 的组合回归，不等同于真实 Spring scheduler live smoke。
  - 真实 Spring scheduler live smoke 已在 Phase 53 补跑 `READY -> CONFIRMED` 通过。

### Next
- 本地 live 调度 smoke 已完成；如需复验，执行 `CLEANUP_FIXTURE=true scripts/smoke-http-sms-provider.sh`。
- 真实供应商参数到位后，复用同一 harness 做实际网关联调。

### Phase 52: HTTP provider live smoke 编排脚本 — complete
- pangu 新增：
  - `scripts/smoke-http-sms-provider.sh`
- 脚本职责：
  - 准备 `990481` HTTP provider READY delivery fixture；
  - 启动 `scripts/fake-sms-provider.mjs`；
  - 通过 `SPRING_APPLICATION_JSON` 启动 pangu，配置 HTTP SMS provider：
    - `sms-provider-mode=http`
    - endpoint 指向 fake provider；
    - bearer token；
    - `template-code=TPL_VOTE_REMINDER`；
    - `provider-message-id-fields=data.smsId`；
    - HMAC `signature-secret`；
    - `reminder-delivery-cron=*/5 * * * * *`；
  - 轮询 DB，等待 `delivery_id=990481` 变为 `delivery_status=3 / provider_message_id=fake-sms-990481`；
  - 自动停止脚本启动的 fake provider 与 pangu 进程；
  - 默认保留 fixture 便于复查，`CLEANUP_FIXTURE=true` 时退出时清理。
- 静态验证：
  - `bash -n scripts/smoke-http-sms-provider.sh` 通过。
  - `node --check scripts/fake-sms-provider.mjs` 通过。
  - `lsof -nP -iTCP:18080 -sTCP:LISTEN; lsof -nP -iTCP:19090 -sTCP:LISTEN` 无残留监听。
- live 验证：
  - 该脚本已在 Phase 53 执行通过；
  - pangu HTTP provider 模式启动成功，scheduler 将 `delivery_id=990481` 回写为 `CONFIRMED`；
  - 供应商回执 `provider_message_id=fake-sms-990481` 已落库；
  - fixture 已清理，`18080/19090` 无残留监听。

### Next
- 需要复验时运行：`CLEANUP_FIXTURE=true scripts/smoke-http-sms-provider.sh`。
- 真实供应商参数到位后，替换脚本中的 endpoint/token/secret/template-code/provider-message-id-fields 做联调。

### Phase 53: HTTP provider scheduler live smoke — complete
- 首次执行 `CLEANUP_FIXTURE=true scripts/smoke-http-sms-provider.sh` 时暴露 Spring 装配问题：
  - `HttpVotingReminderSmsProvider` 同时有生产构造器和测试构造器；
  - Spring 未能选择生产构造器，报 `No default constructor found`。
- 修复：
  - 在 `HttpVotingReminderSmsProvider` 生产构造器上补 `@Autowired`，让 Spring 明确选择配置构造器；
  - `scripts/smoke-http-sms-provider.sh` 在成功时按 `CLEANUP_FIXTURE` 输出自动清理或手工清理提示。
- 定向测试复核：
  - `HttpVotingReminderSmsProviderTest`：4 tests，0 failures，0 errors。
  - `VotingReminderDeliveryDispatchServiceTest`：3 tests，0 failures，0 errors。
- live smoke：
  - 执行 `CLEANUP_FIXTURE=true scripts/smoke-http-sms-provider.sh` 通过；
  - 脚本安装当前 reactor artifacts，启动 fake SMS provider `19090`，启动 pangu HTTP provider 模式 `18080`；
  - 调度器将 `delivery_id=990481` 回写为 `delivery_status=3 / provider_message_id=fake-sms-990481 / attempts=1`。
- 收尾验证：
  - `t_voting_reminder_delivery` 中 `delivery_id=990481` 已清理为空；
  - `18080 / 19090` 无残留监听。

### Next
- 真实供应商参数到位后，复用 `scripts/smoke-http-sms-provider.sh` 的配置形态替换 endpoint、token、secret、template-code、provider-message-id-fields 做实际网关联调。
- yaochi 已可通过投递明细查看 provider message id 与失败原因，真实供应商联调时作为观测入口。

### Phase 54: HTTP provider 真实供应商参数化联调入口 — complete
- 目标：在真实短信供应商参数尚未到位时，先把本地 live smoke 脚本改成可直接复用的真实供应商联调入口，避免参数到位后还要改代码或复制脚本。
- `scripts/smoke-http-sms-provider.sh` 已新增参数：
  - `START_FAKE_SMS`：默认 `true`；设为 `false` 时不启动本地 fake provider，直接使用外部 endpoint；
  - `SMS_PROVIDER_ENDPOINT`；
  - `SMS_PROVIDER_BEARER_TOKEN`；
  - `SMS_PROVIDER_TEMPLATE_CODE`；
  - `SMS_PROVIDER_MESSAGE_ID_FIELDS`；
  - `SMS_PROVIDER_SIGNATURE_SECRET`；
  - `SMS_PROVIDER_SIGNATURE_HEADER`；
  - `SMS_PROVIDER_SIGNATURE_TIMESTAMP_HEADER`；
  - `EXPECTED_PROVIDER_MESSAGE_ID`：默认 `fake-sms-990481`；置空时只校验 CONFIRMED 且回执非空。
- 默认行为保持不变：
  - 不传任何参数时仍启动本地 fake SMS provider；
  - 仍验证 `provider_message_id=fake-sms-990481`；
  - 仍可通过 `CLEANUP_FIXTURE=true` 自动清理 fixture。
- 验证：
  - `bash -n scripts/smoke-http-sms-provider.sh` 通过；
  - `node --check scripts/fake-sms-provider.mjs` 通过；
  - 参数化后默认路径复跑 `CLEANUP_FIXTURE=true scripts/smoke-http-sms-provider.sh` 通过，DB 回写 `3,fake-sms-990481,1`；
  - 复核 `990481` fixture 已清理，`18080 / 19090` 无残留监听。
- 真实供应商联调命令形态：
  `START_FAKE_SMS=false SMS_PROVIDER_ENDPOINT=... SMS_PROVIDER_BEARER_TOKEN=... SMS_PROVIDER_SIGNATURE_SECRET=... SMS_PROVIDER_TEMPLATE_CODE=... SMS_PROVIDER_MESSAGE_ID_FIELDS=... EXPECTED_PROVIDER_MESSAGE_ID=... CLEANUP_FIXTURE=true scripts/smoke-http-sms-provider.sh`

### Next
- 等真实供应商参数到位后，执行 Phase 54 命令形态做测试环境联调。
- 如供应商回执 ID 不可预知，设置 `EXPECTED_PROVIDER_MESSAGE_ID=`，用 DB CONFIRMED + 非空 provider_message_id + yaochi 投递明细作为验收证据。

### Phase 55: HTTP provider 联调前配置校验与自定义签名 header smoke — complete
- 目标：继续降低真实供应商联调风险，让脚本在启动服务和写 DB 前先发现明显配置错误，并验证自定义签名 header 能贯穿 Java provider 与 fake provider。
- `scripts/smoke-http-sms-provider.sh` 新增：
  - `preflight`：校验 `START_FAKE_SMS / CLEANUP_FIXTURE` 必须是 `true|false`，校验 `SMS_PROVIDER_ENDPOINT` 非空；
  - 脱敏配置摘要：输出 endpoint、template code、message id fields、签名 header 等，bearer token / signature secret 只显示掩码；
  - fake provider 启动时同步传入 `FAKE_SMS_SIGNATURE_HEADER` 与 `FAKE_SMS_TIMESTAMP_HEADER`，与 Java provider 的 `SMS_PROVIDER_SIGNATURE_HEADER / SMS_PROVIDER_SIGNATURE_TIMESTAMP_HEADER` 保持一致。
- 负例验证：
  - `START_FAKE_SMS=maybe scripts/smoke-http-sms-provider.sh` 直接退出，提示 `START_FAKE_SMS must be true or false`；
  - `CLEANUP_FIXTURE=maybe scripts/smoke-http-sms-provider.sh` 直接退出，提示 `CLEANUP_FIXTURE must be true or false`；
  - 两个负例均未进入 DB fixture 准备或服务启动。
- 自定义 header live smoke：
  - 执行 `CLEANUP_FIXTURE=true SMS_PROVIDER_SIGNATURE_HEADER=X-Test-Signature SMS_PROVIDER_SIGNATURE_TIMESTAMP_HEADER=X-Test-Timestamp scripts/smoke-http-sms-provider.sh` 通过；
  - DB 回写仍为 `delivery_status=3 / provider_message_id=fake-sms-990481 / attempts=1`；
  - fake provider 日志确认收到 `x-test-signature` 与 `x-test-timestamp`；
  - fixture 已清理，`18080 / 19090` 无残留监听。

### Next
- 真实供应商参数到位后，优先用 Phase 55 的配置摘要核对 endpoint/token/template/signature header/回执字段，再执行联调 smoke。
- 若供应商要求非 HMAC 或特殊 payload 结构，再基于现有 HTTP provider 小范围扩展 adapter。

### Phase 56: HTTP provider smoke 脚本自说明 help — complete
- 目标：把真实供应商联调命令和环境变量说明固化到脚本自身，避免参数到位后只能翻路线图或历史进展记录。
- `scripts/smoke-http-sms-provider.sh` 新增：
  - `--help / -h`；
  - 默认本地 smoke 示例；
  - `START_FAKE_SMS=false` 外部供应商联调示例；
  - `PANGU_PORT / FAKE_SMS_PORT / SMS_PROVIDER_* / EXPECTED_PROVIDER_MESSAGE_ID / CLEANUP_FIXTURE / DB_*` 环境变量说明；
  - 未知参数直接退出并打印 usage。
- 验证：
  - `scripts/smoke-http-sms-provider.sh --help` 返回 0，只打印 usage；
  - `scripts/smoke-http-sms-provider.sh --bad-arg` 返回 2，打印 unknown argument 与 usage；
  - `bash -n scripts/smoke-http-sms-provider.sh` 通过。

### Next
- 等真实供应商参数到位后，先运行 `scripts/smoke-http-sms-provider.sh --help` 核对参数名，再执行外部供应商 smoke。
- 当前不再有可本地补齐的 provider 联调脚手架缺口；下一步需要真实供应商 endpoint/token/template/signature/回执字段。

### Phase 57: HTTP provider dry-run 与外部供应商默认值语义修正 — complete
- 目标：让真实供应商参数到位后可以先做无副作用配置核对，并修正外部供应商模式下误带本地 fake 默认值的问题。
- `scripts/smoke-http-sms-provider.sh` 新增：
  - `DRY_RUN=true`：只执行 preflight 与脱敏配置摘要，然后退出；
  - dry-run 不准备 DB fixture、不跑 Maven、不启动 fake provider、不启动 pangu。
- 默认值语义修正：
  - `START_FAKE_SMS=true` 本地模式继续默认 `local-token / local-secret / TPL_VOTE_REMINDER / data.smsId`；
  - `START_FAKE_SMS=false` 外部供应商模式下，endpoint 必填，bearer token、signature secret、template code、message id fields 默认空，不再自动带本地 fake 值；
  - `EXPECTED_PROVIDER_MESSAGE_ID` 改为只在未设置时默认 `fake-sms-990481`，显式设置为空时表示“任意非空 provider_message_id”。
- 验证：
  - `DRY_RUN=true START_FAKE_SMS=false scripts/smoke-http-sms-provider.sh` 在缺 endpoint 时返回 2；
  - `DRY_RUN=true START_FAKE_SMS=false SMS_PROVIDER_ENDPOINT=https://sms.example.com/send EXPECTED_PROVIDER_MESSAGE_ID= scripts/smoke-http-sms-provider.sh` 返回 0，显示 token/secret/template 为空，回执预期为 `<any-non-empty>`；
  - `DRY_RUN=true scripts/smoke-http-sms-provider.sh` 返回 0，显示本地 fake 默认配置；
  - `CLEANUP_FIXTURE=true scripts/smoke-http-sms-provider.sh` 完整本地 smoke 仍通过；
  - 复核 `990481` fixture 已清理，`18080 / 19090` 无残留监听。

### Next
- 真实供应商参数到位后，先跑 `DRY_RUN=true START_FAKE_SMS=false ... scripts/smoke-http-sms-provider.sh` 核对脱敏配置摘要，再执行真实联调。
- 当前 provider 联调脚手架已覆盖 help、dry-run、preflight、参数化、默认本地 smoke 与自定义 header smoke；下一步需要外部供应商参数或供应商特殊协议要求。

### Phase 58: HTTP provider 数字型回执 ID 兼容 — complete
- 目标：降低真实短信供应商回执格式差异风险；部分供应商会把 `messageId / bizId / smsId` 返回为 JSON number，而不是 string。
- 后端改动：
  - `HttpVotingReminderSmsProvider.providerMessageId` 现在支持 textual 与 numeric JSON node；
  - 数字型回执通过 `asText()` 转为字符串后写入 `provider_message_id`；
  - 现有文本字段、嵌套点路径和缺失回执行为保持不变。
- 测试：
  - `HttpVotingReminderSmsProviderTest.send_readsNumericProviderMessageIdField` 覆盖 `{"data":{"smsId":100200300}}` + `provider-message-id-fields=data.smsId`，断言回执为 `"100200300"`。
- 验证：
  - `mvn -pl pangu-bootstrap -am -Dtest=HttpVotingReminderSmsProviderTest,VotingReminderDeliveryDispatchServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - 8 tests，0 failures，0 errors。

### Next
- 真实供应商参数到位后，优先 dry-run 核对配置，再执行 smoke；若供应商返回数字型回执，当前 adapter 已可落库。
- 若供应商返回数组、XML、表单或需要非 HMAC 签名，再按供应商协议扩展 adapter。

### Phase 59: HTTP provider smoke 可调超时参数 — complete
- 目标：真实短信供应商测试网关可能响应较慢，联调脚本需要能按供应商 SLA 调整 Java provider 的 HTTP timeout，而不是修改代码或临时编辑 `application.yml`。
- `scripts/smoke-http-sms-provider.sh` 新增：
  - `SMS_PROVIDER_TIMEOUT_MILLIS`，默认 `3000`；
  - `--help` 外部供应商示例和环境变量说明已列出该参数；
  - preflight 校验该值必须是正整数；
  - dry-run 脱敏配置摘要会打印 `SMS_PROVIDER_TIMEOUT_MILLIS`；
  - `SPRING_APPLICATION_JSON` 会把该值注入 `platform.voting.sms-provider.timeout-millis`。
- 验证：
  - `bash -n scripts/smoke-http-sms-provider.sh` 通过；
  - `DRY_RUN=true START_FAKE_SMS=false SMS_PROVIDER_ENDPOINT=https://sms.example.com/send SMS_PROVIDER_TIMEOUT_MILLIS=4500 EXPECTED_PROVIDER_MESSAGE_ID= scripts/smoke-http-sms-provider.sh` 返回 0，并显示 timeout 为 `4500`；
  - `DRY_RUN=true START_FAKE_SMS=false SMS_PROVIDER_ENDPOINT=https://sms.example.com/send SMS_PROVIDER_TIMEOUT_MILLIS=0 scripts/smoke-http-sms-provider.sh` 返回 2，并在写 DB / 启服务前提示必须是正整数。

### Next
- 真实供应商参数到位后，dry-run 时一并确认 `SMS_PROVIDER_TIMEOUT_MILLIS` 是否匹配供应商测试环境响应 SLA，再执行真实联调。
- 当前没有重新执行写库 live smoke；本轮只做脚本静态检查和 dry-run / 负例验证。

### Phase 60: HTTP provider 业务成功码校验 — complete
- 目标：避免真实短信网关在 HTTP 200 响应里返回业务失败码时，因响应仍携带 `requestId / smsId` 而被误记为 CONFIRMED。
- 后端改动：
  - `HttpVotingReminderSmsProvider` 新增可选配置：
    - `platform.voting.sms-provider.success-code-field`：业务成功码字段，支持点路径；
    - `platform.voting.sms-provider.success-code-values`：逗号分隔成功值。
  - 未配置 `success-code-field` 时保持旧行为：HTTP 2xx + provider message id 即成功；
  - 已配置时，先校验业务码字段存在且值属于成功值，再读取 provider message id；
  - `success-code-field` 与 `success-code-values` 必须成对配置，避免只要求字段存在但任何值都被接受；
  - 业务失败会抛出 `sms provider business failure ...`，由投递状态机标记 FAILED。
- smoke 脚本同步：
  - `SMS_PROVIDER_SUCCESS_CODE_FIELD` / `SMS_PROVIDER_SUCCESS_CODE_VALUES` 已加入 `scripts/smoke-http-sms-provider.sh`；
  - 本地 fake 模式默认 `code=0`，外部供应商模式默认不启用业务码校验；
  - dry-run 摘要会显示两个参数。
- 配置文档：
  - `application.yml` 已补 `success-code-field / success-code-values` 注释。
- 验证：
  - `mvn -pl pangu-bootstrap -am -Dtest=HttpVotingReminderSmsProviderTest,VotingReminderDeliveryDispatchServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - 11 tests，0 failures，0 errors；
  - `bash -n scripts/smoke-http-sms-provider.sh` 通过；
  - `DRY_RUN=true START_FAKE_SMS=false SMS_PROVIDER_ENDPOINT=https://sms.example.com/send SMS_PROVIDER_SUCCESS_CODE_FIELD=code SMS_PROVIDER_SUCCESS_CODE_VALUES=0,OK EXPECTED_PROVIDER_MESSAGE_ID= scripts/smoke-http-sms-provider.sh` 返回 0，并显示业务成功码配置。
  - 只配置 `SMS_PROVIDER_SUCCESS_CODE_FIELD` 或只配置 `SMS_PROVIDER_SUCCESS_CODE_VALUES` 均返回 2，并在写 DB / 启服务前失败。

### Next
- 真实供应商参数到位后，若网关有 `code/status/success` 这类业务字段，dry-run 时一并配置 `SMS_PROVIDER_SUCCESS_CODE_FIELD / SMS_PROVIDER_SUCCESS_CODE_VALUES`；若供应商仅靠 HTTP 状态表达成败，则保持为空。

### Phase 61: HTTP provider 业务失败码投递状态机覆盖 — complete
- 目标：在组合层证明 HTTP provider 遇到 HTTP 200 + 业务失败码时，会进入投递状态机 FAILED 路径，而不是 CONFIRMED。
- 测试改动：
  - `VotingReminderDeliveryDispatchServiceTest.dispatchPending_withHttpProviderBusinessFailure_marksFailed` 新增 JVM 内 HTTP provider 组合测试；
  - fake HTTP 响应为 `{"code":1001,"data":{"smsId":"should-not-confirm"}}`；
  - provider 配置 `success-code-field=code / success-code-values=0`；
  - 断言 `dispatchPending` 返回 0，调用 `markFailed(1L, contains("business failure"))`，且不调用 `markConfirmed`。
- 验证：
  - `mvn -pl pangu-bootstrap -am -Dtest=HttpVotingReminderSmsProviderTest,VotingReminderDeliveryDispatchServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - 12 tests，0 failures，0 errors。

### Next
- 真实供应商联调时，如果业务失败码来自 HTTP 200 响应，当前链路会把投递明细标记为 FAILED，并在 yaochi 投递详情中暴露错误信息。

### Phase 62: HTTP provider 默认嵌套回执字段兼容 — complete
- 目标：减少真实供应商联调时对 `provider-message-id-fields` 的必填依赖；很多短信网关把回执 ID 放在 `data.messageId / data.smsId / data.bizId / data.requestId`。
- 后端改动：
  - `HttpVotingReminderSmsProvider.DEFAULT_PROVIDER_MESSAGE_ID_FIELDS` 从顶层字段扩展为：
    `providerMessageId / messageId / smsId / bizId / requestId / data.providerMessageId / data.messageId / data.smsId / data.bizId / data.requestId`；
  - 显式配置 `provider-message-id-fields` 时仍完全按配置字段优先，不改变自定义供应商行为。
- 测试：
  - `HttpVotingReminderSmsProviderTest.send_readsDefaultNestedProviderMessageIdField` 覆盖未配置 `provider-message-id-fields` 时读取 `data.smsId`。
- 验证：
  - `mvn -pl pangu-bootstrap -am -Dtest=HttpVotingReminderSmsProviderTest,VotingReminderDeliveryDispatchServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - 13 tests，0 failures，0 errors。

### Next
- 真实供应商如果返回常见 `data.smsId / data.messageId`，可先不配置 `SMS_PROVIDER_MESSAGE_ID_FIELDS`；若字段更特殊，再在 dry-run 阶段显式配置。

### Phase 63: 短信当前验收策略切回 MOCK 优先 — complete
- 用户决策：短信先 MOCK。
- 当前状态：
  - `application.yml` 默认仍是 `platform.voting.sms-provider-mode=mock`；
  - 真实 HTTP provider、fake SMS provider、dry-run、业务成功码、默认嵌套回执等能力保留为后续接供应商时使用；
  - 当前选举闭环验收不再等待真实短信供应商 endpoint/token/template/signature 参数。
- 当前 MOCK 验收口径：
  - 催票发送记录、通知 outbox、逐户投递明细、投递状态机、管理端投递明细展示仍按已完成链路验收；
  - 短信 provider 使用 mock / 本地 fake provider 证明 `READY -> CONFIRMED` 状态回写；
  - 如需复验，执行 `CLEANUP_FIXTURE=true scripts/smoke-http-sms-provider.sh`，不需要外部供应商。

### Next
- 以 MOCK 短信链路作为当前闭环验收口径；真实供应商联调降级为后续增强项。

### Phase 64: MOCK provider 投递状态机组合测试 — complete
- 目标：在短信先 MOCK 的当前策略下，直接证明默认 mock 短信 provider 能驱动投递状态机确认成功。
- 测试改动：
  - `VotingReminderDeliveryDispatchServiceTest.dispatchPending_withMockSmsProvider_marksConfirmed` 使用真实 `MockVotingReminderSmsProvider`；
  - 断言 `dispatchPending` 返回 1；
  - 断言 `markConfirmed(1L, "mock-sms-1")`；
  - 断言不调用 `markFailed`。
- 验证：
  - `mvn -pl pangu-bootstrap -am -Dtest=VotingReminderDeliveryDispatchServiceTest,HttpVotingReminderSmsProviderTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - 14 tests，0 failures，0 errors。

### Next
- MOCK 短信验收已有单测和历史 live smoke 双重证据；后续可进入最终验收复核或整理提交。

### Phase 65: MOCK provider 数据库集成复验 — complete
- 目标：在短信先 MOCK 的当前验收策略下，复验真实 Spring 容器 + 数据库路径，而不是只依赖 mocked repository 组合测试。
- 已确认覆盖：
  - `VotingReminderOutboxRepositoryIntegrationTest.dispatchService_claimsReadyDeliveryAndMarksConfirmed` 插入 READY delivery；
  - dispatch service 领取待投递明细并调用默认 mock provider；
  - 数据库回写 `delivery_status=3`、`attempts=1`、`confirmed_at` 非空；
  - `provider_message_id` 以 `mock-sms-` 开头，并可通过投递明细查询回显。
- 验证：
  - `mvn -pl pangu-bootstrap -am -Dtest=VotingReminderOutboxRepositoryIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - Surefire 报告：4 tests，0 failures，0 errors，0 skipped。

### Next
- MOCK provider 当前已有默认配置、组合测试、数据库集成测试和本地 fake SMS live smoke 证据；下一步做最终收口审计，确认 pangu / yaochi / shennong-app 的验收项没有遗漏。

### Phase 66: MOCK 催票闭环手工验收复现 — complete
- 目标：按用户手工验收时遇到的问题，用当前数据库和真实 HTTP 接口确认可走通账号、验证码和数据链路。
- 数据核验：
  - `13800000001` = `GOV_SUPER_ADMIN / user_id=800001 / tenant_id=NULL`，不能直接访问要求租户上下文的「议题表决看板」接口；
  - `13800000005` = `GOV_OPERATOR / user_id=800005 / tenant_id=10001`，适合访问租户内管理端表决看板；
  - `13800000004` = `GRID_OPERATOR / user_id=800004 / tenant_id=10001`，有 `990480 / building_id=30001 / can_remind=true` 动态催票权限；
  - dev/test 短信验证码默认来自 `MockSmsVerificationStrategy`，当前为 `123456`。
- 真实接口复现：
  - `POST /auth/login` 使用 `13800000004 / 123456` 返回 200，token 中 `tenantId=10001 / role=GRID_OPERATOR`；
  - `GET /reminder/tasks` 返回 `subjectId=990480 / pendingCount=1`；
  - `GET /reminder/tasks/990480/pending` 返回 `uid=70001 / phoneMasked=138****0012`；
  - `POST /voting-subjects/990480/mobilization-reminders` 使用 `13800000004` token 返回 `reminderId=1 / outboxEventId=990491 / targetCount=1`；
  - scheduler 一个周期后，`t_outbox_event.event_id=990491` 回写 `status=3 / attempts=1 / confirmed_at`；
  - `t_voting_reminder_delivery.delivery_id=990488` 回写 `delivery_status=3 / attempts=1 / provider_message_id=mock-sms-990488`；
  - `GET /voting-subjects/990480/reminder-deliveries?buildingId=30001&status=3&limit=50` 使用 `13800000005` token 返回 `deliveryId=990488 / providerMessageId=mock-sms-990488`。

### Next
- 手工验收请使用 `13800000005 / 123456` 查看 yaochi 租户内表决看板，使用 `13800000004 / 123456` 进入 shennong-app worker 工作台；`13800000001` 保留给街道办全局/终审类操作，不用于当前租户内投递明细看板。

### Phase 67: yaochi 表决看板补发起催票入口 — complete
- 发现：
  - yaochi 已有 `sendMobilizationReminder` API 封装；
  - `Voting.tsx` 只有「催票投递明细」查询、筛选和详情，没有 UI 入口发起 `mobilization-reminders`；
  - 因此用户手工走流程时无法纯点击管理端生成催票 outbox，只能调用接口。
- 已补齐：
  - `Voting.tsx` 在投票中议题的「催票投递明细」区块新增「发起催票」按钮；
  - 弹窗输入楼栋 ID 和催票内容；
  - 提交后调用 `sendMobilizationReminder(subjectId, { buildingId, message })`；
  - 成功后提示 `Outbox #...` 并刷新投递明细。
- 验证：
  - `npm run build` in `/Users/juchen/Documents/workspace/yaochi` 通过。

### Next
- 手工验收现在可以全 UI 执行：用 `13800000004 / 123456` 登录 yaochi，在「议题与表决 -> 议题表决看板」选 `990480`，点击「催票投递明细」区块的「发起催票」；稍等 scheduler 后用 `13800000005 / 123456` 或同一租户账号查看 `mock-sms-*` 投递回执。

### Phase 68: shennong-app 催票工作台默认走真实接口 — complete
- 发现：
  - `shennong-app/src/config/env.dev.ts` 仍是 `USE_MOCK=true`；
  - `src/lib/reminder.ts` 的 worker 催票列表、待通知业主、标记通知都会被全局 `USE_MOCK` 拦截成本地 stub；
  - 但全局直接改成 `USE_MOCK=false` 会连带刷脸、房产、责任田等未完全真实化模块也切到后端，扩大手工验收副作用。
- 已补齐：
  - `EnvConfig` 新增可选 `USE_REMINDER_MOCK`；
  - `env.dev.ts` 保持 `USE_MOCK=true`，新增 `USE_REMINDER_MOCK=false`；
  - `reminder.ts` 改为优先读取 `USE_REMINDER_MOCK ?? USE_MOCK`；
  - worker 催票页面底部说明改为“开发配置默认走后端真实催票接口；短信发送由后端 MOCK provider 回写”；
  - README 联调说明改为催票模块使用 `USE_REMINDER_MOCK=false`，不需要关闭全局 `USE_MOCK`。
- 验证：
  - `npm run type-check` in `/Users/juchen/Documents/workspace/shennong-app` 通过。

### Next
- shennong-app 手工验收时，使用 `13800000004 / 123456` 登录后进入 worker 催票工作台，应直接请求真实 `GET /reminder/tasks`、`GET /reminder/tasks/{subjectId}/pending`、`POST /reminder/tasks/{subjectId}/notify`；短信仍按 pangu 后端 mock provider 回写。

### Phase 69: 信托制分期付款前置序号守卫第一切片 — complete
- 目标：对齐源文件“分期付款前置上链序号锁链”要求，先在当前维修资金 / 信托制第一切片模型上阻断第 N 期越过第 N-1 期直接出账。
- 现状约束：
  - 当时仓库还没有独立信托付款表，也没有信托付款链上 `tx_hash` 字段；
  - 现有信托制动账以 `TRUST_FUND_PAYMENT` 治理锁证明双签完成，并以 `t_fund_ledger_entry.business_type=7` 表达已出账流水。
- 已补齐：
  - `TrustFundDisbursementCommand` 新增 `installmentNo` 与 `previousTrustPaymentId`，保留旧构造器默认第 1 期，兼容既有调用；
  - `MaintenanceFundApplicationService.recordTrustFundDisbursement` 对 `installmentNo > 1` 增加 guard：
    - 必须提供 `previousTrustPaymentId`；
    - 前一期 `TRUST_FUND_PAYMENT` 必须已双签解锁；
    - 前一期必须已有 `business_type=7` 的信托出账流水；
  - `MaintenanceFundAccountRepository` / MyBatis 增加 `existsLedgerEntry` 查询。
- 测试：
  - 新增 `trustSecondInstallmentRejectedUntilPreviousLedgerConfirmed`：第 2 期即使自身和前一期都已双签，前一期未写流水时仍拒绝；
  - 新增 `trustSecondInstallmentAllowedAfterPreviousLedgerConfirmed`：第 1 期写入流水后第 2 期允许出账。
- 验证：
  - `mvn -pl pangu-bootstrap -am -Dtest=MaintenanceFundHandoverGuardTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - 10 tests，0 failures，0 errors。

### Next
- 该后续已由 Phase 82 补齐：V3.28 已在资金流水模型新增链上存证字段，并将 guard 升级为前一期链上确认。

### Phase 70: 候选人提名收紧为 G 端基层经办员 — complete
- 发现：
  - 设计稿要求候选人名单由 G 端基层经办员录入，B 端老业委会在选举模块写权限封死；
  - 旧迁移仍把 `candidate:nominate` 授给 `GRID_OPERATOR / COMMITTEE_DIRECTOR / COMMITTEE_MEMBER / OWNER_REPRESENTATIVE` 等角色；
  - `ElectionCandidateService.nominate` 只校验议题类型、状态和租户，旧权限点通过 `@PreAuthorize` 后可以直接提名。
- 已补齐：
  - `ElectionCandidateService` 注入 `UserContextHolder`；
  - ELECTION 候选人提名增加 service 层兜底：仅 `roleKey=GOV_OPERATOR` 且 `deptType IN (2,5)` 可提名；
  - 即使旧角色保留 `candidate:nominate`，也不能绕过 service 护栏。
- 测试：
  - `ElectionCandidateServiceTest` 默认上下文改为 GOV_OPERATOR；
  - 新增 GRID_OPERATOR 即使有旧权限点也被拒绝；
  - 新增 COMMITTEE_DIRECTOR 即使有旧权限点也被拒绝；
  - `ElectionCandidateEndpointMatrixTest` 正向改为 `13800000005 / GOV_OPERATOR`，并断言 `GRID_OPERATOR` 通过 PreAuthorize 后被 service 层 403 / 40923 拦截。
- 验证：
  - `mvn -pl pangu-bootstrap -am -Dtest=ElectionCandidateServiceTest,ElectionCandidateEndpointMatrixTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - 36 tests，0 failures，0 errors。

### Phase 71: candidate:nominate 权限矩阵清理与 yaochi 可见性同步 — complete
- 目标：把 Phase 70 的 service 兜底推进到权限矩阵和管理端可见性，避免旧角色看到提名按钮后再被后端拒绝。
- 后端：
  - 新增 `V3.20__candidate_nominate_role_cleanup.sql`；
  - 删除所有非 `GOV_OPERATOR(role_id=14)` 的 `candidate:nominate` 授权；
  - 保留 / 补齐 `GOV_OPERATOR -> candidate:nominate`；
  - 本地聚焦测试启动时 Flyway 已应用 V3.20，`GRID_OPERATOR` 权限列表已不再包含 `candidate:nominate`。
- yaochi：
  - `SubjectProposal.tsx` 的提名按钮从“只看 `candidate:nominate` 权限”改为 `candidate:nominate + roleKey=GOV_OPERATOR + dept_type IN (2,5)`；
  - `lib/election.ts` 注释同步为 G 端基层经办员护栏。
- 验证：
  - `mvn -pl pangu-bootstrap -am -Dtest=ElectionCandidateServiceTest,ElectionCandidateEndpointMatrixTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - 36 tests，0 failures，0 errors；
  - yaochi `npm run build` 通过。

### Next
- V3.23 已用于 Waiver 双签角色分离；后续迁移从 V3.24+ 开始。下一步继续观察旧 `voting:subject:publish` 通用权限点是否还会在非筹备页面暴露 ELECTION 写入口，后端仍按议题类型兜底。

### Phase 72: yaochi 表决看板 ELECTION 直公示入口收口 — complete
- 发现：
  - `SubjectProposal.tsx` 已隐藏 ELECTION 的直接「公示」按钮，要求走提交初审 / 居委会初审 / 街道终审；
  - 但 `Voting.tsx` 表决看板仍按 `DRAFT + voting:subject:publish` 展示「公示」按钮；
  - 这会让持有旧通用 `voting:subject:publish` 的角色在表决看板看到一个后端会拒绝的选举直公示入口。
- 已补齐：
  - `Voting.tsx` 的 `showPublish` 增加 `t.subjectType !== "ELECTION"`；
  - 注释同步为 GENERAL/MAJOR 可直接公示，ELECTION 必须走双签。
- 验证：
  - yaochi `git diff --check` 通过；
  - yaochi `npm run build` 通过，仅保留既有 Vite chunk-size warning。

### Next
- 剩余 `voting:subject:create/publish` 是通用权限点：GENERAL/MAJOR 和部分日常议题仍需要使用，不能像 `candidate:nominate` 一样直接全量回收。下一步若继续收口，需要拆分 ELECTION 专属权限或按菜单/页面维度继续隐藏选举写入口，同时保留后端 service 层按议题类型兜底。

### Phase 73: ELECTION 立项/提交初审专属权限拆分 — complete
- 发现：
  - `POST /voting-subjects` 过去只看通用 `voting:subject:create`，ELECTION 再由 service 层按 `GOV_OPERATOR + dept_type` 兜底；
  - `POST /voting-subjects/{id}/submit-for-review` 是 ELECTION 专属动作，但也只看通用 `voting:subject:create`；
  - 因此 `COMMUNITY_ADMIN` 这类仍持有通用 create 的角色会通过 controller 预授权，至少进入 service 层，权限矩阵表达不够清楚。
- 后端：
  - 新增 `V3.21__election_subject_create_permission.sql`；
  - 新增能力点 `voting:subject:create:election`，`allowed_dept_categories='G'`；
  - 只授予 `GOV_OPERATOR(role_id=14)`；
  - `SubjectAdminController.propose` 按 `subjectType` 分流预授权：ELECTION 要 `voting:subject:create:election`，GENERAL/MAJOR 仍要 `voting:subject:create`；
  - `submit-for-review` 改为要求 `voting:subject:create:election`；
  - service 层 `GOV_OPERATOR + dept_type IN (2,5)` 护栏保留。
- yaochi：
  - `SubjectProposal.tsx` 的 ELECTION 立项 / 提交初审按钮改为要求 `voting:subject:create:election + GOV_OPERATOR + dept_type IN (2,5)`；
  - 议题筹备菜单支持 `requireAnyPermissions`，命中通用 create 或 ELECTION create 均可进入；
  - `lib/voting.ts` API 注释同步新权限语义。
- 验证：
  - `mvn -pl pangu-bootstrap -am -Dtest=VotingEndpointMatrixTest,ElectionCandidateEndpointMatrixTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - 54 tests，0 failures，0 errors；
  - 测试启动时 Flyway 已应用 V3.21，`GOV_OPERATOR` 权限列表包含 `voting:subject:create:election`，`COMMUNITY_ADMIN` 不包含；
  - yaochi `git diff --check` 通过；
  - yaochi `npm run build` 通过，仅保留既有 Vite chunk-size warning。

### Next
- V3.23 已用于 Waiver 双签角色分离；后续迁移从 V3.24+ 开始。`voting:subject:publish` 仍是 GENERAL/MAJOR 日常公示通用权限，ELECTION 发布已走街道终审 `voting:subject:review:street`，当前先不拆 publish，避免破坏一般议题公示。

### Phase 74: ELECTION 双签角色分离兜底 — complete
- 发现：
  - V3.6 曾把 `voting:subject:review:committee` 同时授给 `COMMUNITY_ADMIN` 和 `GOV_SUPER_ADMIN`；
  - 这会让街道办账号在前端看到居委会初审按钮，且理论上可一人完成居委会初审与街道终审；
  - `ProposalReviewService` 过去只校验状态机和议题类型，没有按角色兜底双签分工。
- 后端：
  - 新增 `V3.22__election_dual_review_role_separation.sql`；
  - 删除 `GOV_SUPER_ADMIN(role_id=1)` 的 `voting:subject:review:committee`；
  - `ProposalReviewService` 注入 `UserContextHolder`；
  - 提交初审仅允许 `GOV_OPERATOR`；
  - 居委会初审仅允许 `COMMUNITY_ADMIN`；
  - 街道终审仅允许 `GOV_SUPER_ADMIN`；
  - 即使后续权限矩阵误配，service 层仍会拒绝角色错位。
- 验证：
  - `mvn -pl pangu-bootstrap -am -Dtest=ProposalReviewServiceTest,VotingEndpointMatrixTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - 50 tests，0 failures，0 errors；
  - 测试启动时 Flyway 已应用 V3.22，`GOV_SUPER_ADMIN` 权限列表不再包含 `voting:subject:review:committee`。

### Next
- V3.23 已用于 Waiver 双签角色分离；后续迁移从 V3.24+ 开始。继续从源文件红线检查是否还有“同一角色可跨阶段代签”的路径，优先看信托制双签是否需要同样的 service 层角色兜底。

### Phase 75: Waiver 双签角色分离兜底 — complete
- 发现：
  - V1.4 曾把 `waiver:approve:committee` 同时授给 `GOV_SUPER_ADMIN` 和 `COMMUNITY_ADMIN`；
  - Waiver 设计是居委会初审、街道办终审，街道办不应执行居委会初审；
  - `WaiverApplicationService` 过去只依赖 controller `@PreAuthorize`，直接 application service 调用或未来误配权限时缺少角色兜底。
- 后端：
  - 新增 `V3.23__waiver_dual_review_role_separation.sql`；
  - 删除 `GOV_SUPER_ADMIN(role_id=1)` 的 `waiver:approve:committee`；
  - `WaiverApplicationService` 注入 `UserContextHolder`；
  - `reviewByCommittee` 仅允许 `COMMUNITY_ADMIN`；
  - `reviewByStreet` 仅允许 `GOV_SUPER_ADMIN`；
  - 角色错位统一抛 `APPROVER_DEPT_INVALID`。
- 测试：
  - `PreAuthorizeMatrixTest` 新增街道办调用 Waiver 居委会初审 403；
  - `ElectionWorkflowIntegrationTest` 的直接 service Waiver 链路显式设置居委会 / 街道上下文，并在 `tearDown` 清理 `UserContextHolder`；
  - 聚焦测试 11/0F/0E。
- 验证：
  - `mvn -pl pangu-bootstrap -am -Dtest=PreAuthorizeMatrixTest,ElectionWorkflowIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - 11 tests，0 failures，0 errors；
  - 测试启动时 Flyway 已应用 V3.23。

### Next
- V3.23 已用于 Waiver 双签角色分离；后续迁移从 V3.24+ 开始。下一步继续审计信托制 `lock:unlock:committee/street` 双签是否存在同一角色跨阶段代签或 service 层缺少角色兜底的问题。

### Phase 76: Governance Lock 双签角色分离兜底 — complete
- 发现：
  - V2.5 曾把 `lock:unlock:street` 同时授给 `GOV_SUPER_ADMIN` 和 `COMMUNITY_ADMIN`；
  - 治理锁语义是业委会主任初签、街道办终签，居委会不应执行终签；
  - `GovernanceLockApplicationService` 过去只依赖 controller `@PreAuthorize`，直接 application service 调用或未来误配权限时缺少角色兜底。
- 后端：
  - 新增 `V3.24__governance_lock_street_role_separation.sql`；
  - 删除 `COMMUNITY_ADMIN(role_id=2)` 的 `lock:unlock:street`；
  - `GovernanceLockApplicationService` 注入 `UserContextHolder`；
  - `committeeSign` 仅允许 `COMMITTEE_DIRECTOR`；
  - `streetSign` 仅允许 `GOV_SUPER_ADMIN`；
  - 角色错位统一抛 `LOCK_ROLE_FORBIDDEN`，web 层映射为 `LockErrorCode.LOCK_ROLE_FORBIDDEN(40112/403)`。
- 测试：
  - `LockPreAuthorizeMatrixTest` 新增居委会终签 403、街道办终签正向预授权到 `LOCK_NOT_FOUND`；
  - `LockMatrixIntegrationTest` 覆盖直接 service 调用时 `COMMUNITY_ADMIN` 即使误配权限也不能终签；
  - `MaintenanceFundHandoverGuardTest` 的信托付款双签解锁链路显式设置业委会主任 / 街道上下文，并在 `tearDown` 清理 `UserContextHolder`；
  - 聚焦测试 25/0F/0E。
- 验证：
  - `mvn -pl pangu-bootstrap -am -Dtest=LockPreAuthorizeMatrixTest,LockMatrixIntegrationTest,MaintenanceFundHandoverGuardTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - 25 tests，0 failures，0 errors；
  - 测试启动时 Flyway 已应用 V3.24。

### Next
- V3.24 已用于 Governance Lock 终签角色分离；后续迁移从 V3.25+ 开始。继续从源文件红线检查是否还有“同一角色可跨阶段代签”的路径，优先看资金公示 compose/publish/audit 是否存在类似 controller 权限与 service 兜底不一致。

### Phase 77: 资金公示动作分工 service 兜底 — complete
- 发现：
  - V2.7 权限数据本身已分工：`disclosure:compose` 给 `COMMITTEE_DIRECTOR / COMMUNITY_ADMIN`，`disclosure:publish` 只给 `COMMITTEE_DIRECTOR`，`disclosure:audit` 给 `GOV_SUPER_ADMIN / COMMUNITY_ADMIN`；
  - `FinanceDisclosureApplicationService` 过去只依赖 controller `@PreAuthorize`，直接 application service 调用或未来误配权限时缺少角色兜底；
  - 这条不是新增迁移问题，不需要改 V2.7 或占用 V3.25。
- 后端：
  - `FinanceDisclosureApplicationService` 注入 `UserContextHolder`；
  - `compose` 仅允许 `COMMITTEE_DIRECTOR / COMMUNITY_ADMIN`；
  - `lockAndPublish` 仅允许 `COMMITTEE_DIRECTOR`；
  - `compare` 仅允许 `GOV_SUPER_ADMIN / COMMUNITY_ADMIN`；
  - 角色错位统一抛 `DISCLOSURE_ROLE_FORBIDDEN`，web 层映射为 `DisclosureErrorCode.DISCLOSURE_ROLE_FORBIDDEN(41110/403)`。
- 测试：
  - `FinanceDisclosureComposeTest` 设置角色上下文，并新增 `GRID_OPERATOR` 不能 compose 的误配兜底；
  - `FinanceDisclosureWorkflowTest` 设置 compose/publish/audit 分段角色上下文，并新增居委会不能 publish、业委会主任不能 audit 的误配兜底；
  - `FinanceDisclosureHandoverGuardTest` 补 mock `UserContextHolder`；
  - `DisclosurePreAuthorizeMatrixTest` 保持 controller 权限矩阵覆盖。
- 验证：
  - `mvn -pl pangu-bootstrap -am -Dtest=FinanceDisclosureComposeTest,FinanceDisclosureWorkflowTest,FinanceDisclosureHandoverGuardTest,DisclosurePreAuthorizeMatrixTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - 22 tests，0 failures，0 errors；
  - 测试启动时 Flyway schema 仍为 V3.24，无新迁移。

### Next
- V3.24 已用于 Governance Lock 终签角色分离；后续迁移从 V3.25+ 开始。继续扫剩余红线：优先检查 `fund:*` 旧权限点与 `disclosure:*` 新权限点是否存在前端入口或 controller service 语义不一致。

### Phase 78: 旧资金公示权限点目录清理 — complete
- 发现：
  - 后端 controller 只使用 V2.7 新 `disclosure:*` 权限；
  - 旧 `fund:disclosure:publish` 仍在 V1.4 种子中，并授给 `GOV_SUPER_ADMIN / PARTY_SECRETARY / COMMITTEE_DIRECTOR`；
  - yaochi 没有财务公示业务入口，但 RBAC 页会通过 `/admin/permissions` 展示 `sys_permission` 全量目录，因此旧权限点会污染可授权目录。
- 后端：
  - 新增 `V3.25__remove_legacy_fund_disclosure_publish_permission.sql`；
  - 删除所有 `sys_role_permission.permission_key='fund:disclosure:publish'`；
  - 删除 `sys_permission.permission_key='fund:disclosure:publish'`。
- 测试：
  - `DisclosurePreAuthorizeMatrixTest` 新增迁移回归断言：旧权限目录项与角色授权均为 0；
  - 披露聚焦测试从 22 个扩展到 23 个。
- 验证：
  - `mvn -pl pangu-bootstrap -am -Dtest=FinanceDisclosureComposeTest,FinanceDisclosureWorkflowTest,FinanceDisclosureHandoverGuardTest,DisclosurePreAuthorizeMatrixTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - 23 tests，0 failures，0 errors；
  - 测试启动时 Flyway 已应用 V3.25。

### Next
- V3.25 已用于旧资金公示权限点清理；后续迁移从 V3.26+ 开始。继续扫剩余旧宽权限点：优先检查 `voting:subject:publish`、`candidate:*` 是否还在非目标角色授权或前端入口里保留误导性写入口。

### Phase 79: 候选人两段审查角色分离兜底 — complete
- 发现：
  - V3.2 曾保留 `GOV_SUPER_ADMIN` 对 `candidate:review:party` 与 `candidate:approve` 的双闸 override；
  - 当前源文件与路线图的正常流程分工是 PARTY_SECRETARY 做党组前置审查，COMMUNITY_ADMIN 做居委会资格审查；
  - `ElectionCandidateService` 过去只对提名做 `GOV_OPERATOR + dept_type` 兜底，partyReview/review 未按角色兜底。
- 后端：
  - 新增 `V3.26__candidate_review_role_separation.sql`；
  - 删除 `GOV_SUPER_ADMIN(role_id=1)` 的 `candidate:review:party` 与 `candidate:approve`；
  - `ElectionCandidateService.partyReview` 仅允许 `PARTY_SECRETARY`；
  - `ElectionCandidateService.review` 仅允许 `COMMUNITY_ADMIN`；
  - 新增 `CANDIDATE_REVIEW_FORBIDDEN` 并映射到 `ElectionErrorCode.CANDIDATE_REVIEW_FORBIDDEN(40954/403)`。
- 测试：
  - `ElectionCandidateServiceTest` 增加街道办超管即使误配权限也不能前置审查 / 资格审查；
  - `ElectionCandidateEndpointMatrixTest` 增加街道办超管调用两段候选人审查 endpoint 均 403；
  - yaochi 未改代码，按钮按权限自然隐藏。
- 验证：
  - `mvn -pl pangu-bootstrap -am -Dtest=ElectionCandidateServiceTest,ElectionCandidateEndpointMatrixTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - 40 tests，0 failures，0 errors；
  - 测试启动时 Flyway 已应用 V3.26；
  - yaochi `npm run build` 通过，仅保留既有 Vite chunk-size warning。

### Next
- V3.26 已用于候选人审查角色分离；后续迁移从 V3.27+ 开始。继续检查 `voting:subject:publish` 是否还需要按 GENERAL/MAJOR 与 ELECTION 语义拆分或目录清理，避免旧通用公示权限误导。（已由 Phase 80 完成）

### Phase 80: ELECTION 发布必须走街道终审 — complete
- 发现：
  - `POST /voting-subjects/{id}/publish` 仍使用旧通用 `voting:subject:publish`；
  - `ProposalLifecycleService.publish` 过去允许 `GOV_SUPER_ADMIN` 对 `PENDING_STREET` ELECTION 直接发布；
  - 该直发路径只更新状态，不写 `review_history`，会绕过 `street-review` 的终审审批留痕语义。
- 后端：
  - 新增 `V3.27__election_publish_requires_street_review.sql`；
  - 删除 `GOV_SUPER_ADMIN(role_id=1)` 的旧通用 `voting:subject:publish`；
  - `ProposalLifecycleService.publish` 直接拒绝 ELECTION，ELECTION 发布只能通过 `ProposalReviewService.streetApprove`；
  - `SubjectAdminController` 注释同步为 `/publish` 仅用于非 ELECTION 直接公示；
  - `VotingMobilizationPermissionMapper` 修复开票激活动员权限时 `timestamptz` 参数类型绑定。
- 测试：
  - `ProposalLifecycleServiceTest` 覆盖 ELECTION 直接 publish 拒绝，含街道办角色；
  - `VotingEndpointMatrixTest` 覆盖街道办调用 `/publish` 403；
  - `ElectionWorkflowEndToEndTest` 更新候选人提名账号为 GOV_OPERATOR，并继续验证 `review_history` 三步留痕；
  - 聚焦测试 84/0F/0E。
- 验证：
  - `mvn -pl pangu-bootstrap -am -Dtest=ProposalLifecycleServiceTest,ProposalReviewServiceTest,VotingEndpointMatrixTest,ElectionWorkflowEndToEndTest,ElectionWorkflowIntegrationTest,ProposalHandoverGuardTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - 测试启动时 Flyway 已应用 / 验证到 V3.27。

### Next
- V3.27 已用于 ELECTION 发布必须走街道终审；V3.28 后续已由 Phase 82 用于资金流水链上存证字段，当前后续迁移从 V3.29+ 开始。
- `voting:subject:publish` 仍保留给 GENERAL/MAJOR 日常公示；ELECTION 立项 / 提交初审 / 发布分别走 `voting:subject:create:election`、`submit-for-review`、`voting:subject:review:street`。

### Phase 81: 多分身路线图状态校准 — complete
- 发现：
  - 路线图 1.1 仍把“经办员与网格员解耦 / 一人多分身”标为 `❌ 数据模型差`；
  - 当前代码与后续章节已经证明 D-mini 已完成：V1 schema 有 `sys_user.uk_account_dept`，V3.9/V3.10 seed 了刘主任网格员分身，后端有 `/auth/shadows` 与 `/auth/switch-shadow`，yaochi Topbar 已同步；
  - `ControllerIntegrationTest` 仍断言网格员拥有 `candidate:nominate`，该断言与 V3.20 “候选人提名只授 GOV_OPERATOR”相冲突。
- 更新：
  - `docs/选举闭环对齐路线图.md` 将多分身状态改为 D-mini 已完成；
  - 梯度 D 关键决策从“开工前需对齐”改为已落地口径：本轮只做 SYS_USER 内部分身切换，不做 C_USER/SYS_USER 跨大类切换 UI；`sys_user_role` 仍保持一分身一角色；
  - `ControllerIntegrationTest` 将网格员登录权限断言改为不包含 `candidate:nominate`。
- 验证：
  - `mvn -pl pangu-bootstrap -am -Dtest=SwitchShadowMatrixTest,SwitchTenantMatrixTest,ControllerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - 13 tests，0 failures，0 errors，1 skipped。

### Phase 82: 信托制分期链上确认 guard — complete
- 发现：
  - Phase 69 已阻断第 N 期越过第 N-1 期，但只校验前一期双签解锁与资金流水存在；
  - `t_fund_ledger_entry` 之前没有链上交易 hash / 确认状态字段，无法表达源文件要求的 `txHash CONFIRMED`；
  - 资金流水是当前仓库信托制出账的真实落账点，适合作为本切片的链上确认承载表。
- 后端：
  - 新增 `V3.28__fund_ledger_chain_attestation.sql`；
  - `t_fund_ledger_entry` 增加 `blockchain_tx_hash / chain_attest_status / chain_confirmed_at`，并约束状态值 1-4；
  - `MaintenanceFundApplicationService.ensurePreviousInstallmentConfirmed` 从“前一期流水存在”升级为“前一期信托出账流水 `chain_attest_status=3` 且 `blockchain_tx_hash` 非空”；
  - 仓储查询改为 `existsConfirmedLedgerEntry`，删除旧 `existsLedgerEntry` 依赖。
- 测试：
  - `MaintenanceFundHandoverGuardTest` 覆盖前一期流水未链上确认时第二期拒绝；
  - 同测覆盖手动标记前一期链上确认后第二期放行；
  - `LockMatrixIntegrationTest` 回归治理锁双签链路。
- 验证：
  - `mvn -pl pangu-bootstrap -am -Dtest=MaintenanceFundHandoverGuardTest,LockMatrixIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - 19 tests，0 failures，0 errors；
  - 测试启动时 Flyway 已应用 V3.28。

### Next
- V3.28 已用于资金流水链上存证字段与信托分期链上确认 guard；后续迁移从 V3.29+ 开始。

## Session: 2026-07-01

### RBAC + ABAC 工作身份授权 — backend complete
- 业务口径确认：同一个自然人账号通过多个 `sys_user` 工作身份承担多职责；`sys_user_role` 仍保持一分身一角色。示例：同一 `t_account` 下 `GOV_OPERATOR` 经办员分身 + `GRID_OPERATOR` 网格员分身，网格员分身再通过 `sys_user_building` 绑定楼栋 ABAC。
- 后端已新增工作身份授权模块：
  - `/api/v1/admin/work-identities/accounts/search?keyword=...`
  - `/api/v1/admin/work-identities/accounts/{accountId}`
  - `/api/v1/admin/work-identities/dept-options?roleKey=...`
  - `/api/v1/admin/work-identities/building-options?deptId=...`
  - `POST /api/v1/admin/work-identities/accounts/{accountId}/shadows`
- 写侧事务中完成：账号校验、角色与部门类型校验、新增 `sys_user`、绑定 `sys_user_role`，并在 OWNER_GROUP 角色下按目标部门租户同步绑定楼栋，满足 deferred trigger。街道超管 `tenantId=null` 时，楼栋选项与写入都以所选部门的 `tenant_id` 为准。
- 测试：
  - `WorkIdentityAdminTest` 覆盖给吴经办员新增网格员分身、绑定楼栋、`/auth/shadows` 列出双分身、`switch-shadow` 切到网格员；
  - 覆盖街道超管无当前租户时按目标网格租户读取楼栋并创建楼栋责任田身份；
  - 覆盖缺楼栋、非楼栋角色带楼栋、重复部门身份、角色部门类型不匹配、无 `admin:user:assign-role` 权限等负例。
- 验证：
  - `mvn -pl pangu-bootstrap -am -Dtest=WorkIdentityAdminTest,BuildingAssignmentTest,SwitchShadowMatrixTest -Dsurefire.failIfNoSpecifiedTests=false test`：28 tests，0 failures，0 errors；
  - `mvn test`：534 tests，0 failures，0 errors，1 skipped。

### RBAC + ABAC 工作身份授权 — yaochi management complete
- 管理端已接入“工作身份与授权”页面：检索自然人账号、查看既有工作身份、按角色选择兼容部门、为 OWNER_GROUP 类角色同步选择楼栋责任田。
- 前端仍以后端为准：页面只编排交互，角色/部门匹配、重复身份、楼栋必填/禁止、授权权限均由后端接口校验。
- 楼栋下拉不再依赖当前登录租户的楼栋责任田视图，而是调用工作身份授权专用楼栋选项接口，按所选部门租户取可绑定楼栋。
- 验证：
  - `npm run build` 通过，仅保留 Vite chunk size 常规提示；
  - 浏览器联调：街道超管登录 `yaochi`，进入“工作身份与授权”，检索 `13800000005`，选择 `GRID_OPERATOR` + 求是第一网格，楼栋列表显示 `#30001/#30002/#30005`，选择 `#30005` 后创建成功，页面显示该自然人已有 `GOV_OPERATOR` 与 `GRID_OPERATOR` 两个工作身份。

### RBAC + ABAC 工作身份授权 — live smoke
- 用当前源码打包并启动 `pangu-bootstrap` jar，Flyway 校验 45 个迁移通过，当前 schema version `3.29`，未再出现 V3.27/V3.28 unresolved migration。
- 通过真实 HTTP 调用验证：
  - `POST /auth/login` 登录 `13800000005`；
  - `GET /auth/shadows` 返回 `GOV_OPERATOR` 与新建 `GRID_OPERATOR` 两个工作分身；
  - `POST /auth/switch-shadow` 切换到新建网格员身份成功，返回 `role_key=GRID_OPERATOR`、`effective_data_scope=OWNER_GROUP`，楼栋范围包含 `30005`。

### Error: yaochi sandbox write blocked
- 现象：当前会话 writable root 仅包含 `/Users/juchen/Documents/workspace/pangu`；首次尝试用补丁工具修改 `/Users/juchen/Documents/workspace/yaochi` 被权限层拒绝。
- 原因：`yaochi` 不在当前沙箱可写根；需要显式授权后才能安全写入该仓库。
- 处理口径：不绕过权限写入；拿到授权后再完成管理端页面，并用 `npm run build` 验证。

## Session: 2026-07-02

### 网格员静态角色与网格节点楼栋范围 — backend complete
- 已将网格员对外静态角色键统一为 `GRID_MEMBER`；新增 `V3.30__grid_member_dept_scope.sql` 在既有库中执行 `GRID_OPERATOR -> GRID_MEMBER` 迁移，并同步历史动员权限记录。
- 新增 `sys_dept_building_scope` 作为 `dept_type=5` 网格组织节点的楼栋范围表；`OWNER_GROUP` 登录上下文从该表与历史 `sys_user_building` 并集反查授权楼栋。
- 工作身份授权新增网格节点楼栋范围接口：
  - `POST /api/v1/admin/work-identities/depts/{communityDeptId}/grid-nodes`
  - `GET /api/v1/admin/work-identities/depts/{deptId}/building-scope`
  - `PUT /api/v1/admin/work-identities/depts/{deptId}/building-scope`
- `grid-nodes` 会在居委会 `dept_type=2` 节点下生成 1-5 号 `dept_type=5` 网格组织节点，重复调用不重复建节点。
- `GRID_MEMBER` 分身创建不再写个人 `sys_user_building`，只要求所属网格节点已有楼栋范围；测试断言新建网格分身个人责任田行数为 0。
- 网格楼栋范围写入收紧为 `COMMUNITY_ADMIN + dept_type=2 + G端`；街道超管和业委会主任都不能替网格员配置楼栋范围。业委会主任仍可配置自治侧志愿者/业主代表责任田。
- `docs/权限矩阵.md` 已同步网格员楼栋范围红线：业委会侧不得配置 `GRID_MEMBER`。
- 验证：
  - `mvn -pl pangu-bootstrap -am -Dtest=WorkIdentityAdminTest,BuildingAssignmentTest,SwitchShadowMatrixTest,SysUserRoleTriggerTest -Dsurefire.failIfNoSpecifiedTests=false test`：35 tests，0 failures，0 errors；
  - `mvn test`：536 tests，0 failures，0 errors，1 skipped。

### Error: Flyway 历史迁移误改
- 现象：首次聚焦测试触发 Flyway checksum mismatch，涉及 V1/V1.2/V1.4/V3.x。
- 原因：机械替换 `GRID_OPERATOR -> GRID_MEMBER` 时误改已应用历史迁移。
- 处理口径：恢复历史迁移文本，角色键变更只通过新增 V3.30 表达；以后不得为改名直接修改已应用迁移。

### Error: 30003 楼栋 seed 不一致
- 现象：`WorkIdentityAdminTest` 的 `BeforeEach` 重置网格范围时，`sys_dept_building_scope` 触发器反复拒绝 `building_id=30003`。
- 原因：旧 `sys_user_building` seed 注释/责任田包含 30003，但当前 `c_owner_property` 只有 30001、30002、30005。
- 处理口径：测试重置只使用产权表真实存在的 30001/30002；新增范围验证使用 30005。

## Session: 2026-07-04

### 网格管理标准操作路径 — partial complete
- pangu 后端 `POST /api/v1/admin/work-identities/depts/{communityDeptId}/grid-nodes` 已兼容请求体 `{ "deptName": "1号网格" }`，支持居委会在指定节点下按输入名称新建单个 `dept_type=5` 网格节点；无请求体时保留原批量生成 1-5 号网格兼容行为。
- 新增 `sys_dept_tenant_scope` 作为居委会管辖小区范围来源；网格楼栋候选和保存都被限制在上级居委会管辖 tenant 集合内，`sys_dept_building_scope` 触发器同步兜底校验跨小区范围。
- yaochi 管理端菜单已对齐到【系统管理】下的【组织架构管理】和【用户账号管理】；组织架构管理页已从“一键生成 1-5 号网格”改为“选居委会 + 输入机构名称 + 新增下级”，楼栋范围改为【分配管辖范围】弹窗并按小区 tenant 分组展示。
- 验证：
  - `mvn -pl pangu-bootstrap -am -Dtest=WorkIdentityAdminTest -Dsurefire.failIfNoSpecifiedTests=false test`：12 tests，0 failures，0 errors；
  - `mvn -pl pangu-bootstrap -am -Dtest=RepairWorkOrderFlowTest -Dsurefire.failIfNoSpecifiedTests=false test`：4 tests，0 failures，0 errors；
  - `mvn test`：545 tests，0 failures，0 errors，1 skipped；
  - yaochi `npm run build` 通过，仅保留 Vite chunk size 常规提示；
  - shennong-app `npm run type-check` 通过。

### Error: ProposalHandoverEndToEndTest 正文字段误判
- 现象：全量 `mvn test` 连续两次在 `ProposalHandoverEndToEndTest.generalProposeFrozenDuringHandover_thenAutoRecoversAfterSettlement` 失败，接口返回 403 / 40923“GENERAL/MAJOR 议题立项必须填写正文”，而测试预期 409 / 40926 换届熔断。
- 原因：第一次补测时仍未带正文；第二次使用了错误字段 `contentHtml`，但后端 `ProposeRequest` 的真实字段是 `content`。
- 处理口径：以后补立项测试请求时先看 DTO 字段名，GENERAL/MAJOR 正文统一传 `content`；本次已改为 `content` 后全量通过。

### 网格管理标准操作路径 — complete
- pangu 后端补齐标准三步路径：
  - 第一步：`POST /api/v1/admin/work-identities/depts/{communityDeptId}/grid-nodes` 支持按输入名称新建单个网格节点；
  - 第二步：`sys_dept_tenant_scope` 限定居委会管辖小区，网格楼栋候选与保存都必须落在上级居委会管辖 tenant 集合内；
  - 第三步：新增 `POST /api/v1/admin/work-identities/accounts`，在同一事务中创建自然人账号、绑定 `sys_user` + `sys_user_role`、回填 `last_active_identity_*`，避免新账号无法登录。
- V3.34 增加矛盾调解的网格数据范围边界：`t_owner_dispute.related_property_opid` 关联房产；`GRID_MEMBER` 只读授权 `dispute:audit`，列表按当前网格 `tenant_id + building_id` 过滤，仍禁止审核/裁决。
- yaochi 管理端完成：
  - 菜单对齐【系统管理】→【组织架构管理】/【用户账号管理】；
  - 组织架构管理支持【新增下级】网格和【分配管辖范围】弹窗；
  - 用户账号管理支持【新增用户】并默认网格员角色，也保留给既有自然人新增工作身份；
  - 新增【矛盾调解】页面，按后端 `dispute:audit` 权限进入，网格员只能看到后端返回的本网格范围数据。
- shennong-app 本轮未新增页面；移动端现有催票/维修处理继续依赖后端登录上下文的 `OWNER_GROUP` 楼栋范围，已通过类型检查确认未受契约变更影响。
- 验证：
  - `mvn -pl pangu-bootstrap -am -Dtest=WorkIdentityAdminTest -Dsurefire.failIfNoSpecifiedTests=false test`：13 tests，0 failures，0 errors；
  - `mvn -pl pangu-bootstrap -am -Dtest=WorkIdentityAdminTest,RepairWorkOrderFlowTest,DisputePreAuthorizeMatrixTest,DisputeWorkflowTest,VotingReminderDeliveryDispatchServiceTest,VotingReminderTaskServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`：46 tests，0 failures，0 errors；
  - `mvn test`：546 tests，0 failures，0 errors，1 skipped；
  - yaochi `npm run build` 通过，仅保留 Vite chunk size 常规提示；
  - shennong-app `npm run type-check` 通过。

### Figma 设计对齐：网格组织 / 角色数据范围 / 数据范围分配 — backend progress
- 已按 shennong 设计确认三个菜单的后端契约：
  - 【网格组织管理】需要网格列表、新建、改名、删除空网格、配置跨小区楼栋范围；
  - 【角色与数据范围配置】中网格员保持静态 RBAC 角色 `GRID_MEMBER`，实际行级范围通过网格聚合，不在角色页写死；
  - 【数据范围分配】需要把网格员分配到一个或多个网格节点，并由这些网格聚合 `AllowedBuildingIds`。
- pangu 后端新增网格员-网格分配权威表 `sys_user_grid_dept_scope`；当存在显式分配时，`GRID_MEMBER` 的 `OWNER_GROUP` 楼栋范围优先从分配网格聚合；没有显式分配时兼容原 `sys_user.dept_id` 所属网格。
- 新增接口：
  - `GET /api/v1/admin/work-identities/users/{userId}/grid-nodes`：读取网格员当前分配的网格；
  - `PUT /api/v1/admin/work-identities/users/{userId}/grid-nodes`：替换网格员分配网格，支持多个网格节点。
- `GET /api/v1/admin/work-identities/accounts/search` 新增可选 `roleKey` 过滤，支持【数据范围分配】先按 `GRID_MEMBER` 精准检索网格员账号，再进入网格分配。
- 登录上下文、工作身份回读、投票动员权限激活均已改为读取显式网格分配后的聚合楼栋范围，避免只按 `sys_user.dept_id` 得到单网格范围。
- `WorkIdentityShadowResponse` 新增 `gridNodes`，账号搜索/详情可直接回显网格员当前分配网格；无显式分配时仍回显其 `sys_user.dept_id` 所属网格，显式多网格分配后回显全部分配节点。
- yaochi 管理端补齐设计页对齐：
  - 菜单改为【网格组织管理】、【角色与数据范围】、【数据范围分配】；
  - 【网格组织管理】接入网格新增、改名、删除空网格、按 tenant 分组分配管辖范围；
  - 【角色与数据范围】补齐原设计的双 tab：`权限矩阵配置` 使用真实角色/权限接口展示并授撤权限、配置角色级数据范围；`用户角色分配` 使用工作身份接口搜索自然人账号并创建新工作身份；
  - 【角色与数据范围】将 `OWNER_GROUP` 展示为“按网格 / 楼栋（AllowedBuildingIds）”，并对 `GRID_MEMBER` 明确提示“静态 RBAC 角色，具体网格在数据范围分配维护”；
  - 【数据范围分配】在 `COMMUNITY_ADMIN` 身份下搜索 `GRID_MEMBER`，调用 `GET/PUT /users/{userId}/grid-nodes` 分配一个或多个网格；志愿者/业主代表仍走个人楼栋责任田接口；
  - 前端类型已消费 `WorkIdentityShadowResponse.gridNodes`，账号搜索回显当前分配网格与聚合楼栋数。
- 验证：
  - `mvn -pl pangu-bootstrap -am -Dtest=WorkIdentityAdminTest -Dsurefire.failIfNoSpecifiedTests=false test`：18 tests，0 failures，0 errors；
  - `mvn -pl pangu-bootstrap -am -Dtest=WorkIdentityAdminTest,RoleAdminMatrixTest,RoleAdminQueryServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`：37 tests，0 failures，0 errors；
  - yaochi `npm run build` 通过，仅保留 Vite chunk size 常规提示；
  - `git diff --check` 通过。

### 报修初勘独立状态与现场照片 — complete
- 对照页面原型和流程方案确认：`SURVEYING` 后必须进入独立的 `SURVEY_COMPLETED`，形成初勘结论、风险等级和现场照片，不能从 `VERIFIED` / `ASSIGNED` 直接提交方案估算。
- pangu 新增 `submit-survey` 动作和 V3.57 状态约束；初勘至少上传 1 张、最多 3 张照片，可补充 1 段短视频，已确认 OSS 对象键、大小与 ETag 写入 `SUBMIT_SURVEY` 审计事件。
- shennong-app 重排物业现场初勘页面，补齐照片/短视频入口，并将正式预算明确移交到物业 PC 管理后台处理。
- `submit-plan` 仅允许从 `SURVEY_COMPLETED` 进入，保留初勘结果并只确认维修范围的估算金额与拟使用资金。
- yaochi 与 shennong-app 已把动作严格拆成：已核验 -> 派单 -> 开始初勘 -> 提交初勘 -> 确认维修范围与估算；同一状态不再并列展示跨阶段按钮。
- 验证：
  - `RepairWorkOrderFlowTest`：9 tests，0 failures，0 errors；
  - `mvn clean test`（沙箱外）：583 tests，0 failures，0 errors，1 skipped；
  - yaochi `npm run build` 通过，仅保留 Vite chunk size 常规提示；
  - shennong-app `npm run type-check` 通过；
- Chrome 本地真实页面验证 `VERIFIED` 仅显示“派单”，`ASSIGNED` 仅显示“开始初勘”，`SURVEYING` 显示初勘结论、风险等级、现场照片和“提交初勘”。

### 报修现场附件接入私有 OSS — complete
- pangu 新增 V3.58 `t_repair_attachment`、附件领域模型、仓储和阿里云 OSS Java SDK 适配器；小程序通过 pangu multipart 接口上传，后端以 `PutObject` 写入私有 Bucket，不向客户端暴露 OSS 上传地址。
- 后端强制校验工单可见范围、现场角色、业务阶段、媒体类型和文件大小，并按实际字节计算 `Content-MD5`；OSS 返回 ETag 后直接创建 `READY` 记录。
- 提交位置纠偏或初勘时只能绑定同工单 `READY` 附件，并转为不可删除的 `BOUND`；OSS 写入失败时不会创建附件记录。
- shennong-app 已改为 `Taro.uploadFile` 上传到 pangu；照片/视频不再以 Base64 放进业务 JSON，也不再直接请求 OSS Endpoint。
- 配置已改为环境变量；真实凭证仅写入被 Git 忽略且权限为 600 的 `.env.local`，仓库未保存凭证明文。
- 验证：`mvn test` 584 tests，0 failures，0 errors，1 skipped；shennong-app `npm run type-check` 与 `npm run build:weapp:dev` 通过。
- 真实联调：已使用新 RAM AccessKey 完成 pangu multipart 上传、签名下载和删除，返回均为 `200`；测试对象和数据库记录已清理。

### 报修询价价格边界 — complete
- 物业内部估算改为默认选填，并由社区规则 `repair_estimate_required` 配置是否必填。
- 工单新增可选 `public_ceiling_price`；只有物业显式公开最高限价时，受邀供应商才能看到该金额。
- 供应商列表与报价提交响应改用独立 DTO，不再返回 `planBudget`、`fundSource`、报修人和内部经办字段。
# Progress: 公共报修位置范围与维修专业拆分

- 已确认问题不是“楼栋与专业互斥”，而是当前表单混合了公共维修范围、具体位置和专业分类三种维度。
- 正在核对公共工单创建、位置纠偏、资金路径和供应商服务类别契约，准备先补后端可信字段与回归测试。

## Historical Progress

# Progress: 楼栋维修默认表决方式与动态进度

- 社区设置“自治与财务规则”已增加“楼栋维修默认表决方式”，支持 `C 端在线表决` 与 `微信接龙`，沿用社区规则的读取、编辑和审计权限。
- 新增 V3.64 社区字段及约束；维修规划策略接口同步返回默认渠道，后端未收到工单渠道时使用社区默认值，显式选择不同渠道时记录为工单覆盖。
- 物业在推荐供应商后会看到社区默认渠道，可在发起前调整；表决创建后渠道写入决策记录，不提供中途切换能力。
- 工单进度第 7 节点已按资金来源动态显示“楼栋表决”“业主大会”“无需表决”或“待定路径”，不再合并两种业务流程。
- 验证：社区设置与维修流程聚焦测试 18 tests 全通过；Pangu 全量 `mvn test` 588 tests，0 failures，0 errors，1 skipped；Yaochi `npm run build` 通过，仅保留既有 chunk size 提示；两仓库 `git diff --check` 通过。

## Historical Progress

# Progress: 业主报修卡片进入详情

- 已为全部业主可见维修卡片增加详情导航和右侧箭头，在线待表决工单在列表标记为“待我表决”。
- 新增 `pages/owner/repair-detail/index`，从后端重新读取当前工单，并承接推荐方案、报价附件预览、同意/不同意/弃权、受影响业主验收和完工评价。
- 微信接龙工单或当前账号没有线上表决资格时，详情页不展示线上投票按钮，并提示按楼栋长通知参与或联系物业核对房屋范围。
- 抽取维修状态颜色、四段进度和日期格式化为共享展示模块，列表与详情保持一致。
- 验证：Shennong `npm run type-check`、`npm run build:weapp:dev` 和构建产物中的详情路由/导航处理均通过。

## Historical Progress

# Progress: 报价附件与追加邀价

- 已完成现状核验，开始扩展报价原件 OSS 附件和报价绑定模型。
- 新增 `QUOTE_DOCUMENT` 附件类型和 `V3.61`，物业与供应商均通过 pangu Java OSS SDK 上传报价原件；报价接口只接收附件 ID，后端校验并绑定后从 ETag 生成兼容哈希。
- 首次邀价与追加邀价已拆分；追加动作只列出未受邀企业、强制填写原因，后端拒绝重复邀价。
- 物业代录报价已改为弹窗，包含供应商、含税总价、来源、说明、原件上传及签章确认；供应商工作台也已移除人工文件标识输入。
- Pangu 全量测试通过（585 tests，0 failures，0 errors，1 skipped）；Yaochi 生产构建通过。
- Chrome 已确认询价中页面显示“邀价已发出”、独立“追加邀价供应商”和“代录供应商报价”，弹窗布局、字段和禁用状态正常。

## Historical Progress
# Progress: 维修工单页面降负重构

- 已完成现有页面职责和状态动作组件盘点，开始拆分列表与详情视图。
- 页面拆分和标签分区已完成；热更新过程中曾短暂出现 `SummaryItem is not defined`，原因是组件调用先于补丁中组件定义落盘，完整构建和后续页面加载均正常。
- 工单列表现在是独立首屏，不再默认选中第一条工单；点击行或查看图标进入全宽详情页。
- 详情页保留工单摘要和进度，操作内容拆为“当前任务、工单信息、供应商与报价、流程记录”四个标签。
- 新供应商登记已由四个常驻输入框改为弹窗，当前任务区域只保留当前阶段真正需要的操作。
- Yaochi `npm run build` 通过；Chrome 已完成桌面和 390x844 移动视口验收，最终重新加载后控制台无新增错误。

## Historical Progress
# 2026-07-12 勘验统一提交入口

- 已新增后端 `submit-inspection` 用例：物业现场人员从已提交、待定位、待核验、已核验、已派单或勘验中任一内部状态提交时，后端在同一事务内补齐受理、位置核验、指派和开始勘验等审计事件，最终进入 `SURVEY_COMPLETED`。
- 已将勘验图片和视频允许上传的状态扩展到勘验完成前，并保留提交时后端校验和附件绑定。
- 已把瑶池管理后台的前置按钮改为统一勘验表单，接入真实附件上传；神农物业端也改为同一接口，不再暴露“核验通过”“开始勘验”等细状态按钮。
- 正在执行后端测试、两个前端构建和浏览器验证。
