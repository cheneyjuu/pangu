# Task Plan: 维修工程项目询价、比价与定商闭环

## Current Goal (2026-07-16)
将旧维修工单中的供应商邀请、报价修订、比价推荐和定商约束迁移到共有部分维修工程项目；竞争性询价必须形成可审计的邀请与报价集合，非竞争方式必须记录适用依据，锁定方案、治理材料和合同必须绑定同一中选报价与供应商。

## Current Phases
| # | 内容 | Status |
|---|---|---|
| 1 | 核对旧工单报价能力、新项目状态机、表决披露和合同约束 | complete |
| 2 | 增加项目级邀价、报价、比价推荐与中选快照 | complete |
| 3 | 将 Yaochi 工程项目流程接入项目级供应商选择 | pending |
| 4 | 将中选报价纳入锁定方案和有权业主披露 | in_progress |
| 5 | 执行后端测试、前端构建、数据迁移与流程回归 | pending |

## Current Completion Criteria
- 新建共有部分工程项目不再把“竞争性询价”当作一个文本选项；必须先邀请供应商、收集有效报价并完成推荐后才能锁定方案。
- 竞争性询价沿用现有业务规则：至少邀请 3 家；有效报价不足 3 家时必须说明继续推荐原因。
- 框架供应商、依法直接委托和紧急指定不伪造比价，但必须绑定已核验供应商、有效报价原件和选择依据；框架方式继续校验有效合作关系。
- 中选结果固化报价、供应商、金额、方式和理由；楼栋接龙/业主大会披露同一结果，合同不得改选其他供应商或超过中选/审价/方案金额。
- 旧工单报价记录继续只读兼容，新产生的共有部分维修只走项目级报价流程，不维护两套可写状态机。

## Current Errors Encountered
| Error | Attempt | Resolution |
|---|---|---|
| Docker daemon 当前未运行，无法用容器命令直接查看 PostgreSQL 测试数据 | 1 | 不重复启动容器假设；后续直接运行既有 Spring 集成测试，按其真实数据源结果处理 |
| Spring 集成测试确认 `localhost:5432` 无 PostgreSQL 监听，应用上下文无法启动 | 1 | 先完成静态实现和测试用例；验证阶段启动项目既有 PostgreSQL 依赖后再运行，不把基础设施失败计为代码失败 |
| 测试支持类的 `post` 帮助方法遮蔽 MockMvc 静态导入，导致测试编译失败 | 1 | 将自定义方法改名为 `postJson`，避免同名解析歧义 |
| 新方案版本建立后项目状态仍为 `PLAN_LOCKED`，项目级邀价错误地只允许 `DRAFT` 项目 | 1 | 写操作同时允许 `DRAFT / PLAN_LOCKED`，但必须存在 `DRAFT` 方案；读操作优先读取草稿，否则读取当前锁定方案 |

## Historical Plan

# Task Plan: 维修实施方案编辑与业主端披露

## Current Goal (2026-07-15)
将维修工程项目从狭窄弹窗调整为可持续编辑的独立方案页面；保留金额、工程项、分摊、验收和付款规则的结构化事实，对说明性字段提供受限富文本；向有权业主开放锁定方案的只读投影，不暴露草稿、内部字段或其他业主隐私。

## Current Phases
| # | 内容 | Status |
|---|---|---|
| 1 | 固化业主可见范围、公开时点、富文本白名单和页面职责 | complete |
| 2 | 后端增加受限富文本清洗及业主侧锁定方案只读接口 | complete |
| 3 | Yaochi 将新建项目弹窗改为全宽独立方案编辑页 | complete |
| 4 | Shennong 在报修详情展示有权查看的锁定方案 | complete |
| 5 | 三端测试、构建、浏览器/小程序产物验证并分仓提交 | complete |

## Current Completion Criteria
- 新建项目不再受通用弹窗 `sm:max-w-lg` 限制，桌面端拥有完整工作区，窄屏可顺序编辑且操作按钮不遮挡内容。
- 问题原因、实施范围、施工管理要求和安全要求使用受限富文本；金额、工程量、日期、资金、分摊、验收阈值和付款比例保持结构化。
- 富文本由后端按统一白名单清洗并规范化，清洗结果进入不可变方案版本和快照哈希，不能只信任前端。
- 业主只能查看已锁定且与本人当前小区、房产范围匹配的项目方案；草稿、内部审计字段、联系方式和其他业主身份不下发。
- C 端按章节展示方案、工程项、资金/分摊、工期、验收和付款节点；项目附件继续使用受控下载/预览能力。

