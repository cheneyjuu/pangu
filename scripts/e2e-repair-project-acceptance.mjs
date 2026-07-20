// 关联业务：以公开 HTTP 接口验收业主报修、混合表决、施工、整改和工程验收通过的完整链路。
import { writeFile } from "node:fs/promises";

const config = {
  baseUrl: requireEnv("PANGU_API_BASE_URL").replace(/\/$/, ""),
  smsCode: requireEnv("PANGU_BDD_SMS_CODE"),
  buildingId: Number(requireEnv("PANGU_BDD_BUILDING_ID")),
  evidenceFile: process.env.PANGU_BDD_EVIDENCE_FILE || "/tmp/pangu-repair-acceptance-evidence.json",
};

if (process.env.PANGU_BDD_ALLOW_MUTATION !== "true") {
  throw new Error("必须显式设置 PANGU_BDD_ALLOW_MUTATION=true 才能创建验收数据");
}
if (!Number.isSafeInteger(config.buildingId)) {
  throw new Error("PANGU_BDD_BUILDING_ID 必须是有效楼栋 ID");
}

const runId = new Date().toISOString().replace(/[-:.TZ]/g, "").slice(0, 14);
const prefix = `PROD-BDD-${runId}`;
const budget = 2207;
const quoteAmount = 1090;
const audit = [];

function requireEnv(name) {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`缺少环境变量 ${name}`);
  return value;
}

function assert(condition, message) {
  if (!condition) throw new Error(`验收断言失败：${message}`);
}

function step(name, details = {}) {
  const entry = { at: new Date().toISOString(), name, ...details };
  audit.push(entry);
  process.stdout.write(`[BDD] ${name}${Object.keys(details).length ? ` ${JSON.stringify(details)}` : ""}\n`);
}

async function request(path, { method = "GET", token, body, form, headers = {} } = {}) {
  const requestHeaders = { Accept: "application/json", ...headers };
  if (token) requestHeaders.Authorization = `Bearer ${token}`;
  let requestBody;
  if (form) {
    requestBody = form;
  } else if (body !== undefined) {
    requestHeaders["Content-Type"] = "application/json";
    requestBody = JSON.stringify(body);
  }
  const response = await fetch(`${config.baseUrl}${path}`, {
    method,
    headers: requestHeaders,
    body: requestBody,
  });
  const text = await response.text();
  let payload;
  try {
    payload = text ? JSON.parse(text) : null;
  } catch {
    throw new Error(`${method} ${path} 返回非 JSON（HTTP ${response.status}）：${text.slice(0, 240)}`);
  }
  if (!response.ok || payload?.code !== 200) {
    throw new Error(`${method} ${path} 失败（HTTP ${response.status}）：${payload?.msg || text.slice(0, 240)}`);
  }
  return payload.data;
}

async function login(phone, clientPortal) {
  const data = await request("/auth/login", {
    method: "POST",
    body: { username: phone, smsCode: config.smsCode, loginType: 1, clientPortal },
  });
  assert(data?.access_token, `${phone} 登录后未返回令牌`);
  return data.access_token;
}

function pdf(name) {
  return new Blob([`%PDF-1.4\n${prefix}\n${name}\n%%EOF`], { type: "application/pdf" });
}

async function upload(path, token, name, fields = {}) {
  const form = new FormData();
  for (const [key, value] of Object.entries(fields)) form.set(key, String(value));
  form.set("file", pdf(name), name);
  return request(path, { method: "POST", token, form });
}

async function uploadProjectAttachment(projectId, token, name) {
  return upload(`/admin/repair-projects/${projectId}/attachments`, token, name);
}

async function ensurePropertyServiceOrganization(tokens) {
  const organizations = await request("/admin/property-service-organizations", {
    token: tokens.committeeDirector,
  });
  const active = organizations.find((item) => item.status === "ACTIVE");
  if (active) {
    step("确认物业服务组织前置资料", { organizationId: active.organizationId, reused: true });
    return active.organizationId;
  }
  throw new Error("隔离验收租户尚未启用物业服务组织，请先完成企业、项目部和物业身份挂接");
}

function threshold(numerator, denominator, comparison) {
  return { numerator, denominator, comparison };
}

function countingRule() {
  return {
    participationOwnerThreshold: threshold(1, 2, "AT_LEAST"),
    participationAreaThreshold: threshold(1, 2, "AT_LEAST"),
    approvalOwnerThreshold: threshold(1, 2, "GREATER_THAN"),
    approvalAreaThreshold: threshold(1, 2, "GREATER_THAN"),
  };
}

