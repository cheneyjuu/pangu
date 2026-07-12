# Findings: 报修勘验统一提交入口

- 当前进度已把“受理、核验、初勘”合并展示为“勘验”，但动作区仍按 `SUBMITTED -> PENDING_VERIFY -> VERIFIED -> ASSIGNED -> SURVEYING` 要求用户逐个点击，展示与实际办理含义冲突。
- Shennong 现场端已经具备勘验结论、风险等级、照片和视频上传，但仍要先点击核验、开始勘验后才能提交；Yaochi 管理后台只在 `SURVEYING` 显示勘验表单。
- 真实业务需要的是一个“提交勘验记录”用例，两端只是采集渠道不同；受理、位置核验、派单、开始初勘应由后端统一编排并留痕，而不是继续暴露为多个用户动作。
- 现有照片/视频附件只允许在 `SURVEYING` 上传，因此统一入口必须先打开勘验附件上传窗口，或新增能同时上传并提交的后端接口，不能只做前端连续调用。

## Historical Findings

# Findings: 公共报修位置范围与维修专业拆分

- “维修楼栋”描述空间归属，“维修专业”描述服务能力，两者可以并存；原页面真正的问题是没有单独表达楼栋公共部位与小区公共区域。
- 旧后端用 `building_id IS NULL` 推导“待现场定位”，导致已知的小区道路、门岗、中心花园等位置也无法直接登记。
- `PUBLIC_FACILITY` 与给排水、消防、电梯不属于同一分类层级，不能继续作为维修专业默认值。
- Yaochi 使用 `FIRE`，供应商匹配与详情展示使用 `FIRE_PROTECTION`，会造成供应商能力匹配失效；需要服务端统一规范化而不能只改文案。
- Shennong 原本已有楼栋/小区范围选择，但请求没有提交该字段，小区公共区域仍会被后端误判为待现场定位。
- 公共区域范围还决定资金约束：楼栋公共部位不能选择小区公共维修资金，小区公共区域不能选择楼栋维修资金。

## Historical Findings

# Findings: 楼栋维修表决渠道二选一

- 现有 `t_repair_local_decision` 没有渠道字段，默认全部按微信接龙处理；物业端会逐户录入选择并强制提交微信截图哈希。
- 现有 `t_repair_solitaire_entry` 已按 `decision_id + room_id` 唯一，可承载在线逐户当前选择，但需要补充 C 端提交账号、渠道和修改审计边界。
- C 端 `/me/repairs` 只返回本人报修，不包含同楼栋其他公共维修；在线表决必须新增按业主房产范围查询的独立接口。
- 业主身份上下文提供 `accountId + uid + tenantId + L2`，可用 `opid` 校验本人房屋并把选择绑定到确定的 `room_id`，不能由前端直接声明楼栋或面积。
- 同一房屋可能有多个共有权人；在线表决资格必须沿用既有投票代表规则（优先 `is_voting_delegate=1`，再按 `opid` 兜底），否则同一房屋会出现多人覆盖同一票的争议。
- 推荐报价的原件受现有附件权限保护；C 端需要经过在线表决资格校验后获取短时效预览凭证，不能暴露 OSS 长期地址。
- 微信渠道继续由物业完成结构化核验和截图上传；在线渠道由系统保存逐户选择并在物业结束表决时补齐未参与户后统一计票。
- 现有完成接龙 DTO 把明细和截图哈希都声明为必填，会在请求到达业务层前阻断 ONLINE；必须改为由服务按渠道分别校验。
- 现有维修附件种类不含微信截图，物业页面只能填写“文件标识”，不是真实上传；微信渠道应新增受状态约束的截图附件并在完成表决时绑定审计。

## Historical Findings

# Findings: Excel 报价附件转 PDF 预览

- Apache POI 不提供高保真 Excel 到 PDF 渲染；当前需求强调打印/版式预览，使用 LibreOffice Calc 的 `calc_pdf_Export` 更符合目标。
- LibreOffice 官方命令行支持 `--headless --convert-to pdf --outdir`；转换进程应使用独立 `UserInstallation`，避免并发请求争用同一用户 profile。
- 当前上传链路已经持有 Excel 原始字节，但历史附件只能从 OSS 读取；因此对象存储端口需要补充受控读取和派生对象存在性检查。
- 原附件对象键包含 UUID 且上传后不可变，可以用 `<original-key>.preview.pdf` 作为确定性的派生键，无需新增数据库字段；首次生成、后续复用。
- 转换属于预览增强，不能阻塞真实报价原件上传；上传阶段不强制转换，首次预览失败时保留明确下载原件入口。
- 本机已具备 LibreOffice 命令，生产环境仍需通过 `PANGU_LIBREOFFICE_COMMAND` 指向服务器内的 `soffice` 可执行文件。
- 真实样例使用“等线”等 Office 中文字体；仅安装 LibreOffice 不足以保证中文输出。转换进程现在生成独立 Fontconfig 配置，将常见中文字体映射到 `PANGU_EXCEL_PREVIEW_CJK_FONT` 及系统可用回退字体。
- 真实文件 `合生江湾2025年门牌幢收入详情.xlsx` 已完成 OSS 上传、Java 转换、派生 PDF 上传和短时效预览；生成 12 页 PDF，首屏中文、金额、底色和表格边界均可读。
- 派生对象键带渲染版本号 `.preview-v1.pdf`，同一原件重复预览复用转换结果；后续调整渲染策略时可提升版本避免继续使用旧缓存。

## Historical Findings

# Findings: 供应商报价附件弹窗预览

- 当前“查看原件”调用 `/download-url` 后直接 `window.open`，浏览器会根据文件类型和响应头自动下载或跳转，无法提供一致的站内预览体验。
- 附件下载凭证已经在 `RepairAttachmentService` 中完成工单可见性、附件归属、供应商访问范围和 READY/BOUND 状态校验；预览凭证应复用同一校验，不另开无鉴权对象地址。
- 阿里云 OSS Java SDK 可通过 `GeneratePresignedUrlRequest + ResponseHeaderOverrides` 生成短时效预览地址；实测 OSS 不允许该请求覆盖 `Content-Type`，应沿用上传对象已保存的类型，只请求内联展示方式。
- 报价附件允许 PDF、图片、Word 和 Excel；浏览器稳定支持图片/PDF 内嵌，Office 文档应在弹窗中展示元数据并保留显式下载，而不是伪装成可预览。
- 真实 OSS 图片附件返回 200 且浏览器成功解码为 `2610 x 1532`；点击“预览附件”没有创建新标签页或自动下载。
- 390x844 移动端下预览弹窗为 `390 x 844` 全屏，页面 `scrollWidth` 为 390，原图按 362px 宽等比缩放，无横向溢出。

## Historical Findings

# Findings: 维修工单侧边详情页

- 当前 `selected` 存在时组件会提前返回详情页，因此列表被完全替换；改为始终渲染列表，并由受控 `Sheet` 承载详情即可保留搜索、筛选和滚动状态。
- 通用 `SheetContent` 默认固定 `w-3/4 / sm:max-w-sm` 且始终带遮罩，不适合当前包含 14 步进度、报价列表和表单的宽侧页，也会阻断用户点击左侧列表切换工单。
- 适合本场景的形态是非模态、无背景遮罩的右侧宽侧页：桌面约 72vw、最大 1120px，移动端全屏；阻止“点击外部自动关闭”，但不阻断外部列表点击。
- 详情已经收敛为“办理 / 详情与记录”两个工作面，可以原样迁入侧页，不改变业务动作和后端契约。
- 非模态 Sheet 的外部点击默认会触发关闭；为支持直接点击另一行切换工单，使用行标记和短生命周期切换标志区分“切换工单”与普通关闭动作。
- 桌面 1512px 视口下侧页宽约 1089px，左侧仍能看到工单标题与列表上下文；移动端 390px 视口下侧页宽为 390px，页面 `scrollWidth` 也是 390px。

## Historical Findings

# Findings: 维修工单详情信息架构优化

- 全局面包屑目前只读取导航模块和页面标签，缺少详情页可写入的第三层状态。
- 当前详情页将“当前任务、工单信息、供应商与报价、流程记录”拆成四个页签；报价阶段的供应商推荐表单同时存在于“当前任务”，报价资料又在另一页签，用户需要来回切换。
- `SupplierQuoteArchive` 已具备报价加载与原件查看能力，适合直接承载“推荐此供应商”行级操作；推荐方式和理由属于该动作参数，应在确认弹窗中填写。
- 工单信息和流程记录均为只读，可以合并为同一页签连续浏览；当前动作与报价则合并为同一办理工作面。
- 移动端 390x844 实测页面 `scrollWidth` 与视口同为 390px；推荐弹窗会纵向排列表单与按钮，不存在横向溢出或遮挡。
- 浏览器控制 API 将用户已有标签接入放在 `browser.user.claimTab`，页面刷新放在 `tab.reload`；这类分层 API 应先核对能力归属，避免凭命名猜测。

## Historical Findings

# Findings: 发出邀价后的按钮状态

- 后端已将工单从 `PLAN_SUBMITTED` 推进到 `QUOTE_COLLECTING`，但 `ActionPanel` 的 `invitedSupplierDeptIds` 没有在成功后清空，因此三个复选框和“发出邀价”按钮仍保持激活态。
- 原 `doAction` 吞掉异常且始终返回 `void`，子组件无法区分成功与失败，不能安全地只在成功后清空选择。
- 询价阶段允许补充邀请供应商，因此不能永久隐藏按钮；正确交互是无新选择时显示禁用的“邀价已发出”，重新选择后显示“补充发出邀价”。

## Historical Findings

# Findings: 报修业务文案校正

- 页面把 `SUPPLIER_RECOMMENDED` 显示为“已定供应商”，与物业只能形成推荐供应商的业务边界冲突。
- 进度节点“民意/表决”没有区分楼栋维修的楼栋接龙与小区公共维修的业主大会表决。
- “主任确认”遗漏副主任任一人也可确认；“确认并盖章”还会误导为确认人必须亲自盖章，实际是确认后再由业委会成员或确认人完成盖章。
- 资金卡片直接显示 `LOW`、`BUILDING_MAINTENANCE_FUND`，且把楼栋维修资金和小区公共维修资金统称为“共有资金”，不符合已确认业务术语。
- 审计流水直接显示后端动作码，不利于物业按真实业务理解过程。

## Historical Findings

# Findings: 供应商激活后待报价为空

- 93 号激活邀请已成功激活，绑定供应商组织 `4684`（上海麦玺隆机械科技有限公司）和个人工作账号 `1002256`。
- 该企业在 `t_repair_quote_invitation` 中没有任何维修邀价记录；214 号工单仍为 `PLAN_SUBMITTED`，尚未执行“发出邀价”。
- 供应商工作台按 `supplier_dept_id + t_repair_quote_invitation` 查询，账号激活只授予登录身份，不应自动产生维修工单可见权。
- 根因是界面没有充分区分“发送账号激活邀请”和“发出维修邀价”，不是账号绑定或供应商工作台查询错误。

## Historical Findings

# Findings: 供应商企业与账号状态优化

## 2026-07-11 Current Findings
- 页面“待核验”来自 `t_supplier_org_profile.verification_status`，仅表示企业法律主体未核验，与个人账号是否激活无关。
- 当前本地数据：盘古验收维修服务有限公司已激活账号 `13800000031`；上海麦玺隆机械科技有限公司联系人为 `18917761234` 但尚无账号和邀请；海万科物业服务有限公司没有联系人，无法创建在线账号。
- 发出邀价会为有完整联系人的未激活供应商自动创建邀请，但供应商列表响应没有返回邀请编号或账号状态，导致页面无法反馈自动创建结果。
- 最小改造应在供应商列表查询中聚合激活身份与最新有效邀请，而不是把账号状态混入企业核验状态。
- `PENDING_VERIFICATION` 仅表示企业法律主体尚未完成平台审核；供应商个人账号可以已经激活，两者不能共用一个徽标。
- 供应商登录名是激活邀请绑定的个人手机号，不是企业名称或统一社会信用代码；同一企业可有多个独立个人账号。
- 物业没有企业主体审核权限，本次只提高状态透明度，不新增“核验通过”操作。
- 当前没有企业核验操作入口；本轮不把企业核验权错误授予物业，只改善状态语义和账号激活闭环可见性。

## Historical Findings

# Findings: 供应商最小登记

## 2026-07-11 Current Findings
- 当前 REST 请求、应用服务和 `t_supplier_org_profile` 同时强制统一社会信用代码、联系人和手机号非空，不能只按企业名称登记。
- 邀价动作会自动调用 `ensureContactInvitation`；如果联系人缺失仍沿用现有校验，会导致整个邀价事务失败，必须改为“无联系人则跳过账号邀请”。
- PostgreSQL 唯一列允许多个 `NULL`，统一社会信用代码放宽为空后仍能保持已填写代码的全局唯一性。
- 临时供应商需要按“当前租户 + 精确企业名称 + 未填写统一社会信用代码”复用，并允许同一登记接口补齐缺失资料，避免重复组织。
- 供应商签约前已有独立企业核验守卫，本次只放宽登记和邀价，不降低签约核验要求。

## Historical Findings

# Findings: 报修治理路径自动分流

## 2026-07-11 Current Findings
- Yaochi 在同一个 `PLAN_SUBMITTED` 状态块中渲染供应商登记/邀价，又在页面下方独立渲染“治理路径判定”，所以两个互斥动作同时出现。
- 后端 `routePlan` 仅按“物业内部资金且金额小于 5 万”决定 `APPROVED`，其他情况直接进入 `GOVERNANCE_PENDING`；它没有检查维修范围、邀价、定商、接龙/业主大会、报审或审价。
- 楼栋维修使用 `BUILDING_MAINTENANCE_FUND` 时，当前 `routePlan` 会直接跳到 `GOVERNANCE_PENDING`，绕过完整楼栋维修流程，属于状态机漏洞。
- `routePlan` 的合理适用面仅是无需供应商/无需治理表决的私有或物业包干维修快捷路径；楼栋和小区公共维修必须进入供应商与治理流程。
- 最终不保留独立 `routePlan`：物业包干维修在 `submitPlan` 内自动进入 `APPROVED`，其他合法资金来源停留在供应商选择阶段。
- 定商后的治理入口按资金来源收口：`BUILDING_MAINTENANCE_FUND` 只能楼栋接龙，`COMMUNITY_MAINTENANCE_FUND` / `PUBLIC_REVENUE` 只能业主大会。

## Historical Findings

# Findings: 当前供应商账号激活闭环