## Current Errors Encountered
| Error | Attempt | Resolution |
|---|---|---|
| 沙箱内全量测试无法连接本地 PostgreSQL，Mockito agent 也不能附加 JVM | 1 | 在沙箱外按相同提交重跑；629 项测试通过，确认属于验证环境限制而非代码失败 |

## Historical Plan

# Task Plan: 报修勘验统一提交入口

## Current Goal (2026-07-12)
将“勘验”作为物业真正办理的第一项业务动作：物业人员既可在 Shennong C 端现场填写并上传照片/视频，也可回到 Yaochi 管理后台提交同一份勘验记录；受理、位置核验、派单、开始初勘继续作为后端留痕，不再要求用户逐个点击。

## Current Phases
| # | 内容 | Status |
|---|---|---|
| 1 | 核对既有前置状态、附件绑定及两端提交契约 | complete |
| 2 | 增加后端统一提交勘验用例并保留内部审计事件 | complete |
| 3 | 调整 Yaochi 与 Shennong 的勘验表单和附件交互 | complete |
| 4 | 补回归测试并执行两端构建和浏览器验收 | complete |

## Current Completion Criteria
- `SUBMITTED` 等勘验前置状态不再显示“受理/派单/开始初勘”作为用户必点动作，直接展示勘验表单。
- C 端和管理后台提交同一组可信字段：现场位置、勘验结论、风险等级、照片及可选短视频。
- 后端在一个事务中完成必要前置状态推进和最终 `SURVEY_COMPLETED`，每一步仍保留可审计事件。
- 至少一张现场照片，附件必须属于当前工单并由后端绑定；视频保持可选。
- 既有已进入 `PENDING_VERIFY / VERIFIED / ASSIGNED / SURVEYING` 的工单也可继续提交，不被新入口阻塞。

## Historical Plan

# Task Plan: 公共报修位置范围与维修专业拆分

## Current Goal (2026-07-11)
将登记工单中的“维修范围、具体位置、维修专业”拆成独立维度：楼栋公共部位选择楼栋，小区公共区域记录明确位置，无法确认时进入待现场定位；同时统一维修专业分类代码，避免“公共设施”与给排水、消防等专业混用。

## Current Phases
| # | 内容 | Status |
|---|---|---|
| 1 | 核对报修位置范围、分类代码和资金路径的数据契约 | complete |
| 2 | 扩展后端公共维修位置范围与分类规范化 | complete |
| 3 | 重构登记页面的位置范围、具体位置和维修专业交互 | complete |
| 4 | 补回归测试并执行前后端与浏览器验收 | complete |

## Current Completion Criteria
- 楼栋公共部位必须选择有效楼栋；小区公共区域不伪造楼栋，但必须填写明确位置；未知位置继续使用待现场定位。
- 后端持久化公共维修位置范围，位置纠偏后仍能正确区分楼栋与小区公共区域。
- “维修专业”只包含同一层级的专业类别，不再把“公共设施”当成默认专业。
- 消防分类统一使用 `FIRE_PROTECTION`，长期合作供应商筛选与工单展示使用同一代码。
- 既有工单和旧客户端分类代码保持可读取，新增工单统一写入规范代码。

## Current Errors Encountered
| Error | Attempt | Resolution |
|---|---|---|
| 公共区域范围和分类规范化回归测试失败：后端忽略 `publicAreaScope`，无楼栋一律进入待定位，`FIRE` 原样保存 | 1 | 作为预期红灯，下一阶段增加可信位置范围字段、创建校验和分类别名规范化 |
| 全量维修流程测试中的框架供应商仍写入旧分类 `PUBLIC_FACILITY`，规范化查询后匹配不到 | 1 | 测试数据改用规范代码 `PUBLIC_AREA_FACILITY`，生产迁移同步规范历史关系数据 |

## Historical Plan

# Task Plan: 楼栋维修默认表决方式与动态进度

## Current Goal (2026-07-11)
允许有权限的社区治理角色在社区设置中配置“楼栋维修默认表决方式”；物业发起具体楼栋维修表决时继承默认值并可在启动前覆盖。同时按工单资金路径动态显示“楼栋表决”或“业主大会”，不再合并两个不同流程。