async function activateMixedVotingRule(tokens) {
  const fields = [
    "ALLOWED_MEETING_FORMS",
    "PLAN_PUBLICITY_DAYS",
    "MEETING_NOTICE_DAYS",
    "VALID_DELIVERY_METHODS",
    "NON_RESPONSE_POLICY",
    "PROXY_VOTING_POLICY",
    "VOTING_CHANNEL_POLICY",
    "ONLINE_IDENTITY_VERIFICATION",
    "PAPER_BALLOT_SEAL",
    "DUPLICATE_VOTE_POLICY",
    "COUNTING_RULES",
    "RESULT_ANNOUNCEMENT_DAYS",
  ];
  const sourceClauseReferences = Object.fromEntries(
    fields.map((field) => [field, { pageNumber: 1, clause: `第1条 ${field}` }]),
  );
  const configuration = {
    allowedMeetingForms: ["WRITTEN_CONSULTATION", "INTERNET", "ONLINE_AND_OFFLINE"],
    planPublicityDays: 0,
    meetingNoticeDays: 0,
    validDeliveryMethods: ["ELECTRONIC", "DOOR_TO_DOOR"],
    nonResponsePolicy: "NOT_PARTICIPATED",
    proxyVotingPolicy: "WRITTEN_AUTHORIZATION_REQUIRED",
    votingChannelPolicy: "PAPER_AND_ONLINE",
    onlineIdentityVerificationRequired: true,
    paperBallotSealRequired: true,
    duplicateVotePolicy: "ONLINE_PREVAILS",
    countingRules: { GENERAL: countingRule(), MAJOR: countingRule() },
    resultAnnouncementDays: 0,
    sourceClauseReferences,
  };
  const form = new FormData();
  form.set("ruleName", `${prefix}维修混合表决依据`);
  form.set("ruleVersion", prefix);
  form.set("effectiveDate", new Date(Date.now() - 86400000).toISOString().slice(0, 10));
  form.set("changeReason", "隔离生产验收：验证线上、纸质及重复票裁决");
  form.set(
    "configuration",
    new Blob([JSON.stringify(configuration)], { type: "application/json" }),
    "configuration.json",
  );
  form.set("file", pdf("维修混合表决规则.pdf"), "维修混合表决规则.pdf");
  const draft = await request("/admin/owners-assembly-rules/drafts", {
    method: "POST",
    token: tokens.propertyManager,
    form,
  });
  await request(`/admin/owners-assembly-rules/${draft.ruleId}/submit`, {
    method: "POST",
    token: tokens.propertyManager,
  });
  for (const field of fields) {
    await request(`/admin/owners-assembly-rules/${draft.ruleId}/field-confirmations/${field}/confirm`, {
      method: "POST",
      token: tokens.committeeDirector,
    });
  }
  const active = await request(`/admin/owners-assembly-rules/${draft.ruleId}/activate`, {
    method: "POST",
    token: tokens.committeeDirector,
  });
  assert(active.status === "ACTIVE", "混合表决规则未生效");
  step("登记并启用混合表决规则", { ruleId: active.ruleId });
  return active.ruleId;
}

async function createSurveyedWorkOrder(tokens) {
  const workOrder = await request("/me/repairs/public", {
    method: "POST",
    token: tokens.owner,
    body: {
      publicAreaScope: "BUILDING",
      buildingId: config.buildingId,
      locationText: "楼栋公共外墙窗框交界",
      title: `${prefix}业主报修`,
      description: "现场发现楼栋公共部位渗水，需要完成勘验后纳入维修工程。",
      category: "WATERPROOFING",
    },
  });
  assert(workOrder.status === "SUBMITTED", "业主报修未提交");
  const reportForm = new FormData();
  reportForm.set("contentType", "image/jpeg");
  reportForm.set("file", new Blob([`${prefix}-owner-report`], { type: "image/jpeg" }), "业主报修现场照片.jpg");
  await request(`/me/repairs/${workOrder.workOrderId}/attachments`, {
    method: "POST",
    token: tokens.owner,
    form: reportForm,
  });
  const action = (suffix, token, body) => request(
    `/admin/repair-work-orders/${workOrder.workOrderId}${suffix}`,
    { method: "POST", token, body },
  );
  await action("/accept", tokens.propertyStaff, { remark: "物业受理楼栋公共部位报修" });
  await action("/verify-location", tokens.propertyStaff, { remark: "现场核验楼栋和公共范围" });
  await action("/assign", tokens.propertyManager, {
    assignedUserId: 800202,
    assigneeRoleKey: "PROPERTY_STAFF",
    remark: "派工完成现场勘验",
  });
  await action("/start-survey", tokens.propertyStaff, { remark: "开始现场勘验" });
  const surveyForm = new FormData();
  surveyForm.set("attachmentKind", "SURVEY_IMAGE");
  surveyForm.set("contentType", "image/jpeg");
  surveyForm.set(
    "file",
    new Blob([`${prefix}-survey-image`], { type: "image/jpeg" }),
    "楼栋现场勘验照片.jpg",
  );
  const survey = await request(`/admin/repair-work-orders/${workOrder.workOrderId}/attachments`, {
    method: "POST",
    token: tokens.propertyStaff,
    form: surveyForm,
  });
  const surveyed = await action("/submit-survey", tokens.propertyStaff, {
    surveySummary: "楼栋外墙窗框交界密封层老化，雨后存在渗水痕迹。",
    riskLevel: "MEDIUM",
    evidenceImageAttachmentIds: [survey.attachmentId],
    remark: "现场勘验已完成",
  });
  assert(surveyed.status === "SURVEY_COMPLETED", "工单未完成现场勘验");
  step("完成业主报修和物业现场勘验", { workOrderId: workOrder.workOrderId });
  return workOrder.workOrderId;
}