## 2026-07-11 Current Findings
- `V3.53` 已建 `t_supplier_activation_invitation`，但当前 Java/MyBatis/Controller 无创建或激活逻辑。
- 本地数据库没有 `SERVICE_PROVIDER_MANAGER` 或 `SERVICE_PROVIDER_STAFF` 用户，供应商工作台无法真实登录验收。
- 已实现供应商组织登记、邀价、在线报价端点和独立供应商菜单，但组织登记只创建 `sys_dept + t_supplier_org_profile + t_supplier_tenant_relation`。
- 需要复用现有 `t_account / sys_user / sys_user_role` 账号体系，避免平行自建密码系统；具体账号创建与登录约束需继续核验。
- 现有账号模型已经满足“组织 + 个人账号”：`sys_dept` 是供应商组织，`t_account` 是手机号唯一自然人，`sys_user` 是自然人在组织内的工作身份，`sys_user_role` 绑定供应商角色；无需新增平行 membership 表。
- `AuthService.login` 使用手机号 + 短信验证码，不存在密码字段；激活成功只需创建/复用 `t_account`、新增供应商 `sys_user`、绑定角色并回填 last-active identity。
- `WorkIdentityRepository` 已具备账号、工作身份、角色绑定与 last-active identity 的写能力，但其应用服务受后台操作员权限约束，不适合直接复用为公开邀请激活入口；供应商激活应有独立应用服务并复用 repository 端口。
- 供应商组织 `sys_dept.dept_type=9 / dept_category=S / tenant_id=NULL`，符合跨租户服务商模型；具体可见工单由 `t_repair_quote_invitation.supplier_dept_id` 限定。
- 系统没有独立“发送登录验证码”接口，但已有按环境切换的 `SmsVerificationStrategy`：dev/test 固定 `123456`，prod 从 Redis 一次性消费验证码。供应商激活可复用同一验证策略，避免另造验证码规则。
- 供应商部门 `tenant_id=NULL` 时 `UserContext` 的 tenant 也为 null；供应商业务端点必须继续按 `supplier_dept_id` 做授权，不能错误依赖 tenant。
- 公开激活入口需要加入 SecurityConfig 白名单；安全边界应是“受邀手机号 + 短信验证码 + 未过期且未消费的邀请”，不应把默认密码或共享 token 暴露给物业。
- `sys_user_role` 已允许记录 `granted_by`，邀请激活时可记录原始邀请人，保留授权审计链。
- `sys_user_role` 以 `user_id` 为主键落实“一工作身份一角色”；同一供应商多人经办应共享供应商 `sys_dept`，每个自然人各建一条 `sys_user`，无需新建 Membership 表。
- 现有 `SmsVerificationStrategy` 位于 interfaces 层并直接抛 Web 异常，不适合由 application 激活事务复用；应抽为 application 端口，由 dev/test 与 prod 适配器实现一次性校验。
- 供应商组织注册与邀价目前只写组织/租户关系和报价邀请，不会创建账号激活邀请；需要补独立邀请接口，并在邀价时为未开通联系人确保存在有效邀请。
- 激活成功后继续通过既有 `/api/v1/auth/login` 签发 JWT，激活接口不同时承担会话签发职责。
- 仅删除供应商角色的旧 `sys_role_menu` 绑定不足以隔离菜单：现有查询会把 `required_permission / required_any_permissions / required_role_keys` 全为空的内部通用菜单开放给所有角色。
- 导航隔离应是角色策略而不是供应商硬编码；新增 `sys_role.navigation_isolated` 后，外部协作角色只接收显式 `sys_role_menu`，供应商最终只看到 `supplier-service / supplier-workbench`。
- 本地验收账号已通过真实邀请激活链创建：`13800000031`，供应商组织 `盘古验收维修服务有限公司`，角色 `SERVICE_PROVIDER_STAFF`，可见邀价工单 `RO-20260708-000099`。

## Historical Findings

# Findings: 选举闭环对齐推进

## Current State
- `docs/选举闭环对齐路线图.md` 已明确五个梯度：A 权限矩阵、B 议题状态机、C HANDOVER_LOCK 完整化、D 多分身、E 监控基线/Merkle。
- 当前工作树已有 A 阶段后端改动：`V3.5__election_role_realignment.sql`、`ProposalLifecycleService` 护栏、错误码/translator、相关测试。
- 当前工作树已完成 B 阶段后端最小切片：`V3.6__voting_subject_dual_review.sql`、双签 domain 状态动作、`ProposalReviewService`、controller 端点、错误码和测试。
- 当前路线图已把 B 阶段关键决策落文档：仅 ELECTION 双签；候选人审查独立；Waiver 独立；不引入 APPROVED 中间态，街道办终审通过直接 PUBLISHED。

## A 阶段已观察到的实现
- `V3.5__election_role_realignment.sql` 新增 role_id=14 `GOV_OPERATOR`，授予 `voting:subject:create`、`voting:subject:audit`、`candidate:nominate`、`waiver:submit`、`identity:switch`。
- `V3.5__election_role_realignment.sql` 给 `GOV_SUPER_ADMIN` 补 `voting:subject:publish`（该历史授权已由 V3.27 回收，ELECTION 发布改为只走 `street-review`）。
- `V3.5__election_role_realignment.sql` seed `13800000005` / `800005` 为 `GOV_OPERATOR`。
- `ProposalLifecycleService.propose` 对 ELECTION 增加源文件约束：仅 G 端 `GOV_OPERATOR` 可立项；街道办 / 居委会进入后续终审 / 初审，不直接新建 ELECTION。
- `ProposalLifecycleService.publish` 对 ELECTION 增加街道办独占护栏，并要求候选人池至少 1 名 APPROVED（该早期实现已由 Phase 80 收口为直接拒绝 ELECTION；候选人池校验移到 `street-review`）。

## B 阶段已完成后端切片
- Flyway `V3.6__voting_subject_dual_review.sql` 已放开 `t_voting_subject.status` 取值 7/8，增加 `review_history JSONB`，新增双签权限点并授权。
- Domain 已新增 `PENDING_COMMITTEE(7)`、`PENDING_STREET(8)`，并在 `VotingSubjectActions` 增加 5 个 ELECTION 专属双签动作。
- Application 已新增 `ProposalReviewService`；`streetApprove` 会校验候选人池至少 1 名 APPROVED；5 个双签动作都会追加 `review_history` 审批轨迹。
- Interfaces 已在 `SubjectAdminController` 增加 `submit-for-review`、`committee-review`、`street-review`。
- Web errors 已新增 `SUBJECT_NOT_PENDING_COMMITTEE`、`SUBJECT_NOT_PENDING_STREET`、`REVIEW_REJECT_REASON_REQUIRED` 映射。
- Tests 已新增 `ProposalReviewServiceTest`，并扩展 `ElectionWorkflowEndToEndTest`、`ProposalLifecycleServiceTest`、`VotingEndpointMatrixTest`。

## B 阶段剩余缺口
- yaochi 管理端已对接 B 阶段状态展示与三步双签操作。
- shennong-app C 端已同步新增状态 / 登录字段类型，但不向业主展示双签中状态。

## yaochi 管理端观察
- `/Users/juchen/Documents/workspace/yaochi` 是管理端，已有 A 阶段前端改动。
- yaochi 已有 A 阶段痕迹：`gov_operator` 已进入 `types.ts`、`auth.ts`、`store.tsx`、`nav.ts`。
- `SubjectProposal.tsx` 已把 ELECTION 立项 UI 收紧为仅 `GOV_OPERATOR + dept_type IN (2,5)` 显示选举类型。
- `voting.ts` 已补 `PENDING_COMMITTEE / PENDING_STREET` 与 `submitSubjectForReview`、`committeeReviewSubject`、`streetReviewSubject` 三个 API 封装。
- `SubjectProposal.tsx` 已纳入 `PENDING_COMMITTEE / PENDING_STREET`，并把 ELECTION 的直接公示按钮替换成提交初审 / 居委会初审 / 街道办终审按钮。

## pangu dept_type 精确化
- `sys_dept.dept_type` 已从 `UserContextMapper.xml` 装配进 `UserContext.deptType`。
- `AuthService.login` 已在 `user_info` 中返回 `dept_type`，JWT 仍保持不嵌角色/权限/部门元数据。
- `ProposalLifecycleService.propose` 已从 `roleKey=GOV_OPERATOR + deptCategory=G` 收紧为 `roleKey=GOV_OPERATOR + deptType IN (2,5)`。
- `ProposalLifecycleServiceTest` 已新增 `GOV_OPERATOR` 挂党组织 `dept_type=6` 时拒绝 ELECTION 立项的负例。
- `ControllerIntegrationTest` 已断言登录响应中的 `dept_type` 来自真实 `UserContextLoader` 装配。

## shennong-app C 端观察
- `/Users/juchen/Documents/workspace/shennong-app` 是 C 端小程序。
- C 端 owner 列表只展示 `PUBLISHED / VOTING / CLOSED / SETTLED`，与后端业主可见议题口径一致，`PENDING_COMMITTEE / PENDING_STREET` 不应暴露给业主。
- 已完成最小类型兼容：`src/lib/auth.ts` 的 `UserInfo` 增加 `dept_type`，`src/lib/owner-voting.ts` 的 `SubjectStatus` 增加 `PENDING_COMMITTEE / PENDING_STREET`，`voting-list` 补空态 label。

## Verification Notes
- 聚焦测试命令需要带 `-am`，否则 pangu-bootstrap 可能使用未重编译的上游模块旧字节码。
- 沙箱内 `mvn clean test` 会因本地 PostgreSQL/Redis 与 Mockito self-attach 受限失败；完整回归需要提升权限运行。
- 最新完整后端回归：`mvn clean test`，440 tests，0 failures，0 errors，1 skipped。
- 最新管理端回归：yaochi `npm run build` 通过。
- 最新 C 端回归：shennong-app `npm run type-check` 通过。

## C-mini HANDOVER_LOCK 进展
- 当前数据库迁移没有独立 `sys_tenant` 表，因此 C-mini 没有按原路线图直接改 `sys_tenant`，而是新增 `t_tenant_term_state` 专用表承载 `NORMAL / HANDOVER_LOCK`。
- `HandoverCircuitBreaker` 已变为“持久任期锁优先 + 在途 ELECTION 查询派生兜底”。
- `VotingApplicationService.settle` 会在 ELECTION 结算成功后写入 `HANDOVER_LOCK`，并记录触发锁定的 `subjectId`。
- `TenantTermLockService.confirmHandover` + `POST /api/v1/handover/confirm` 已提供街道办备案通过恢复 `NORMAL` 的最小闭环。
- yaochi 已新增 `confirmHandover()` API；`SubjectProposal` 保留 SETTLED ELECTION 并显示街道办“备案通过”动作。
- C-mini 验证：
  - pangu 聚焦：`mvn -pl pangu-bootstrap -am -Dtest=HandoverCircuitBreakerTest,ElectionProposeAndRouterTest,VotingEndpointMatrixTest -Dsurefire.failIfNoSpecifiedTests=false test`，30 tests，0 failures，0 errors。
  - pangu 全量：`mvn clean test`，428 tests，0 failures，0 errors，1 skipped。
  - yaochi：`npm run build` 通过，仍有 Vite chunk-size warning。
- C 第一轮闭环已完成：任期锁、三类资金熔断第一切片、备案通过恢复 NORMAL、老主任密钥回收 mock 钩子均已落地并验证。

## C 资金侧盘点
- 当前仓库没有路线图中点名的 `FundExpenseService / TrustFundService / MaintenanceFundService` 三个 application 写服务。
- 已存在 V2.2 维修资金账户与流水表：`t_maintenance_fund_account`、`t_fund_ledger_entry`；迁移注释写明“动态总分平衡由 ApplicationService 在动账时维护”，但当前只实现了 `FundLedgerQueryGateway` 只读聚合。
- 已存在 `LockEntityType.FUND_LEDGER_PUBLISH` 和治理锁全链路测试，但它覆盖的是发布锁，不是维修资金真实动账。
- 现有财务公示 `FinanceDisclosureApplicationService.lockAndPublish` 已接入 `HandoverCircuitBreaker`，但这是公示发布熔断，不是资金动账熔断。
- 维修资金支取、公共收益划拨、信托制双签动账已先落在既有维修资金账户/流水模型；信托制动账用 `TRUST_FUND_PAYMENT` 治理锁证明双签完成。

## C 维修资金支取熔断进展
- 已新增 `TenantTermLockGuard`，阈值来自 `platform.handover.large-amount-threshold`，默认 `10000.00`；金额 `>= 阈值` 判定为大额。
- 已新增 `MaintenanceFundApplicationService.recordMaintenanceExpense` 作为维修资金支取真实写路径：
  - `SELECT ... FOR UPDATE` 锁定 `t_maintenance_fund_account`；
  - 校验 tenant、金额、可用余额；
  - 扣减 `total_balance`、递增 `version`；
  - 写入 `t_fund_ledger_entry`，`business_type=4`，`direction=2`。
- 已新增 `MaintenanceFundHandoverGuardTest`：
  - HANDOVER_LOCK + `10000.00` 拒绝，余额/流水不变；
  - HANDOVER_LOCK + `9999.99` 放行，余额扣减并写流水；
  - 无 HANDOVER_LOCK + `10000.00` 放行。
- 最新后端全量验证：`mvn clean test`，431 tests，0 failures，0 errors，1 skipped。

## C 公共收益划拨熔断进展
- 已新增 `PublicRevenueTransferCommand` 与 `MaintenanceFundApplicationService.recordPublicIncomeTransfer`，作为公共收益划拨入维修资金账户的第一条真实写路径。
- 该写路径复用 `TenantTermLockGuard`：
  - HANDOVER_LOCK + `10000.00` 公共收益划拨拒绝，账户余额/流水不变；
  - HANDOVER_LOCK + `9999.99` 公共收益划拨放行，增加账户余额并写流水。
- `MaintenanceFundAccountRepository` / mapper 已新增 `credit` 乐观版本更新，公共收益划拨流水写入 `t_fund_ledger_entry`，`business_type=3`，`direction=1`。
- 最新聚焦验证：`mvn -pl pangu-bootstrap -am -Dtest=MaintenanceFundHandoverGuardTest -Dsurefire.failIfNoSpecifiedTests=false test`，5 tests，0 failures，0 errors。
- 最新后端全量验证：`mvn clean test`，433 tests，0 failures，0 errors，1 skipped。

## C 信托制双签动账熔断进展
- 已新增 Flyway `V3.8__trust_fund_payment_lock_entity.sql`，将 `t_governance_lock.chk_lock_entity_type` 扩展为允许 `TRUST_FUND_PAYMENT`。
- `LockEntityType` 已新增 `TRUST_FUND_PAYMENT`，`LockMatrixIntegrationTest` 已覆盖 lock -> committeeSign -> streetSign -> verifyLocked 全链路。
- 已新增 `TrustFundDisbursementCommand` 与 `MaintenanceFundApplicationService.recordTrustFundDisbursement`：
  - 先执行 `HANDOVER_LOCK + 大额阈值` 守卫；
  - 再校验对应 `TRUST_FUND_PAYMENT` 治理锁已 `FULLY_UNLOCKED`；
  - 锁定账户、校验 tenant / 可用余额、扣减余额、写流水；
  - 流水写入 `business_type=7`，`direction=2`。
- `MaintenanceFundHandoverGuardTest` 已覆盖：
  - 未完成双签的信托付款拒绝且不写流水；
  - 双签完成后 `9999.99` 小额信托动账放行；
  - HANDOVER_LOCK 下即使已双签，`10000.00` 大额信托动账仍熔断。
- 最新聚焦验证：`mvn -pl pangu-bootstrap -am -Dtest=MaintenanceFundHandoverGuardTest,LockMatrixIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`，15 tests，0 failures，0 errors。
- 最新后端全量验证：`mvn clean test`，437 tests，0 failures，0 errors，1 skipped。