## Current Phases
| # | 内容 | Status |
|---|---|---|
| 1 | 核对社区设置权限、楼栋表决启动契约与进度组件 | complete |
| 2 | 增加社区级楼栋维修默认表决方式及审计 | complete |
| 3 | 将默认值下发到工单并支持启动前单工单覆盖 | complete |
| 4 | 按资金来源动态显示第 7 个流程节点 | complete |
| 5 | 补后端回归、前端构建与页面契约验收 | complete |

## Current Completion Criteria
- 社区设置保存 `ONLINE` 或 `WECHAT` 作为楼栋维修新表决的默认渠道，并记录既有社区设置审计。
- 物业在推荐供应商后看到社区默认值，可为当前工单改选另一渠道；创建表决后渠道锁定，不允许中途切换或混用。
- 后端在未显式传入渠道时使用社区默认值，显式传入不同值时按工单覆盖并在事件中标记来源。
- 使用楼栋维修资金的工单显示“楼栋表决”；使用小区公共维修资金或公共收益的工单显示“业主大会”。
- 社区默认渠道不影响业主大会的纸质/线上投票方式配置。

## Historical Plan

# Task Plan: 业主报修卡片进入详情

## Current Goal (2026-07-11)
修复 Shennong 业主端报修卡片点击无响应：列表只承担浏览与入口职责，点击进入原生详情页；C 端在线表决、报价预览、验收和评价在详情页办理，微信接龙工单只展示状态和参与提示。

## Current Phases
| # | 内容 | Status |
|---|---|---|
| 1 | 复现并定位报修卡片没有点击处理器 | complete |
| 2 | 新增业主维修详情页并迁移工单操作 | complete |
| 3 | 接通列表跳转、在线表决和报价预览 | complete |
| 4 | 执行类型检查、小程序构建及产物核验 | complete |

## Current Completion Criteria
- 点击任一可见报修卡片进入对应维修详情页，返回后仍回到维修列表。
- 在线表决工单在详情页显示推荐方案、供应商报价、附件和逐房屋表决入口。
- 微信接龙或当前账号不具备线上表决资格时，不出现线上投票按钮，并明确下一步。
- 验收、整改和完工评价不再挤在列表卡片中，统一在详情页办理。

## Historical Plan

# Task Plan: 楼栋维修表决渠道二选一

## Current Goal (2026-07-11)
楼栋维修在物业推荐供应商后，由物业选择“C 端在线表决”或“微信接龙”且二者互斥；在线渠道由范围内业主查看推荐方案和报价后逐户表决，微信渠道由楼栋长发起、物业上传并核验截图，之后统一进入物业报审。

## Current Phases
| # | 内容 | Status |
|---|---|---|
| 1 | 核对现有接龙、业主身份、报价附件和报审模型 | complete |
| 2 | 增加表决渠道、在线业主表决和渠道专属完成规则 | complete |
| 3 | 调整 Yaochi 渠道选择、在线进度及微信材料上传 | complete |
| 4 | 接入 Shennong 业主待表决列表、报价预览和选择提交 | complete |
| 5 | 补回归并执行三端构建和真实流程验证 | complete |

## Current Completion Criteria
- 同一楼栋维修只能选择 ONLINE 或 WECHAT，一个决策周期内不可混用。
- ONLINE 仅向范围内且已实名的业主展示推荐供应商、当前有效报价和维修方案，并按其名下房屋提交同意/不同意/弃权。
- WECHAT 保留楼栋长线下发起，物业逐户核验并上传微信截图；C 端不出现在线表决入口。
- 两种渠道均按同一楼栋人数与面积口径计算结果；在线渠道不要求微信截图，微信渠道必须有截图证据。
- 物业仅在表决完成后整理正式报审材料并提交业委会。

## Current Errors Encountered
| Error | Attempt | Resolution |
|---|---|---|
| 在线表决测试使用同一房屋的非最终投票代表，待表决列表为空 | 1 | 保留生产代码的“一房一代表”限制，测试改用按 `is_voting_delegate DESC, opid ASC` 选出的代表业主身份 |
| 抽取预览凭证构造方法后仍引用原方法局部变量 `attachmentId`，后端编译失败 | 1 | 改为从已校验的 `RepairAttachment` 读取 `attachmentId()` |

## Historical Plan

# Task Plan: Excel 报价附件转 PDF 预览

## Current Goal (2026-07-11)
对 Excel 报价原件保留原文件下载，同时由 Java 后端通过 LibreOffice 无界面转换为派生 PDF，前端沿用现有 PDF 弹窗预览。