function projectRequest(workOrderId) {
  return {
    projectName: `${prefix}楼栋共有部位维修`,
    scopeType: "BUILDING",
    buildingId: config.buildingId,
    plan: {
      planDescription: "本方案依据已勘验的楼栋公共部位来源形成；参考报价在方案冻结前收集。",
      budgetTotal: budget,
      workPoints: [{
        businessName: "楼栋外墙窗框交界渗水维修点",
        buildingId: config.buildingId,
        locationType: "COMMON_AREA",
        commonAreaName: "楼栋公共外墙窗框交界",
        spaceName: "楼栋公共部位",
        component: "外墙防水节点和窗框密封层",
        specificPart: "窗框周边老化密封层",
        symptom: "雨后窗框周边可见渗水痕迹",
        causeStatus: "PENDING_INVESTIGATION",
        proposedMeasure: "清理既有密封层并按勘验结论修复防水节点",
        technicalRequirements: "施工前后留存同角度照片，避免破坏相邻饰面",
        preliminaryEstimatedAmount: budget,
        estimateSource: "现场勘验初步估算",
        linkedWorkOrderIds: [workOrderId],
      }],
      attachments: [],
    },
  };
}

async function createSupplier(tokens) {
  const supplierPhone = `139${String(Date.now()).slice(-8)}`;
  const creditCode = `91310001${runId.slice(-10)}`;
  const supplierDeptId = await request("/admin/supplier-organizations", {
    method: "POST",
    token: tokens.propertyManager,
    body: {
      legalName: `${prefix}维修施工单位`,
      unifiedSocialCreditCode: creditCode,
      contactName: "生产验收施工经办人",
      contactPhone: supplierPhone,
    },
  });
  await request(`/admin/supplier-organizations/${supplierDeptId}/manual-verifications`, {
    method: "POST",
    token: tokens.propertyManager,
    body: {
      unifiedSocialCreditCode: creditCode,
      sourceCode: "GSXT_WEB",
      verificationResult: "PASSED",
      evidenceReference: `${prefix}-GSXT`,
      remark: "隔离生产验收供应商企业登记信息已核对",
    },
  });
  const invitation = await request(
    `/admin/supplier-organizations/${supplierDeptId}/activation-invitations`,
    {
      method: "POST",
      token: tokens.propertyManager,
      body: { contactName: "生产验收施工经办人", contactPhone: supplierPhone, validHours: 24 },
    },
  );
  const activated = await request("/supplier-activation/activate", {
    method: "POST",
    body: {
      invitationId: invitation.invitationId,
      phone: supplierPhone,
      smsCode: config.smsCode,
      operatorName: "生产验收施工经办人",
    },
  });
  const supplierToken = await login(supplierPhone, "B");
  step("登记、核验并激活施工单位", {
    supplierDeptId,
    supplierUserId: activated.userId,
  });
  return { supplierDeptId, supplierUserId: activated.userId, supplierToken };
}

function localDateTime(value = Date.now()) {
  return new Date(value).toISOString().slice(0, 19);
}

function dateOffset(days) {
  return new Date(Date.now() + days * 86400000).toISOString().slice(0, 10);
}

function freezeRequest(version, basisAttachmentId) {
  return {
    expectedProjectVersion: version,
    supplierSelectionMethod: "COMPETITIVE_QUOTATION",
    supplierEvaluationRule: "LOWEST_COMPLIANT_QUOTE",
    minimumInvitedSupplierCount: 1,
    minimumValidQuoteCount: 1,
    constructionManagementRequirements: "施工单位按已锁定点位组织施工，物业逐阶段核验留存材料。",
    evidenceRequirements: [
      { stage: "BEFORE_CONSTRUCTION", description: "施工前现场及保护措施", required: true },
      { stage: "MATERIAL_ENTRY", description: "主要材料合格证明和进场照片", required: true },
      { stage: "DURING_CONSTRUCTION", description: "施工过程关键节点照片", required: true },
      { stage: "COMPLETION", description: "完工现场和竣工资料", required: true },
    ],
    safetyRequirements: "做好高处作业和相邻公共区域防护，施工完成后清场。",
    settlementMethod: "ACTUAL_QUANTITY",
    plannedStartDate: dateOffset(1),
    plannedCompletionDate: dateOffset(11),
    warrantyDays: 365,
    acceptanceMethod: "物业项目负责人和受影响业主按竣工资料现场验收",
    acceptanceRequirements: [
      {
        requirementCode: "PROPERTY",
        businessName: "物业现场验收",
        eligibleRoles: ["PROPERTY_TECHNICAL_COSIGNER"],
        minimumPassingCount: 1,
        evidenceRequired: true,
      },
      {
        requirementCode: "AFFECTED_OWNER",
        businessName: "受影响业主验收",
        eligibleRoles: ["AFFECTED_OWNER"],
        minimumPassingCount: 1,
        evidenceRequired: true,
      },
    ],
    acceptanceFinalizerRoles: ["PROPERTY_TECHNICAL_COSIGNER"],
    acceptanceBasisAttachmentIds: [basisAttachmentId],
    acceptanceBasisSummary: "依据工程责任和验收约定，由物业和费用承担房屋业主共同验收",
    affectedOwnerScopeDescription: "本实施方案费用承担房屋的已核验业主",
    minimumAffectedOwnerAcceptors: 1,
    affectedOwnerPassRule: "ALL",
  };
}