## C 老主任密钥回收 mock 进展
- 已新增 `CommitteeKeyRevocationGateway` 领域端口，表达“街道办备案通过后回收老主任密钥”的副作用。
- 已新增 `MockCommitteeKeyRevocationGateway` 日志型 infrastructure 实现；当前不引入真实密钥/证书表。
- `TenantTermLockService.confirmHandover` 已在 `releaseHandoverLock` 之后调用 `revokeOutgoingDirectorKeys(tenantId, confirmedByUserId)`。
- 已新增 `TenantTermLockServiceTest`：
  - ELECTION 结算触发 HANDOVER_LOCK，但不触发密钥回收；
  - GENERAL 不触发 HANDOVER_LOCK；
  - confirmHandover 先释放任期锁，再触发老主任密钥回收 mock。
- 最新聚焦验证：`mvn -pl pangu-bootstrap -am -Dtest=TenantTermLockServiceTest,HandoverCircuitBreakerTest,VotingEndpointMatrixTest -Dsurefire.failIfNoSpecifiedTests=false test`，29 tests，0 failures，0 errors。
- 最新后端全量验证：`mvn clean test`，440 tests，0 failures，0 errors，1 skipped。

## D-mini 多分身切卡后端进展
- `sys_user.uk_account_dept` 已支持同一 `account_id` 在不同部门下拥有多个 SYS_USER 分身；`sys_user_role` 仍保持 `user_id` 单列 PK，即“每个分身一条角色绑定”，无需改主键。
- 已新增 Flyway `V3.9__seed_identity_shadow_switch.sql`：给刘主任 `account_id=999803` 新增网格员分身 `user_id=800006`，角色 `GRID_OPERATOR`，并绑定楼栋。
- 已新增 Flyway `V3.10__adjust_identity_shadow_seed_assignment.sql`：将 800006 的楼栋占用迁到隔离楼栋 `39999`，避免干扰责任田基线楼栋 `30001/30002`。
- 已新增 `IdentityShadowMapper` / XML，用于查询当前自然人名下可用 SYS_USER 分身列表与单个分身归属。
- 已新增 `GET /api/v1/auth/shadows` 与 `POST /api/v1/auth/switch-shadow`：
  - shadows 返回当前账号所有可用 SYS_USER 分身及 `role_key / dept_name / active`；
  - switch-shadow 校验 `account_id + targetUserId` 归属后刷新 JWT，并回填 `t_account.last_active_identity_id`。
- `UserContextLoader` 已收紧装配查询：SYS_USER / C_USER 都按 `account_id + activeIdentityId` 加载，避免伪造 token 只替换 `activeIdentityId` 越权加载其他账号身份。
- D-mini seed 新增第二个网格员后，`BuildingAssignmentTest` 的同角色互斥场景已从旧的“只有一个网格员所以幂等”改为真实验证“800006 分到 30001 时被 800004 占用拒绝”。
- 最新聚焦验证：
  - `mvn -pl pangu-bootstrap -am -Dtest=SwitchShadowMatrixTest,SwitchTenantMatrixTest,ControllerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`，13 tests，0 failures，0 errors，1 skipped。
  - `mvn -pl pangu-bootstrap -am -Dtest=BuildingAssignmentTest,SwitchShadowMatrixTest -Dsurefire.failIfNoSpecifiedTests=false test`，21 tests，0 failures，0 errors。
- 最新全量验证：`mvn clean test`，443 tests，0 failures，0 errors，1 skipped。
- yaochi 管理端已同步 Topbar 切卡 UI：真实加载 `/auth/shadows`，调用 `/auth/switch-shadow` 后刷新 token / user_info / 菜单权限；`npm run build` 通过，仍只有 Vite chunk-size warning。
- shennong-app 本轮无 C 端可见行为变化。

## E1 分母冻结 / Merkle 存证进展
- 源文件 `/Users/juchen/Documents/Notes/选举闭环.md` 要求：创建选举时立即触发房产清洗并冻结分母 Merkle root，之后产权转移/拆分不改变该次选举分母。
- 当前仓库 V2.3 已存在 `t_voting_denominator_snapshot` 与 `aggregate_hash`，且 `DefaultVotingDenominatorResolver` 已能按行级 SHA256 计算 Merkle root；E1 不新增迁移，改为复用该表并把冻结时点前移。
- 已完成后端改动：
  - `ProposalLifecycleService.propose` 对 ELECTION 议题插入后立即调用 `VotingDenominatorResolver.resolve`。
  - `DefaultVotingDenominatorResolver` 先读既有快照；同一 `subject_id` 已存在快照时直接复用，不再刷新分母和行级明细。
  - `VotingProgressQueryService` 进行中进度优先使用冻结分母，结算态补充冻结 Merkle 证据。
  - `SubjectProgressResponse` 新增 `denominatorSnapshotId / denominatorMerkleRoot`。
- 已完成三仓同步：
  - yaochi `SubjectProgress` 类型新增 `denominatorSnapshotId / denominatorMerkleRoot` 可空字段。
  - shennong-app `SubjectProgress` 类型新增同名可空字段。
- 关键测试覆盖：
  - ELECTION 立项即生成分母快照，`aggregate_hash` 长度 64。
  - 立项后新增房产不改变该议题进度分母，进度继续返回冻结快照 ID 与 Merkle root。
  - GENERAL 立项不强制调用分母解析，避免非选举议题行为扩大。
  - E2E 选举流程把业主/房产 seed 前移到立项前，并断言快照总面积、总人数、Merkle root。
- 最新验证：
  - pangu 聚焦：`mvn -pl pangu-bootstrap -am -Dtest=ElectionProposeAndRouterTest,VotingProgressQueryTest,ProposalLifecycleServiceTest,VotingProgressCalculatorTest,ProposalHandoverGuardTest -Dsurefire.failIfNoSpecifiedTests=false test`，40 tests，0 failures，0 errors。
  - pangu E2E：`mvn -pl pangu-bootstrap -am -Dtest=ElectionWorkflowEndToEndTest -Dsurefire.failIfNoSpecifiedTests=false test`，1 test，0 failures，0 errors。
  - pangu 全量：`mvn clean test`，444 tests，0 failures，0 errors，1 skipped。
  - yaochi：`npm run build` 通过，仍有既有 Vite chunk-size warning。
  - shennong-app：`npm run type-check` 通过。

## E2a 投票写入侧监控基线进展
- 当前投票写入链路集中在 `VoteSubmissionService.cast`：所有闸门通过后写 `t_vote_item`，此前没有监控侧写入。
- 已新增 `VoteCastMonitorGateway` 领域端口与 `VoteCastEvent`：
  - 应用层只在成功写票后发布监控事件；
  - 通过 `TransactionSynchronization.afterCommit` 在主事务提交后记录 Redis 基线；
  - 没有事务同步时立即记录，便于单测。
- 已新增 `RedissonVoteCastMonitorGateway`：
  - `bf:vote-cast:{subjectId}`：Bloom 基线，元素为 `opid:targetId`；
  - `counter:vote-cast:{subjectId}:total`：成功写票总数；
  - `counter:vote-cast:{subjectId}:unsigned`：无签名票数，作为纸票/线下票候选基线；
  - `counter:vote-cast:{subjectId}:last-at`：最近一次写票时间；
  - `counter:vote-cast:{subjectId}:rapid-interval`：相邻写票间隔小于 30 秒的次数。
- 监控写入失败只打 warn，不影响投票主链路。
- 测试覆盖：
  - `VoteSubmissionServiceTest` / `ElectionVoteSubmissionTest`：成功写票才调用监控端口，重复/失败投票不调用。
  - `VoteCastMonitorGatewayIntegrationTest`：真实 Redisson 写 Bloom、total、unsigned、rapid-interval。
- 最新验证：
  - pangu 聚焦：`mvn -pl pangu-bootstrap -am -Dtest=VoteSubmissionServiceTest,ElectionVoteSubmissionTest,VoteCastMonitorGatewayIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`，19 tests，0 failures，0 errors。
  - pangu 全量：`mvn clean test`，445 tests，0 failures，0 errors，1 skipped。
- 当时剩余 E2b：
  - 已补 `unsigned / total` 阈值判定与管理端告警查询面；
  - 当时仍未引入明确 `vote_channel` 字段，“纸票/线下票”继续由 `signatureHash` 为空推断；该临时口径已由后续显式 `vote_channel` 替换。

## E2b 监控告警查询面进展
- 已新增 `VoteCastMonitorSnapshot` 领域视图，表达总票、无签名票、无签名占比、阈值、快速连续写票计数及告警结果。
- `VoteCastMonitorGateway` 已新增 `loadCounters(subjectId)`，`RedissonVoteCastMonitorGateway` 从既有 Redis 计数器读取 `total / unsigned / rapid-interval`。
- 已新增 `VoteMonitorQueryService`：
  - 先按 `subjectId + tenantId` 校验议题归属；
  - `unsignedRatio = unsigned / total`，保留 4 位小数；
  - `unsignedRatio > platform.voting.monitor.unsigned-ratio-threshold` 触发纸票候选占比告警；
  - `rapidIntervalCount >= platform.voting.monitor.rapid-interval-threshold` 触发快速连续写票告警。
- `SubjectAdminController` 已新增 `GET /api/v1/voting-subjects/{subjectId}/monitor`，权限为 `voting:subject:audit`，响应 DTO 为 `VoteMonitorResponse`。
- `application.yml` 已新增默认阈值：
  - `platform.voting.monitor.unsigned-ratio-threshold: "0.30"`
  - `platform.voting.monitor.rapid-interval-threshold: 1`
- yaochi 管理端已同步 API 契约：`src/app/lib/voting.ts` 新增 `VoteMonitor` 类型与 `getSubjectMonitor(subjectId)`。
- shennong-app 本轮无 C 端可见行为变化，不需要同步。
- 最新验证：
  - pangu 聚焦：`mvn -pl pangu-bootstrap -am -Dtest=VoteMonitorQueryServiceTest,VoteCastMonitorGatewayIntegrationTest,VotingEndpointMatrixTest -Dsurefire.failIfNoSpecifiedTests=false test`，29 tests，0 failures，0 errors。
  - pangu 全量：`mvn clean test`，450 tests，0 failures，0 errors，1 skipped。
  - yaochi：`npm run build` 通过，仍有既有 Vite chunk-size warning。

## E3 C1-C5 拒绝理由码 + JSONB 证据链进展
- 源文件要求“G 端拒绝必须挂 C1-C5 五种客观理由 + JSONB 证据链”；本轮已覆盖 Waiver 与候选人审查两类拒绝路径。
- 后端新增统一校验：
  - `ElectionRejectReasonCode` 只允许 `C1 / C2 / C3 / C4 / C5`。
  - `RejectEvidencePolicy` 要求拒绝时必须提供合法 reason code 与非空 JSON object；通过时不要求证据。
  - 新增错误码 `REJECT_REASON_CODE_REQUIRED(40952)` 与 `REJECT_EVIDENCE_REQUIRED(40953)`，HTTP 400。
- 受影响接口：
  - `POST /api/v1/election-candidates/{id}/party-review`
  - `POST /api/v1/election-candidates/{id}/review`
  - `POST /api/v1/waivers/{id}/committee-review`
  - `POST /api/v1/waivers/{id}/street-review`
- 数据库迁移 `V3.11__election_reject_reason_evidence.sql` 已新增：
  - `t_election_candidate.reject_reason_code / reject_evidence_json / reject_reviewer_user_id / reject_review_stage / rejected_at`。
  - `t_party_ratio_waiver.committee_reject_reason_code / committee_reject_evidence_json / street_reject_reason_code / street_reject_evidence_json`。
  - C1-C5 check constraint 与 JSONB object 非空约束。
- 候选人返回 `CandidateResponse` 已透出拒绝理由码、证据、审核人、审核阶段；Waiver 返回 `WaiverResponse` 已透出居委会/街道拒绝理由码与证据。
- yaochi 管理端已同步候选人拒绝 API 契约，拒绝时采集 C1-C5 reason code 与证据 note 后提交；当前用 `window.prompt` 完成最小交互，后续可替换为正式 Dialog。
- shennong-app 本轮无 C 端可见行为变化，不需要同步。
- 最新验证：
  - pangu 聚焦：`mvn -pl pangu-bootstrap -am -Dtest=ElectionCandidateServiceTest,ElectionCandidateEndpointMatrixTest,ElectionWorkflowIntegrationTest,ElectionWorkflowEndToEndTest -Dsurefire.failIfNoSpecifiedTests=false test`，38 tests，0 failures，0 errors。
  - pangu 全量：`mvn clean test`，454 tests，0 failures，0 errors，1 skipped。
  - yaochi：`npm run build` 通过，仍有既有 Vite chunk-size warning。

## E4 Clock Suspend 进展
- 当前倒计时入口已确认：
  - `VotingOpenScheduler` 扫描 `status=PUBLISHED AND vote_start_at <= now`，调用 `ProposalLifecycleService.openVoting`。
  - `VotingDeadlineScheduler` 扫描 `status=VOTING AND vote_end_at < now`，调用 `VotingApplicationService.settle`。
  - 因此 Clock Suspend 直接作用在 `vote_start_at / vote_end_at`，比新增独立倒计时引擎更贴合现状。
- 后端已新增 Flyway：
  - `V3.12__voting_clock_suspend.sql`：`t_voting_subject.clock_suspended_at / clock_suspended_by_subject_id`，并给 active scheduler 扫描建立部分索引。
  - `V3.13__voting_clock_suspend_delete_policy.sql`：将自引用 FK 改为 `ON DELETE SET NULL`，避免删除触发换届议题时被暂停来源引用阻挡。
- `TenantTermLockService.engageAfterElectionSettled` 已在 ELECTION 结算触发 HANDOVER_LOCK 后暂停同租户内 `GENERAL/MAJOR + PUBLISHED/VOTING` 议题倒计时。
- `TenantTermLockService.confirmHandover` 已在备案恢复 NORMAL 前先恢复暂停倒计时：
  - PUBLISHED：顺延 `vote_start_at` 与 `vote_end_at`；
  - VOTING：顺延 `vote_end_at`，不改已发生的 `vote_start_at`；
  - 恢复后清空暂停标记。
- scheduler 查询已排除 `clock_suspended_at IS NOT NULL` 的议题，确保暂停期间不会自动开票或自动截止结算。
- `AdminSubjectResponse` / `OwnerSubjectResponse` 已透出 `clockSuspendedAt / clockSuspendedBySubjectId`；yaochi `AdminSubject` 与 shennong-app `OwnerSubject` 类型已同步。
- 测试覆盖：
  - `TenantTermLockServiceTest` 验证 ELECTION 结算触发暂停、GENERAL 不触发、备案恢复先 resume 再 release/revoke。
  - `ElectionWorkflowIntegrationTest.clockSuspend_handoverLockPausesAndResumesPublishedVotingSubjects` 验证真实 DB 暂停、scheduler 排除、恢复顺延时间。
- 最新验证：
  - pangu 聚焦：`mvn -pl pangu-bootstrap -am -Dtest=ElectionWorkflowEndToEndTest,DataScopeTest,ElectionWorkflowIntegrationTest,TenantTermLockServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`，12 tests，0 failures，0 errors。
  - pangu 全量：`mvn clean test`，455 tests，0 failures，0 errors，1 skipped。
  - yaochi：`npm run build` 通过，仍有既有 Vite chunk-size warning。
  - shennong-app：`npm run type-check` 通过。