## Current Phases
| # | 内容 | Status |
|---|---|---|
| 1 | 核对附件上传、对象存储、预览接口和 LibreOffice 运行环境 | complete |
| 2 | 实现 Excel 到 PDF 转换与派生预览对象管理 | complete |
| 3 | 接入前端 PDF 预览并标明转换状态 | complete |
| 4 | 补测试并执行后端、前端和浏览器验收 | complete |

## Current Completion Criteria
- `.xls`、`.xlsx` 原件不被替换，下载操作仍返回原始 Excel。
- 首次预览按权限读取原件、转换并写入确定性的派生 PDF 对象；后续预览复用派生对象。
- 转换进程使用独立临时目录和 LibreOffice profile，设置超时并校验 PDF 输出，禁止拼接 shell 命令。
- 删除未绑定 Excel 原件时同步清理派生 PDF；转换失败不影响已上传原件。
- 前端继续在站内弹窗预览 PDF，并明确提示“Excel 已转换为 PDF 预览”。

## Errors Encountered
| Error | Attempt | Resolution |
|---|---|---|
| 中文 Excel 使用原始字体转换后，PDF 中中文字符缺失 | 2 | 为每次转换生成独立 Fontconfig 配置，将常见中文 Office 字体回退到可配置 CJK 字体，并用真实中文工作簿渲染验收 |
| 后端运行期间直接覆盖可执行 Spring Boot jar，停机时两次出现嵌套依赖类加载失败 | 2 | 归因于运行中的可执行 jar 被重新打包覆盖；后续固定为先停止进程、再打包、最后启动，生产部署也应使用不可变发布文件 |

## Historical Plan

# Task Plan: 供应商报价附件弹窗预览

## Current Goal (2026-07-11)
将供应商报价附件从“点击后自动下载/打开新窗口”改为站内弹窗预览；图片和 PDF 直接预览，Office 文件显示文件信息并由用户明确点击下载原件。

## Current Phases
| # | 内容 | Status |
|---|---|---|
| 1 | 核对附件权限、OSS 签名地址和前端查看逻辑 | complete |
| 2 | 增加后端短时效 inline 预览凭证 | complete |
| 3 | 实现图片/PDF 弹窗预览和显式下载 | complete |
| 4 | 补接口回归并执行后端、前端和浏览器验收 | complete |

## Current Completion Criteria
- 点击“预览附件”只打开站内弹窗，不自动下载或跳转新窗口。
- 图片和 PDF 使用短时效预览地址在弹窗中展示。
- Word、Excel 等不可稳定内嵌的格式显示文件名、类型、大小和明确的“下载原件”按钮。
- 预览与下载沿用后端工单可见性、供应商附件访问和审计边界，不暴露长期 OSS 地址。
- 桌面端弹窗适合阅读，移动端全屏且无横向溢出。

## Errors Encountered
| Error | Attempt | Resolution |
|---|---|---|
| OSS 拒绝预签名地址中的 `response-content-type`，返回 `InvalidRequest` | 1 | 不覆盖对象上传时已保存的 `Content-Type`，预览地址只请求 `inline` 响应方式；真实 OSS 请求恢复为 200 |
| 移动端自动化直接点击抽屉导航元素多次提示元素在视口外 | 2 | 归因于抽屉元素仍在可访问树但发生位移；改为触发语义点击完成验收，不把测试工具规避写入产品代码 |

## Historical Plan

# Task Plan: 维修工单侧边详情页

## Current Goal (2026-07-11)
将维修工单详情从替换列表的独立页面改为右侧宽侧页：桌面保留列表上下文，移动端全屏展示，侧页内部继续使用“办理 / 详情与记录”两个工作面。

## Current Phases
| # | 内容 | Status |
|---|---|---|
| 1 | 核对现有详情渲染、Sheet 能力和移动端约束 | complete |
| 2 | 将详情改为非模态右侧宽侧页并保留列表 | complete |
| 3 | 调整面包屑、固定头部和侧页独立滚动 | complete |
| 4 | 执行构建及桌面/移动端浏览器验收 | complete |

## Current Completion Criteria
- 点击工单行或查看按钮后，从右侧打开详情，列表不被详情页面替换。
- 桌面端侧页宽度满足进度、报价和表单操作；移动端使用全屏宽度。
- 点击其他可见工单可直接切换侧页内容，关闭后保留筛选条件和列表位置。
- 主面包屑保持“物业管理 > 维修工单”，侧页内部显示工单详情标题。
- 侧页头部固定、内容独立滚动，不出现横向溢出或控件遮挡。