async function recordPaperBallot(projectId, packageId, subjectId, opid, tokens, suffix) {
  const deliveryAttachment = await uploadProjectAttachment(
    projectId,
    tokens.committeeDirector,
    `${suffix}-纸质材料送达凭证.pdf`,
  );
  const delivery = await request(`/admin/repair-projects/${projectId}/voting/paper-deliveries`, {
    method: "POST",
    token: tokens.committeeDirector,
    body: {
      opid,
      recipientName: "业主本人",
      deliveryMethod: "DOOR_TO_DOOR",
      evidenceAttachmentId: deliveryAttachment.attachmentId,
      deliveredAt: new Date().toISOString(),
    },
  });
  await request(
    `/admin/repair-projects/${projectId}/voting/paper-deliveries/${delivery.paperDeliveryId}/review`,
    {
      method: "POST",
      token: tokens.committeeMember,
      body: { decision: "CONFIRM", reviewNote: "第二名工作人员已核对纸质材料送达凭证" },
    },
  );
  const ballotAttachment = await uploadProjectAttachment(
    projectId,
    tokens.committeeDirector,
    `${suffix}-纸质表决票原件.pdf`,
  );
  const ballot = await request(`/admin/repair-projects/${projectId}/voting/paper-ballots`, {
    method: "POST",
    token: tokens.committeeDirector,
    body: {
      opid,
      ballotNumber: `${prefix}-PAPER-${suffix}`,
      attachmentId: ballotAttachment.attachmentId,
      receivedAt: new Date().toISOString(),
    },
  });
  const entry = await request(
    `/admin/repair-projects/${projectId}/voting/paper-ballots/${ballot.paperBallotId}/entries`,
    {
      method: "POST",
      token: tokens.committeeDirector,
      body: { items: [{ subjectId, determination: "VALID", choice: "SUPPORT" }] },
    },
  );
  const reviewed = await request(
    `/admin/repair-projects/${projectId}/voting/paper-ballots/${ballot.paperBallotId}/entries/${entry.entryId}/review`,
    {
      method: "POST",
      token: tokens.committeeMember,
      body: { decision: "CONFIRM", reviewNote: "第二名工作人员已核对纸质表决票录入" },
    },
  );
  assert(reviewed.outcomes?.[0]?.status === "COUNTED", "纸质表决票未计入本次表决");
  return { packageId, paperBallotId: ballot.paperBallotId };
}

async function waitUntil(instant, label) {
  const remaining = new Date(instant).getTime() - Date.now() + 1200;
  if (remaining > 0) {
    step(`等待${label}`, { seconds: Math.ceil(remaining / 1000) });
    await new Promise((resolve) => setTimeout(resolve, remaining));
  }
}