## E 后续：显式 vote_channel 写入契约进展
- 源文件与路线图要求：替代 E2 阶段临时的 `signatureHash == null` 纸票/线下票推断，显式区分线上票、纸票、线下代录票。
- 后端已新增 `VoteChannel`：`ONLINE / PAPER / OFFLINE_PROXY`，数据库值为 `1 / 2 / 3`。
- Flyway `V3.14__vote_channel.sql` 已为 `t_vote_item` 新增 `vote_channel SMALLINT NOT NULL DEFAULT 1`、check constraint 与 `(subject_id, vote_channel)` 索引；既有历史票默认按 C 端线上票处理。
- 投票提交链路已贯通：
  - `CastVoteCommand` / `CastVoteRequest` 新增 `voteChannel`，旧构造器与旧客户端缺省为 `ONLINE`。
  - `VoteItem` 新增 `voteChannel`，保留旧 5 参数构造器，避免计票引擎测试调用面破坏。
  - `VoteItemRow` / MyBatis / repository 已读写 `vote_channel`。
  - `VoteSubmissionService` 写票后把显式通道带入 `VoteCastEvent`。
- Redis 监控口径已调整：
  - `counter:vote-cast:{subjectId}:unsigned` 继续沿用旧 key 名，但语义升级为显式 `PAPER / OFFLINE_PROXY` 票数。
  - `VoteCastEvent.unsignedLikePaper()` 不再看签名是否为空，而是看 `voteChannel.paperLike()`。
- shennong-app 已同步：
  - `CastVoteRequest` 新增 `voteChannel?: 'ONLINE' | 'PAPER' | 'OFFLINE_PROXY' | null`。
  - 业主端正常投票提交固定传 `voteChannel: 'ONLINE'`，避免 GENERAL/ELECTION 无签名线上票被误计为纸票。
- yaochi 本轮无投票提交入口改动；管理端监控类型继续兼容既有 `unsignedCount / unsignedRatio / unsignedAlert` 字段。
- 最新验证：
  - pangu 聚焦：`mvn -pl pangu-bootstrap -am -Dtest=VoteSubmissionServiceTest,ElectionVoteSubmissionTest,VoteCastMonitorGatewayIntegrationTest,VoteMonitorQueryServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`，24 tests，0 failures，0 errors。
  - shennong-app：`npm run type-check` 已补跑通过。

## E 后续：业主代表 / 楼栋长催票权限事件驱动激活进展
- 现状判断：
  - 设计稿要求投票期事件驱动自动激活催票 / 线下票核销权；
  - 当前已有 `sys_user_building` 楼栋责任田、`OWNER_REPRESENTATIVE` / `GRID_OPERATOR` 角色和 `voting:subject:audit` 粗权限，但缺投票期动态授权状态。
- 后端新增 Flyway `V3.15__voting_mobilization_permission.sql`：
  - 新表 `t_voting_mobilization_permission`，按 `subject_id + user_id + building_id` 唯一；
  - 字段包含 `can_remind / can_offline_proxy / activated_at / expires_at / deactivated_at / status`；
  - status=1 ACTIVE，status=2 INACTIVE。
- 后端新增模型与仓储：
  - `VotingMobilizationPermission` 领域模型；
  - `VotingMobilizationPermissionRepository` 端口；
  - `VotingMobilizationPermissionMapper` / XML / repository 实现。
- 激活规则：
  - `ProposalLifecycleService.openVoting` 在 PUBLISHED -> VOTING 成功后调用 `VotingMobilizationService.activateForVotingOpened`；
  - SQL 从 `sys_user_building` 读取责任楼栋，覆盖 `OWNER_REPRESENTATIVE` 与 `GRID_OPERATOR`；
  - COMMUNITY 议题激活租户内全部责任楼栋；BUILDING 议题只激活匹配 `scopeReferenceId` 的责任楼栋；
  - 授权过期时间取议题 `voteEndAt`。
- 失效规则：
  - `ProposalLifecycleService.cancel` 在撤回成功后失效该议题动员权限；
  - `VotingApplicationService.settle` 在结算成功后失效该议题动员权限。
- 管理端接口：
  - 新增 `GET /api/v1/voting-subjects/{subjectId}/mobilization-permissions/me`；
  - 返回当前 sys_user 在该议题下仍生效的楼栋级 `canRemind / canOfflineProxy`；
  - 非 VOTING 议题返回空数组，C 端无 sys_role 权限直接 403。
- yaochi 管理端已同步：
  - `VotingMobilizationPermission` 类型；
  - `getMyMobilizationPermissions(subjectId)` API。
- shennong-app 本轮无 C 端可见行为变化，仅补跑 type-check。
- 最新验证：
  - pangu 聚焦：`mvn -pl pangu-bootstrap -am -Dtest=ProposalLifecycleServiceTest,VotingMobilizationServiceTest,VotingEndpointMatrixTest -Dsurefire.failIfNoSpecifiedTests=false test`，53 tests，0 failures，0 errors。
  - yaochi：`npm run build` 通过，仍有既有 Vite chunk-size warning。
  - shennong-app：`npm run type-check` 通过。

## E 后续：真实催票发送记录 / 通知 Outbox 进展
- 源文件要求投票期自动激活业主代表 / 楼栋长催票能力；上一切片已完成动态授权，本轮补上“可追踪的一次催票请求”。
- 后端新增 Flyway `V3.16__voting_mobilization_reminder.sql`：
  - 扩展 `t_outbox_event.event_type` check constraint，新增 `4 = VOTING_REMINDER_REQUESTED`；
  - 新建 `t_voting_mobilization_reminder`，记录 `subject_id / building_id / sent_by_user_id / permission_id / target_count / outbox_event_id / sent_at`。
- 后端新增模型与端口：
  - `VotingMobilizationReminder`；
  - `VotingMobilizationReminderRepository`；
  - `VotingReminderOutboxGateway`。
- 基础设施实现：
  - `VotingMobilizationReminderMapper` 统计该楼栋未投 `opid` 数并插入发送记录；
  - `OutboxVotingReminderGateway` 写入 `t_outbox_event`，payload 包含 subject、tenant、building、sender、permission、targetCount、message。
- 应用服务 `VotingMobilizationService.sendReminder`：
  - 要求当前上下文是管理端 `SYS_USER`；
  - 要求议题属于当前租户且状态为 `VOTING`；
  - 要求当前用户在目标楼栋有 ACTIVE 且 `canRemind=true` 的动态权限；
  - 成功后先写 outbox，再把 `outboxEventId` 写入催票记录。
- 管理端接口：
  - 新增 `POST /api/v1/voting-subjects/{subjectId}/mobilization-reminders`；
  - 请求体：`{ buildingId, message? }`；
  - 权限粗筛：`voting:subject:audit`，楼栋级准入由动态权限兜底。
- yaochi 管理端已同步：
  - `SendMobilizationReminderInput`；
  - `VotingMobilizationReminder`；
  - `sendMobilizationReminder(subjectId, input)`。
- shennong-app 已同步：
  - `src/lib/reminder.ts` 新增同名类型与 `sendMobilizationReminder` API；
  - 既有 worker 逐户 `markNotified` 仍保留旧 mock，因为它表达“逐户标记已通知”，与本轮后端“按楼栋发起一次催票请求”不是同一语义。
- 最新验证：
  - pangu 聚焦：`mvn -pl pangu-bootstrap -am -Dtest=VotingMobilizationServiceTest,VotingEndpointMatrixTest -Dsurefire.failIfNoSpecifiedTests=false test`，34 tests，0 failures，0 errors。
  - yaochi：`npm run build` 通过，仍有既有 Vite chunk-size warning；首次沙箱运行因 `.vite-temp` 写权限 EPERM 失败，授权重跑通过。
  - shennong-app：`npm run type-check` 通过。

## E 后续：线下代录写票管理端入口进展
- 目标：让投票期动态授权中的 `canOfflineProxy` 真正约束管理端线下代录写票，而不是继续停留在可查询权限位。
- 后端新增：
  - `OfflineProxyVoteCommand`；
  - `OfflineProxyVoteRequest`；
  - `POST /api/v1/voting-subjects/{subjectId}/offline-proxy-votes`。
- `VotingMobilizationService.castOfflineProxyVote`：
  - 要求当前上下文为管理端 `SYS_USER`；
  - 要求议题属于当前租户且状态为 `VOTING`；
  - 从 `opid` 反查真实 `uid / tenantId / buildingId`，避免请求体伪造 uid/building；
  - 要求当前用户在该楼栋有 ACTIVE 且 `canOfflineProxy=true` 的动态权限；
  - 调用统一 `VoteSubmissionService.cast`，并固定 `voteChannel=OFFLINE_PROXY`。
- `VoteSubmissionService` 调整：
  - `MAJOR` 线上票仍要求 L3 face-auth；
  - `PAPER / OFFLINE_PROXY` 视为线下凭证路径，不复用 C 端人脸认证上下文；
  - 仍保留 opid 有效性、scope、候选人、重复票和监控计数规则。
- yaochi 管理端已同步：
  - `OfflineProxyVoteInput`；
  - `VoteAcknowledgement`；
  - `castOfflineProxyVote(subjectId, input)`。
- shennong-app 已同步：
  - `src/lib/reminder.ts` 新增同名类型与 API，供 worker 工作台后续接入；
  - 现有逐户 mock 页面仍未强接，因为页面还缺真实 pending owner 后端接口。
- 最新验证：
  - pangu 聚焦：`mvn -pl pangu-bootstrap -am -Dtest=VoteSubmissionServiceTest,VotingMobilizationServiceTest,VotingEndpointMatrixTest -Dsurefire.failIfNoSpecifiedTests=false test`，52 tests，0 failures，0 errors。
  - yaochi：`npm run build` 通过，仍有既有 Vite chunk-size warning；沙箱内首次因 `.vite-temp` 写权限 EPERM，授权重跑通过。
  - shennong-app：`npm run type-check` 通过。

## E 后续：通知 Outbox 消费器进展
- 目标：让 `VOTING_REMINDER_REQUESTED` 不只落库，而是进入可重试消费链路。
- 后端新增端口与服务：
  - `VotingReminderDeliveryCommand`；
  - `VotingReminderDeliveryGateway`；
  - `VotingReminderOutboxRepository`；
  - `VotingReminderOutboxConsumerService`。
- Outbox DB 流转：
  - `claimPending(limit)` 领取 `event_type=4` 且 `status IN (PENDING=1, FAILED=4)`、`attempts < 5` 的事件；
  - 领取时 `status=SUBMITTED=2`、`attempts+1`、刷新 `last_attempt_at`、清空 `last_error`；
  - 投递成功后 `markConfirmed` 写 `status=CONFIRMED=3 / confirmed_at`；
  - 投递失败后 `markFailed` 写 `status=FAILED=4 / last_error`，后续 tick 可重试。
- 基础设施实现：
  - `OutboxEventMapper` / XML 扩展 select-for-update claim 与 confirmed/failed 回写；
  - `VotingReminderOutboxRepositoryImpl`；
  - `MockVotingReminderDeliveryGateway`，后续真实短信/Push/站内信 provider 替换该端口。
- 调度器：
  - 新增 `VotingReminderOutboxScheduler`；
  - 默认 cron：`platform.voting.reminder-outbox-cron: "15 * * * * *"`；
  - 默认 batch：`platform.voting.reminder-outbox-batch-size: 50`。
- 三仓同步判断：
  - 本轮没有新增或修改 HTTP 契约；
  - yaochi / shennong-app 不需要代码同步，前一轮的催票与线下代录 API 契约继续可用。
- 最新验证：
  - pangu 聚焦：`mvn -pl pangu-bootstrap -am -Dtest=VotingReminderOutboxConsumerServiceTest,VotingMobilizationServiceTest,VotingEndpointMatrixTest -Dsurefire.failIfNoSpecifiedTests=false test`，40 tests，0 failures，0 errors。
  - pangu outbox DB 集成：`mvn -pl pangu-bootstrap -am -Dtest=VotingReminderOutboxConsumerServiceTest,VotingReminderOutboxRepositoryIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`，4 tests，0 failures，0 errors。

## E 后续：worker 真实待催票列表接口进展
- 目标：替换 shennong-app worker 页 `listReminderTasks/listPendingOwners/markNotified` 的后端 mock 缺口，让网格员 / 楼栋长能看到自己动态责任田内的待催票议题与未投业主，并逐户记录电话 / 上门 / 微信已通知。
- 后端新增 Flyway `V3.17__voting_mobilization_owner_notice.sql`：
  - 新建 `t_voting_mobilization_owner_notice`；
  - 按 `subject_id + uid + channel` 唯一，记录通知渠道、备注、通知人、通知时间；
  - 索引支持按议题/业主查看最近通知与按通知人追踪工作量。
- 后端新增模型与端口：
  - `ReminderChannel`；
  - `ReminderTask`；
  - `ReminderPendingOwner`；
  - `VotingReminderTaskRepository`。
- 应用服务 `VotingReminderTaskService`：
  - 要求当前上下文为管理端 `SYS_USER`；
  - `listTasks` 返回当前用户 ACTIVE `canRemind` 楼栋内的 VOTING 议题，以及责任田内应投 / 未投户数；
  - `listPendingOwners` 返回指定议题下责任田内未投业主、脱敏手机号、楼栋 / 房屋、已通知渠道、最近通知时间与备注；
  - `markNotified` 校验当前用户对该业主所在楼栋有 ACTIVE `canRemind` 权限，并且该业主尚未投票，然后 upsert 逐户通知记录。
- 接口：
  - `GET /api/v1/reminder/tasks`；
  - `GET /api/v1/reminder/tasks/{subjectId}/pending`；
  - `POST /api/v1/reminder/tasks/{subjectId}/notify`，请求体 `{ uid, channel, note? }`；
  - 粗权限均为 `voting:subject:audit`，楼栋级准入由动态授权兜底。
- 三仓同步判断：
  - shennong-app 既有真实 API 路径和类型已经与新增后端接口一致；但文件注释和页面提示仍写“后端 PR-E 未完成 / 仅前端记录”。
  - 本轮尝试更新 shennong-app 注释和提示文案时，因仓库位于 pangu writable root 之外，补丁被审批层使用限额拒绝；未绕过执行。
  - yaochi 未引用 `reminder/tasks`、`listReminderTasks`、`listPendingOwners` 或 `markNotified`，本轮无管理端代码同步点。
- 最新验证：
  - pangu 聚焦：`mvn -pl pangu-bootstrap -am -Dtest=VotingReminderTaskRepositoryIntegrationTest,VotingEndpointMatrixTest -Dsurefire.failIfNoSpecifiedTests=false test`，34 tests，0 failures，0 errors。
  - shennong-app `npm run type-check` 因同一审批层使用限额未能执行；待可授权环境补跑。

## E 后续：shennong-app worker 催票真实分支说明进展
- 目标：清理 C 端 worker 催票页仍然残留的“后端未完成 / 仅前端记录”提示，使其与 pangu 已完成的真实接口契约一致。
- shennong-app 已完成：
  - `src/lib/reminder.ts` 顶部说明改为 `USE_MOCK=true` 返回前端 stub 数据、`USE_MOCK=false` 调用 pangu 真实接口；
  - 真实接口列明为 `GET /reminder/tasks`、`GET /reminder/tasks/{subjectId}/pending`、`POST /reminder/tasks/{subjectId}/notify`；
  - `src/pages/worker/reminder-list/index.tsx` 页面说明改为 mock 本地演示 / 真实后端逐户通知记录双分支；
  - 页面底部提示改为“生产环境 USE_MOCK=false 后将写入后端逐户通知记录”。