## Errors Encountered
| Error | Attempt | Resolution |
|---|---|---|
| 浏览器用嵌套 `getByRole("heading", { level: 2 })` 读取侧页标题时严格模式命中多个标题 | 1 | 不重复该选择器，改用详情快照和唯一工单标题核对切换结果 |

## Historical Plan

# Task Plan: 维修工单详情信息架构优化

## Current Goal (2026-07-11)
补齐维修工单详情面包屑层级，将四个分散页签收敛为“办理”和“详情与记录”两个工作面；报价列表直接提供推荐供应商入口，推荐理由和选择方式改在弹窗中填写。

## Current Phases
| # | 内容 | Status |
|---|---|---|
| 1 | 核对详情页、全局面包屑和供应商推荐现状 | complete |
| 2 | 增加“工单详情”面包屑层级 | complete |
| 3 | 合并办理与供应商报价、合并只读信息与流程记录 | complete |
| 4 | 将供应商推荐参数迁入报价行弹窗 | complete |
| 5 | 执行前端构建和桌面/移动端浏览器验收 | complete |

## Current Completion Criteria
- 面包屑显示“物业管理 > 维修工单 > 工单详情”，返回列表后恢复两级。
- 详情页只保留“办理”和“详情与记录”两个页签。
- 报价列表直接提供“推荐此供应商”操作，页面不再重复展示报价查看入口和内联推荐表单。
- 推荐方式、推荐理由、响应不足三家说明及长期合作关系在弹窗中完成。
- 桌面和移动端不存在控件拥挤、遮挡或横向溢出。

## Errors Encountered
| Error | Attempt | Resolution |
|---|---|---|
| 清理旧报价表单的大块补丁上下文未匹配 | 1 | 改为按状态声明、加载逻辑、渲染区和弹窗分别做小范围补丁 |
| 浏览器标签连接误用了 `tabs.claim` | 1 | 查明当前 API 为 `user.claimTab`，后续使用正确入口且不重复失败调用 |
| 移动端直接点击隐藏侧栏中的维修工单超时 | 1 | 先点击移动端“打开导航”，再从可见侧栏进入维修工单 |
| Playwright CLI 将验收截图自动加入暂存区 | 1 | 验收结束后删除 `.playwright-cli` 并仅取消暂存该临时目录，未影响业务改动 |

## Historical Plan

# Task Plan: 发出邀价后的按钮状态

## Current Goal (2026-07-11)
发出维修邀价成功后立即清空供应商选择并将按钮切换为不可操作的完成态；询价过程中重新选择供应商时，按钮只用于补充发出邀价。

## Current Phases
| # | 内容 | Status |
|---|---|---|
| 1 | 核对邀价动作与页面本地选择状态 | complete |
| 2 | 让动作结果返回成功标识并清空选择 | complete |
| 3 | 区分首次邀价、已发出和补充邀价按钮文案 | complete |
| 4 | 执行前端构建和浏览器验收 | complete |

## Current Completion Criteria
- 首次邀价成功后，按钮立即变为禁用的“邀价已发出”。
- 失败时保留供应商选择，方便修正后重试。
- 询价中重新选择供应商后显示“补充发出邀价”。
- 不因重复点击产生含义不明的再次邀价。

## Historical Plan

# Task Plan: 报修业务文案校正

## Current Goal (2026-07-11)
把维修工单页面中的技术枚举、模糊状态和不准确流程说明改为真实业务用语，明确物业只推荐供应商、楼栋接龙与业主大会分流、主任或副主任任一人确认以及业委会另行盖章。

## Current Phases
| # | 内容 | Status |
|---|---|---|
| 1 | 核对页面文案与已确认维修流程 | complete |
| 2 | 校正状态、资金来源、流程节点和审计动作 | complete |
| 3 | 按资金来源展示对应流程说明 | complete |
| 4 | 执行前端构建和浏览器验收 | complete |

## Current Completion Criteria
- 页面不直接显示 `LOW`、`BUILDING_MAINTENANCE_FUND` 等技术枚举。
- 物业动作表述为“推荐供应商”，不写成物业已经最终定商。
- 楼栋维修显示楼栋接龙，小区公共维修显示业主大会表决。
- 主任或副主任任一人确认与业委会盖章是两个独立节点。
- 审计流水显示中文业务动作。