async function run() {
  const tokens = {
    propertyManager: await login("13800000021", "B"),
    propertyStaff: await login("13800000022", "B"),
    committeeDirector: await login("13800000011", "B"),
    committeeMember: await login("13800000013", "B"),
    owner: await login("13800000113", "C"),
  };
  step("登录隔离生产验收角色", { tenantId: 10001, buildingId: config.buildingId });
  const organizationId = await ensurePropertyServiceOrganization(tokens);
  const ruleId = await activateMixedVotingRule(tokens);
  const workOrderId = await createSurveyedWorkOrder(tokens);

  const created = await request("/admin/repair-projects", {
    method: "POST",
    token: tokens.propertyManager,
    body: projectRequest(workOrderId),
  });
  const projectId = created.project.projectId;
  const projectNo = created.project.projectNo;
  const planId = created.plans[0].planId;
  const workPointId = created.currentPlanWorkPoints[0].workPointId;
  assert(created.decisionScope.verificationStatus === "CONFIRMED", "工程决定范围未核验");
  step("从勘验工单建立维修工程方案", { projectId, projectNo, planId, workPointId });

  const supplier = await createSupplier(tokens);
  const invited = await request(`/admin/repair-projects/${projectId}/sourcing/invitations`, {
    method: "POST",
    token: tokens.propertyManager,
    body: {
      supplierDeptIds: [supplier.supplierDeptId],
      deadline: localDateTime(Date.now() + 3 * 86400000),
    },
  });
  const invitationId = invited.invitations[0].invitationId;
  const quoteAttachment = await uploadProjectAttachment(
    projectId,
    supplier.supplierToken,
    "施工单位报价原件.pdf",
  );
  const quote = await request(`/supplier/repair-projects/${projectId}/quotes`, {
    method: "POST",
    token: supplier.supplierToken,
    body: {
      invitationId,
      quoteAmount,
      taxRate: 9,
      quoteSummary: "报价原件已核对；税率以报价单头为准。",
      attachmentId: quoteAttachment.attachmentId,
      constructionPeriodDays: 10,
      warrantyDays: 365,
      originalAmountConfirmed: true,
      quoteLines: [
        {
          workPointId,
          itemName: "外墙窗框防水维修材料和人工",
          lineType: "CONSTRUCTION_MEASURE",
          workDescription: "清理老化密封层并修复外墙防水节点",
          quantity: 1,
          unit: "项",
          unitPriceExcludingTax: 900,
        },
        {
          itemName: "运输和清运",
          lineType: "TRANSPORT_CLEANUP",
          workDescription: "项目通用运输和清运费用",
          quantity: 1,
          unit: "项",
          unitPriceExcludingTax: 100,
        },
      ],
    },
  });
  assert(quote.confirmationStatus === "ONLINE_CONFIRMED", "施工单位报价未进入有效报价");
  step("完成施工单位邀价和有效报价", { supplierDeptId: supplier.supplierDeptId, quoteId: quote.quoteId });

  const responsibilityAttachment = await uploadProjectAttachment(
    projectId,
    tokens.propertyManager,
    "工程责任与专项维修资金使用依据.pdf",
  );
  const responsibility = await request(`/admin/repair-projects/${projectId}/responsibility-determinations`, {
    method: "POST",
    token: tokens.propertyManager,
    body: {
      expectedProjectVersion: created.project.version,
      responsibilityPath: "SHARED_COMMON_REPAIR",
      fundingSourceType: "SPECIAL_MAINTENANCE_LEDGER",
      basisAttachmentId: responsibilityAttachment.attachmentId,
      basisReference: "本工程经勘验属于楼栋共有维修，需由相关业主决定后使用专项维修资金。",
    },
  });
  const confirmed = await request(
    `/admin/repair-projects/${projectId}/responsibility-determinations/${responsibility.responsibilityDetermination.determinationId}/confirm`,
    {
      method: "POST",
      token: tokens.committeeDirector,
      body: {
        expectedProjectVersion: responsibility.project.version,
        confirmationNote: "已核验共有责任、专项维修资金路径和后续相关业主决定程序。",
      },
    },
  );
  const frozen = await request(`/admin/repair-projects/${projectId}/plans/${planId}/freeze-for-authorization`, {
    method: "POST",
    token: tokens.propertyManager,
    body: freezeRequest(confirmed.project.version, responsibilityAttachment.attachmentId),
  });
  assert(frozen.project.status === "AUTHORIZATION_IN_PROGRESS", "工程方案未进入授权程序");
  step("确认责任费用意见并提交实施方案", { projectVersion: frozen.project.version });

  const options = await request(`/admin/repair-projects/${projectId}/voting/preparation-options`, {
    token: tokens.propertyManager,
  });
  assert(options.ready === true && options.blockingItems.length === 0, "表决准备仍有阻塞事项");
  const ballotTemplate = await uploadProjectAttachment(projectId, tokens.committeeDirector, "相关业主表决票模板.pdf");
  const voteStartAt = new Date(Date.now() + 5000);
  const voteEndAt = new Date(voteStartAt.getTime() + 30000);
  const prepared = await request(`/admin/repair-projects/${projectId}/voting/prepare`, {
    method: "POST",
    token: tokens.committeeDirector,
    body: {
      expectedProjectVersion: frozen.project.version,
      collectionMode: "PAPER_AND_ONLINE",
      paperBallotTemplateAttachmentId: ballotTemplate.attachmentId,
      voteStartAt: voteStartAt.toISOString(),
      voteEndAt: voteEndAt.toISOString(),
    },
  });
  await waitUntil(voteStartAt, "表决开始时间");
  const opened = await request(`/admin/repair-projects/${projectId}/voting/open`, {
    method: "POST",
    token: tokens.committeeDirector,
    body: { expectedLinkVersion: prepared.voting.version },
  });
  assert(opened.voting.status === "VOTING", "相关业主表决未开始");

  const disclosure = await request(`/me/repair-projects/${projectId}/voting`, { token: tokens.owner });
  assert(disclosure.properties.length >= 2, "隔离楼栋至少需要两个可表决房屋以覆盖混合收票");
  const subjectId = prepared.subject.subjectId;
  const packageId = prepared.executionPackage.packageId;
  const firstOpid = disclosure.properties[0].opid;
  const secondOpid = disclosure.properties[1].opid;
  await recordPaperBallot(projectId, packageId, subjectId, firstOpid, tokens, "A");
  await recordPaperBallot(projectId, packageId, subjectId, secondOpid, tokens, "B");
  await request(`/me/repair-projects/${projectId}/voting/acknowledgements`, {
    method: "POST",
    token: tokens.owner,
    body: { opid: firstOpid, packageHash: disclosure.packageHash, confirmed: true },
  });
  await request(`/me/repair-projects/${projectId}/voting/ballots`, {
    method: "POST",
    token: tokens.owner,
    headers: { "Idempotency-Key": `${prefix}-${firstOpid}` },
    body: {
      opid: firstOpid,
      packageHash: disclosure.packageHash,
      confirmed: true,
      decisions: [{ subjectId, choice: "SUPPORT" }],
    },
  });
  await waitUntil(voteEndAt, "表决截止时间");
  const settled = await request(`/admin/repair-projects/${projectId}/voting/settle`, {
    method: "POST",
    token: tokens.committeeDirector,
    body: { expectedLinkVersion: opened.voting.version },
  });
  assert(settled.voting.status === "SETTLED", "表决未完成计票");
  assert(settled.voting.result === "PASSED", "相关业主表决未通过");
  assert(settled.project.status === "AUTHORIZED", "表决通过后工程未获得授权");
  assert(Number(settled.result.supportArea) > 0, "计票结果未汇总支持面积");
  step("完成线上与纸质混合表决", {
    packageId,
    subjectId,
    result: settled.voting.result,
    duplicateVotePolicy: "ONLINE_PREVAILS",
  });

  const locked = await request(`/admin/repair-projects/${projectId}/plans/${planId}/lock`, {
    method: "POST",
    token: tokens.propertyManager,
    body: { expectedProjectVersion: settled.project.version },
  });
  assert(locked.plans[0].status === "LOCKED", "最终实施方案未锁定");
  const selectionEvidence = await uploadProjectAttachment(projectId, tokens.committeeDirector, "施工单位评审记录.pdf");
  const selected = await request(`/admin/repair-projects/${projectId}/sourcing/selection`, {
    method: "POST",
    token: tokens.committeeDirector,
    body: {
      quoteId: quote.quoteId,
      selectionRationale: "该报价满足实施方案要求，且为本次有效报价。",
      selectionEvidenceAttachmentId: selectionEvidence.attachmentId,
    },
  });
  assert(selected.selection.quoteId === quote.quoteId, "中选施工单位与有效报价不一致");

  const beforeContract = await request(`/admin/repair-projects/${projectId}`, { token: tokens.propertyManager });
  const contractAttachment = await uploadProjectAttachment(projectId, tokens.propertyManager, "维修施工合同.pdf");
  const propertySignature = await uploadProjectAttachment(projectId, tokens.propertyManager, "物业签署页.pdf");
  const supplierSignature = await uploadProjectAttachment(projectId, supplier.supplierToken, "施工单位签署页.pdf");
  const contract = await request(`/admin/repair-projects/${projectId}/contract`, {
    method: "POST",
    token: tokens.propertyManager,
    body: {
      expectedProjectVersion: beforeContract.project.version,
      supplierDeptId: supplier.supplierDeptId,
      contractAmount: quoteAmount,
      contractAttachmentId: contractAttachment.attachmentId,
      signatures: [
        {
          partyType: "PROPERTY",
          signerName: "物业项目负责人",
          signerUserId: 800201,
          signatureMethod: "ELECTRONIC",
          signatureAttachmentId: propertySignature.attachmentId,
          signedAt: localDateTime(Date.now() - 120000),
        },
        {
          partyType: "SUPPLIER",
          signerName: "施工单位项目负责人",
          signerUserId: supplier.supplierUserId,
          signatureMethod: "ELECTRONIC",
          signatureAttachmentId: supplierSignature.attachmentId,
          signedAt: localDateTime(Date.now() - 60000),
        },
      ],
    },
  });
  assert(contract.status === "EFFECTIVE", "维修施工合同未生效");
  const beforeStart = await request(`/admin/repair-projects/${projectId}`, { token: tokens.propertyManager });
  await request(`/admin/repair-projects/${projectId}/execution/start`, {
    method: "POST",
    token: tokens.propertyManager,
    body: { expectedProjectVersion: beforeStart.project.version },
  });
  step("确定施工单位、签订合同并开工", { contractId: contract.contractId });

  for (const stage of ["BEFORE_CONSTRUCTION", "MATERIAL_ENTRY", "DURING_CONSTRUCTION", "COMPLETION"]) {
    const evidence = await uploadProjectAttachment(projectId, supplier.supplierToken, `${stage}-施工记录.pdf`);
    const record = await request(`/admin/repair-projects/${projectId}/execution-records`, {
      method: "POST",
      token: supplier.supplierToken,
      body: {
        workPointId,
        stage,
        description: `${stage}阶段施工记录`,
        occurredAt: localDateTime(Date.now() - 60000),
        attachmentIds: [evidence.attachmentId],
      },
    });
    const verified = await request(
      `/admin/repair-projects/${projectId}/execution-records/${record.recordId}/verification`,
      {
        method: "POST",
        token: tokens.propertyManager,
        body: { status: "VERIFIED", opinion: "已与现场和实施方案核对一致" },
      },
    );
    assert(verified.verificationStatus === "VERIFIED", `${stage}施工记录未核验`);
  }
  const qualification = await uploadProjectAttachment(projectId, supplier.supplierToken, "材料合格证明.pdf");
  const materialPhoto = await uploadProjectAttachment(projectId, supplier.supplierToken, "材料进场照片.pdf");
  const material = await request(`/admin/repair-projects/${projectId}/material-inspections`, {
    method: "POST",
    token: supplier.supplierToken,
    body: {
      workPointId,
      materialName: "耐候密封材料",
      brand: "已核验品牌",
      model: "JW-01",
      specification: "适用于外墙窗框防水节点",
      quantity: 1,
      unit: "批",
      manufacturer: "生产验收材料企业",
      qualificationAttachmentId: qualification.attachmentId,
      photoAttachmentIds: [materialPhoto.attachmentId],
    },
  });
  const verifiedMaterial = await request(
    `/admin/repair-projects/${projectId}/material-inspections/${material.inspectionId}/verification`,
    {
      method: "POST",
      token: tokens.propertyManager,
      body: { status: "VERIFIED", opinion: "合格证明、规格和进场实物一致" },
    },
  );
  assert(verifiedMaterial.status === "VERIFIED", "材料进场记录未核验");
  step("完成施工过程和材料核验", { executionRecordCount: 4, inspectionId: material.inspectionId });

  async function submitAndVerifySettlement(fileName, varianceReason) {
    const attachment = await uploadProjectAttachment(projectId, supplier.supplierToken, fileName);
    const settlement = await request(`/admin/repair-projects/${projectId}/settlement`, {
      method: "POST",
      token: supplier.supplierToken,
      body: {
        settlementAttachmentId: attachment.attachmentId,
        taxRate: 9,
        items: [{
          workPointId,
          actualQuantity: 1,
          unit: "项",
          actualUnitPrice: 1000,
          varianceReason,
        }],
      },
    });
    const current = await request(`/admin/repair-projects/${projectId}`, { token: tokens.propertyManager });
    const verified = await request(`/admin/repair-projects/${projectId}/settlement/verification`, {
      method: "POST",
      token: tokens.propertyManager,
      body: {
        expectedProjectVersion: current.project.version,
        approved: true,
        opinion: "结算金额、工程量和完工材料核验一致",
      },
    });
    assert(verified.status === "VERIFIED", "竣工结算未通过核验");
    return verified;
  }

  await submitAndVerifySettlement("第一轮竣工结算单.pdf", "按合同和现场核验工程量结算");
  const ownerTask = await request(`/me/repair-projects/${projectId}/acceptance`, { token: tokens.owner });
  const acceptanceRoomId = ownerTask.affectedRoomIds[0];
  const ownerAcceptanceForm = new FormData();
  ownerAcceptanceForm.set("file", pdf("第一轮业主验收照片.pdf"), "第一轮业主验收照片.pdf");
  const ownerEvidence = await request(`/me/repair-projects/${projectId}/acceptance/attachments`, {
    method: "POST",
    token: tokens.owner,
    form: ownerAcceptanceForm,
  });
  await request(`/me/repair-projects/${projectId}/acceptance`, {
    method: "POST",
    token: tokens.owner,
    body: {
      roomId: acceptanceRoomId,
      conclusion: "PASSED",
      participantName: "受影响业主",
      opinion: "现场查看后确认维修结果符合方案",
      evidenceAttachmentId: ownerEvidence.attachmentId,
    },
  });
  const rectificationEvidence = await uploadProjectAttachment(projectId, tokens.propertyManager, "第一轮工程验收整改记录.pdf");
  await request(`/admin/repair-projects/${projectId}/acceptance/property-technical`, {
    method: "POST",
    token: tokens.propertyManager,
    body: {
      conclusion: "RECTIFICATION_REQUIRED",
      participantName: "物业项目负责人",
      participantOrganization: "本小区物业服务项目部",
      opinion: "现场发现一处收边不平整，需整改后重新验收",
      evidenceAttachmentId: rectificationEvidence.attachmentId,
    },
  });
  const firstRoundProject = await request(`/admin/repair-projects/${projectId}`, { token: tokens.propertyManager });
  const firstRound = await request(`/admin/repair-projects/${projectId}/acceptance/finalization`, {
    method: "POST",
    token: tokens.propertyManager,
    body: {
      expectedProjectVersion: firstRoundProject.project.version,
      resultAttachmentId: rectificationEvidence.attachmentId,
      remark: "按第一轮验收意见完成收边整改后重新提交结算和验收",
    },
  });
  assert(firstRound.status === "RECTIFICATION_REQUIRED", "第一轮验收未进入整改");
  step("第一轮工程验收提出整改", { acceptanceId: firstRound.acceptanceId, roundNo: firstRound.roundNo });

  const secondSettlement = await submitAndVerifySettlement(
    "整改后竣工结算单.pdf",
    "已按第一轮验收意见完成整改并重新确认工程量",
  );
  assert(secondSettlement.versionNo === 2, "整改后未形成第二版竣工结算");
  const secondOwnerTask = await request(`/me/repair-projects/${projectId}/acceptance`, { token: tokens.owner });
  assert(secondOwnerTask.round.roundNo === 2, "整改后未建立第二轮验收");
  const secondOwnerForm = new FormData();
  secondOwnerForm.set("file", pdf("整改后业主验收照片.pdf"), "整改后业主验收照片.pdf");
  const secondOwnerEvidence = await request(`/me/repair-projects/${projectId}/acceptance/attachments`, {
    method: "POST",
    token: tokens.owner,
    form: secondOwnerForm,
  });
  await request(`/me/repair-projects/${projectId}/acceptance`, {
    method: "POST",
    token: tokens.owner,
    body: {
      roomId: acceptanceRoomId,
      conclusion: "PASSED",
      participantName: "受影响业主",
      opinion: "整改后现场复核通过",
      evidenceAttachmentId: secondOwnerEvidence.attachmentId,
    },
  });
  const propertyAcceptanceEvidence = await uploadProjectAttachment(projectId, tokens.propertyManager, "整改后物业验收记录.pdf");
  const propertyAcceptance = await request(`/admin/repair-projects/${projectId}/acceptance/property-technical`, {
    method: "POST",
    token: tokens.propertyManager,
    body: {
      conclusion: "PASSED",
      participantName: "物业项目负责人",
      participantOrganization: "本小区物业服务项目部",
      opinion: "已核对整改结果、施工记录、材料和竣工结算",
      evidenceAttachmentId: propertyAcceptanceEvidence.attachmentId,
    },
  });
  assert(propertyAcceptance.conclusion === "PASSED", "物业复验未通过");
  const resultEvidence = await uploadProjectAttachment(projectId, tokens.propertyManager, "工程验收结果.pdf");
  const pending = await request(`/admin/repair-projects/${projectId}`, { token: tokens.propertyManager });
  const acceptance = await request(`/admin/repair-projects/${projectId}/acceptance/finalization`, {
    method: "POST",
    token: tokens.propertyManager,
    body: {
      expectedProjectVersion: pending.project.version,
      resultAttachmentId: resultEvidence.attachmentId,
      remark: "实施方案约定的验收条件均已满足，工程验收通过",
    },
  });
  assert(acceptance.status === "PASSED", "工程验收结论未通过");
  const completed = await request(`/admin/repair-projects/${projectId}`, { token: tokens.propertyManager });
  assert(completed.project.status === "COMPLETED", "工程项目未在验收通过时完成");
  const execution = await request(`/admin/repair-projects/${projectId}/execution`, { token: tokens.propertyManager });
  assert(execution.executionRecords.filter((item) => item.verificationStatus === "VERIFIED").length === 4,
    "已核验施工记录不是四个方案要求阶段");
  assert(execution.paymentRequests.length === 0, "验收通过不应自动产生付款申请");
  assert(execution.completionDisclosure == null, "验收通过不应自动产生完工公示");
  step("整改复验后工程验收通过", {
    projectId,
    projectNo,
    status: completed.project.status,
    acceptanceStatus: acceptance.status,
  });

  const evidence = {
    runId,
    generatedAt: new Date().toISOString(),
    baseUrl: config.baseUrl,
    tenantId: 10001,
    buildingId: config.buildingId,
    organizationId,
    ruleId,
    workOrderId,
    projectId,
    projectNo,
    projectStatus: completed.project.status,
    acceptanceStatus: acceptance.status,
    acceptanceRoundNo: execution.acceptance.roundNo,
    verifiedExecutionRecordCount: execution.executionRecords.filter(
      (item) => item.verificationStatus === "VERIFIED",
    ).length,
    paymentRequestCount: execution.paymentRequests.length,
    completionDisclosureCount: execution.completionDisclosure == null ? 0 : 1,
    audit,
  };
  await writeFile(config.evidenceFile, `${JSON.stringify(evidence, null, 2)}\n`, "utf8");
  process.stdout.write(`[BDD] 验收证据已写入 ${config.evidenceFile}\n`);
}

await run();