- README 已将 PR-E 从“后端缺口”调整为“已接入真实后端能力”，并保留 PR-F / PR-G 为仍待真实供应商接入的 mock 项。
- 最新验证：
  - shennong-app：`npm run type-check` 通过。
- 剩余边界：
  - 这次只完成文案、注释和类型检查；尚未做 `USE_MOCK=false` 真机 / 联调链路验证。

## E 后续：yaochi 催票投递明细筛选进展
- 目标：让管理端表决看板不仅能看到最近投递明细，还能按楼栋和投递状态定位问题，支持失败排查和供应商回执核对。
- yaochi 已完成：
  - `src/app/components/pages/Voting.tsx` 新增楼栋 ID 输入筛选；
  - 新增投递状态筛选：全部状态、待投递、投递中、已确认、失败待重试；
  - 楼栋输入在点击“查询”或回车后生效，避免输入过程中连续请求；
  - 状态筛选变更后自动重查；
  - 存在筛选条件时显示“重置”按钮，一键恢复最近 50 条全量明细。
- API 仍复用既有 `listReminderDeliveries(subjectId, { buildingId, status, limit })`，没有新增后端契约。
- 最新验证：
  - yaochi：`npm run build` 通过；仍只有既有 Vite chunk-size warning。

## E 后续：yaochi 催票投递记录详情查看进展
- 目标：管理端排查单条催票投递失败时，不只看表格摘要，还能查看完整 outbox、供应商回执、时间线和错误信息。
- yaochi 已完成：
  - `src/app/components/pages/Voting.tsx` 的投递明细表新增“查看”动作；
  - 新增投递详情 Dialog，展示 delivery id、subject、building、uid、opid、脱敏手机号、channel、attempts、outbox event、provider message id；
  - 展示创建、最近尝试、提交、确认、失败时间；
  - 展示 message template 与完整 `lastError`，避免表格截断影响排障。
- API 仍复用 `listReminderDeliveries` 已返回字段，没有新增后端契约。
- 最新验证：
  - yaochi：`npm run build` 通过；仍只有既有 Vite chunk-size warning。

## E 后续：HTTP 短信 provider 签名 / 模板 / 回执映射进展
- 目标：在具体短信供应商未最终确定前，把 provider 从“固定 bearer + 固定顶层回执字段”推进到可配置签名、模板参数和回执字段路径，减少后续接厂商 SDK / HTTP API 的代码改动面。
- 后端已完成：
  - `HttpVotingReminderSmsProvider` 保持默认 bearer 兼容；
  - payload 新增 `templateCode`，为空时沿用 `messageTemplate`；
  - payload 新增 `templateParams`，包含 `subjectId / tenantId / buildingId / opid / uid / message`；
  - 新增 `provider-message-id-fields` 配置，支持逗号列表和点路径，如 `data.smsId`；
  - 新增 HMAC-SHA256 签名能力：配置 `signature-secret` 后发送 timestamp header 与 signature header；
  - 签名串为 `timestamp + "\n" + body`，默认 header 为 `X-Pangu-Timestamp / X-Pangu-Signature`，可配置。
- 配置项已写入 `application.yml`：
  - `platform.voting.sms-provider.template-code`
  - `platform.voting.sms-provider.provider-message-id-fields`
  - `platform.voting.sms-provider.signature-secret`
  - `platform.voting.sms-provider.signature-header`
  - `platform.voting.sms-provider.signature-timestamp-header`
- 测试覆盖：
  - 默认 payload / bearer / 顶层 `providerMessageId` 兼容；
  - 嵌套回执字段 `data.smsId`；
  - HMAC 签名头与自定义模板 code；
  - 非 2xx provider 响应仍抛错，交由既有状态机回写 FAILED。
- 最新验证：
  - provider 切片：`mvn -pl pangu-bootstrap -am -Dtest=HttpVotingReminderSmsProviderTest,VotingReminderDeliveryDispatchServiceTest,VotingReminderOutboxRepositoryIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`，10 tests，0 failures，0 errors。
  - 通知链路聚焦回归：`mvn -pl pangu-bootstrap -am -Dtest=HttpVotingReminderSmsProviderTest,VotingReminderDeliveryDispatchServiceTest,VotingReminderOutboxConsumerServiceTest,VotingReminderOutboxRepositoryIntegrationTest,VotingMobilizationServiceTest,VotingEndpointMatrixTest -Dsurefire.failIfNoSpecifiedTests=false test`，54 tests，0 failures，0 errors。

## E 后续：shennong-app worker 催票真实接口 smoke 进展
- 目标：把 `USE_MOCK=false` worker 催票真实联调从手工点小程序，推进为可重复执行的接口 smoke；真实 token / 运行环境到位后可直接验证三条 PR-E 接口。
- shennong-app 已完成：
  - 新增 `scripts/smoke-reminder-real.mjs`；
  - `package.json` 新增 `npm run smoke:reminder`；
  - README 新增 worker 催票真实接口 smoke 用法。
- smoke 行为：
  - 默认只读：`GET /reminder/tasks`，再对首条或指定 `PANGU_REMINDER_SUBJECT_ID` 执行 `GET /reminder/tasks/{subjectId}/pending`；
  - 校验任务和待通知业主响应字段形状；
  - 仅当显式设置 `PANGU_REMINDER_NOTIFY_UID` 时才执行 `POST /reminder/tasks/{subjectId}/notify`；
  - 支持 `PANGU_API_BASE_URL / PANGU_REMINDER_NOTIFY_CHANNEL / PANGU_REMINDER_NOTIFY_NOTE`。
- 最新验证：
  - shennong-app：`npm run smoke:reminder -- --help` 通过；
  - shennong-app：`npm run type-check` 通过。
- 剩余边界：
  - 尚未拿真实工作端 token 跑 live smoke；下一步需要 pangu 服务、工作端 SYS_USER token、存在 ACTIVE `canRemind` 的 VOTING 议题。

## E 后续：催票逐户投递明细进展
- 目标：让 `VOTING_REMINDER_REQUESTED` outbox 消费不再只是 mock 日志，而是生成后续短信 / Push / 站内信 provider 可消费的逐户投递明细。
- 后端新增 Flyway `V3.18__voting_reminder_delivery.sql`：
  - 新建 `t_voting_reminder_delivery`；
  - 字段包含 `outbox_event_id / subject_id / tenant_id / building_id / opid / uid / phone / channel / message_template / message / delivery_status`；
  - `delivery_status`：1 READY，2 SUBMITTED，3 CONFIRMED，4 FAILED；
  - `UNIQUE(outbox_event_id, opid, channel)` 保证 outbox 重试不会重复生成同一房产同一渠道投递。
- 基础设施实现：
  - 新增 `VotingReminderDeliveryMapper` / XML；
  - 新增 `DatabaseVotingReminderDeliveryGateway`，默认启用；
  - 原 `MockVotingReminderDeliveryGateway` 改为 `platform.voting.reminder-delivery-mode=mock` 时启用；
  - `application.yml` 新增 `platform.voting.reminder-delivery-mode: database`。
- 展开规则：
  - 消费时按 outbox payload 的 `subjectId / tenantId / buildingId` 重新查询当前仍未投票且账号正常的业主房产；
  - 写入 SMS 渠道 READY 明细；
  - 如果 outbox 重试，`ON CONFLICT DO NOTHING` 保持幂等。
- 三仓同步判断：
  - 本轮没有新增 HTTP/API 契约；
  - yaochi / shennong-app 不需要代码同步。
- 最新验证：
  - pangu outbox DB 集成：`mvn -pl pangu-bootstrap -am -Dtest=VotingReminderOutboxConsumerServiceTest,VotingReminderOutboxRepositoryIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`，5 tests，0 failures，0 errors。
  - pangu 聚焦回归：`mvn -pl pangu-bootstrap -am -Dtest=VotingReminderOutboxConsumerServiceTest,VotingReminderOutboxRepositoryIntegrationTest,VotingMobilizationServiceTest,VotingEndpointMatrixTest -Dsurefire.failIfNoSpecifiedTests=false test`，45 tests，0 failures，0 errors。

## E 后续：催票投递状态机与 mock provider 进展
- 目标：让 `t_voting_reminder_delivery` 的 READY 明细进入可调度、可重试、可观测的投递状态机；真实短信供应商尚未配置时先用 mock provider 驱动完整状态回写。
- 后端新增 Flyway `V3.19__voting_reminder_delivery_dispatch.sql`：
  - 为 `t_voting_reminder_delivery` 增加 `attempts / last_attempt_at / submitted_at / confirmed_at / failed_at / provider_message_id / last_error`；
  - 新增 READY/FAILED 部分索引，支持调度器按状态与尝试次数领取。
- 新增领域端口与模型：
  - `VotingReminderDeliveryItem`；
  - `VotingReminderDeliveryReceipt`；
  - `VotingReminderSmsProvider`；
  - `VotingReminderDeliveryRepository`。
- 新增应用服务与调度器：
  - `VotingReminderDeliveryDispatchService`：领取 READY/FAILED 且 `attempts < 5` 的明细，调用短信 provider，成功回写 CONFIRMED，失败回写 FAILED；
  - `VotingReminderDeliveryScheduler`：默认每分钟第 30 秒调度；
  - `application.yml` 新增 `platform.voting.reminder-delivery-cron` 与 `reminder-delivery-batch-size`。
- 基础设施实现：
  - `VotingReminderDeliveryMapper` / XML 扩展 claim/confirm/fail；
  - `VotingReminderDeliveryRepositoryImpl`；
  - `MockVotingReminderSmsProvider`，返回 `mock-sms-{deliveryId}` 作为 provider message id。
- 三仓同步判断：
  - 本轮没有新增 HTTP/API 契约；
  - yaochi / shennong-app 不需要代码同步。
- 最新验证：
  - pangu 投递状态机切片：`mvn -pl pangu-bootstrap -am -Dtest=VotingReminderDeliveryDispatchServiceTest,VotingReminderOutboxRepositoryIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`，6 tests，0 failures，0 errors。
  - pangu 聚焦回归：`mvn -pl pangu-bootstrap -am -Dtest=VotingReminderDeliveryDispatchServiceTest,VotingReminderOutboxConsumerServiceTest,VotingReminderOutboxRepositoryIntegrationTest,VotingMobilizationServiceTest,VotingEndpointMatrixTest -Dsurefire.failIfNoSpecifiedTests=false test`，48 tests，0 failures，0 errors。

## E 后续：催票投递明细查询接口进展
- 目标：让管理端能够查询 `t_voting_reminder_delivery` 的逐户投递状态，用于催票记录详情、失败排查和真实短信供应商接入后的回执观测。
- 后端新增只读查询契约：
  - `GET /api/v1/voting-subjects/{subjectId}/reminder-deliveries`；
  - 权限沿用 `voting:subject:audit`；
  - 查询参数支持 `buildingId`、`status`、`limit`，limit 由应用层收敛到 `1..500`。
- 应用与领域：
  - 新增 `VotingReminderDeliveryStatus` 领域视图；
  - 新增 `VotingReminderDeliveryQueryRepository`；
  - 新增 `VotingReminderDeliveryQueryService`，校验当前上下文为 SYS_USER、subject 存在且属于当前 tenant 后再查询。
- 基础设施：
  - `VotingReminderDeliveryMapper.listBySubject` 按 `tenant_id / subject_id / building_id / delivery_status` 查询；
  - 手机号在 SQL 查询侧脱敏为 `138****0012` 形态；
  - 查询结果按 `created_at DESC, delivery_id DESC` 排序。
- Web 响应：
  - 新增 `VotingReminderDeliveryStatusResponse`；
  - 返回 delivery id、outbox event、楼栋、opid、uid、脱敏手机号、渠道、模板、状态、attempts、时间戳、provider message id 与 last error。
- 三仓同步判断：
  - 本轮新增的是 pangu 后端管理端 HTTP 契约；yaochi 还没有对应页面或 API wrapper，下一步应在管理端议题详情 / 催票记录页接入。
  - shennong-app 不消费管理端投递明细接口，本轮不需要 C 端同步。
- 最新验证：
  - pangu 查询接口切片：`mvn -pl pangu-bootstrap -am -Dtest=VotingReminderOutboxRepositoryIntegrationTest,VotingEndpointMatrixTest -Dsurefire.failIfNoSpecifiedTests=false test`，39 tests，0 failures，0 errors。
  - pangu 通知链路聚焦回归：`mvn -pl pangu-bootstrap -am -Dtest=VotingReminderDeliveryDispatchServiceTest,VotingReminderOutboxConsumerServiceTest,VotingReminderOutboxRepositoryIntegrationTest,VotingMobilizationServiceTest,VotingEndpointMatrixTest -Dsurefire.failIfNoSpecifiedTests=false test`，50 tests，0 failures，0 errors。

## E 后续：yaochi 投递明细展示进展
- 目标：把 pangu 已暴露的 `reminder-deliveries` 查询契约同步到管理端，避免投递状态只停留在后端可查。
- yaochi API 层：
  - `src/app/lib/voting.ts` 新增 `ReminderDeliveryStatusCode`；
  - 新增 `VotingReminderDeliveryStatus` 类型，对齐后端 `VotingReminderDeliveryStatusResponse`；
  - 新增 `listReminderDeliveries(subjectId, { buildingId?, status?, limit? })`。
- yaochi 页面层：
  - `src/app/components/pages/Voting.tsx` 表决看板在投票明细之后新增“催票投递明细”表；
  - 默认拉取最近 50 条逐户投递记录；
  - 展示 UID/OPID、楼栋、脱敏手机号、渠道、READY/SUBMITTED/CONFIRMED/FAILED 状态、attempts、provider message id / outbox id、更新时间与失败原因；
  - 同步补齐该页对 `PENDING_COMMITTEE / PENDING_STREET` 状态枚举的 label 与 step 映射。
- shennong-app 同步判断：
  - 该接口是管理端观测接口，C 端不消费；本轮无需 shennong-app 改动。
- 最新验证：
  - yaochi：`npm run build` 通过；仍有既有 Vite chunk-size warning。

## E 后续：HTTP 短信 provider 适配器进展
- 目标：在没有锁定具体短信供应商 SDK 的情况下，把真实短信网关接入点落到当前投递状态机里，替代只能返回 `mock-sms-*` 的本地 provider。
- 后端新增：
  - `HttpVotingReminderSmsProvider`；
  - 默认 `MockVotingReminderSmsProvider` 改为条件组件：`platform.voting.sms-provider-mode=mock` 或未配置时启用；
  - HTTP provider 仅在 `platform.voting.sms-provider-mode=http` 时启用。
- 配置项：
  - `platform.voting.sms-provider-mode: mock`；
  - `platform.voting.sms-provider.endpoint`；
  - `platform.voting.sms-provider.bearer-token`；
  - `platform.voting.sms-provider.timeout-millis: 3000`。