## Historical Plan

# Task Plan: 供应商激活后待报价为空

## Current Goal (2026-07-11)
确认供应商账号激活后工作台为空的真实原因，保持“账号激活”和“维修邀价”两个业务事实独立，并优化空状态与物业端提示，避免把开通账号误认为已经收到工单。

## Current Phases
| # | 内容 | Status |
|---|---|---|
| 1 | 核对激活邀请、供应商组织、维修邀价与工单状态 | complete |
| 2 | 验证供应商工作台查询边界 | complete |
| 3 | 优化供应商空状态和物业端账号邀请提示 | complete |
| 4 | 执行前端构建和浏览器验收 | complete |

## Current Completion Criteria
- 不把账号激活错误地转换成维修邀价。
- 未收到邀价时明确显示“账号已开通，但物业尚未发出维修邀价”。
- 物业端账号邀请动作明确说明不会发出维修邀价。
- 物业真正发出邀价后，供应商工作台仍按企业组织显示待报价工单。

## Historical Plan

# Task Plan: 供应商企业与账号状态优化

## Current Goal (2026-07-11)
把“企业主体核验”和“个人账号激活”拆成两个清晰状态；发出邀价后让物业能直接看到供应商登录手机号、激活状态和邀请编号，避免把“企业待核验”误解为账号不可用。

## Current Phases
| # | 内容 | Status |
|---|---|---|
| 1 | 核验企业核验、账号身份和激活邀请真实数据 | complete |
| 2 | 扩展供应商查询模型，返回账号与邀请状态 | complete |
| 3 | 优化 Yaochi 双状态展示和邀请反馈 | complete |
| 4 | 补测试并执行后端、前端、浏览器验证 | complete |
| 5 | 更新业务文档和运行进度 | complete |

## Current Completion Criteria
- 企业状态明确显示为“企业待核验/企业已核验”，不再使用含义模糊的“待核验”。
- 账号状态独立显示：联系人未补充、未发送邀请、待激活、账号已激活。
- 待激活状态展示邀请编号和登录手机号；发出邀价自动生成邀请后页面可见。
- 已激活供应商显示实际登录手机号，不再重复发送邀请。
- 无联系人供应商继续支持物业代录报价，签约前企业核验守卫不变。

## Historical Plan

# Task Plan: 供应商最小登记

## Current Goal (2026-07-11)
允许物业只填写企业名称完成供应商临时登记；统一社会信用代码、企业联系人和联系人手机号均为可选信息，缺失资料不得阻塞邀价和物业代录报价，但账号激活与签约前核验仍须在资料补齐后进行。

## Current Phases
| # | 内容 | Status |
|---|---|---|
| 1 | 核验供应商登记、数据库约束和激活邀请依赖 | complete |
| 2 | 放宽后端与数据库约束并保护无联系人邀价 | complete |
| 3 | 调整 Yaochi 可选字段、列表空值和激活动作 | complete |
| 4 | 补回归测试并执行全量验证 | complete |
| 5 | 更新业务文档和运行进度 | complete |

## Current Completion Criteria
- 仅填写企业名称可以成功登记并加入待邀价供应商列表。
- 可选字段为空时数据库、响应和管理端均正确处理，不出现空值字符串或页面异常。
- 无联系人供应商仍可收到维修邀价并由物业代录报价，但系统不自动生成账号激活邀请。
- 后续用同一企业名称补充资料时复用临时供应商记录，不重复创建组织。
- 签约前企业核验规则保持不变。

## Historical Plan

# Task Plan: 报修治理路径自动分流

## Current Goal (2026-07-11)
移除方案提交阶段由物业手工触发的“治理路径判定”，改为后端依据维修范围、资金来源和流程配置自动进入正确的邀价或简化路径，避免楼栋/公共维修跳过邀价、定商、民意表决和报审。

## Current Phases
| # | 内容 | Status |
|---|---|---|
| 1 | 核验现有 route-plan、邀价、定商和治理状态机 | complete |
| 2 | 收口后端自动分流规则并补状态机回归测试 | complete |
| 3 | 移除 Yaochi 手工按钮并校正当前动作 | complete |
| 4 | 执行后端、前端和浏览器验收 | complete |
| 5 | 更新业务文档和进度记录 | complete |