- HTTP provider 行为：
  - POST JSON 到配置的 endpoint；
  - payload 包含 `deliveryId / outboxEventId / subjectId / tenantId / buildingId / opid / uid / phone / channel / messageTemplate / message / attempts`；
  - 配置 bearer token 时发送 `Authorization: Bearer ...`；
  - 2xx 响应中按 `providerMessageId / messageId / bizId / requestId` 顺序读取供应商回执；
  - 非 2xx、网络异常、回执缺失都会抛异常，由现有 `VotingReminderDeliveryDispatchService` 回写 FAILED 与 last_error，后续 tick 重试。
- 三仓同步判断：
  - 该能力不新增 HTTP API，不需要 yaochi / shennong-app 同步；
  - yaochi 已能通过投递明细表看到 HTTP provider 返回的 provider message id 或失败原因。
- 最新验证：
  - HTTP provider 切片：`mvn -pl pangu-bootstrap -am -Dtest=HttpVotingReminderSmsProviderTest,VotingReminderDeliveryDispatchServiceTest,VotingReminderOutboxRepositoryIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`，8 tests，0 failures，0 errors。
  - 通知链路聚焦回归：`mvn -pl pangu-bootstrap -am -Dtest=HttpVotingReminderSmsProviderTest,VotingReminderDeliveryDispatchServiceTest,VotingReminderOutboxConsumerServiceTest,VotingReminderOutboxRepositoryIntegrationTest,VotingMobilizationServiceTest,VotingEndpointMatrixTest -Dsurefire.failIfNoSpecifiedTests=false test`，52 tests，0 failures，0 errors。

## E 后续：shennong-app worker 催票只读 live smoke 进展
- 目标：用真实运行中的 pangu 后端和工作端 SYS_USER token 验证 shennong-app `npm run smoke:reminder` 不只停留在 help/type-check。
- 环境结论：
  - 本地 `pangu-postgres` / `pangu-redis` 容器均已运行。
  - 8080 端口是 2026-06-28 由 IntelliJ 启动的旧 `PanguApplication` JVM；该旧进程上 `GET /reminder/tasks` 返回 500，不能代表当前代码。
  - 当前代码后端已在 18080 启动成功，Flyway schema 当前版本为 3.19。
- 登录结论：
  - 种子账号 `13800000004 / 123456` 可登录。
  - JWT 身份为 `SYS_USER / GRID_OPERATOR / active_identity_id=800004 / tenant_id=10001`，具备 `voting:subject:audit`。
- smoke 结论：
  - `PANGU_API_BASE_URL=http://127.0.0.1:18080/pangu/api/v1 PANGU_TOKEN=<jwt> npm run smoke:reminder` 执行通过。
  - `GET /reminder/tasks` 返回 200，响应数组结构通过脚本校验。
  - 当前本地库返回 `count=0`，说明缺少活跃 VOTING + ACTIVE `canRemind` + 未投业主的可催票任务；pending owners 与 notify 写入未触发。
- 下一步判断：
  - 不是前端脚本或鉴权契约问题；下一步应准备一条可催票数据，再验证 `GET /reminder/tasks/{subjectId}/pending` 与 `POST /reminder/tasks/{subjectId}/notify`。

## E 后续：shennong-app worker 催票 pending/notify live smoke 进展
- 目标：补齐只读 smoke 的数据缺口，证明 worker 真实接口从任务列表、待催票业主到逐户通知写入都能闭环。
- 本地 fixture：
  - 新增 `scripts/prepare-reminder-smoke.sql`，固定准备 `subject_id=990480` 的 VOTING ELECTION 议题。
  - 新增 `scripts/cleanup-reminder-smoke.sql`，按 `subject_id=990480` 清理本地联调数据。
  - fixture 绑定 `sys_user=800004 / GRID_OPERATOR`、楼栋 `30001`、业主 `uid=70001 / opid=1`。
- 验证结果：
  - 准备脚本输出 `ready / subject_id=990480 / user_id=800004 / uid=70001 / opid=1`。
  - shennong-app smoke 指向当前代码后端 `18080`，并设置 `PANGU_REMINDER_SUBJECT_ID=990480 / PANGU_REMINDER_NOTIFY_UID=70001` 后通过。
  - 脚本输出 `GET /reminder/tasks ok, count=1`、`GET /reminder/tasks/990480/pending ok, count=1`、`POST /reminder/tasks/990480/notify ok`。
  - DB 验证 `t_voting_mobilization_owner_notice` 已写入 `subject_id=990480 / uid=70001 / channel=PHONE / notified_by_user_id=800004 / note='codex smoke notify'`。
- 三仓同步判断：
  - pangu：新增本地 fixture 准备/清理脚本。
  - shennong-app：README 已补充使用 pangu fixture 的 smoke 命令。
  - yaochi：本轮是 worker 接口 live smoke，不新增管理端页面或接口契约。
- 下一步判断：
  - worker 催票真实接口链路已经从脚本层验证完成；
  - 剩余真实通知闭环重点转向具体短信供应商参数配置与 HTTP provider 联调。

## E 后续：HTTP provider 本地 smoke harness 进展
- 目标：在具体短信供应商参数到位前，先把本地可重复的 HTTP provider 联调脚手架补齐，覆盖 bearer、HMAC、模板 code、嵌套回执字段和投递状态机的外部网关边界。
- 新增工具：
  - `scripts/fake-sms-provider.mjs`：Node 标准库实现的本地短信假网关；
  - `scripts/prepare-http-sms-provider-smoke.sql`：准备 `delivery_id=990481` 的 READY 投递明细；
  - `scripts/cleanup-http-sms-provider-smoke.sql`：清理 `990481` 联调数据。
- fake provider 行为：
  - 监听 `POST /sms`；
  - 可通过 `FAKE_SMS_BEARER` 校验 `Authorization: Bearer ...`；
  - 可通过 `FAKE_SMS_SIGNATURE_SECRET` 校验 `X-Pangu-Timestamp` 与 `X-Pangu-Signature`；
  - 将收到的 payload 追加到 JSONL 日志；
  - 返回 `{"code":0,"data":{"smsId":"fake-sms-{deliveryId}"}}`，用于验证 `provider-message-id-fields=data.smsId`。
- 已验证：
  - prepare SQL 可成功准备 READY delivery；
  - fake provider 可启动并已停止；
  - `node --check scripts/fake-sms-provider.mjs` 通过；
  - cleanup SQL 可成功清理 fixture；
  - `19090` 端口无残留监听。
- live 调度验证：
  - `CLEANUP_FIXTURE=true scripts/smoke-http-sms-provider.sh` 已启动当前 pangu HTTP provider 模式并通过；
  - 调度器将 DB READY 明细回写为 `delivery_status=3 / provider_message_id=fake-sms-990481 / attempts=1`；
  - fixture 退出后已自动清理，`18080 / 19090` 无残留监听。
- 下一步判断：
  - 本地 provider live smoke 已完成；
  - 若供应商参数到位，用同一命令替换 endpoint/token/secret/template-code/provider-message-id-fields，并通过 yaochi 投递明细观测 provider message id 与失败原因。

## E 后续：HTTP provider dispatch 组合回归测试进展
- 目标：在 live 调度受本地提权限制时，先把 `VotingReminderDeliveryDispatchService` 和真实 `HttpVotingReminderSmsProvider` 串起来，避免 provider 与状态机只被分离测试覆盖。
- 新增覆盖：
  - `VotingReminderDeliveryDispatchServiceTest.dispatchPending_withHttpProvider_postsSignedPayloadAndMarksConfirmed`。
  - 使用 JVM 内 `HttpServer` 模拟短信网关；
  - dispatch service 使用真实 `HttpVotingReminderSmsProvider`；
  - repository 保持 mock，用于断言 `markConfirmed` 回写。
- 已验证能力：
  - HTTP provider 发出 bearer token；
  - HMAC 签名可由测试按同一算法复算；
  - payload 包含配置模板 code 与 templateParams；
  - 响应嵌套字段 `data.smsId` 被解析为 provider message id；
  - dispatch service 最终调用 `markConfirmed(1L, "fake-sms-1")`。
- 验证命令：
  - `mvn -pl pangu-bootstrap -am -Dtest=VotingReminderDeliveryDispatchServiceTest,HttpVotingReminderSmsProviderTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - 7 tests，0 failures，0 errors。
- 剩余边界：
  - 这不是 Spring scheduler + DB READY delivery 的 live smoke；
  - Spring scheduler + DB READY delivery 的 live smoke 已在后续 Phase 53 补跑通过。

## E 后续：HTTP provider live smoke 编排脚本进展
- 目标：把 Phase 50 的散落手工步骤收敛为一个可重复执行入口，降低后续补跑 Spring scheduler + DB READY delivery live smoke 的操作成本。
- 新增脚本：
  - `scripts/smoke-http-sms-provider.sh`
- 脚本覆盖步骤：
  - 执行 `prepare-http-sms-provider-smoke.sql` 准备 READY delivery；
  - 启动 `fake-sms-provider.mjs`；
  - 通过 `SPRING_APPLICATION_JSON` 启动 pangu HTTP provider 模式；
  - 等待调度器将 `delivery_id=990481` 回写为 `CONFIRMED`；
  - 校验 `provider_message_id=fake-sms-990481`；
  - 停止脚本启动的进程。
- 设计边界：
  - 默认保留 fixture，便于失败后查库和查日志；
  - 可设置 `CLEANUP_FIXTURE=true` 在退出时自动清理；
  - 如果 `PANGU_PORT` 已被占用，脚本会拒绝执行，避免影响用户已有后端进程。
- 静态验证：
  - `bash -n scripts/smoke-http-sms-provider.sh` 通过；
  - `node --check scripts/fake-sms-provider.mjs` 通过；
  - `18080/19090` 无残留监听。
- live 验证：
  - 后续已执行 `CLEANUP_FIXTURE=true scripts/smoke-http-sms-provider.sh`；
  - pangu HTTP provider 模式启动成功，scheduler 将 `delivery_id=990481` 回写为 `CONFIRMED`；
  - 供应商回执 `provider_message_id=fake-sms-990481` 已落库；
  - fixture 已清理，`18080/19090` 无残留监听。

## E 后续：HTTP provider scheduler live smoke 进展
- 目标：补齐 Spring scheduler + DB READY delivery + HTTP provider + fake SMS 的端到端验证，证明本地脚手架不只停留在静态检查和 JVM 内组合测试。
- 发现的问题：
  - live 启动时 Spring 报 `HttpVotingReminderSmsProvider` 没有默认构造器；
  - 根因是该类同时存在生产配置构造器和包内测试构造器，Spring 未能明确选择生产构造器。
- 修复结果：
  - 生产构造器已补 `@Autowired`；
  - 定向测试报告显示 `HttpVotingReminderSmsProviderTest` 4/0F/0E、`VotingReminderDeliveryDispatchServiceTest` 3/0F/0E。
- live smoke 结果：
  - `CLEANUP_FIXTURE=true scripts/smoke-http-sms-provider.sh` 通过；
  - fake SMS provider 校验 bearer/HMAC 并返回 `data.smsId=fake-sms-990481`；
  - pangu scheduler 将 `t_voting_reminder_delivery.delivery_id=990481` 回写为 `delivery_status=3 / provider_message_id=fake-sms-990481 / attempts=1`。
- 收尾：
  - `990481` fixture 已被 `cleanup-http-sms-provider-smoke.sql` 清理；
  - `18080 / 19090` 均无残留监听。
- 下一步判断：
  - 本地 HTTP provider 闭环已经完成；
  - 后续真正缺口只剩具体短信供应商参数与测试环境联调。

## E 后续：HTTP provider 真实供应商参数化联调入口进展
- 目标：把已经跑通的 fake provider live smoke 转成真实供应商可直接复用的联调入口。
- 脚本能力：
  - `START_FAKE_SMS=true` 默认保留本地 fake provider 路径；
  - `START_FAKE_SMS=false` 时不启动 fake provider，直接调用 `SMS_PROVIDER_ENDPOINT`；
  - 可通过环境变量传入 bearer token、template code、provider message id 字段路径、签名 secret、签名 header、时间戳 header；
  - `EXPECTED_PROVIDER_MESSAGE_ID` 默认校验 `fake-sms-990481`，置空时只校验 delivery 已 CONFIRMED 且 `provider_message_id` 非空。
- 判断：
  - 后续接真实供应商不需要再改 Java 代码或复制脚本；
  - 真实参数到位后，直接运行参数化脚本，并用 DB 与 yaochi 投递明细观测回执。

## E 后续：HTTP provider 联调前配置校验与自定义签名 header 进展
- 发现：
  - Phase 54 已能参数化 endpoint/token/signature/template，但脚本没有在启动前输出脱敏配置摘要；
  - fake provider 路径没有显式接收自定义签名 header 名，真实联调前无法证明自定义 header 名贯穿双端。
- 已补齐：
  - `scripts/smoke-http-sms-provider.sh` 增加 preflight，布尔开关非法时直接退出；
  - 配置摘要对 token 和 signature secret 做掩码；
  - fake provider 启动时同步使用 `SMS_PROVIDER_SIGNATURE_HEADER / SMS_PROVIDER_SIGNATURE_TIMESTAMP_HEADER`。
- 验证：
  - 非法 `START_FAKE_SMS=maybe` 与 `CLEANUP_FIXTURE=maybe` 均在写 DB 前失败；
  - 自定义 `X-Test-Signature / X-Test-Timestamp` 的完整 live smoke 通过；
  - fake provider JSONL 日志中出现 `x-test-signature` 与 `x-test-timestamp`，证明 Java provider 发出的 header 与 fake provider 校验配置一致。

## E 后续：HTTP provider smoke 脚本自说明 help 进展
- 发现：
  - pangu 根目录没有 README；
  - HTTP provider 真实供应商联调命令此前只记录在路线图和进展文件里，不适合作为执行入口。
- 已补齐：
  - `scripts/smoke-http-sms-provider.sh --help` 输出本地 smoke、外部供应商 smoke 示例和环境变量说明；
  - 未知参数会返回 2 并打印 usage，避免静默忽略误输入。
- 验证：
  - `--help` 不触发 DB fixture、Maven install 或服务启动；
  - `--bad-arg` 不触发副作用并返回 2；
  - `bash -n` 通过。

## E 后续：HTTP provider dry-run 与外部供应商默认值语义进展
- 发现：
  - `EXPECTED_PROVIDER_MESSAGE_ID=` 会被 shell 默认值覆盖成 `fake-sms-990481`，无法表达“任意非空回执”；
  - `START_FAKE_SMS=false` 外部供应商模式下，未传 token/secret/template 时会继承本地 fake 默认值，不符合“可选配置为空”的真实联调语义。
- 已修正：
  - `EXPECTED_PROVIDER_MESSAGE_ID` 只在未设置时默认 `fake-sms-990481`，显式空值表示任意非空 provider message id；
  - 外部供应商模式下 endpoint 必填，token/secret/template/message-id-fields 默认空；
  - 新增 `DRY_RUN=true`，只输出脱敏配置并退出，不触碰 DB 或服务。
- 验证：
  - 外部模式缺 endpoint 会在 dry-run 返回 2；
  - 外部模式只传 endpoint 且 `EXPECTED_PROVIDER_MESSAGE_ID=` 时，输出 `<empty>` token/secret/template 与 `<any-non-empty>` 回执预期；
  - 本地模式 dry-run 仍输出 fake 默认配置；
  - 默认完整 smoke 仍通过，fixture 和端口均已清理。

## E 后续：HTTP provider 数字型回执 ID 兼容进展
- 发现：
  - 原 `providerMessageId` 解析只接受 JSON text node；
  - 部分短信供应商常见返回为数字型 `messageId / bizId / smsId`，会导致 2xx 响应被误判为“missing providerMessageId”。
- 已补齐：
  - `HttpVotingReminderSmsProvider` 对配置的回执字段同时接受 textual 与 numeric node；
  - numeric node 统一转为字符串保存，避免 repository/schema 继续扩展。
- 验证：
  - 新增数字型嵌套回执测试；
  - HTTP provider + dispatch 定向测试 8/0F/0E。

## E 后续：HTTP provider smoke 可调超时参数进展
- 发现：
  - Java provider 已支持 `platform.voting.sms-provider.timeout-millis`；
  - smoke 脚本此前没有暴露对应环境变量，真实供应商测试环境慢响应时需要改代码或配置文件才能调整。
- 已补齐：
  - `scripts/smoke-http-sms-provider.sh` 新增 `SMS_PROVIDER_TIMEOUT_MILLIS`，默认 `3000`；
  - preflight 校验正整数，dry-run 摘要显示该值；
  - `SPRING_APPLICATION_JSON` 注入 `platform.voting.sms-provider.timeout-millis`。
- 验证：
  - `bash -n` 通过；
  - 外部供应商 dry-run 使用 `SMS_PROVIDER_TIMEOUT_MILLIS=4500` 返回 0；
  - `SMS_PROVIDER_TIMEOUT_MILLIS=0` 在副作用前返回 2。

## E 后续：HTTP provider 业务成功码校验进展
- 发现：
  - 很多短信供应商用 HTTP 200 承载业务失败，并在响应体中通过 `code/status/success` 表达成败；
  - 原 provider 只要 HTTP 2xx 且能解析 provider message id 就会确认成功，存在把业务失败响应误写为 CONFIRMED 的风险。
- 已补齐：
  - `HttpVotingReminderSmsProvider` 支持 `success-code-field / success-code-values`；
  - 成功码字段支持点路径，成功值支持逗号分隔；
  - 未配置成功码字段时保持旧兼容行为；
  - 配置后先校验业务成功码，再读取回执 ID；
  - 成功码字段和值必须成对配置，避免“字段存在即成功”的弱校验。
- 脚本同步：
  - `scripts/smoke-http-sms-provider.sh` 新增 `SMS_PROVIDER_SUCCESS_CODE_FIELD / SMS_PROVIDER_SUCCESS_CODE_VALUES`；
  - 本地 fake 模式默认 `code=0`，外部供应商模式默认空，避免对未知供应商强加规则。
- 验证：
  - HTTP provider + dispatch 定向测试 12/0F/0E；
  - `bash -n` 通过；
  - 外部供应商 dry-run 可显示业务成功码配置；
  - 成功码字段和值任一缺失时，脚本在副作用前返回 2。

## E 后续：HTTP provider 业务失败码投递状态机覆盖进展
- 发现：
  - provider 单测已证明业务失败码会抛异常；
  - 但组合层还需要证明异常会被 `VotingReminderDeliveryDispatchService` 转成投递明细 FAILED，而不是吞掉或误确认。
- 已补齐：
  - `VotingReminderDeliveryDispatchServiceTest` 新增 HTTP 200 + `code=1001` 组合测试；
  - 断言 `markFailed` 收到包含 `business failure` 的错误信息；
  - 断言不调用 `markConfirmed`。
- 验证：
  - HTTP provider + dispatch 定向测试 12/0F/0E。

## E 后续：HTTP provider 默认嵌套回执字段兼容进展
- 发现：
  - 真实短信供应商常把回执 ID 放在 `data.messageId / data.smsId / data.bizId / data.requestId`；
  - 原默认候选只覆盖顶层字段，未显式配置 `provider-message-id-fields` 时会漏掉常见嵌套回执。
- 已补齐：
  - 默认回执字段扩展为顶层 `providerMessageId / messageId / smsId / bizId / requestId` 加 `data.*` 对应嵌套字段；
  - 显式配置字段时仍按配置优先，不改变特殊供应商适配方式。
- 验证：
  - 新增未配置字段时读取 `data.smsId` 的 provider 单测；
  - HTTP provider + dispatch 定向测试 13/0F/0E。

## E 后续：短信 MOCK 优先验收决策
- 用户已明确“短信先 MOCK”。
- 当前默认配置已经符合该方向：`platform.voting.sms-provider-mode=mock`。
- HTTP provider 与真实供应商 smoke 脚本保留为后续增强能力，不再作为当前选举闭环完成的阻塞项。
- 当前验收重点回到：
  - 催票请求落库；
  - outbox 消费并展开逐户投递明细；
  - mock / fake provider 驱动投递明细进入 CONFIRMED；
  - yaochi 可查看投递明细、筛选和详情；
  - shennong-app worker 催票 pending/notify smoke 保持通过。

## E 后续：MOCK provider 投递状态机组合测试进展
- 发现：
  - 用户已将短信当前策略调整为 MOCK；
  - 需要有一个最小组合测试直接证明 `MockVotingReminderSmsProvider` 能驱动 dispatch 成功确认。
- 已补齐：
  - `VotingReminderDeliveryDispatchServiceTest` 新增 mock provider 组合测试；
  - 断言 provider message id 为 `mock-sms-{deliveryId}`；
  - 断言状态机调用 `markConfirmed`，不调用 `markFailed`。
- 验证：
  - HTTP/mock provider 定向测试 14/0F/0E。

## E 后续：MOCK provider 数据库集成复验
- 发现：
  - 组合测试已证明 mock provider 与 dispatch service 的交互；
  - 当前验收还需要 Spring 容器、数据库领取、状态回写和查询回显的证据。
- 已确认：
  - `VotingReminderOutboxRepositoryIntegrationTest` 覆盖 READY delivery 被 dispatch service 领取；
  - mock provider 生成 `mock-sms-{deliveryId}`；
  - 数据库回写 CONFIRMED、attempts、confirmed_at 和 provider message id；
  - repository 查询能回显脱敏手机号与 `mock-sms-*`。
- 验证：
  - `VotingReminderOutboxRepositoryIntegrationTest` Surefire 报告 4/0F/0E/0S。

## E 后续：手工验收账号与验证码修正
- 发现：
  - `13800000001` 的 `sys_dept.tenant_id` 为空，是街道办全局账号；访问租户内表决看板会触发 `requireTenantId()` 的“未识别到租户上下文”；
  - 当前 dev/test mock 短信验证码默认是 `123456`，不是 `666666`；使用错误验证码会得到 401 `SMS_CODE_INVALID`；
  - `13800000004` 后端登录实测成功，返回 `tenantId=10001 / role=GRID_OPERATOR`，并且有 `990480` 的 `can_remind=true` 动态权限。
- 手工验收实测：
  - 使用 `13800000004 / 123456` 发起 `990480` 催票成功，生成 `outboxEventId=990491`；
  - outbox scheduler 消费后状态为 CONFIRMED；
  - delivery scheduler + mock SMS provider 回写 `deliveryId=990488 / provider_message_id=mock-sms-990488`；
  - 使用 `13800000005 / 123456` 查询管理端投递明细接口返回该投递记录。

## E 后续：yaochi 手工发起催票入口缺口
- 发现：
  - 管理端表决看板只有投递明细查询，没有发起催票按钮；
  - `src/app/lib/voting.ts` 已有 `sendMobilizationReminder`，但没有页面调用；
  - 这会导致手工验收只能通过 HTTP 接口生成 outbox，不符合“菜单点击走流程”的要求。
- 已补齐：
  - `src/app/components/pages/Voting.tsx` 新增「发起催票」按钮和弹窗；
  - 仅在选中议题处于 `VOTING` 时显示；
  - 提交后调用后端 `POST /voting-subjects/{subjectId}/mobilization-reminders`，成功后刷新明细。
- 验证：
  - yaochi `npm run build` 通过。

## E 后续：shennong-app 催票 mock 开关过粗
- 发现：
  - `shennong-app` 开发环境全局 `USE_MOCK=true` 会让 worker 催票任务、待通知业主、逐户标记通知继续走本地 stub；
  - 当前目标是“短信先 MOCK”，不是“催票业务数据前端 mock”；
  - 但全局切 `USE_MOCK=false` 会把刷脸、房产、责任田等其他仍依赖 stub 的模块也切到后端，副作用过大。
- 已补齐：
  - 新增 `USE_REMINDER_MOCK` 作为催票模块细粒度开关；
  - 开发环境保持 `USE_MOCK=true`，但设置 `USE_REMINDER_MOCK=false`；
  - `src/lib/reminder.ts` 优先按 `USE_REMINDER_MOCK` 决定是否 mock；
  - worker 催票页面和 README 已同步真实联调说明。
- 验证：
  - shennong-app `npm run type-check` 通过。

## C 后续：信托制分期付款前置序号守卫
- 发现：
  - 源文件要求信托制分期付款必须等 N-1 已确认后才能执行第 N 期；
  - 第一切片时仓库没有独立信托付款表，也没有信托付款链上 `tx_hash` 字段；
  - 第一切片先用 `TRUST_FUND_PAYMENT` 治理锁证明双签完成，并通过 `t_fund_ledger_entry.business_type=7` 表达信托出账已落账；
  - Phase 82 已把链上确认承载字段补到资金流水表。
- 已补齐：
  - `TrustFundDisbursementCommand` 增加 `installmentNo / previousTrustPaymentId`；
  - 第 1 期继续兼容旧构造器；
  - Phase 69 先要求前一期 `TRUST_FUND_PAYMENT` 已双签解锁，且前一期 `business_type=7` 出账流水已写入；
  - Phase 82 通过 V3.28 在 `t_fund_ledger_entry` 增加 `blockchain_tx_hash / chain_attest_status / chain_confirmed_at`；
  - 第 N 期现要求前一期 `business_type=7` 出账流水已链上确认：`chain_attest_status=3` 且 `blockchain_tx_hash` 非空；
  - 没有前一期确认时抛 `TRUST_PREVIOUS_INSTALLMENT_NOT_CONFIRMED`。
- 验证：
  - `MaintenanceFundHandoverGuardTest` 新增第 2 期越序拒绝、前一期流水未链上确认拒绝、前一期链上确认后第 2 期放行用例；
  - `LockMatrixIntegrationTest` 回归治理锁双签链路；
  - 聚焦测试 19/0F/0E。
- 边界：
  - 当前链上确认字段落在资金流水表，覆盖本仓库真实信托出账路径；若后续拆独立信托付款表，应把同一确认语义迁移或同步到新表。

## B/G 权限后续：候选人提名角色护栏
- 发现：
  - 源文件要求候选人名单由 G 端基层经办员录入；
  - 旧权限矩阵曾给 `GRID_OPERATOR / COMMITTEE_DIRECTOR / COMMITTEE_MEMBER / OWNER_REPRESENTATIVE` 等角色保留 `candidate:nominate`；
  - 仅靠 `@PreAuthorize("hasAuthority('candidate:nominate')")` 会让旧授权绕过“B 端老业委会选举模块写权限封死”的业务红线，因此需要 service 护栏与权限矩阵双层收口。
- 已补齐：
  - `ElectionCandidateService.nominate` 增加 `UserContextHolder` 上下文校验；
  - 只有 `roleKey=GOV_OPERATOR` 且 `deptType IN (2,5)` 可提名 ELECTION 候选人；
  - 旧授权角色即使通过 PreAuthorize，也会被 service 层 `PROPOSE_FORBIDDEN_FOR_TYPE` 拦截；
  - `V3.20__candidate_nominate_role_cleanup.sql` 已删除所有非 `GOV_OPERATOR(role_id=14)` 的 `candidate:nominate` 授权，并保留 / 补齐 `GOV_OPERATOR -> candidate:nominate`；
  - yaochi `SubjectProposal.tsx` 已把提名按钮可见性同步为 `candidate:nominate + roleKey=GOV_OPERATOR + dept_type IN (2,5)`。
- 验证：
  - `ElectionCandidateServiceTest` 覆盖 GRID_OPERATOR / COMMITTEE_DIRECTOR 旧权限点绕过失败；
  - `ElectionCandidateEndpointMatrixTest` 覆盖 GOV_OPERATOR 正向、GRID_OPERATOR 在权限矩阵层 403；
  - 聚焦测试 36/0F/0E；
  - yaochi `npm run build` 通过。
- 边界：
  - `candidate:nominate` 已完成迁移级清理；后续如需继续收口旧 `voting:subject:create/publish` 通用权限点，应单独处理菜单 / 权限迁移，同时保留后端按议题类型兜底。

## B/G 权限后续：表决看板 ELECTION 直公示入口
- 发现：
  - `SubjectProposal.tsx` 已对齐 ELECTION 双签流，不显示直接「公示」；
  - `Voting.tsx` 表决看板仍按 `DRAFT + voting:subject:publish` 显示「公示」；
  - 旧 `voting:subject:publish` 是 GENERAL/MAJOR/ELECTION 共用权限点，不能证明当前角色可直公示选举议题。
- 已补齐：
  - `Voting.tsx` 将 `showPublish` 收紧为 `DRAFT + 非 ELECTION + voting:subject:publish`；
  - ELECTION 继续只在议题筹备页走提交初审 / 居委会初审 / 街道终审。
- 验证：
  - yaochi `git diff --check` 通过；
  - yaochi `npm run build` 通过。
- 边界：
  - 这次先收口可见入口，不拆 `voting:subject:publish` 权限点；后续若要权限模型彻底分离，需要新增 ELECTION 专属 publish/create 权限或做迁移级权限拆分。

## B/G 权限后续：ELECTION 立项/提交初审专属权限
- 发现：
  - `voting:subject:create` 同时服务 GENERAL/MAJOR 日常立项与 ELECTION 立项；
  - ELECTION 立项虽然已有 service 层 `GOV_OPERATOR + dept_type IN (2,5)` 护栏，但 controller 预授权无法表达“选举立项唯一执行人”；
  - ELECTION `submit-for-review` 过去也复用通用 create，`COMMUNITY_ADMIN` 会通过预授权进入 service。
- 已补齐：
  - V3.21 新增 `voting:subject:create:election`，仅授予 `GOV_OPERATOR(role_id=14)`；
  - `SubjectAdminController.propose` 按 `subjectType` 选择通用 create 或 ELECTION create；
  - `submit-for-review` 改为 ELECTION create 专属权限；
  - yaochi ELECTION 立项 / 提交初审可见性同步为 `voting:subject:create:election + GOV_OPERATOR + dept_type IN (2,5)`；
  - yaochi nav 增加 `requireAnyPermissions`，让议题筹备菜单同时支持通用立项与 ELECTION 专属立项。
- 验证：
  - pangu 矩阵聚焦测试 54/0F/0E；
  - yaochi `git diff --check` 与 `npm run build` 通过。
- 边界：
  - `voting:subject:publish` 暂不拆：GENERAL/MAJOR 仍需要日常公示，ELECTION 发布主链路已通过街道终审 `voting:subject:review:street` 完成。

## B/G 权限后续：ELECTION 双签角色分离
- 发现：
  - V3.6 同时给 `GOV_SUPER_ADMIN` 和 `COMMUNITY_ADMIN` 授予 `voting:subject:review:committee`；
  - 前端按权限展示按钮，因此街道办账号会看到居委会初审入口；
  - 后端 service 没有角色兜底时，错误授权会让街道办一人完成初审 + 终审。
- 已补齐：
  - V3.22 删除 `GOV_SUPER_ADMIN -> voting:subject:review:committee`；
  - `ProposalReviewService` 增加角色兜底：
    - `submitForCommitteeReview` 仅 `GOV_OPERATOR`；
    - `committeeApprove / committeeReject` 仅 `COMMUNITY_ADMIN`；
    - `streetApprove / streetReject` 仅 `GOV_SUPER_ADMIN`。
- 验证：
  - `VotingEndpointMatrixTest` 新增街道办调用居委会初审 403；
  - `ProposalReviewServiceTest` 新增街道办不能居委会初审、居委会不能街道终审两个误配兜底用例；
  - 聚焦测试 50/0F/0E。
- 边界：
  - yaochi 不需要新增代码：街道办居委会初审按钮由权限列表自然隐藏；已有 `showCommitteeReview` / `showStreetReview` 按不同权限展示。

## B/G 权限后续：Waiver 双签角色分离
- 发现：
  - V1.4 初始权限把 `waiver:approve:committee` 给了 `GOV_SUPER_ADMIN` 与 `COMMUNITY_ADMIN`；
  - Waiver 业务语义是居委会初审、街道办终审，街道办不应代做居委会初审；
  - `WaiverApplicationService` 过去没有读取当前角色，直接 service 调用缺少兜底。
- 已补齐：
  - V3.23 删除 `GOV_SUPER_ADMIN -> waiver:approve:committee`；
  - `WaiverApplicationService.reviewByCommittee` 仅允许 `COMMUNITY_ADMIN`；
  - `WaiverApplicationService.reviewByStreet` 仅允许 `GOV_SUPER_ADMIN`；
  - 角色错位统一抛 `APPROVER_DEPT_INVALID`。
- 验证：
  - `PreAuthorizeMatrixTest` 覆盖街道办 Waiver 居委会初审 403；
  - `ElectionWorkflowIntegrationTest` 覆盖直接 service 调用时正确角色上下文可完成 Waiver 双签链路；
  - 聚焦测试 11/0F/0E。
- 边界：
  - 本轮不改 Waiver 前端；前端按钮本来按 `waiver:approve:committee/street` 权限展示，权限回收后街道办初审入口自然隐藏。

## B/G 权限后续：Governance Lock 双签角色分离
- 发现：
  - V2.5 初始权限把 `lock:unlock:street` 给了 `GOV_SUPER_ADMIN` 与 `COMMUNITY_ADMIN`；
  - 治理锁双签用于信托付款等解锁链路，语义是业委会主任初签、街道办终签，居委会不应终签；
  - `GovernanceLockApplicationService` 过去没有读取当前角色，直接 service 调用缺少兜底。
- 已补齐：
  - V3.24 删除 `COMMUNITY_ADMIN -> lock:unlock:street`；
  - `GovernanceLockApplicationService.committeeSign` 仅允许 `COMMITTEE_DIRECTOR`；
  - `GovernanceLockApplicationService.streetSign` 仅允许 `GOV_SUPER_ADMIN`；
  - 新增 `LOCK_ROLE_FORBIDDEN` 并映射到 `LockErrorCode.LOCK_ROLE_FORBIDDEN(40112/403)`。
- 验证：
  - `LockPreAuthorizeMatrixTest` 覆盖居委会终签 403、街道办终签正向预授权；
  - `LockMatrixIntegrationTest` 覆盖直接 service 调用时居委会无法终签；
  - `MaintenanceFundHandoverGuardTest` 覆盖信托分期付款 guard 依赖的治理锁双签链路仍可用；
  - 聚焦测试 25/0F/0E。
- 边界：
  - 本轮不改治理锁前端；权限回收后居委会侧终签入口应随权限列表自然隐藏，后端 service 层已兜底未来误配。

## B/G 权限后续：资金公示动作分工 service 兜底
- 发现：
  - V2.7 权限矩阵已经正确表达资金公示动作分工；
  - `FinanceDisclosureApplicationService` 过去没有读取当前角色，直接 service 调用会绕过 compose / publish / audit 的角色语义；
  - 该问题不需要新增 Flyway，避免无意义占用 V3.25。
- 已补齐：
  - `compose` 仅允许 `COMMITTEE_DIRECTOR / COMMUNITY_ADMIN`；
  - `lockAndPublish` 仅允许 `COMMITTEE_DIRECTOR`；
  - `compare` 仅允许 `GOV_SUPER_ADMIN / COMMUNITY_ADMIN`；
  - 新增 `DISCLOSURE_ROLE_FORBIDDEN` 并映射到 `DisclosureErrorCode.DISCLOSURE_ROLE_FORBIDDEN(41110/403)`。
- 验证：
  - `FinanceDisclosureComposeTest` 覆盖错误角色不能 compose；
  - `FinanceDisclosureWorkflowTest` 覆盖错误角色不能 publish / audit，且完整 compose → publish → get → compare 链路仍可用；
  - `FinanceDisclosureHandoverGuardTest` 覆盖发布熔断分支仍先通过角色兜底后进入 HANDOVER 判断；
  - `DisclosurePreAuthorizeMatrixTest` 覆盖 controller 权限矩阵；
  - 聚焦测试 22/0F/0E。
- 边界：
  - 本轮不改前端；前端入口仍按已有 `disclosure:*` 权限展示。下一步应检查旧 `fund:disclosure:publish` 是否仍在任何入口或 controller 语义中被使用。

## B/G 权限后续：旧资金公示权限点目录清理
- 发现：
  - `fund:disclosure:publish` 已不被 controller 使用，真实资金公示通路走 `disclosure:compose/publish/audit`；
  - 旧权限点仍在 `sys_permission` 和三类角色授权里，会出现在 yaochi RBAC 权限目录中；
  - 该权限语义过宽，继续保留会误导管理员重新授权一个无实际 endpoint 的旧能力点。
- 已补齐：
  - V3.25 删除旧 `fund:disclosure:publish` 的所有角色授权；
  - V3.25 删除旧 `fund:disclosure:publish` 权限目录项；
  - 保留 `fund:account:read` 与 V2.7 `disclosure:*` 新权限族。
- 验证：
  - `DisclosurePreAuthorizeMatrixTest` 断言旧权限目录项与角色授权计数均为 0；
  - 披露聚焦测试 23/0F/0E；
  - Flyway 已从 3.24 迁移到 3.25。
- 边界：
  - 本轮不清理历史设计文档中的 V1.4 说明；它们记录的是旧阶段事实，不是当前运行态。

## B/G 权限后续：候选人两段审查角色分离
- 发现：
  - `candidate:nominate` 已通过 V3.20 清到 GOV_OPERATOR，且 service 已做 `GOV_OPERATOR + dept_type IN (2,5)` 兜底；
  - `candidate:review:party` 与 `candidate:approve` 仍保留街道办超管授权；
  - 正常流程分工应为 PARTY_SECRETARY 前置审查、COMMUNITY_ADMIN 居委会资格审查，街道办超管不应日常代签两段候选人审查。
- 已补齐：
  - V3.26 删除 `GOV_SUPER_ADMIN -> candidate:review:party / candidate:approve`；
  - `ElectionCandidateService.partyReview` 仅允许 `PARTY_SECRETARY`；
  - `ElectionCandidateService.review` 仅允许 `COMMUNITY_ADMIN`；
  - 新增 `CANDIDATE_REVIEW_FORBIDDEN` 并映射到 `ElectionErrorCode.CANDIDATE_REVIEW_FORBIDDEN(40954/403)`。
- 验证：
  - `ElectionCandidateServiceTest` 覆盖街道办超管误配兜底；
  - `ElectionCandidateEndpointMatrixTest` 覆盖街道办超管两段候选人审查 endpoint 403；
  - 候选人聚焦测试 40/0F/0E；
  - yaochi `npm run build` 通过。
- 边界：
  - yaochi 按权限展示候选人审查按钮，本轮权限回收后街道办入口自然隐藏，无需前端改动。

## B/G 权限后续：ELECTION 发布只能走街道终审
- 发现：
  - `voting:subject:publish` 是旧通用权限，仍服务 GENERAL/MAJOR 日常公示；
  - V3.5 曾把它授给 `GOV_SUPER_ADMIN`，用于表达“街道办发布选举公示”；
  - 双签流落地后，真正的选举发布入口是 `street-review`，会追加 `review_history`；
  - 继续允许街道办调用 `/publish` 会绕过终审审批留痕，只做状态更新。
- 已补齐：
  - V3.27 删除 `GOV_SUPER_ADMIN -> voting:subject:publish`；
  - `ProposalLifecycleService.publish` 对 ELECTION 直接拒绝；
  - `SubjectAdminController` 注释明确 `/publish` 只用于非 ELECTION 直接公示；
  - `VotingMobilizationPermissionMapper` 补 `timestamptz` cast，修复 E2E 开票激活动员权限的真实 SQL 类型问题。
- 验证：
  - `ProposalLifecycleServiceTest` 覆盖 ELECTION 直接 publish 拒绝；
  - `VotingEndpointMatrixTest` 覆盖街道办 `/publish` 403；
  - `ElectionWorkflowEndToEndTest` 继续验证 `submitForCommitteeReview / committeeApprove / streetApprove` 三步 `review_history`；
  - 聚焦测试 84/0F/0E。
- 边界：
  - 不拆 `voting:subject:publish` 目录项；它仍是 GENERAL/MAJOR 日常公示能力点。
  - yaochi 之前已隐藏 ELECTION 直接公示按钮，本轮无需再改前端。

## 报修初勘遗漏独立完成态
- 原型和流程方案已经明确 `SURVEYING -> SURVEY_COMPLETED`，并要求形成现场勘验记录和照片；实现却把初勘字段塞进 `submit-plan`，且允许从 `VERIFIED`、`ASSIGNED` 直接提交，导致派单与初勘表单同时出现并可绕过现场勘验。
- 修正原则：位置核验只确认空间与责任边界；初勘必须在派单、开始勘验后独立提交，至少一张现场照片；初勘完成后才能确认维修范围、估算金额和资金来源，再进入供应商邀价。

## 报修附件 OSS 接入
- 之前的现场照片/视频只停留在页面选择和业务 JSON 字段，没有真实对象存储、上传确认或可下载对象，因此不能作为可调阅的原始证据。
- 当前改为小程序上传 pangu + 后端 Java OSS SDK `PutObject`；AccessKey 只在后端环境变量中，小程序不再获得 OSS URL，也不直接访问 OSS Endpoint。
- 后端按实际文件字节计算 `Content-MD5`，校验类型和大小，取得 ETag 后一次性创建 `READY` 记录，避免客户端签名直传和二次确认造成的中间状态。
- 当前提供的 AccessKey 在 Java SDK 请求中仍可能被 OSS 以 `SignatureDoesNotMatch` 拒绝；若重构后复验仍失败，根因是云端 AccessKey 与所给 Secret 不匹配或凭证已失效，而不是客户端跨域或直传逻辑。
# Findings: 公共报修位置范围与维修专业拆分

- 当前登记页把“已知楼栋 / 待现场定位”当成全部位置范围，因此小区道路、门岗、中心泵房等已知公共区域无法准确登记：不选楼栋就会被后端判为待现场定位。
- `PUBLIC_FACILITY` 是公共资产范围或兜底概念，与给排水、电气、消防、电梯等专业类别不在同一层级；页面又默认选中它，导致专业分类失真。
- 登记页消防提交 `FIRE`，详情和框架供应商筛选使用 `FIRE_PROTECTION`，会造成显示回退和长期合作供应商无法匹配。
- 维修楼栋是位置维度，维修专业是服务能力维度；两者应正交，楼栋不应限制可选专业。

## Historical Findings

# Findings: 楼栋维修默认表决方式与动态进度

- 楼栋维修表决渠道属于社区自治规则，但物业不应因此获得社区规则编辑权限；社区设置仍由既有治理角色维护，物业只通过维修规划策略接口读取生效默认值。
- 默认渠道只适用于使用楼栋维修资金的新表决。小区公共维修资金和公共收益继续进入业主大会流程，不复用该配置。
- 单工单覆盖只需要在创建楼栋表决时传入渠道；决策记录已固化 `decision_channel`，创建后状态机没有切换渠道动作，天然满足启动后锁定和禁止混用。
- 进度节点不能按固定文案表达两套流程，应以资金来源决定：楼栋维修资金显示“楼栋表决”，小区公共维修资金或公共收益显示“业主大会”，物业包干显示“无需表决”。

## Historical Findings

# Findings: 业主报修卡片点击无响应

- `pages/owner/repair-list/index.tsx` 的普通工单卡片只有展示结构，没有 `onClick` 或详情路由，因此点击不会产生导航或选中状态。
- 在线楼栋表决虽然另有待表决块，但微信接龙工单不会进入该接口；统一从维修卡片进入详情，才能同时覆盖在线表决与微信接龙两种渠道。
- 目标页面职责调整为：列表负责检索和状态提示，详情负责查看方案、报价、表决、验收和评价，避免列表卡片承担跨阶段表单。

## Historical Findings

# Findings: 报价附件与追加邀价

- 当前“补充发出邀价”复用了首次邀价主按钮，无法表达首次邀价已完成，也没有明确追加原因。
- 当前报价接口信任前端填写的 `attachmentHash`，并未关联 OSS 附件记录；物业与供应商页面都要求人工输入文件标识。
- 既有 Java OSS 附件服务只允许现场图片/视频，数据库附件类型约束也只包含三类现场证据。
- 目标实现以 `QUOTE_DOCUMENT` 附件记录为可信来源，报价只提交 `attachmentId`，哈希由后端从 OSS ETag 派生并绑定审计动作。

## Historical Findings
# Findings: 维修工单页面降负重构

- 当前页面在宽屏下同时展示列表、详情、进度、动态操作、资金和完整流水，主任务没有形成视觉焦点。
- `ActionPanel` 已按工单状态只渲染当前可执行动作，可直接作为详情页“当前任务”主区域，不需要改动状态机。
- 列表加载会默认选中第一条工单，导致用户无法停留在纯列表视图；需要取消自动选中。
- 详情信息可归并为四个标签：当前任务、工单信息、供应商与报价、流程记录，避免一次性展示全部内容。

## Historical Findings
# 2026-07-12 勘验统一提交入口补充确认

- 后端现有 `accept`、`verifyLocation`、`assign`、`startSurvey`、`submitSurvey` 都是独立事务入口；管理端仍显示“受理”是因为页面直接映射了这组细状态动作，而不是勘验业务动作。
- `submitSurvey` 已经具备摘要、风险、1-3 张图片和可选视频的可信附件校验，可以抽成统一勘验完成逻辑复用；旧接口保留兼容，新增统一入口负责在同一事务内补齐内部状态和审计事件。
- 现场附件当前只允许在 `SURVEYING` 上传。统一入口要先上传附件再提交，因此必须把 `SURVEY_IMAGE` / `SURVEY_VIDEO` 的可上传状态放宽到勘验完成前的各个内部状态，最终仍由后端在提交时绑定附件。
- 瑶池原勘验表单实际发送的是 `evidenceImagesBase64`，与后端 `evidenceImageAttachmentIds` 契约不一致；本次不保留该前端绕行，改为和神农端一致的 Java OSS 附件上传后提交附件 ID。