## Current Completion Criteria
- `PLAN_SUBMITTED` 页面不再出现“治理路径判定”按钮。
- 楼栋维修和公共维修不能从方案提交直接跳到 `GOVERNANCE_PENDING` 或 `APPROVED`。
- 需要供应商的维修自动进入邀价；是否比价仍服从既有可配置策略，不在本次倒退为固定三家。
- 私有/物业包干维修保留无需供应商的简化路径，但由后端自动决定。
- 自动分流具备后端测试，Yaochi 构建与真实页面验收通过。

## Historical Plan

# Task Plan: 供应商账号激活闭环

## Current Goal (2026-07-11)
补齐维修供应商“组织登记 -> 激活邀请 -> 个人账号激活 -> 绑定供应商组织 -> 独立登录供应商工作台 -> 在线报价”的真实闭环，并提供可验收的供应商账号。

## Current Phases
| # | 内容 | Status |
|---|---|---|
| 1 | 核验现有账号、身份、供应商组织与邀请模型 | complete |
| 2 | 设计并实现后端激活契约、账号绑定和审计 | complete |
| 3 | 准备供应商验收账号并贯通 Yaochi 登录/工作台 | complete |
| 4 | 补测试并执行后端全量、前端构建和浏览器验收 | complete |
| 5 | 完成缺口审计并更新业务文档 | complete |

## Current Completion Criteria
- 供应商组织与个人账号分离，不生成共享默认密码。
- 激活邀请只存内部随机凭据哈希，公开激活校验邀请编号、受邀手机号、短信验证码、有效期、一次性使用和组织归属。
- 激活后创建或复用自然人账号，绑定 `SERVICE_PROVIDER_STAFF` 与供应商组织。
- 同一企业可有多个独立经办人账号；报价记录具体 `user_id`。
- 未注册/未激活供应商不阻断物业代录报价路径。
- 有一个明确的本地验收供应商账号可以登录 Yaochi 供应商工作台并看到邀价。
- 后端测试、Yaochi 构建与浏览器流程均通过。

## Historical Plan

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
| Flyway checksum mismatch for V1/V1.2/V1.4/V3.x | 网格员角色重命名聚焦测试第 1 次 | 不改已应用历史迁移；恢复历史迁移文本，只在新增 V3.30 中执行 `GRID_OPERATOR -> GRID_MEMBER` 迁移 |
| `sys_dept_building_scope` reset 写入 30003 被触发器拒绝 | 网格员范围聚焦测试第 2 次（同一 BeforeEach 重复 7 次） | V1 注释/责任田 seed 中有 30003，但当前 `c_owner_property` 无该楼栋；测试清理只恢复产权表真实存在的 30001/30002 |

## Next Tasks
- shennong-app：开发环境保留全局 `USE_MOCK=true`，但催票工作台已独立 `USE_REMINDER_MOCK=false` 默认走真实接口；本地 pending/notify live smoke 已跑通，后续如需复验，先执行 `scripts/prepare-reminder-smoke.sql`，完成后可用 `scripts/cleanup-reminder-smoke.sql` 清理 fixture。
- yaochi：投递明细已接入表决看板，支持楼栋 / 状态筛选与单条详情查看；表决中议题已补「发起催票」入口，可直接调用 `mobilization-reminders` 生成 outbox。
- 短信当前策略：先按 MOCK 验收；`application.yml` 默认 `platform.voting.sms-provider-mode=mock`，当前闭环不再被真实短信供应商参数阻塞。
- MOCK 复验：`VotingReminderOutboxRepositoryIntegrationTest` 已验证真实 Spring + PostgreSQL/Redis 测试环境下的 `READY -> CONFIRMED / mock-sms-*`；2026-07-01 又用真实 HTTP 手工链路复现 `990480 -> outbox 990491 -> delivery 990488 -> mock-sms-990488`。
- 真实供应商：作为后续可选项保留；拿到参数后再运行 `DRY_RUN=true START_FAKE_SMS=false ... scripts/smoke-http-sms-provider.sh` 做真实网关联调。
- V3.28 已用于资金流水链上存证字段与信托分期链上确认 guard；后续迁移从 V3.29+ 开始。
- `voting:subject:create` 仍保留给 GENERAL/MAJOR 通用立项；ELECTION 立项 / 提交初审已切到 `voting:subject:create:election`。`voting:subject:publish` 仍保留给 GENERAL/MAJOR 日常公示，ELECTION 发布只能走 `voting:subject:review:street`。

## Current Goal: 网格员静态角色与跨小区数据范围

### Goal
核验并补齐：网格员可跨小区；楼栋数据范围只能由居委会侧人员分配；`GRID_MEMBER` 是全系统统一静态角色，具体 1/2/3 号网格由 `sys_dept.dept_type=5` 网格组织节点与楼栋行级范围解耦承载。

### Phases
| # | 内容 | Status |
|---|---|---|
| 1 | 核验现有角色、部门、楼栋责任田、工作身份授权实现 | complete |
| 2 | 对照需求列出缺口与冲突，确认无需外部法规查询的技术边界 | complete |
| 3 | 若缺失，补齐数据库迁移、应用服务、接口与测试 | complete |
| 4 | 跑聚焦验证并记录结果 | complete |

### Verification Checklist
- [x] 只有居委会侧管理身份能为网格员/网格节点配置楼栋范围，业委会侧身份被拒绝。
- [x] `GRID_MEMBER` 作为静态 role_key 存在并用于网格员分身，不再依赖新建 5 个角色表达 5 个网格。
- [x] 居委会节点可一键生成 5 个 `dept_type=5` 网格组织节点，重复调用保持幂等。
- [x] 网格分身的 `dept_id` 指向 `dept_type=5` 网格节点，`effective_data_scope=OWNER_GROUP`。
- [x] 楼栋范围通过 `sys_dept_building_scope` 挂在网格组织节点上；`OWNER_GROUP` 登录上下文从节点范围与历史用户责任田并集反查授权楼栋。

### Verification Result
- `mvn -pl pangu-bootstrap -am -Dtest=WorkIdentityAdminTest,BuildingAssignmentTest,SwitchShadowMatrixTest,SysUserRoleTriggerTest -Dsurefire.failIfNoSpecifiedTests=false test`：35 tests，0 failures，0 errors。
- `mvn test`：536 tests，0 failures，0 errors，1 skipped。

## Current Goal: 报修初勘独立状态与现场证据

### Phases
| # | 内容 | Status |
|---|---|---|
| 1 | 对照原型和方案核验派单、初勘、方案估算状态 | complete |
| 2 | 新增 `SURVEY_COMPLETED`、`submit-survey` 与现场照片/短视频校验 | complete |
| 3 | 拆分 yaochi / shennong-app 各阶段动作 | complete |
| 4 | 聚焦测试、全量测试、构建和 Chrome 页面回归 | complete |

### Verification Result
- `RepairWorkOrderFlowTest`：9 tests，0 failures，0 errors。
- `mvn clean test`（沙箱外）：583 tests，0 failures，0 errors，1 skipped。
- yaochi build、shennong-app type-check 和 Chrome 页面状态回归通过。

## Current Goal: 报修现场附件接入私有 OSS

### Phases
| # | 内容 | Status |
|---|---|---|
| 1 | 核验现有附件字段、角色权限、状态机和 OSS 配置边界 | complete |
| 2 | 实现 pangu multipart 接收与 Java OSS SDK `PutObject`、下载和删除 | complete |
| 3 | shennong-app 接入 `Taro.uploadFile` 与业务动作绑定 | complete |
| 4 | 聚焦测试、全量测试、小程序类型检查和构建 | complete |
| 5 | 使用真实 AccessKey 完成 PUT/GET/DELETE 冒烟测试 | complete |

### Verification Result
- `mvn test`：584 tests，0 failures，0 errors，1 skipped。
- shennong-app `npm run type-check`、`npm run build:weapp:dev` 通过。
- 客户端直传已移除；新 RAM AccessKey 已通过 Java SDK `PutObject` 上传、签名下载和删除真实冒烟测试，测试数据已清理。
# Task Plan: 报价附件与追加邀价

- [completed] 后端支持报价原件通过 Java OSS 上传并与报价记录绑定
- [completed] 管理端拆分首次邀价与追加邀价，并将物业代录报价改为弹窗
- [completed] 供应商工作台改为直接上传报价附件
- [completed] 完成后端测试、前端构建和浏览器验收
- [completed] 提交 pangu 与 yaochi 代码

## Historical Plans
# Task Plan: 维修工单页面降负重构

- [completed] 将工单列表和工单详情拆为独立视图
- [completed] 详情页按当前任务、工单信息、供应商报价、流程记录分区
- [completed] 保持当前状态动作和已完结资料可追溯
- [completed] 完成 Yaochi 构建和 Chrome 桌面验收

## Historical Plans
