// 关联业务：编排维修工单从登记、勘验、方案、表决、报审、盖章到施工验收的状态机。
package com.pangu.application.repair;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.application.committee.CommitteeSealService;
import com.pangu.application.handover.TenantTermLockGuard;
import com.pangu.application.repair.command.AssignRepairCommand;
import com.pangu.application.repair.command.CompleteRepairContractCommand;
import com.pangu.application.repair.command.CompleteRepairLocalDecisionCommand;
import com.pangu.application.repair.command.CorrectRepairLocationCommand;
import com.pangu.application.repair.command.CreateRepairContractCommand;
import com.pangu.application.repair.command.CreateRepairPaymentRequestCommand;
import com.pangu.application.repair.command.CreatePrivateRepairCommand;
import com.pangu.application.repair.command.CreatePublicRepairCommand;
import com.pangu.application.repair.command.EvaluateRepairCommand;
import com.pangu.application.repair.command.RecommendRepairSupplierCommand;
import com.pangu.application.repair.command.RegisterSupplierOrganizationCommand;
import com.pangu.application.repair.command.InviteRepairSuppliersCommand;
import com.pangu.application.repair.command.RecordRepairAcceptanceCommand;
import com.pangu.application.repair.command.RepairActionCommand;
import com.pangu.application.repair.command.SealRepairGovernanceCommand;
import com.pangu.application.repair.command.StartRepairAssemblyDecisionCommand;
import com.pangu.application.repair.command.StartRepairLocalDecisionCommand;
import com.pangu.application.repair.command.SetRepairAcceptanceScopeCommand;
import com.pangu.application.repair.command.SubmitRepairApprovalPackageCommand;
import com.pangu.application.repair.command.SubmitRepairSupplierQuoteCommand;
import com.pangu.application.repair.command.SubmitRepairOnlineVoteCommand;
import com.pangu.application.repair.command.SubmitRepairInspectionCommand;
import com.pangu.application.repair.command.SubmitRepairPlanCommand;
import com.pangu.application.repair.command.SubmitRepairSurveyCommand;
import com.pangu.application.repair.command.ReviewRepairPriceCommand;
import com.pangu.domain.common.Page;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.asset.OwnerPropertyDetail;
import com.pangu.domain.model.assembly.OwnersAssemblyPackage;
import com.pangu.domain.model.committee.CommitteeElectronicSeal;
import com.pangu.domain.model.committee.CommitteeSealUsageRecord;
import com.pangu.domain.model.repair.RepairBuildingDecisionSnapshot;
import com.pangu.domain.model.repair.RepairAttachment;
import com.pangu.domain.model.repair.RepairAttachmentKind;
import com.pangu.domain.model.repair.RepairAttachmentStatus;
import com.pangu.domain.model.repair.RepairAcceptanceRecord;
import com.pangu.domain.model.repair.RepairAcceptanceSummary;
import com.pangu.domain.model.repair.RepairApprovalAttachment;
import com.pangu.domain.model.repair.RepairContractSignature;
import com.pangu.domain.model.repair.RepairDecisionRoom;
import com.pangu.domain.model.repair.RepairFrameworkRelation;
import com.pangu.domain.model.repair.RepairLocationOption;
import com.pangu.domain.model.repair.RepairLocalDecision;
import com.pangu.domain.model.repair.RepairLocalDecisionChannel;
import com.pangu.domain.model.repair.RepairLocalDecisionScopeType;
import com.pangu.domain.model.repair.RepairOwnerLocalDecision;
import com.pangu.domain.model.repair.RepairQuoteConfirmationStatus;
import com.pangu.domain.model.repair.RepairQuoteInvitation;
import com.pangu.domain.model.repair.RepairQuoteSubmissionSource;
import com.pangu.domain.model.repair.RepairCategory;
import com.pangu.domain.model.repair.RepairPublicAreaScope;
import com.pangu.domain.model.repair.RepairSource;
import com.pangu.domain.model.repair.RepairSpaceScope;
import com.pangu.domain.model.repair.RepairSupplierQuote;
import com.pangu.domain.model.repair.RepairSupplierQuoteStatus;
import com.pangu.domain.model.repair.RepairSupplierOrganization;
import com.pangu.domain.model.repair.RepairSupplierRecommendation;
import com.pangu.domain.model.repair.RepairSupplierSelectionMethod;
import com.pangu.domain.model.repair.RepairSolitaireEntry;
import com.pangu.domain.model.repair.RepairVoteChoice;
import com.pangu.domain.model.repair.RepairWorkOrder;
import com.pangu.domain.model.repair.RepairWorkOrderEvent;
import com.pangu.domain.model.repair.RepairWorkOrderStatus;
import com.pangu.domain.model.user.WorkIdentityBuildingScope;
import com.pangu.domain.repository.OwnersAssemblyRepository;
import com.pangu.domain.repository.CommitteeSealRepository;
import com.pangu.domain.repository.CommunitySettingsRepository;
import com.pangu.domain.repository.ElectronicSealProvider;
import com.pangu.domain.repository.RepairAttachmentRepository;
import com.pangu.domain.repository.RepairEvidenceObjectStorage;
import com.pangu.domain.repository.RepairWorkOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;

import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.BUILDING_NOT_IN_SCOPE;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.FORBIDDEN;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.HANDOVER_LOCKED;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.INVALID_STATUS;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.LOCATION_NOT_VERIFIED;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.NOT_FOUND;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.PARAM_INVALID;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.PROPERTY_NOT_OWNED;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.STORAGE_UNAVAILABLE;

@Service
@RequiredArgsConstructor
public class RepairWorkOrderService {

    private static final int DEDUP_MINUTES = 30;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_EVIDENCE_IMAGE_COUNT = 3;
    private static final int MAX_EVIDENCE_IMAGE_BASE64_LENGTH = 2_800_000;
    private static final String FUND_PROPERTY_INTERNAL = "PROPERTY_INTERNAL";
    private static final String FUND_PUBLIC_REVENUE = "PUBLIC_REVENUE";
    private static final String FUND_BUILDING_MAINTENANCE = "BUILDING_MAINTENANCE_FUND";
    private static final String FUND_COMMUNITY_MAINTENANCE = "COMMUNITY_MAINTENANCE_FUND";
    private static final String SEAL_METHOD_UPLOADED_PHYSICAL = "UPLOADED_PHYSICAL";
    private static final String SEAL_METHOD_UPLOADED_EXTERNAL_ELECTRONIC =
            "UPLOADED_EXTERNAL_ELECTRONIC";
    private static final String SEAL_METHOD_PLATFORM_ELECTRONIC = "PLATFORM_ELECTRONIC";
    private static final Set<String> GOVERNANCE_SEAL_METHODS = Set.of(
            SEAL_METHOD_UPLOADED_PHYSICAL,
            SEAL_METHOD_UPLOADED_EXTERNAL_ELECTRONIC,
            SEAL_METHOD_PLATFORM_ELECTRONIC);
    private static final Set<String> REPAIR_FUND_SOURCES = Set.of(
            FUND_PROPERTY_INTERNAL,
            FUND_PUBLIC_REVENUE,
            FUND_BUILDING_MAINTENANCE,
            FUND_COMMUNITY_MAINTENANCE);
    private static final Set<String> ASSEMBLY_FUND_SOURCES = Set.of(
            FUND_PUBLIC_REVENUE,
            FUND_COMMUNITY_MAINTENANCE);

    private static final Set<String> INTAKE_ROLES = Set.of("PROPERTY_STAFF", "PROPERTY_MANAGER");
    private static final Set<String> FIELD_ROLES = Set.of(
            "PROPERTY_STAFF", "PROPERTY_MANAGER", "GRID_MEMBER", "VOLUNTEER",
            "OWNER_REPRESENTATIVE");
    private static final Set<String> PROPERTY_QUOTE_ROLES = Set.of("PROPERTY_STAFF", "PROPERTY_MANAGER");
    private static final Set<String> SUPPLIER_ROLES = Set.of("SERVICE_PROVIDER_STAFF", "SERVICE_PROVIDER_MANAGER");
    private static final Set<String> SUPPLIER_RECOMMEND_ROLES = Set.of("PROPERTY_MANAGER");
    private static final Set<String> LOCAL_DECISION_ROLES = Set.of(
            "OWNER_REPRESENTATIVE", "PROPERTY_STAFF", "PROPERTY_MANAGER");
    private static final Set<String> MANAGER_ROLES = Set.of(
            "PROPERTY_MANAGER", "COMMITTEE_DIRECTOR", "COMMUNITY_ADMIN", "GOV_SUPER_ADMIN");
    private static final Set<String> GOVERNANCE_ROLES = Set.of(
            "COMMITTEE_DIRECTOR", "COMMITTEE_MEMBER", "COMMUNITY_ADMIN", "PARTY_SECRETARY", "GOV_SUPER_ADMIN");
    private static final Set<String> SEAL_ROLES = Set.of("COMMITTEE_DIRECTOR", "COMMITTEE_MEMBER");
    private static final Set<String> ACCEPTANCE_ROLES = Set.of(
            "OWNER_REPRESENTATIVE", "PROPERTY_STAFF", "PROPERTY_MANAGER",
            "COMMITTEE_DIRECTOR", "COMMITTEE_MEMBER");

    private final RepairWorkOrderRepository repository;
    private final RepairAttachmentRepository attachmentRepository;
    private final OwnersAssemblyRepository ownersAssemblyRepository;
    private final CommunitySettingsRepository communitySettingsRepository;
    private final UserContextHolder userContextHolder;
    private final TenantTermLockGuard tenantTermLockGuard;
    private final ObjectMapper objectMapper;
    private final SupplierActivationService supplierActivationService;
    private final CommitteeSealService committeeSealService;
    private final CommitteeSealRepository committeeSealRepository;
    private final RepairEvidenceObjectStorage objectStorage;

    @Transactional
    public RepairWorkOrder createPrivate(CreatePrivateRepairCommand command) {
        UserContext ctx = requireOwnerContext();
        String title = requireText(command.title(), "title");
        OwnerPropertyDetail property = repository.findOwnerProperty(ctx.uid(), ctx.tenantId(), command.opid())
                .orElseThrow(() -> new RepairWorkOrderApplicationException(PROPERTY_NOT_OWNED,
                        "房产不属于当前业主或不在当前小区 opid=" + command.opid()));
        if (property.accountStatus() == null || property.accountStatus() != 1) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN,
                    "该房产尚未完成线下核销或账户状态异常，禁止报修 opid=" + command.opid());
        }
        RepairWorkOrder duplicate = duplicate(ctx, RepairSpaceScope.PRIVATE, property.roomId(), property.buildingId(), title);
        if (duplicate != null) {
            return duplicate;
        }
        RepairWorkOrder inserted = repository.insert(new RepairWorkOrder(
                null, null, ctx.tenantId(), title, trim(command.description()), RepairSource.C_OWNER_APP,
                RepairSpaceScope.PRIVATE, null, RepairWorkOrderStatus.SUBMITTED, ctx.accountId(),
                ctx.uid(), null, property.roomId(), property.buildingId(), null,
                false, false, null, null, null, normalizeCategory(command.category()), null,
                trim(command.evidenceText()), null, null, null, true, null, null, 0L, null, null));
        event(inserted, "OWNER_SUBMIT_PRIVATE", null, inserted.status(), "业主提交私有空间报修");
        return inserted;
    }

    @Transactional
    public RepairWorkOrder createPublic(CreatePublicRepairCommand command) {
        UserContext ctx = requireOwnerContext();
        String title = requireText(command.title(), "title");
        Long buildingId = command.buildingId();
        RepairPublicAreaScope publicAreaScope = resolvePublicAreaScope(
                command.publicAreaScope(), buildingId, command.locationText());
        if (buildingId != null && !repository.buildingExists(ctx.tenantId(), buildingId)) {
            throw new RepairWorkOrderApplicationException(BUILDING_NOT_IN_SCOPE,
                    "楼栋不在当前小区范围内 buildingId=" + buildingId);
        }
        boolean needManualLocation = publicAreaScope == null;
        RepairWorkOrder duplicate = duplicate(ctx, RepairSpaceScope.PUBLIC, null, buildingId, title);
        if (duplicate != null) {
            return duplicate;
        }
        RepairWorkOrderStatus status = needManualLocation
                ? RepairWorkOrderStatus.NEED_MANUAL_LOCATION
                : RepairWorkOrderStatus.SUBMITTED;
        RepairWorkOrder inserted = repository.insert(new RepairWorkOrder(
                null, null, ctx.tenantId(), title, trim(command.description()), RepairSource.C_OWNER_APP,
                RepairSpaceScope.PUBLIC, publicAreaScope, status, ctx.accountId(), ctx.uid(), null,
                null, buildingId, trim(command.locationText()), needManualLocation, false,
                null, null, null, normalizeCategory(command.category()), null, trim(command.evidenceText()),
                null, null, null, true, null, null, 0L, null, null));
        event(inserted, "OWNER_SUBMIT_PUBLIC", null, inserted.status(),
                needManualLocation ? "公共报修信息不足，进入现场补充" : "业主提交公共区域报修");
        return inserted;
    }

    @Transactional
    public RepairWorkOrder createAdminPublic(CreatePublicRepairCommand command) {
        UserContext ctx = requireRole(INTAKE_ROLES, "当前角色无权登记报修");
        String title = requireText(command.title(), "title");
        Long tenantId = requireTenant(ctx);
        Long buildingId = command.buildingId();
        RepairPublicAreaScope publicAreaScope = resolvePublicAreaScope(
                command.publicAreaScope(), buildingId, command.locationText());
        if (buildingId != null && !repository.buildingExists(tenantId, buildingId)) {
            throw new RepairWorkOrderApplicationException(BUILDING_NOT_IN_SCOPE,
                    "楼栋不在当前小区范围内 buildingId=" + buildingId);
        }
        boolean needManualLocation = publicAreaScope == null;
        RepairWorkOrderStatus status = needManualLocation
                ? RepairWorkOrderStatus.NEED_MANUAL_LOCATION
                : RepairWorkOrderStatus.SUBMITTED;
        RepairWorkOrder inserted = repository.insert(new RepairWorkOrder(
                null, null, tenantId, title, trim(command.description()), RepairSource.ADMIN_PC,
                RepairSpaceScope.PUBLIC, publicAreaScope, status, ctx.accountId(), null, ctx.userId(),
                null, buildingId, trim(command.locationText()), needManualLocation, false,
                null, null, null, normalizeCategory(command.category()), null, trim(command.evidenceText()),
                null, null, null, true, null, null, 0L, null, null));
        event(inserted, "ADMIN_REGISTER_PUBLIC", null, inserted.status(),
                needManualLocation ? "物业登记公共报修，待现场定位" : "物业登记公共报修");
        return inserted;
    }

    @Transactional(readOnly = true)
    public List<RepairWorkOrder> listMine() {
        UserContext ctx = requireOwnerContext();
        return repository.listForOwner(ctx.accountId(), ctx.uid(), ctx.tenantId());
    }

    @Transactional(readOnly = true)
    public RepairWorkOrder findMine(Long workOrderId) {
        UserContext ctx = requireOwnerContext();
        return repository.findByIdForOwner(workOrderId, ctx.accountId(), ctx.uid(), ctx.tenantId())
                .orElseThrow(() -> new RepairWorkOrderApplicationException(NOT_FOUND,
                        "工单不存在或不在当前业主可见范围内 workOrderId=" + workOrderId));
    }

    @Transactional(readOnly = true)
    public List<Long> listMyAcceptanceRooms(Long workOrderId) {
        UserContext ctx = requireOwnerContext();
        RepairWorkOrder order = repository.findById(workOrderId)
                .orElseThrow(() -> new RepairWorkOrderApplicationException(NOT_FOUND, "工单不存在"));
        if (!order.tenantId().equals(ctx.tenantId())) {
            throw new RepairWorkOrderApplicationException(NOT_FOUND, "工单不在当前小区");
        }
        return repository.listOwnerAcceptanceRooms(order.workOrderId(), order.tenantId(), ctx.uid());
    }

    @Transactional(readOnly = true)
    public Page<RepairWorkOrder> pageAdmin(RepairWorkOrderStatus status,
                                           RepairSpaceScope scope,
                                           String keyword,
                                           int page,
                                           int size) {
        UserContext ctx = requireSysContext();
        Long tenantId = ctx.tenantId();
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        List<WorkIdentityBuildingScope> buildingScopes = ctx.authorizedBuildingScopes().stream().toList();
        long total = repository.countForAdmin(
                tenantId, ctx.roleKey(), ctx.userId(), buildingScopes, status, scope, trim(keyword));
        List<RepairWorkOrder> items = repository.listForAdmin(
                tenantId, ctx.roleKey(), ctx.userId(), buildingScopes, status, scope, trim(keyword), safePage, safeSize);
        return new Page<>(items, total, safePage, safeSize);
    }

    @Transactional(readOnly = true)
    public List<RepairWorkOrder> listSupplierWorkOrders() {
        UserContext ctx = requireRole(SUPPLIER_ROLES, "当前角色不是供应商工作账号");
        if (ctx.deptId() == null) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, "供应商账号未绑定企业组织");
        }
        return repository.listForSupplier(ctx.deptId());
    }

    @Transactional(readOnly = true)
    public RepairPlanningPolicy getPlanningPolicy() {
        UserContext ctx = requireRole(FIELD_ROLES, "当前角色无权读取维修方案配置");
        return planningPolicy(requireTenant(ctx));
    }

    @Transactional(readOnly = true)
    public List<RepairSupplierOrganization> listSupplierOrganizations() {
        UserContext ctx = requireSysContext();
        return repository.listSupplierOrganizations(requireTenant(ctx));
    }

    @Transactional(readOnly = true)
    public List<RepairSupplierQuote> listSupplierQuotes(Long workOrderId) {
        RepairWorkOrder order = findAdmin(workOrderId);
        return repository.listSupplierQuotes(order.workOrderId(), order.tenantId());
    }

    /**
     * 读取合同阶段唯一可用的供应商候选，并在后端判定企业主体是否具备签约资格。
     */
    @Transactional(readOnly = true)
    public RepairContractSupplierCandidate getContractSupplierCandidate(Long workOrderId) {
        RepairWorkOrder order = findAdmin(workOrderId);
        RepairSupplierRecommendation recommendation = repository.findLatestRecommendation(
                        order.workOrderId(), order.tenantId())
                .orElseThrow(() -> new RepairWorkOrderApplicationException(
                        NOT_FOUND, "工单尚未形成供应商推荐结果"));
        RepairSupplierQuote quote = repository.findQuote(
                        recommendation.quoteId(), order.workOrderId(), order.tenantId())
                .orElseThrow(() -> new RepairWorkOrderApplicationException(
                        INVALID_STATUS, "推荐供应商的有效报价不存在，请重新确认报价"));

        if (quote.supplierDeptId() == null) {
            return new RepairContractSupplierCandidate(
                    quote.quoteId(), null, quote.supplierName(), quote.quoteAmount(),
                    null, true, null);
        }

        String verificationStatus = repository.findSupplierOrganization(
                        order.tenantId(), quote.supplierDeptId())
                .map(RepairSupplierOrganization::verificationStatus)
                .orElse("NOT_REGISTERED");
        boolean contractEligible = "VERIFIED".equals(verificationStatus);
        return new RepairContractSupplierCandidate(
                quote.quoteId(), quote.supplierDeptId(), quote.supplierName(), quote.quoteAmount(),
                verificationStatus, contractEligible,
                contractEligible ? null : contractSupplierEligibilityMessage(verificationStatus));
    }

    @Transactional(readOnly = true)
    public List<RepairSupplierQuote> listSupplierQuoteHistory(Long workOrderId, Long supplierDeptId) {
        RepairWorkOrder order = findAdmin(workOrderId);
        if (supplierDeptId == null) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "supplierDeptId 必填");
        }
        return repository.listSupplierQuoteHistory(order.workOrderId(), order.tenantId(), supplierDeptId);
    }

    private String contractSupplierEligibilityMessage(String verificationStatus) {
        return switch (verificationStatus) {
            case "PENDING_VERIFICATION" -> "推荐供应商的企业主体尚未完成独立核验，暂不能发起合同签署";
            case "REJECTED" -> "推荐供应商的企业主体核验未通过，暂不能发起合同签署";
            case "DISABLED" -> "推荐供应商已停用，暂不能发起合同签署";
            case "NOT_REGISTERED" -> "推荐供应商组织不存在，暂不能发起合同签署";
            default -> "推荐供应商当前不具备签约资格";
        };
    }

    @Transactional(readOnly = true)
    public List<RepairQuoteInvitation> listQuoteInvitations(Long workOrderId) {
        RepairWorkOrder order = findAdmin(workOrderId);
        return repository.listQuoteInvitations(order.workOrderId(), order.tenantId());
    }

    @Transactional(readOnly = true)
    public List<RepairFrameworkRelation> listActiveFrameworkRelations(String serviceCategory) {
        UserContext ctx = requireSysContext();
        String normalizedCategory = trim(serviceCategory) == null ? null : normalizeCategory(serviceCategory);
        return repository.listActiveFrameworkRelations(requireTenant(ctx), normalizedCategory);
    }

    @Transactional(readOnly = true)
    public RepairWorkOrder findAdmin(Long workOrderId) {
        UserContext ctx = requireSysContext();
        RepairWorkOrder order = repository.findById(workOrderId)
                .orElseThrow(() -> new RepairWorkOrderApplicationException(NOT_FOUND,
                        "工单不存在 workOrderId=" + workOrderId));
        assertVisible(ctx, order);
        return order;
    }

    @Transactional(readOnly = true)
    public List<RepairWorkOrderEvent> listEvents(Long workOrderId) {
        RepairWorkOrder order = findAdmin(workOrderId);
        return repository.listEvents(order.workOrderId(), order.tenantId());
    }

    @Transactional(readOnly = true)
    public List<RepairLocationOption> listLocationOptions() {
        UserContext ctx = requireRole(FIELD_ROLES, "当前角色无权查看维修位置选项");
        boolean ownerGroup = "OWNER_GROUP".equals(ctx.dataScopeType() == null ? null : ctx.dataScopeType().getValue());
        List<WorkIdentityBuildingScope> buildingScopes = ctx.authorizedBuildingScopes().stream().toList();
        if (ownerGroup && buildingScopes.isEmpty()) {
            return List.of();
        }
        return repository.listLocationOptions(ctx.tenantId(), buildingScopes, ownerGroup);
    }

    @Transactional
    public RepairWorkOrder accept(Long workOrderId, RepairActionCommand command) {
        UserContext ctx = requireRole(INTAKE_ROLES, "当前角色无权受理报修");
        RepairWorkOrder order = loadVisible(ctx, workOrderId);
        requireStatus(order, RepairWorkOrderStatus.SUBMITTED, RepairWorkOrderStatus.NEED_MANUAL_LOCATION);
        RepairWorkOrderStatus next = order.needManualLocation()
                ? RepairWorkOrderStatus.NEED_MANUAL_LOCATION
                : RepairWorkOrderStatus.PENDING_VERIFY;
        return transition(order, order.withStatus(next, order.needManualLocation(), false, true),
                "ACCEPT", command.remark());
    }

    @Transactional
    public RepairWorkOrder correctLocation(Long workOrderId, CorrectRepairLocationCommand command) {
        UserContext ctx = requireRole(FIELD_ROLES, "当前角色无权纠偏报修位置");
        RepairWorkOrder order = loadVisible(ctx, workOrderId);
        RepairPublicAreaScope publicAreaScope = order.spaceScope() == RepairSpaceScope.PUBLIC
                ? resolvePublicAreaScope(command.publicAreaScope(), command.buildingId(), command.locationText())
                : null;
        if (order.spaceScope() == RepairSpaceScope.PRIVATE
                && command.buildingId() == null && command.roomId() == null) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "buildingId 或 roomId 至少填写一个");
        }
        if (command.buildingId() != null && !repository.buildingExists(order.tenantId(), command.buildingId())) {
            throw new RepairWorkOrderApplicationException(BUILDING_NOT_IN_SCOPE,
                    "楼栋不在当前小区范围内 buildingId=" + command.buildingId());
        }
        if (command.roomId() != null && !repository.roomExists(order.tenantId(), command.buildingId(), command.roomId())) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID,
                    "房号不在当前小区或楼栋范围内 roomId=" + command.roomId());
        }
        RepairWorkOrder next = order.withLocation(command.buildingId(), command.roomId(),
                trim(command.locationText()), publicAreaScope,
                RepairWorkOrderStatus.PENDING_VERIFY, false, false, true);
        List<RepairAttachment> attachments = readyAttachments(order, command.evidenceImageAttachmentIds(),
                RepairAttachmentKind.LOCATION_IMAGE, 0, MAX_EVIDENCE_IMAGE_COUNT);
        RepairWorkOrder persisted = transition(order, next, "CORRECT_LOCATION", command.reason(),
                attachmentEvidencePayload(command.fieldSupplement(), attachments));
        bindAttachments(order, attachments, "CORRECT_LOCATION");
        return persisted;
    }

    @Transactional
    public RepairWorkOrder verifyLocation(Long workOrderId, RepairActionCommand command) {
        UserContext ctx = requireRole(FIELD_ROLES, "当前角色无权核验现场");
        RepairWorkOrder order = loadVisible(ctx, workOrderId);
        requireStatus(order, RepairWorkOrderStatus.SUBMITTED, RepairWorkOrderStatus.PENDING_VERIFY,
                RepairWorkOrderStatus.NEED_MANUAL_LOCATION);
        if (order.needManualLocation()) {
            throw new RepairWorkOrderApplicationException(LOCATION_NOT_VERIFIED,
                    "工单位置仍需现场补充，禁止核验通过");
        }
        boolean communityLocationComplete = order.spaceScope() == RepairSpaceScope.PUBLIC
                && order.publicAreaScope() == RepairPublicAreaScope.COMMUNITY
                && trim(order.locationText()) != null;
        if (!communityLocationComplete && order.buildingId() == null && order.roomId() == null) {
            throw new RepairWorkOrderApplicationException(LOCATION_NOT_VERIFIED,
                    "工单未绑定楼栋或房屋，禁止核验通过");
        }
        return transition(order, order.withStatus(RepairWorkOrderStatus.VERIFIED, false, true, false),
                "VERIFY_LOCATION", command.remark(),
                fieldEvidencePayload(command.fieldSupplement(), command.evidenceImagesBase64()));
    }

    @Transactional
    public RepairWorkOrder assign(Long workOrderId, AssignRepairCommand command) {
        UserContext ctx = requireRole(MANAGER_ROLES, "当前角色无权派单");
        RepairWorkOrder order = loadVisible(ctx, workOrderId);
        requireStatus(order, RepairWorkOrderStatus.VERIFIED, RepairWorkOrderStatus.ASSIGNED);
        Long assignee = command.assignedUserId() == null ? ctx.userId() : command.assignedUserId();
        RepairWorkOrder next = order.withAssignment(assignee, trim(command.assigneeRoleKey()), ctx.deptId(),
                RepairWorkOrderStatus.ASSIGNED);
        return transition(order, next, "ASSIGN", command.remark());
    }

    @Transactional
    public RepairWorkOrder startSurvey(Long workOrderId, RepairActionCommand command) {
        UserContext ctx = requireRole(FIELD_ROLES, "当前角色无权开始初勘");
        RepairWorkOrder order = loadVisible(ctx, workOrderId);
        requireStatus(order, RepairWorkOrderStatus.ASSIGNED);
        return transition(order, order.withStatus(RepairWorkOrderStatus.SURVEYING, false, true, false),
                "START_SURVEY", command.remark());
    }

    @Transactional
    public RepairWorkOrder submitSurvey(Long workOrderId, SubmitRepairSurveyCommand command) {
        UserContext ctx = requireRole(FIELD_ROLES, "当前角色无权提交现场初勘");
        RepairWorkOrder order = loadVisible(ctx, workOrderId);
        requireStatus(order, RepairWorkOrderStatus.SURVEYING);
        return completeInspection(order, command.surveySummary(), command.riskLevel(),
                command.evidenceImageAttachmentIds(), command.evidenceVideoAttachmentId(), command.remark());
    }

    @Transactional
    public RepairWorkOrder submitInspection(Long workOrderId, SubmitRepairInspectionCommand command) {
        UserContext ctx = requireRole(FIELD_ROLES, "当前角色无权提交现场勘验");
        RepairWorkOrder order = loadVisible(ctx, workOrderId);
        requireStatus(order,
                RepairWorkOrderStatus.SUBMITTED,
                RepairWorkOrderStatus.NEED_MANUAL_LOCATION,
                RepairWorkOrderStatus.PENDING_VERIFY,
                RepairWorkOrderStatus.VERIFIED,
                RepairWorkOrderStatus.ASSIGNED,
                RepairWorkOrderStatus.SURVEYING);

        if (order.status() == RepairWorkOrderStatus.SUBMITTED) {
            RepairWorkOrderStatus nextStatus = order.needManualLocation()
                    ? RepairWorkOrderStatus.NEED_MANUAL_LOCATION
                    : RepairWorkOrderStatus.PENDING_VERIFY;
            order = transition(order, order.withStatus(nextStatus, order.needManualLocation(), false, true),
                    "ACCEPT", "勘验提交时系统完成受理");
        }
        if (order.status() == RepairWorkOrderStatus.NEED_MANUAL_LOCATION) {
            order = correctInspectionLocation(order, command);
        }
        if (order.status() == RepairWorkOrderStatus.PENDING_VERIFY) {
            assertLocationComplete(order);
            order = transition(order, order.withStatus(RepairWorkOrderStatus.VERIFIED, false, true, false),
                    "VERIFY_LOCATION", "勘验提交时核验现场位置",
                    fieldEvidencePayload(command.fieldSupplement(), List.of()));
        }
        if (order.status() == RepairWorkOrderStatus.VERIFIED) {
            RepairWorkOrder assigned = order.withAssignment(ctx.userId(), ctx.roleKey(), ctx.deptId(),
                    RepairWorkOrderStatus.ASSIGNED);
            order = transition(order, assigned, "ASSIGN", "勘验提交时指派当前处理人");
        }
        if (order.status() == RepairWorkOrderStatus.ASSIGNED) {
            order = transition(order, order.withStatus(RepairWorkOrderStatus.SURVEYING, false, true, false),
                    "START_SURVEY", "勘验提交时开始现场勘验");
        }
        return completeInspection(order, command.surveySummary(), command.riskLevel(),
                command.evidenceImageAttachmentIds(), command.evidenceVideoAttachmentId(), command.remark());
    }

    private RepairWorkOrder correctInspectionLocation(RepairWorkOrder order,
                                                       SubmitRepairInspectionCommand command) {
        RepairPublicAreaScope publicAreaScope = order.spaceScope() == RepairSpaceScope.PUBLIC
                ? resolvePublicAreaScope(command.publicAreaScope(), command.buildingId(), command.locationText())
                : null;
        validateInspectionLocation(order, command.buildingId(), command.roomId(),
                command.locationText(), publicAreaScope);
        RepairWorkOrder next = order.withLocation(command.buildingId(), command.roomId(),
                trim(command.locationText()), publicAreaScope,
                RepairWorkOrderStatus.PENDING_VERIFY, false, false, true);
        return transition(order, next, "CORRECT_LOCATION", "勘验提交时补充现场位置",
                fieldEvidencePayload(command.fieldSupplement(), List.of()));
    }

    private RepairWorkOrder completeInspection(RepairWorkOrder order,
                                               String summary,
                                               String risk,
                                               List<Long> imageAttachmentIds,
                                               Long videoAttachmentId,
                                               String remark) {
        requireStatus(order, RepairWorkOrderStatus.SURVEYING);
        String surveySummary = requireText(summary, "surveySummary");
        String riskLevel = requireText(risk, "riskLevel").toUpperCase();
        if (!Set.of("LOW", "MEDIUM", "HIGH").contains(riskLevel)) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID,
                    "riskLevel 仅支持 LOW、MEDIUM、HIGH");
        }
        List<RepairAttachment> evidenceImages = readyAttachments(order, imageAttachmentIds,
                RepairAttachmentKind.SURVEY_IMAGE, 1, MAX_EVIDENCE_IMAGE_COUNT);
        List<RepairAttachment> evidenceVideo = videoAttachmentId == null
                ? List.of()
                : readyAttachments(order, List.of(videoAttachmentId),
                RepairAttachmentKind.SURVEY_VIDEO, 0, 1);
        List<RepairAttachment> attachments = new ArrayList<>(evidenceImages);
        attachments.addAll(evidenceVideo);
        RepairWorkOrder next = order.withSurvey(surveySummary, riskLevel,
                RepairWorkOrderStatus.SURVEY_COMPLETED);
        RepairWorkOrder persisted = transition(order, next, "SUBMIT_SURVEY", remark,
                surveyEvidencePayload(surveySummary, riskLevel, attachments));
        bindAttachments(order, attachments, "SUBMIT_SURVEY");
        return persisted;
    }

    @Transactional
    public RepairWorkOrder submitPlan(Long workOrderId, SubmitRepairPlanCommand command) {
        UserContext ctx = requireRole(FIELD_ROLES, "当前角色无权提交方案预算");
        RepairWorkOrder order = loadVisible(ctx, workOrderId);
        if (!order.locationLocked()) {
            throw new RepairWorkOrderApplicationException(LOCATION_NOT_VERIFIED,
                    "未完成现场核验，禁止提交方案预算");
        }
        requireStatus(order, RepairWorkOrderStatus.SURVEY_COMPLETED,
                RepairWorkOrderStatus.PLAN_SUBMITTED,
                RepairWorkOrderStatus.PLAN_REVISION_REQUIRED);
        RepairPlanningPolicy policy = planningPolicy(order.tenantId());
        if (policy.internalEstimateRequired() && command.planBudget() == null) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "当前社区要求填写物业内部估算金额");
        }
        if (command.planBudget() != null && command.planBudget().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "物业内部估算金额必须大于 0");
        }
        if (command.publicCeilingPrice() != null
                && command.publicCeilingPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "公开最高限价必须大于 0");
        }
        String fundSource = requireAllowed(command.fundSource(), "fundSource", REPAIR_FUND_SOURCES);
        validateFundSourceForPublicArea(order, fundSource);
        if (FUND_PROPERTY_INTERNAL.equals(fundSource) && command.publicCeilingPrice() != null) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID,
                    "物业包干维修不进入供应商邀价，不能设置公开最高限价");
        }
        boolean directPropertyExecution = FUND_PROPERTY_INTERNAL.equals(fundSource);
        RepairWorkOrderStatus nextStatus = directPropertyExecution
                ? RepairWorkOrderStatus.APPROVED
                : RepairWorkOrderStatus.PLAN_SUBMITTED;
        RepairWorkOrder next = order.withPlan(command.planBudget(), command.publicCeilingPrice(),
                fundSource, nextStatus);
        return transition(order, next, "SUBMIT_PLAN", command.remark(), jsonPayload(Map.of(
                "route", directPropertyExecution ? "DIRECT_PROPERTY_EXECUTION" : "SUPPLIER_SELECTION",
                "fundSource", fundSource)));
    }

    @Transactional
    public RepairWorkOrder startQuoteCollection(Long workOrderId, RepairActionCommand command) {
        UserContext ctx = requireRole(FIELD_ROLES, "当前角色无权发起供应商报价");
        RepairWorkOrder order = loadVisible(ctx, workOrderId);
        requireStatus(order, RepairWorkOrderStatus.PLAN_SUBMITTED, RepairWorkOrderStatus.QUOTE_COLLECTING);
        return transition(order, order.withStatus(RepairWorkOrderStatus.QUOTE_COLLECTING, false, true, false),
                "START_QUOTE_COLLECTION", command.remark());
    }

    @Transactional
    public Long registerSupplierOrganization(RegisterSupplierOrganizationCommand command) {
        UserContext ctx = requireRole(SUPPLIER_RECOMMEND_ROLES, "当前角色无权登记供应商组织");
        String legalName = requireText(command.legalName(), "legalName");
        if (legalName.length() > 50) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "供应商名称不能超过 50 个字符");
        }
        String uscc = trim(command.unifiedSocialCreditCode());
        if (uscc != null) {
            uscc = uscc.toUpperCase();
        }
        if (uscc != null && !uscc.matches("[0-9A-Z]{18}")) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "统一社会信用代码必须为 18 位");
        }
        String contactName = trim(command.contactName());
        if (contactName != null && contactName.length() > 80) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "企业联系人不能超过 80 个字符");
        }
        String contactPhone = trim(command.contactPhone());
        if (contactPhone != null && !contactPhone.matches("1[3-9][0-9]{9}")) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "联系人手机号格式不正确");
        }
        return repository.registerSupplierOrganization(requireTenant(ctx), legalName, uscc,
                contactName, contactPhone, ctx.userId());
    }

    @Transactional
    public RepairWorkOrder inviteSuppliers(Long workOrderId, InviteRepairSuppliersCommand command) {
        UserContext ctx = requireRole(SUPPLIER_RECOMMEND_ROLES, "当前角色无权邀请维修供应商");
        RepairWorkOrder order = loadVisible(ctx, workOrderId);
        requireStatus(order, RepairWorkOrderStatus.PLAN_SUBMITTED, RepairWorkOrderStatus.QUOTE_COLLECTING);
        List<Long> supplierDeptIds = command.supplierDeptIds() == null
                ? List.of()
                : command.supplierDeptIds().stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (supplierDeptIds.isEmpty() || supplierDeptIds.size() > 20) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "每次必须邀请 1 至 20 家供应商");
        }
        boolean appendInvitation = order.status() == RepairWorkOrderStatus.QUOTE_COLLECTING;
        if (appendInvitation && trim(command.remark()) == null) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "追加邀价必须填写原因");
        }
        Set<Long> existingSupplierDeptIds = repository.listQuoteInvitations(order.workOrderId(), order.tenantId())
                .stream().map(RepairQuoteInvitation::supplierDeptId).collect(java.util.stream.Collectors.toSet());
        if (supplierDeptIds.stream().anyMatch(existingSupplierDeptIds::contains)) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "所选供应商中包含已发出邀价的企业");
        }
        for (Long supplierDeptId : supplierDeptIds) {
            repository.findSupplierLegalName(supplierDeptId)
                    .orElseThrow(() -> new RepairWorkOrderApplicationException(NOT_FOUND,
                            "供应商组织不存在 supplierDeptId=" + supplierDeptId));
        }
        if (command.deadline() != null && !command.deadline().isAfter(LocalDateTime.now())) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "报价截止时间必须晚于当前时间");
        }
        repository.inviteSuppliers(order.workOrderId(), order.tenantId(), ctx.userId(),
                supplierDeptIds, command.deadline());
        supplierDeptIds.forEach(supplierDeptId -> supplierActivationService.ensureContactInvitation(
                order.tenantId(), supplierDeptId, order.workOrderId(), ctx.userId()));
        RepairWorkOrder next = order.withStatus(RepairWorkOrderStatus.QUOTE_COLLECTING, false, true, false);
        return transition(order, next, appendInvitation ? "APPEND_REPAIR_SUPPLIERS" : "INVITE_REPAIR_SUPPLIERS",
                command.remark(), jsonPayload(Map.of(
                "supplierDeptIds", supplierDeptIds,
                "invitationCount", supplierDeptIds.size())));
    }

    @Transactional
    public RepairWorkOrder requestSupplierQuoteRevisions(Long workOrderId,
                                                         InviteRepairSuppliersCommand command) {
        UserContext ctx = requireRole(SUPPLIER_RECOMMEND_ROLES, "当前角色无权要求供应商修订报价");
        RepairWorkOrder order = loadVisible(ctx, workOrderId);
        requireStatus(order, RepairWorkOrderStatus.PLAN_SUBMITTED,
                RepairWorkOrderStatus.QUOTE_COLLECTING, RepairWorkOrderStatus.QUOTE_SUBMITTED);
        List<Long> supplierDeptIds = command.supplierDeptIds() == null
                ? List.of()
                : command.supplierDeptIds().stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (supplierDeptIds.isEmpty() || supplierDeptIds.size() > 20) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "每次必须选择 1 至 20 家供应商修订报价");
        }
        String revisionReason = requireText(command.remark(), "修订报价原因");
        if (command.deadline() != null && !command.deadline().isAfter(LocalDateTime.now())) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "报价截止时间必须晚于当前时间");
        }
        repository.lockQuoteSubmission(order.workOrderId());
        for (Long supplierDeptId : supplierDeptIds) {
            RepairSupplierQuote latestQuote = repository.findLatestSupplierQuote(
                            order.workOrderId(), order.tenantId(), supplierDeptId)
                    .orElseThrow(() -> new RepairWorkOrderApplicationException(NOT_FOUND,
                            "所选供应商没有可修订的历史报价 supplierDeptId=" + supplierDeptId));
            if (latestQuote.quoteStatus() == RepairSupplierQuoteStatus.REVISION_REQUESTED) {
                throw new RepairWorkOrderApplicationException(PARAM_INVALID,
                        "所选供应商已有待提交的修订报价 supplierDeptId=" + supplierDeptId);
            }
            if (latestQuote.quoteStatus() != RepairSupplierQuoteStatus.ACTIVE) {
                throw new RepairWorkOrderApplicationException(PARAM_INVALID,
                        "所选供应商没有当前有效报价 supplierDeptId=" + supplierDeptId);
            }
        }
        repository.inviteSupplierRevisions(order.workOrderId(), order.tenantId(), ctx.userId(),
                supplierDeptIds, command.deadline(), revisionReason);
        supplierDeptIds.forEach(supplierDeptId -> {
            repository.markActiveQuoteRevisionRequested(order.workOrderId(), order.tenantId(), supplierDeptId);
            supplierActivationService.ensureContactInvitation(
                    order.tenantId(), supplierDeptId, order.workOrderId(), ctx.userId());
        });
        RepairWorkOrder next = order.withStatus(RepairWorkOrderStatus.QUOTE_COLLECTING, false, true, false);
        return transition(order, next, "REQUEST_SUPPLIER_QUOTE_REVISIONS", revisionReason, jsonPayload(Map.of(
                "supplierDeptIds", supplierDeptIds,
                "supplierCount", supplierDeptIds.size())));
    }

    @Transactional
    public RepairWorkOrder reusePreviousSupplierQuote(Long workOrderId, RepairActionCommand command) {
        UserContext ctx = requireRole(SUPPLIER_RECOMMEND_ROLES, "当前角色无权沿用上一轮供应商报价");
        RepairWorkOrder order = loadVisible(ctx, workOrderId);
        requireStatus(order, RepairWorkOrderStatus.PLAN_SUBMITTED,
                RepairWorkOrderStatus.QUOTE_COLLECTING, RepairWorkOrderStatus.QUOTE_SUBMITTED);
        String reuseReason = requireText(command.remark(), "沿用上一轮报价原因");
        repository.lockQuoteSubmission(order.workOrderId());
        RepairSupplierRecommendation previousRecommendation = repository.findLatestRecommendation(
                        order.workOrderId(), order.tenantId())
                .orElseThrow(() -> new RepairWorkOrderApplicationException(NOT_FOUND, "没有可沿用的历史供应商推荐记录"));
        RepairSupplierQuote quote = repository.findQuote(
                        previousRecommendation.quoteId(), order.workOrderId(), order.tenantId())
                .orElseThrow(() -> new RepairWorkOrderApplicationException(PARAM_INVALID,
                        "上一轮推荐报价已失效，请要求供应商修订报价"));
        if (quote.confirmationStatus() == null || !quote.confirmationStatus().confirmedForContract()) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "上一轮推荐报价尚未完成供应商确认，不能直接沿用");
        }
        RepairSupplierRecommendation recommendation = repository.insertRecommendation(
                new RepairSupplierRecommendation(
                        null,
                        order.workOrderId(),
                        order.tenantId(),
                        quote.quoteId(),
                        ctx.userId(),
                        "沿用上一轮推荐：" + reuseReason,
                        previousRecommendation.singleSource(),
                        previousRecommendation.singleSourceReason(),
                        previousRecommendation.selectionMethod(),
                        previousRecommendation.insufficientQuoteReason(),
                        previousRecommendation.frameworkRelationId(),
                        null));
        RepairWorkOrder next = order.withStatus(RepairWorkOrderStatus.SUPPLIER_RECOMMENDED, false, true, false);
        return transition(order, next, "REUSE_SUPPLIER_QUOTE", reuseReason, jsonPayload(Map.of(
                "previousRecommendationId", previousRecommendation.recommendationId(),
                "recommendationId", recommendation.recommendationId(),
                "quoteId", quote.quoteId(),
                "quoteRevisionNo", quote.revisionNo(),
                "supplierName", quote.supplierName())));
    }

    @Transactional
    public RepairWorkOrder submitSupplierQuote(Long workOrderId, SubmitRepairSupplierQuoteCommand command) {
        UserContext ctx = requireSysContext();
        boolean supplierSubmission = SUPPLIER_ROLES.contains(ctx.roleKey());
        if (!supplierSubmission && !PROPERTY_QUOTE_ROLES.contains(ctx.roleKey())) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN,
                    "当前角色无权提交供应商报价 roleKey=" + ctx.roleKey());
        }
        RepairWorkOrder order = supplierSubmission
                ? loadForSupplier(ctx, workOrderId)
                : loadVisible(ctx, workOrderId);
        requireStatus(order, RepairWorkOrderStatus.QUOTE_COLLECTING, RepairWorkOrderStatus.QUOTE_SUBMITTED);
        if (command.quoteAmount() == null || command.quoteAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "quoteAmount 必须为非负数");
        }
        Long supplierDeptId = supplierSubmission ? ctx.deptId() : command.supplierDeptId();
        if (supplierDeptId == null) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "supplierDeptId 必填");
        }
        String supplierName = repository.findSupplierLegalName(supplierDeptId)
                .orElseThrow(() -> new RepairWorkOrderApplicationException(NOT_FOUND,
                        "供应商组织不存在或不可用 supplierDeptId=" + supplierDeptId));
        RepairQuoteSubmissionSource submissionSource = supplierSubmission
                ? RepairQuoteSubmissionSource.SUPPLIER_ONLINE
                : RepairQuoteSubmissionSource.PROPERTY_ENTRY;
        RepairQuoteConfirmationStatus confirmationStatus = supplierSubmission
                ? RepairQuoteConfirmationStatus.ONLINE_CONFIRMED
                : parseEnum(command.confirmationStatus(), RepairQuoteConfirmationStatus.class,
                        RepairQuoteConfirmationStatus.PENDING_SUPPLIER_CONFIRMATION, "confirmationStatus");
        if (!supplierSubmission && confirmationStatus == RepairQuoteConfirmationStatus.ONLINE_CONFIRMED) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "物业代录不能标记为供应商在线确认");
        }
        if (command.attachmentId() == null) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "报价原件附件必填");
        }
        List<RepairAttachment> quoteAttachments = readyAttachments(order, List.of(command.attachmentId()),
                RepairAttachmentKind.QUOTE_DOCUMENT, 1, 1);
        RepairAttachment quoteAttachment = quoteAttachments.getFirst();
        if (supplierSubmission && !ctx.accountId().equals(quoteAttachment.uploadedByAccountId())) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, "供应商只能提交本人上传的报价原件");
        }
        String attachmentHash = requireText(quoteAttachment.etag(), "报价原件 ETag");
        if (!supplierSubmission && trim(command.originalSource()) == null) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "物业代录必须记录原始报价来源");
        }
        repository.lockQuoteSubmission(order.workOrderId());
        RepairSupplierQuote previousQuote = repository.findLatestSupplierQuote(
                order.workOrderId(), order.tenantId(), supplierDeptId).orElse(null);
        int revisionNo = previousQuote == null ? 1 : previousQuote.revisionNo() + 1;
        boolean replacePreviousQuote = previousQuote != null
                && previousQuote.quoteStatus() != RepairSupplierQuoteStatus.SUPERSEDED;
        if (replacePreviousQuote) {
            repository.supersedeQuote(previousQuote.quoteId());
        }
        RepairSupplierQuote quote = repository.insertQuote(new RepairSupplierQuote(
                null,
                order.workOrderId(),
                order.tenantId(),
                supplierName,
                command.quoteAmount(),
                trim(command.quoteSummary()),
                quoteAttachment.attachmentId(),
                attachmentHash,
                ctx.userId(),
                ctx.roleKey(),
                supplierSubmission,
                confirmationStatus.confirmedForContract(),
                supplierDeptId,
                command.quoteInvitationId(),
                submissionSource,
                confirmationStatus,
                trim(command.originalSource()),
                attachmentHash,
                RepairSupplierQuoteStatus.ACTIVE,
                revisionNo,
                null,
                null));
        if (replacePreviousQuote) {
            repository.linkSupersededQuote(previousQuote.quoteId(), quote.quoteId());
        }
        bindAttachments(order, quoteAttachments, "SUBMIT_SUPPLIER_QUOTE");
        RepairWorkOrder next = order.withStatus(RepairWorkOrderStatus.QUOTE_SUBMITTED, false, true, false);
        return transition(order, next, "SUBMIT_SUPPLIER_QUOTE", command.remark(), jsonPayload(Map.of(
                "quoteId", quote.quoteId(),
                "supplierName", quote.supplierName(),
                "quoteAmount", quote.quoteAmount(),
                "revisionNo", quote.revisionNo(),
                "replacedQuoteId", replacePreviousQuote ? previousQuote.quoteId() : "",
                "submissionSource", submissionSource,
                "confirmationStatus", confirmationStatus)));
    }

    @Transactional
    public RepairWorkOrder recommendSupplier(Long workOrderId, RecommendRepairSupplierCommand command) {
        UserContext ctx = requireRole(SUPPLIER_RECOMMEND_ROLES, "当前角色无权推荐维修供应商");
        RepairWorkOrder order = loadVisible(ctx, workOrderId);
        requireStatus(order, RepairWorkOrderStatus.QUOTE_SUBMITTED, RepairWorkOrderStatus.SUPPLIER_RECOMMENDED);
        if (command.quoteId() == null) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "quoteId 必填");
        }
        RepairSupplierQuote quote = repository.findQuote(command.quoteId(), order.workOrderId(), order.tenantId())
                .orElseThrow(() -> new RepairWorkOrderApplicationException(NOT_FOUND, "报价不存在 quoteId=" + command.quoteId()));
        RepairSupplierSelectionMethod selectionMethod = parseEnum(command.selectionMethod(),
                RepairSupplierSelectionMethod.class, RepairSupplierSelectionMethod.COMPETITIVE_QUOTATION,
                "selectionMethod");
        int quoteCount = repository.countQuotes(order.workOrderId(), order.tenantId());
        int invitationCount = repository.countQuoteInvitations(order.workOrderId(), order.tenantId());
        String insufficientQuoteReason = trim(command.insufficientQuoteReason());
        if (selectionMethod == RepairSupplierSelectionMethod.COMPETITIVE_QUOTATION) {
            if (invitationCount < 3) {
                throw new RepairWorkOrderApplicationException(PARAM_INVALID, "竞争性比价必须先邀请至少 3 家供应商");
            }
            if (quoteCount < 3 && insufficientQuoteReason == null) {
                throw new RepairWorkOrderApplicationException(PARAM_INVALID, "报价不足 3 家时必须说明继续推荐的理由");
            }
        } else if (trim(command.recommendationReason()) == null) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "跳过比价必须填写选择依据");
        }
        if (selectionMethod == RepairSupplierSelectionMethod.FRAMEWORK_SUPPLIER
                && command.frameworkRelationId() == null) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "框架供应商必须关联有效的长期合作关系");
        }
        if (selectionMethod == RepairSupplierSelectionMethod.FRAMEWORK_SUPPLIER
                && !repository.frameworkRelationActive(command.frameworkRelationId(), order.tenantId(),
                quote.supplierDeptId(), order.category())) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "框架供应商关系已过期或不适用于当前维修类别");
        }
        boolean directSelection = selectionMethod != RepairSupplierSelectionMethod.COMPETITIVE_QUOTATION;
        RepairSupplierRecommendation recommendation = repository.insertRecommendation(new RepairSupplierRecommendation(
                null,
                order.workOrderId(),
                order.tenantId(),
                quote.quoteId(),
                ctx.userId(),
                trim(command.recommendationReason()),
                directSelection,
                directSelection ? trim(command.recommendationReason()) : null,
                selectionMethod,
                insufficientQuoteReason,
                command.frameworkRelationId(),
                null));
        RepairWorkOrder next = order.withStatus(RepairWorkOrderStatus.SUPPLIER_RECOMMENDED, false, true, false);
        return transition(order, next, "RECOMMEND_SUPPLIER", command.remark(), jsonPayload(Map.of(
                "recommendationId", recommendation.recommendationId(),
                "quoteId", quote.quoteId(),
                "supplierName", quote.supplierName(),
                "quoteCount", quoteCount,
                "invitationCount", invitationCount,
                "selectionMethod", selectionMethod)));
    }

    @Transactional
    public RepairWorkOrder startLocalDecision(Long workOrderId, StartRepairLocalDecisionCommand command) {
        UserContext ctx = requireRole(LOCAL_DECISION_ROLES, "当前角色无权发起楼栋维修表决");
        RepairWorkOrder order = loadVisible(ctx, workOrderId);
        requireStatus(order, RepairWorkOrderStatus.SUPPLIER_RECOMMENDED);
        if (!FUND_BUILDING_MAINTENANCE.equals(order.fundSource())) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID,
                    "仅使用楼栋专项维修资金的维修可以发起楼栋表决");
        }
        if (order.buildingId() == null) {
            throw new RepairWorkOrderApplicationException(LOCATION_NOT_VERIFIED, "楼栋表决必须先绑定楼栋");
        }
        if ("OWNER_REPRESENTATIVE".equals(ctx.roleKey()) && !ctx.authorizedBuildingScopes()
                .contains(new WorkIdentityBuildingScope(order.tenantId(), order.buildingId()))) {
            throw new RepairWorkOrderApplicationException(BUILDING_NOT_IN_SCOPE, "楼主无权发起该楼栋表决");
        }
        RepairLocalDecisionScopeType scopeType = parseEnum(command.scopeType(), RepairLocalDecisionScopeType.class,
                RepairLocalDecisionScopeType.BUILDING, "scopeType");
        RepairPlanningPolicy planningPolicy = planningPolicy(order.tenantId());
        RepairLocalDecisionChannel communityDefaultChannel = parseEnum(
                planningPolicy.buildingRepairDefaultDecisionChannel(),
                RepairLocalDecisionChannel.class, RepairLocalDecisionChannel.WECHAT,
                "buildingRepairDefaultDecisionChannel");
        String requestedChannel = trim(command.decisionChannel());
        RepairLocalDecisionChannel decisionChannel = parseEnum(requestedChannel,
                RepairLocalDecisionChannel.class, communityDefaultChannel, "decisionChannel");
        String channelSource = requestedChannel != null && decisionChannel != communityDefaultChannel
                ? "WORK_ORDER_OVERRIDE"
                : "COMMUNITY_DEFAULT";
        String unitName = trim(command.unitName());
        if (scopeType == RepairLocalDecisionScopeType.BUILDING_UNIT && unitName == null) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "按单元接龙时 unitName 必填");
        }
        if (scopeType == RepairLocalDecisionScopeType.BUILDING) {
            unitName = null;
        }
        RepairBuildingDecisionSnapshot snapshot = repository
                .loadBuildingDecisionSnapshot(order.tenantId(), order.buildingId(), unitName)
                .orElseThrow(() -> new RepairWorkOrderApplicationException(PARAM_INVALID, "未找到楼栋业主分母"));
        if (snapshot.totalOwnerCount() <= 0 || snapshot.totalArea() == null
                || snapshot.totalArea().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "楼栋业主分母为空，禁止发起接龙");
        }
        RepairLocalDecision decision = repository.insertLocalDecision(new RepairLocalDecision(
                null,
                order.workOrderId(),
                order.tenantId(),
                order.buildingId(),
                scopeType,
                decisionChannel,
                unitName,
                trim(command.scopeLabel()),
                snapshot.totalOwnerCount(),
                snapshot.totalArea(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                "COLLECTING",
                null,
                null));
        RepairWorkOrder next = order.withStatus(RepairWorkOrderStatus.LOCAL_DECISION_PENDING, false, true, false);
        return transition(order, next, "START_LOCAL_DECISION", command.remark(), jsonPayload(Map.of(
                "decisionId", decision.decisionId(),
                "buildingId", decision.buildingId(),
                "scopeType", decision.scopeType(),
                "decisionChannel", decision.decisionChannel(),
                "decisionChannelSource", channelSource,
                "unitName", decision.unitName() == null ? "" : decision.unitName(),
                "totalOwnerCount", decision.totalOwnerCount(),
                "totalArea", decision.totalArea())));
    }

    @Transactional
    public RepairWorkOrder completeLocalDecision(Long workOrderId, CompleteRepairLocalDecisionCommand command) {
        UserContext ctx = requireRole(PROPERTY_QUOTE_ROLES, "仅物业可以汇总并结束楼栋维修表决");
        RepairWorkOrder order = loadVisible(ctx, workOrderId);
        requireStatus(order, RepairWorkOrderStatus.LOCAL_DECISION_PENDING);
        RepairLocalDecision decision = repository.findLocalDecision(order.workOrderId(), order.tenantId())
                .orElseThrow(() -> new RepairWorkOrderApplicationException(NOT_FOUND, "接龙决策不存在"));
        if (!"COLLECTING".equals(decision.result())) {
            throw new RepairWorkOrderApplicationException(INVALID_STATUS,
                    "仅进行中的楼栋表决可以结束并结算 result=" + decision.result());
        }
        String evidenceAttachmentHash = null;
        List<RepairAttachment> evidenceAttachments = List.of();
        LocalDecisionTally tally;
        if (decision.decisionChannel() == RepairLocalDecisionChannel.WECHAT) {
            if (command.evidenceAttachmentId() != null) {
                evidenceAttachments = readyAttachments(order, List.of(command.evidenceAttachmentId()),
                        RepairAttachmentKind.SOLITAIRE_SCREENSHOT, 1, 1);
                RepairAttachment screenshot = evidenceAttachments.getFirst();
                evidenceAttachmentHash = "attachment:%d:%s".formatted(
                        screenshot.attachmentId(), screenshot.etag());
            } else {
                evidenceAttachmentHash = requireText(command.evidenceAttachmentHash(), "evidenceAttachmentHash");
            }
            tally = tallyLocalDecision(command, decision);
            repository.replaceSolitaireEntries(decision.decisionId(), decision.tenantId(), ctx.userId(), tally.entries());
            repository.insertSolitaireEvidence(decision.decisionId(), decision.tenantId(), evidenceAttachmentHash,
                    ctx.accountId(), ctx.userId());
            bindAttachments(order, evidenceAttachments, "COMPLETE_LOCAL_DECISION");
        } else {
            List<RepairSolitaireEntry> onlineEntries = repository.listSolitaireEntries(
                    decision.decisionId(), decision.tenantId());
            Map<Long, RepairSolitaireEntry> submittedByRoom = onlineEntries.stream()
                    .collect(java.util.stream.Collectors.toMap(RepairSolitaireEntry::roomId, entry -> entry));
            List<CompleteRepairLocalDecisionCommand.Entry> completedEntries = repository
                    .listDecisionRooms(decision.tenantId(), decision.buildingId(), decision.unitName()).stream()
                    .map(room -> {
                        RepairSolitaireEntry entry = submittedByRoom.get(room.roomId());
                        return entry == null
                                ? new CompleteRepairLocalDecisionCommand.Entry(
                                        room.roomId(), null, RepairVoteChoice.NOT_VOTED.name(), null)
                                : new CompleteRepairLocalDecisionCommand.Entry(
                                        entry.roomId(), entry.ownerUid(), entry.choice().name(), entry.originalText());
                    })
                    .toList();
            tally = tallyLocalDecisionEntries(completedEntries, decision);
        }
        boolean passed = RepairLocalDecision.passesThreshold(
                tally.participatedOwnerCount(), tally.participatedArea(),
                tally.agreeOwnerCount(), tally.agreeArea(),
                decision.totalOwnerCount(), decision.totalArea());
        String result = passed ? "PASSED" : "FAILED";
        RepairLocalDecision updated = new RepairLocalDecision(
                decision.decisionId(),
                decision.workOrderId(),
                decision.tenantId(),
                decision.buildingId(),
                decision.scopeType(),
                decision.decisionChannel(),
                decision.unitName(),
                decision.scopeLabel(),
                decision.totalOwnerCount(),
                decision.totalArea(),
                tally.participatedOwnerCount(),
                tally.participatedArea(),
                tally.agreeOwnerCount(),
                tally.agreeArea(),
                tally.disagreeOwnerCount(),
                tally.disagreeArea(),
                tally.abstainOwnerCount(),
                tally.abstainArea(),
                tally.invalidOwnerCount(),
                tally.invalidArea(),
                evidenceAttachmentHash,
                false,
                result,
                decision.createTime(),
                decision.updateTime());
        repository.updateLocalDecisionResult(updated);
        RepairWorkOrderStatus nextStatus = passed
                ? RepairWorkOrderStatus.LOCAL_DECISION_PASSED
                : RepairWorkOrderStatus.PLAN_REVISION_REQUIRED;
        return transition(order, order.withStatus(nextStatus, false, true, false),
                "COMPLETE_LOCAL_DECISION", command.remark(), jsonPayload(Map.of(
                        "decisionId", decision.decisionId(),
                        "decisionChannel", decision.decisionChannel(),
                        "result", result,
                        "participatedOwnerCount", tally.participatedOwnerCount(),
                        "agreeOwnerCount", tally.agreeOwnerCount(),
                        "totalOwnerCount", decision.totalOwnerCount(),
                        "agreeArea", tally.agreeArea(),
                        "totalArea", decision.totalArea())));
    }

    @Transactional
    public RepairWorkOrder pauseLocalDecision(Long workOrderId, RepairActionCommand command) {
        UserContext ctx = requireRole(PROPERTY_QUOTE_ROLES, "仅物业可以暂停楼栋表决");
        RepairWorkOrder order = loadVisible(ctx, workOrderId);
        requireStatus(order, RepairWorkOrderStatus.LOCAL_DECISION_PENDING);
        RepairLocalDecision decision = repository.findLocalDecision(order.workOrderId(), order.tenantId())
                .orElseThrow(() -> new RepairWorkOrderApplicationException(NOT_FOUND, "楼栋表决不存在"));
        if (!"COLLECTING".equals(decision.result())) {
            throw new RepairWorkOrderApplicationException(INVALID_STATUS, "仅进行中的楼栋表决可以暂停");
        }
        repository.updateLocalDecisionResult(decision.withResult("PAUSED"));
        event(order, "PAUSE_LOCAL_DECISION", order.status(), order.status(), command.remark(),
                jsonPayload(Map.of("decisionId", decision.decisionId(), "preserveVotes", true)));
        return order;
    }

    @Transactional
    public RepairWorkOrder resumeLocalDecision(Long workOrderId, RepairActionCommand command) {
        UserContext ctx = requireRole(PROPERTY_QUOTE_ROLES, "仅物业可以恢复楼栋表决");
        RepairWorkOrder order = loadVisible(ctx, workOrderId);
        requireStatus(order, RepairWorkOrderStatus.LOCAL_DECISION_PENDING);
        RepairLocalDecision decision = repository.findLocalDecision(order.workOrderId(), order.tenantId())
                .orElseThrow(() -> new RepairWorkOrderApplicationException(NOT_FOUND, "楼栋表决不存在"));
        if (!"PAUSED".equals(decision.result())) {
            throw new RepairWorkOrderApplicationException(INVALID_STATUS, "仅已暂停的楼栋表决可以恢复");
        }
        repository.updateLocalDecisionResult(decision.withResult("COLLECTING"));
        event(order, "RESUME_LOCAL_DECISION", order.status(), order.status(), command.remark(),
                jsonPayload(Map.of("decisionId", decision.decisionId(), "preserveVotes", true)));
        return order;
    }

    @Transactional(readOnly = true)
    public List<RepairDecisionRoom> listLocalDecisionRooms(Long workOrderId) {
        UserContext ctx = requireRole(LOCAL_DECISION_ROLES, "当前角色无权查看楼栋接龙分母");
        RepairWorkOrder order = loadVisible(ctx, workOrderId);
        requireStatus(order, RepairWorkOrderStatus.LOCAL_DECISION_PENDING);
        RepairLocalDecision decision = repository.findLocalDecision(order.workOrderId(), order.tenantId())
                .orElseThrow(() -> new RepairWorkOrderApplicationException(NOT_FOUND, "接龙决策不存在"));
        return repository.listDecisionRooms(decision.tenantId(), decision.buildingId(), decision.unitName());
    }

    @Transactional(readOnly = true)
    public RepairLocalDecision getLocalDecision(Long workOrderId) {
        UserContext ctx = requireRole(LOCAL_DECISION_ROLES, "当前角色无权查看楼栋维修表决");
        RepairWorkOrder order = loadVisible(ctx, workOrderId);
        requireStatus(order, RepairWorkOrderStatus.LOCAL_DECISION_PENDING,
                RepairWorkOrderStatus.LOCAL_DECISION_PASSED);
        return repository.findLocalDecision(order.workOrderId(), order.tenantId())
                .orElseThrow(() -> new RepairWorkOrderApplicationException(NOT_FOUND, "楼栋表决不存在"));
    }

    @Transactional(readOnly = true)
    public List<RepairOwnerLocalDecision> listMyLocalDecisions() {
        UserContext ctx = requireOwnerContext();
        return repository.listOwnerLocalDecisions(ctx.uid(), ctx.tenantId());
    }

    @Transactional
    public RepairOwnerLocalDecision submitMyOnlineVote(Long decisionId, SubmitRepairOnlineVoteCommand command) {
        UserContext ctx = requireOwnerContext();
        if (command.opid() == null) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "opid 必填");
        }
        RepairVoteChoice choice = parseEnum(command.choice(), RepairVoteChoice.class, null, "choice");
        if (!Set.of(RepairVoteChoice.AGREE, RepairVoteChoice.DISAGREE, RepairVoteChoice.ABSTAIN)
                .contains(choice)) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "在线表决仅支持同意、不同意或弃权");
        }
        RepairOwnerLocalDecision decision = repository.findOwnerLocalDecision(
                        decisionId, command.opid(), ctx.uid(), ctx.tenantId())
                .orElseThrow(() -> new RepairWorkOrderApplicationException(NOT_FOUND,
                        "表决不存在、已结束或房屋不在表决范围内"));
        repository.submitOnlineDecisionVote(decision.decisionId(), ctx.tenantId(), decision.roomId(),
                ctx.uid(), ctx.accountId(), choice.name(), decision.buildArea());
        RepairWorkOrder order = repository.findById(decision.workOrderId()).orElseThrow();
        event(order, "OWNER_CAST_LOCAL_DECISION", order.status(), order.status(), "业主提交楼栋维修在线表决",
                jsonPayload(Map.of("decisionId", decisionId, "opid", command.opid(), "choice", choice)));
        return repository.findOwnerLocalDecision(decisionId, command.opid(), ctx.uid(), ctx.tenantId())
                .orElseThrow();
    }

    @Transactional
    public RepairWorkOrder startAssemblyDecision(Long workOrderId, StartRepairAssemblyDecisionCommand command) {
        UserContext ctx = requireRole(SUPPLIER_RECOMMEND_ROLES, "当前角色无权关联业主大会表决包");
        RepairWorkOrder order = loadVisible(ctx, workOrderId);
        requireStatus(order, RepairWorkOrderStatus.SUPPLIER_RECOMMENDED);
        if (!ASSEMBLY_FUND_SOURCES.contains(order.fundSource())) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID,
                    "仅小区公共维修资金或公共收益维修可以关联业主大会表决");
        }
        if (command.packageId() == null) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "packageId 必填");
        }
        OwnersAssemblyPackage ballotPackage = ownersAssemblyRepository
                .findPackage(command.packageId(), order.tenantId())
                .orElseThrow(() -> new RepairWorkOrderApplicationException(NOT_FOUND,
                        "业主大会表决包不存在 packageId=" + command.packageId()));
        if (!"PACKAGE_DRAFT".equals(ballotPackage.status())
                && !"PUBLIC_NOTICE".equals(ballotPackage.status())
                && !"VOTING".equals(ballotPackage.status())
                && !"SETTLED".equals(ballotPackage.status())) {
            throw new RepairWorkOrderApplicationException(INVALID_STATUS,
                    "表决包状态不允许关联维修工单 status=" + ballotPackage.status());
        }
        repository.insertAssemblyDecision(order.workOrderId(), order.tenantId(), ballotPackage.packageId());
        RepairWorkOrder next = order.withStatus(RepairWorkOrderStatus.ASSEMBLY_DECISION_PENDING, false, true, false);
        return transition(order, next, "START_ASSEMBLY_DECISION", command.remark(), jsonPayload(Map.of(
                "packageId", ballotPackage.packageId(),
                "packageStatus", ballotPackage.status(),
                "votingChannelPolicy", ballotPackage.votingChannelPolicy())));
    }

    @Transactional
    public RepairWorkOrder completeAssemblyDecision(Long workOrderId, RepairActionCommand command) {
        UserContext ctx = requireRole(MANAGER_ROLES, "当前角色无权确认业主大会表决结果");
        RepairWorkOrder order = loadVisible(ctx, workOrderId);
        requireStatus(order, RepairWorkOrderStatus.ASSEMBLY_DECISION_PENDING);
        Long packageId = repository.findAssemblyDecisionPackageId(order.workOrderId(), order.tenantId())
                .orElseThrow(() -> new RepairWorkOrderApplicationException(NOT_FOUND, "维修工单未关联业主大会表决包"));
        OwnersAssemblyPackage ballotPackage = ownersAssemblyRepository
                .findPackage(packageId, order.tenantId())
                .orElseThrow(() -> new RepairWorkOrderApplicationException(NOT_FOUND,
                        "业主大会表决包不存在 packageId=" + packageId));
        if (!"SETTLED".equals(ballotPackage.status())) {
            throw new RepairWorkOrderApplicationException(INVALID_STATUS,
                    "业主大会表决包未完成结算 status=" + ballotPackage.status());
        }
        boolean passed = ownersAssemblyRepository.allSubjectsPassed(packageId, order.tenantId());
        String result = passed ? "PASSED" : "FAILED";
        repository.updateAssemblyDecisionResult(order.workOrderId(), order.tenantId(), result);
        RepairWorkOrderStatus nextStatus = passed
                ? RepairWorkOrderStatus.APPROVAL_DOCUMENT_PREPARING
                : RepairWorkOrderStatus.PLAN_REVISION_REQUIRED;
        return transition(order, order.withStatus(nextStatus, false, true, false),
                "COMPLETE_ASSEMBLY_DECISION", command.remark(), jsonPayload(Map.of(
                        "packageId", packageId,
                        "result", result)));
    }

    @Transactional
    public RepairWorkOrder submitApprovalPackage(Long workOrderId, SubmitRepairApprovalPackageCommand command) {
        UserContext ctx = requireRole(PROPERTY_QUOTE_ROLES, "当前角色无权提交维修报审文件");
        RepairWorkOrder order = loadVisible(ctx, workOrderId);
        requireStatus(order, RepairWorkOrderStatus.LOCAL_DECISION_PASSED,
                RepairWorkOrderStatus.APPROVAL_DOCUMENT_PREPARING);
        RepairLocalDecision decision = repository.findLocalDecision(order.workOrderId(), order.tenantId())
                .orElse(null);
        boolean requireWechatEvidence = decision != null
                && decision.decisionChannel() == RepairLocalDecisionChannel.WECHAT;
        RepairAttachment officialDocument = readyAttachments(
                order, List.of(command.officialDocumentAttachmentId()),
                RepairAttachmentKind.APPROVAL_DOCUMENT, 1, 1).getFirst();
        List<RepairAttachment> uploadedSolitaireScreenshots = List.of();
        if (requireWechatEvidence && command.solitaireScreenshotAttachmentIds() != null
                && !command.solitaireScreenshotAttachmentIds().isEmpty()) {
            uploadedSolitaireScreenshots = readyAttachments(
                    order, command.solitaireScreenshotAttachmentIds(),
                    RepairAttachmentKind.SOLITAIRE_SCREENSHOT, 1, 3);
        }
        List<RepairApprovalAttachment> attachments = new ArrayList<>();
        attachments.add(new RepairApprovalAttachment(
                "APPROVAL_DOCUMENT", attachmentHash(officialDocument),
                officialDocument.originalFileName(), 1));
        int sortOrder = 2;
        if (requireWechatEvidence) {
            if (!uploadedSolitaireScreenshots.isEmpty()) {
                for (RepairAttachment screenshot : uploadedSolitaireScreenshots) {
                    attachments.add(new RepairApprovalAttachment(
                            "SOLITAIRE_SCREENSHOT", attachmentHash(screenshot),
                            screenshot.originalFileName(), sortOrder++));
                }
            } else {
                String evidenceHash = requireText(decision.evidenceAttachmentHash(), "evidenceAttachmentHash");
                attachments.add(new RepairApprovalAttachment(
                        "SOLITAIRE_SCREENSHOT", evidenceHash, "微信接龙截图", sortOrder));
            }
        }
        validateApprovalAttachments(attachments, requireWechatEvidence);
        String officialDocumentHash = attachmentHash(officialDocument);
        String mergedPackageHash = sha256(attachments.stream()
                .map(item -> item.sortOrder() + "|" + item.attachmentType() + "|" + item.attachmentHash())
                .collect(java.util.stream.Collectors.joining("\n")));
        Long packageId = repository.insertApprovalPackage(
                order.workOrderId(),
                order.tenantId(),
                officialDocumentHash,
                mergedPackageHash,
                requireWechatEvidence,
                ctx.userId(),
                attachments);
        List<RepairAttachment> uploadedAttachments = new ArrayList<>();
        uploadedAttachments.add(officialDocument);
        uploadedAttachments.addAll(uploadedSolitaireScreenshots);
        bindAttachments(order, uploadedAttachments, "SUBMIT_APPROVAL_PACKAGE");
        return transition(order, order.withStatus(RepairWorkOrderStatus.PRICE_REVIEW_PENDING, false, true, false),
                "SUBMIT_APPROVAL_PACKAGE", command.remark(), jsonPayload(Map.of(
                        "packageId", packageId,
                        "attachmentCount", attachments.size(),
                        "printedAndAttached", requireWechatEvidence)));
    }

    @Transactional
    public RepairWorkOrder reviewPrice(Long workOrderId, ReviewRepairPriceCommand command) {
        UserContext ctx = requireRole(GOVERNANCE_ROLES, "当前角色无权进行维修审价");
        RepairWorkOrder order = loadVisible(ctx, workOrderId);
        requireStatus(order, RepairWorkOrderStatus.PRICE_REVIEW_PENDING);
        String reviewMode = requireAllowed(command.reviewMode(), "reviewMode",
                Set.of("INTERNAL_PRICE_REVIEW", "THIRD_PARTY_COST_AUDIT"));
        String conclusion = requireAllowed(command.conclusion(), "conclusion",
                Set.of("APPROVED", "RETURNED", "HOLD"));
        if (command.reviewedAmount() == null || command.reviewedAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "reviewedAmount 必须为非负数");
        }
        String reviewReportHash = trim(command.reviewReportHash());
        if ("THIRD_PARTY_COST_AUDIT".equals(reviewMode) && reviewReportHash == null) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "第三方审价必须上传审价报告");
        }
        Long packageId = repository.findActiveApprovalPackageId(order.workOrderId(), order.tenantId())
                .orElseThrow(() -> new RepairWorkOrderApplicationException(NOT_FOUND, "未找到有效报审文件包"));
        repository.insertPriceReview(order.workOrderId(), packageId, order.tenantId(), reviewMode,
                command.reviewedAmount(), reviewReportHash, conclusion, trim(command.opinion()), ctx.userId());
        RepairWorkOrderStatus nextStatus;
        if ("APPROVED".equals(conclusion)) {
            repository.updateApprovalPackageStatus(packageId, order.tenantId(), "APPROVED");
            nextStatus = RepairWorkOrderStatus.GOVERNANCE_PENDING;
        } else if ("RETURNED".equals(conclusion)) {
            repository.updateApprovalPackageStatus(packageId, order.tenantId(), "RETURNED");
            nextStatus = RepairWorkOrderStatus.APPROVAL_DOCUMENT_PREPARING;
        } else {
            nextStatus = RepairWorkOrderStatus.PRICE_REVIEW_PENDING;
        }
        return transition(order, order.withStatus(nextStatus, false, true, false),
                "REVIEW_PRICE", command.opinion(), jsonPayload(Map.of(
                        "packageId", packageId,
                        "reviewMode", reviewMode,
                        "reviewedAmount", command.reviewedAmount(),
                        "conclusion", conclusion)));
    }

    @Transactional
    public RepairWorkOrder governanceApprove(Long workOrderId, RepairActionCommand command) {
        UserContext ctx = requireSysContext();
        RepairWorkOrder order = loadVisible(ctx, workOrderId);
        requireStatus(order, RepairWorkOrderStatus.GOVERNANCE_PENDING);
        assertHandoverUnlocked(order);
        String position = governanceConfirmPosition(ctx, order);
        repository.insertGovernanceApproval(order.workOrderId(), order.tenantId(), ctx.userId(), position, trim(command.remark()));
        return transition(order, order.withStatus(RepairWorkOrderStatus.GOVERNANCE_CONFIRMED, false, true, false),
                "GOVERNANCE_CONFIRM", command.remark(), jsonPayload(Map.of("approverPosition", position)));
    }

    @Transactional
    public RepairWorkOrder sealGovernance(Long workOrderId, SealRepairGovernanceCommand command) {
        UserContext ctx = requireRole(SEAL_ROLES, "当前角色无权加盖业委会公章");
        RepairWorkOrder order = loadVisible(ctx, workOrderId);
        requireStatus(order, RepairWorkOrderStatus.GOVERNANCE_CONFIRMED);
        String sealingMethod = requireAllowed(
                command.sealingMethod(), "sealingMethod", GOVERNANCE_SEAL_METHODS);
        if (SEAL_METHOD_PLATFORM_ELECTRONIC.equals(sealingMethod) && command.sealedAttachmentId() != null) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "平台电子签章不能同时提交已盖章文件");
        }
        if (!SEAL_METHOD_PLATFORM_ELECTRONIC.equals(sealingMethod) && command.electronicSealId() != null) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "上传已盖章文件不能同时选择平台电子印章");
        }
        GovernanceSealResult result = switch (sealingMethod) {
            case SEAL_METHOD_UPLOADED_PHYSICAL -> uploadedGovernanceSeal(
                    order, command.sealedAttachmentId(), false);
            case SEAL_METHOD_UPLOADED_EXTERNAL_ELECTRONIC -> uploadedGovernanceSeal(
                    order, command.sealedAttachmentId(), true);
            case SEAL_METHOD_PLATFORM_ELECTRONIC -> platformGovernanceSeal(
                    order, ctx, command.electronicSealId());
            default -> throw new RepairWorkOrderApplicationException(PARAM_INVALID, "不支持的盖章方式");
        };
        try {
            Long usageId = committeeSealRepository.insertUsage(new CommitteeSealUsageRecord(
                    null, order.tenantId(), result.electronicSealId(), result.sealName(),
                    "REPAIR", order.workOrderId(), order.title(), sealingMethod,
                    result.sourceAttachmentId(), result.sealedAttachment().attachmentId(),
                    result.sourceFileHash(), result.sealedFileHash(), result.providerTransactionId(),
                    result.certificateSerial(), result.verificationStatus(), result.simulated(),
                    ctx.userId(), null, trim(command.remark()), null));
            bindAttachments(order, List.of(result.sealedAttachment()), "GOVERNANCE_SEAL");
            repository.insertGovernanceSeal(order.workOrderId(), order.tenantId(), ctx.userId(),
                    usageId, result.sealType(), result.sealedFileHash(), trim(command.remark()));

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("usageId", usageId);
            payload.put("sealingMethod", sealingMethod);
            payload.put("sealType", result.sealType());
            payload.put("sealedAttachmentId", result.sealedAttachment().attachmentId());
            payload.put("sealedFileHash", result.sealedFileHash());
            payload.put("verificationStatus", result.verificationStatus());
            payload.put("simulated", result.simulated());
            return transition(order, order.withStatus(RepairWorkOrderStatus.SEALED, false, true, false),
                    "GOVERNANCE_SEAL", command.remark(), jsonPayload(payload));
        } catch (RuntimeException ex) {
            cleanupGeneratedSeal(result.generatedObjectKey(), ex);
            throw ex;
        }
    }

    private GovernanceSealResult uploadedGovernanceSeal(
            RepairWorkOrder order, Long sealedAttachmentId, boolean verifyExternalElectronicSeal) {
        if (sealedAttachmentId == null) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "请上传已盖章的报审文件");
        }
        RepairAttachment attachment = readyAttachments(
                order, List.of(sealedAttachmentId), RepairAttachmentKind.GOVERNANCE_SEALED_DOCUMENT, 1, 1)
                .getFirst();
        byte[] content = readAttachmentContent(attachment);
        String sealedFileHash = sha256(content);
        if (!verifyExternalElectronicSeal) {
            return new GovernanceSealResult(
                    null, "业主委员会实物印章", "OWNERS_COMMITTEE", null, attachment,
                    null, sealedFileHash, null, null, "PHYSICAL_STAMP_FILE_UPLOADED",
                    false, null);
        }
        if (!"application/pdf".equals(attachment.contentType())) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "外部电子签章验签仅支持 PDF 文件");
        }
        ElectronicSealProvider.VerificationResult verification;
        try {
            verification = committeeSealService.provider().verify(new ElectronicSealProvider.VerificationRequest(
                    attachment.originalFileName(), content, sealedFileHash));
        } catch (IllegalArgumentException ex) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            throw new RepairWorkOrderApplicationException(STORAGE_UNAVAILABLE, "电子签章验签服务不可用", ex);
        }
        return new GovernanceSealResult(
                null, "外部电子印章", "OWNERS_COMMITTEE", null, attachment,
                null, sealedFileHash, verification.providerTransactionId(),
                verification.certificateSerial(), verification.verificationStatus(),
                committeeSealService.provider().simulated(), null);
    }

    private GovernanceSealResult platformGovernanceSeal(
            RepairWorkOrder order, UserContext context, Long electronicSealId) {
        if (electronicSealId == null) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "请选择要使用的电子印章");
        }
        CommitteeElectronicSeal seal = committeeSealService.requireUsableSeal(electronicSealId, context);
        RepairAttachment source = attachmentRepository.findLatestByKind(
                        order.workOrderId(), order.tenantId(), RepairAttachmentKind.APPROVAL_DOCUMENT)
                .orElseThrow(() -> new RepairWorkOrderApplicationException(
                        NOT_FOUND, "未找到可供盖章的正式报审文件"));
        if (!"application/pdf".equals(source.contentType())) {
            throw new RepairWorkOrderApplicationException(
                    PARAM_INVALID, "模拟电子签章仅支持 PDF，请由物业重新上传 PDF 版正式报审文件");
        }
        byte[] sourceContent = readAttachmentContent(source);
        String sourceFileHash = sha256(sourceContent);
        ElectronicSealProvider.SignedDocument signed;
        try {
            signed = committeeSealService.provider().sign(new ElectronicSealProvider.SignRequest(
                    seal.providerSealId(), seal.certificateSerial(), source.originalFileName(),
                    sourceContent, sourceFileHash));
        } catch (IllegalArgumentException ex) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            throw new RepairWorkOrderApplicationException(STORAGE_UNAVAILABLE, "电子签章服务不可用", ex);
        }
        validateSignedDocument(signed);
        String objectKey = governanceSealedObjectKey(order);
        RepairAttachment sealedAttachment = storeGeneratedGovernanceSeal(
                order, context, objectKey, signed.content());
        return new GovernanceSealResult(
                seal.sealId(), seal.sealName(), seal.sealType().name(), source.attachmentId(),
                sealedAttachment, sourceFileHash, sha256(signed.content()),
                signed.providerTransactionId(), signed.certificateSerial(), signed.verificationStatus(),
                seal.simulated() || committeeSealService.provider().simulated(), objectKey);
    }

    private void validateSignedDocument(ElectronicSealProvider.SignedDocument signed) {
        if (signed == null || signed.content() == null || signed.content().length == 0
                || !"application/pdf".equalsIgnoreCase(trim(signed.contentType()))
                || trim(signed.providerTransactionId()) == null
                || trim(signed.verificationStatus()) == null) {
            throw new RepairWorkOrderApplicationException(
                    STORAGE_UNAVAILABLE, "电子签章服务返回的签章文件或审计标识不完整");
        }
    }

    private RepairAttachment storeGeneratedGovernanceSeal(
            RepairWorkOrder order, UserContext context, String objectKey, byte[] content) {
        RepairEvidenceObjectStorage.StoredObjectMetadata metadata;
        try {
            metadata = objectStorage.put(objectKey, content, "application/pdf", contentMd5(content));
        } catch (RuntimeException ex) {
            throw new RepairWorkOrderApplicationException(STORAGE_UNAVAILABLE, "保存模拟盖章文件失败", ex);
        }
        if (metadata.size() != content.length
                || !"application/pdf".equalsIgnoreCase(trim(metadata.contentType()))
                || trim(metadata.etag()) == null) {
            cleanupGeneratedSeal(objectKey, null);
            throw new RepairWorkOrderApplicationException(STORAGE_UNAVAILABLE, "模拟盖章文件完整性校验失败");
        }
        try {
            return attachmentRepository.insert(new RepairAttachment(
                    null, order.workOrderId(), order.tenantId(),
                    RepairAttachmentKind.GOVERNANCE_SEALED_DOCUMENT, objectKey,
                    "盖章文件-" + order.orderNo() + ".pdf", "application/pdf",
                    content.length, metadata.size(), metadata.etag(), RepairAttachmentStatus.READY,
                    context.accountId(), null, null, LocalDateTime.now()));
        } catch (RuntimeException ex) {
            cleanupGeneratedSeal(objectKey, ex);
            throw ex;
        }
    }

    private byte[] readAttachmentContent(RepairAttachment attachment) {
        try {
            byte[] content = objectStorage.read(attachment.objectKey());
            if (content == null || content.length == 0) {
                throw new RepairWorkOrderApplicationException(STORAGE_UNAVAILABLE, "附件内容为空");
            }
            return content;
        } catch (RepairWorkOrderApplicationException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new RepairWorkOrderApplicationException(STORAGE_UNAVAILABLE, "读取报审附件失败", ex);
        }
    }

    private String governanceSealedObjectKey(RepairWorkOrder order) {
        LocalDate today = LocalDate.now();
        return "repair/%d/%d/%s/%04d/%02d/%s.pdf".formatted(
                order.tenantId(), order.workOrderId(),
                RepairAttachmentKind.GOVERNANCE_SEALED_DOCUMENT.name().toLowerCase(Locale.ROOT),
                today.getYear(), today.getMonthValue(), UUID.randomUUID());
    }

    private String contentMd5(byte[] content) {
        try {
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance("MD5").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JVM 不支持 MD5", ex);
        }
    }

    private void cleanupGeneratedSeal(String objectKey, RuntimeException original) {
        if (objectKey == null) {
            return;
        }
        try {
            objectStorage.delete(objectKey);
        } catch (RuntimeException cleanupError) {
            if (original != null) {
                original.addSuppressed(cleanupError);
            }
        }
    }

    @Transactional
    public RepairWorkOrder createContract(Long workOrderId, CreateRepairContractCommand command) {
        UserContext ctx = requireRole(SUPPLIER_RECOMMEND_ROLES, "当前角色无权创建维修合同");
        RepairWorkOrder order = loadVisible(ctx, workOrderId);
        requireStatus(order, RepairWorkOrderStatus.SEALED);
        if (command.contractAmount() == null || command.contractAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "contractAmount 必须为非负数");
        }
        BigDecimal approvedAmount = repository.findLatestApprovedPrice(order.workOrderId(), order.tenantId())
                .orElseThrow(() -> new RepairWorkOrderApplicationException(INVALID_STATUS, "合同前必须完成审价"));
        if (command.contractAmount().compareTo(approvedAmount) > 0) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "合同金额超过审定金额，必须重新报审");
        }
        String supplierName = command.supplierDeptId() == null
                ? requireText(command.supplierName(), "supplierName")
                : repository.findSupplierLegalName(command.supplierDeptId())
                .orElseThrow(() -> new RepairWorkOrderApplicationException(NOT_FOUND, "供应商组织不存在"));
        if (command.supplierDeptId() != null
                && !repository.supplierVerified(order.tenantId(), command.supplierDeptId())) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "供应商企业主体尚未完成独立核验");
        }
        if (!repository.recommendedQuoteMatches(order.workOrderId(), order.tenantId(),
                command.supplierDeptId(), supplierName)) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID,
                    "合同供应商必须与物业入选供应商一致");
        }
        String signingMethod = requireAllowed(command.signingMethod(), "signingMethod",
                Set.of("ONLINE", "OFFLINE", "MIXED"));
        Long contractId = repository.insertContract(
                order.workOrderId(), order.tenantId(), command.supplierDeptId(), supplierName,
                command.contractAmount(), requireText(command.repairScopeHash(), "repairScopeHash"),
                requireText(command.fundSource(), "fundSource"), signingMethod,
                requireText(command.contractFileHash(), "contractFileHash"), ctx.userId());
        return transition(order, order.withStatus(RepairWorkOrderStatus.CONTRACT_SIGNING, false, true, false),
                "CREATE_CONTRACT", command.remark(), jsonPayload(Map.of(
                        "contractId", contractId,
                        "supplierName", supplierName,
                        "contractAmount", command.contractAmount(),
                        "signingMethod", signingMethod)));
    }

    @Transactional
    public RepairWorkOrder completeContract(Long workOrderId, CompleteRepairContractCommand command) {
        UserContext ctx = requireRole(SUPPLIER_RECOMMEND_ROLES, "当前角色无权确认三方合同签署完成");
        RepairWorkOrder order = loadVisible(ctx, workOrderId);
        requireStatus(order, RepairWorkOrderStatus.CONTRACT_SIGNING);
        List<RepairContractSignature> signatures = command.signatures() == null
                ? List.of()
                : List.copyOf(command.signatures());
        validateContractSignatures(signatures);
        Long contractId = repository.findActiveContractId(order.workOrderId(), order.tenantId())
                .orElseThrow(() -> new RepairWorkOrderApplicationException(NOT_FOUND, "未找到待签署合同"));
        repository.insertContractSignatures(contractId, signatures);
        repository.markRecommendedQuoteContractConfirmed(order.workOrderId(), order.tenantId());
        String finalContractFileHash = requireText(command.finalContractFileHash(), "finalContractFileHash");
        if (repository.markContractEffective(contractId, finalContractFileHash) != 1) {
            throw new RepairWorkOrderApplicationException(INVALID_STATUS, "合同状态已变化，请刷新后重试");
        }
        return transition(order, order.withStatus(RepairWorkOrderStatus.CONTRACT_EFFECTIVE, false, true, false),
                "COMPLETE_CONTRACT", command.remark(), jsonPayload(Map.of(
                        "contractId", contractId,
                        "partyCount", signatures.size(),
                        "finalContractFileHash", finalContractFileHash)));
    }

    @Transactional
    public RepairWorkOrder createPaymentRequest(Long workOrderId, CreateRepairPaymentRequestCommand command) {
        UserContext ctx = requireRole(SUPPLIER_RECOMMEND_ROLES, "当前角色无权发起维修付款申请");
        RepairWorkOrder order = loadVisible(ctx, workOrderId);
        requireStatus(order, RepairWorkOrderStatus.CONTRACT_EFFECTIVE, RepairWorkOrderStatus.IN_PROGRESS,
                RepairWorkOrderStatus.PENDING_ACCEPTANCE, RepairWorkOrderStatus.ACCEPTANCE_EXCEPTION,
                RepairWorkOrderStatus.COMPLETED);
        String milestoneType = requireAllowed(command.milestoneType(), "milestoneType",
                Set.of("ADVANCE", "PROGRESS", "ACCEPTANCE", "WARRANTY"));
        if (command.requestedAmount() == null || command.requestedAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "requestedAmount 必须大于零");
        }
        Long contractId = repository.findEffectiveContractId(order.workOrderId(), order.tenantId())
                .orElseThrow(() -> new RepairWorkOrderApplicationException(INVALID_STATUS, "未找到已生效合同"));
        BigDecimal remaining = repository.findContractPaymentRemaining(contractId);
        if (remaining == null || command.requestedAmount().compareTo(remaining) > 0) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "付款申请超过合同剩余可申请金额");
        }
        Long paymentRequestId = repository.insertPaymentRequest(
                order.workOrderId(), contractId, order.tenantId(), milestoneType, command.requestedAmount(),
                requireText(command.conditionEvidenceHash(), "conditionEvidenceHash"), ctx.userId());
        event(order, "CREATE_PAYMENT_REQUEST", order.status(), order.status(), command.remark(), jsonPayload(Map.of(
                "paymentRequestId", paymentRequestId,
                "contractId", contractId,
                "milestoneType", milestoneType,
                "requestedAmount", command.requestedAmount())));
        return order;
    }

    @Transactional
    public RepairWorkOrder startWork(Long workOrderId, RepairActionCommand command) {
        UserContext ctx = requireRole(FIELD_ROLES, "当前角色无权开工");
        RepairWorkOrder order = loadVisible(ctx, workOrderId);
        requireStatus(order, RepairWorkOrderStatus.APPROVED, RepairWorkOrderStatus.CONTRACT_EFFECTIVE);
        return transition(order, order.withStatus(RepairWorkOrderStatus.IN_PROGRESS, false, true, false),
                "START_WORK", command.remark());
    }

    @Transactional
    public RepairWorkOrder submitAcceptance(Long workOrderId, RepairActionCommand command) {
        UserContext ctx = requireRole(FIELD_ROLES, "当前角色无权提交验收");
        RepairWorkOrder order = loadVisible(ctx, workOrderId);
        requireStatus(order, RepairWorkOrderStatus.IN_PROGRESS, RepairWorkOrderStatus.RECTIFICATION_REQUIRED);
        return transition(order, order.withStatus(RepairWorkOrderStatus.PENDING_ACCEPTANCE, false, true, false),
                "SUBMIT_ACCEPTANCE", command.remark());
    }

    @Transactional
    public RepairWorkOrder setAcceptanceScope(Long workOrderId, SetRepairAcceptanceScopeCommand command) {
        UserContext ctx = requireRole(PROPERTY_QUOTE_ROLES, "当前角色无权设置受影响房屋范围");
        RepairWorkOrder order = loadVisible(ctx, workOrderId);
        requireStatus(order, RepairWorkOrderStatus.PLAN_SUBMITTED, RepairWorkOrderStatus.QUOTE_COLLECTING,
                RepairWorkOrderStatus.QUOTE_SUBMITTED, RepairWorkOrderStatus.SUPPLIER_RECOMMENDED,
                RepairWorkOrderStatus.LOCAL_DECISION_PENDING, RepairWorkOrderStatus.LOCAL_DECISION_PASSED,
                RepairWorkOrderStatus.PRICE_REVIEW_PENDING, RepairWorkOrderStatus.GOVERNANCE_PENDING,
                RepairWorkOrderStatus.GOVERNANCE_CONFIRMED, RepairWorkOrderStatus.SEALED,
                RepairWorkOrderStatus.CONTRACT_SIGNING, RepairWorkOrderStatus.CONTRACT_EFFECTIVE);
        List<SetRepairAcceptanceScopeCommand.AffectedRoom> rooms = command.rooms() == null
                ? List.of()
                : List.copyOf(command.rooms());
        if (rooms.isEmpty()) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "至少选择一户受影响房屋");
        }
        Set<Long> uniqueRoomIds = new HashSet<>();
        List<Long> roomIds = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        for (SetRepairAcceptanceScopeCommand.AffectedRoom room : rooms) {
            if (room == null || room.roomId() == null || !uniqueRoomIds.add(room.roomId())) {
                throw new RepairWorkOrderApplicationException(PARAM_INVALID, "受影响房屋不能为空或重复");
            }
            if (!repository.roomExists(order.tenantId(), order.buildingId(), room.roomId())) {
                throw new RepairWorkOrderApplicationException(PARAM_INVALID,
                        "受影响房屋不属于工单楼栋 roomId=" + room.roomId());
            }
            roomIds.add(room.roomId());
            reasons.add(trim(room.affectedReason()));
        }
        repository.replaceAcceptanceScope(order.workOrderId(), order.tenantId(), ctx.userId(), roomIds, reasons);
        event(order, "SET_ACCEPTANCE_SCOPE", order.status(), order.status(), command.remark(), jsonPayload(Map.of(
                "affectedRoomCount", roomIds.size(),
                "roomIds", roomIds)));
        return order;
    }

    @Transactional
    public RepairWorkOrder recordAcceptance(Long workOrderId, RecordRepairAcceptanceCommand command) {
        UserContext ctx = requireRole(ACCEPTANCE_ROLES, "当前角色无权记录维修验收");
        RepairWorkOrder order = loadVisible(ctx, workOrderId);
        requireStatus(order, RepairWorkOrderStatus.PENDING_ACCEPTANCE,
                RepairWorkOrderStatus.ACCEPTANCE_EXCEPTION, RepairWorkOrderStatus.RECTIFICATION_REQUIRED);
        String participantType = requireAllowed(command.participantType(), "participantType", Set.of(
                "AFFECTED_OWNER", "OWNER_REPRESENTATIVE", "PROPERTY_REPRESENTATIVE", "COMMITTEE_REPRESENTATIVE"));
        assertAcceptanceParticipantRole(ctx, participantType);
        Long roomId = command.roomId();
        if ("AFFECTED_OWNER".equals(participantType)) {
            if (roomId == null || !repository.roomInAcceptanceScope(order.workOrderId(), order.tenantId(), roomId)) {
                throw new RepairWorkOrderApplicationException(PARAM_INVALID, "受影响业主验收必须关联范围内房屋");
            }
        } else {
            roomId = null;
        }
        String conclusion = requireAllowed(command.conclusion(), "conclusion",
                Set.of("PASSED", "RECTIFICATION_REQUIRED", "UNREACHABLE", "AUTHORIZED"));
        if ("RECTIFICATION_REQUIRED".equals(conclusion) && trim(command.opinion()) == null) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "要求整改必须填写具体问题");
        }
        repository.insertAcceptanceRecord(order.workOrderId(), order.tenantId(), new RepairAcceptanceRecord(
                roomId, participantType, null, ctx.userId(), requireText(command.participantName(), "participantName"),
                conclusion, trim(command.opinion()), trim(command.signatureHash()), trim(command.evidenceHash()),
                ctx.userId()));
        RepairWorkOrderStatus nextStatus = "RECTIFICATION_REQUIRED".equals(conclusion)
                ? RepairWorkOrderStatus.RECTIFICATION_REQUIRED
                : order.status();
        return transition(order, order.withStatus(nextStatus, false, true, false),
                "RECORD_ACCEPTANCE", command.remark(), jsonPayload(Map.of(
                        "participantType", participantType,
                        "conclusion", conclusion,
                        "roomId", roomId == null ? 0L : roomId)));
    }

    @Transactional
    public RepairWorkOrder recordMyAcceptance(Long workOrderId, RecordRepairAcceptanceCommand command) {
        UserContext ctx = requireOwnerContext();
        RepairWorkOrder order = repository.findById(workOrderId)
                .orElseThrow(() -> new RepairWorkOrderApplicationException(NOT_FOUND, "工单不存在"));
        if (!order.tenantId().equals(ctx.tenantId())) {
            throw new RepairWorkOrderApplicationException(NOT_FOUND, "工单不在当前小区");
        }
        requireStatus(order, RepairWorkOrderStatus.PENDING_ACCEPTANCE,
                RepairWorkOrderStatus.ACCEPTANCE_EXCEPTION, RepairWorkOrderStatus.RECTIFICATION_REQUIRED);
        Long roomId = command.roomId();
        if (roomId == null || !repository.ownerOwnsAcceptanceRoom(
                order.workOrderId(), order.tenantId(), roomId, ctx.uid())) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, "当前业主不是该受影响房屋的核验业主");
        }
        String conclusion = requireAllowed(command.conclusion(), "conclusion",
                Set.of("PASSED", "RECTIFICATION_REQUIRED", "AUTHORIZED"));
        if ("RECTIFICATION_REQUIRED".equals(conclusion) && trim(command.opinion()) == null) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "要求整改必须填写具体问题");
        }
        repository.insertAcceptanceRecord(order.workOrderId(), order.tenantId(), new RepairAcceptanceRecord(
                roomId, "AFFECTED_OWNER", ctx.accountId(), null,
                requireText(command.participantName(), "participantName"), conclusion, trim(command.opinion()),
                trim(command.signatureHash()), trim(command.evidenceHash()), null));
        RepairWorkOrderStatus nextStatus = "RECTIFICATION_REQUIRED".equals(conclusion)
                ? RepairWorkOrderStatus.RECTIFICATION_REQUIRED
                : order.status();
        return transition(order, order.withStatus(nextStatus, false, true, false),
                "OWNER_RECORD_ACCEPTANCE", command.remark(), jsonPayload(Map.of(
                        "roomId", roomId,
                        "conclusion", conclusion)));
    }

    @Transactional
    public RepairWorkOrder acceptCompleted(Long workOrderId, RepairActionCommand command) {
        UserContext ctx = requireRole(LOCAL_DECISION_ROLES, "当前角色无权完成验收定案");
        RepairWorkOrder order = loadVisible(ctx, workOrderId);
        requireStatus(order, RepairWorkOrderStatus.PENDING_ACCEPTANCE, RepairWorkOrderStatus.ACCEPTANCE_EXCEPTION);
        if (order.spaceScope() == RepairSpaceScope.PRIVATE) {
            return transition(order, order.withStatus(RepairWorkOrderStatus.COMPLETED, false, true, false),
                    "ACCEPT_COMPLETED", command.remark());
        }
        RepairAcceptanceSummary summary = repository.summarizeAcceptance(order.workOrderId(), order.tenantId());
        if (summary.rectificationCount() > 0) {
            return transition(order, order.withStatus(RepairWorkOrderStatus.RECTIFICATION_REQUIRED, false, true, false),
                    "FINALIZE_ACCEPTANCE", command.remark(), acceptanceSummaryPayload(summary));
        }
        if (summary.affectedRoomCount() == 0 || !summary.ownerRepresentativePassed()) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "必须完成受影响房屋和楼组长验收");
        }
        if (summary.unreachableCount() > 0) {
            if (!summary.propertyRepresentativePassed()) {
                throw new RepairWorkOrderApplicationException(PARAM_INVALID,
                        "存在失联业主时必须由物业代表补充现场证据并见证");
            }
            return transition(order, order.withStatus(RepairWorkOrderStatus.ACCEPTANCE_EXCEPTION, false, true, false),
                    "FINALIZE_ACCEPTANCE_EXCEPTION", command.remark(), acceptanceSummaryPayload(summary));
        }
        if (summary.passedAffectedRoomCount() < summary.affectedRoomCount()) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "仍有受影响房屋未完成验收");
        }
        return transition(order, order.withStatus(RepairWorkOrderStatus.COMPLETED, false, true, false),
                "ACCEPT_COMPLETED", command.remark(), acceptanceSummaryPayload(summary));
    }

    @Transactional
    public RepairWorkOrder requestRectification(Long workOrderId, RepairActionCommand command) {
        UserContext ctx = requireRole(ACCEPTANCE_ROLES, "当前角色无权要求整改");
        RepairWorkOrder order = loadVisible(ctx, workOrderId);
        requireStatus(order, RepairWorkOrderStatus.PENDING_ACCEPTANCE);
        return transition(order, order.withStatus(RepairWorkOrderStatus.RECTIFICATION_REQUIRED, false, true, false),
                "REQUEST_RECTIFICATION", command.remark());
    }

    @Transactional
    public RepairWorkOrder evaluate(Long workOrderId, EvaluateRepairCommand command) {
        UserContext ctx = requireOwnerContext();
        RepairWorkOrder order = findMine(workOrderId);
        requireStatus(order, RepairWorkOrderStatus.COMPLETED);
        if (!ctx.accountId().equals(order.reporterAccountId())) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, "只有报修人可评价该工单");
        }
        if (command.satisfactionScore() == null || command.satisfactionScore() < 1 || command.satisfactionScore() > 5) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "satisfactionScore 必须为 1-5");
        }
        return transition(order, order.withEvaluation(command.satisfactionScore(), trim(command.comment()),
                RepairWorkOrderStatus.EVALUATED), "OWNER_EVALUATE", "业主评价");
    }

    @Transactional
    public RepairWorkOrder archive(Long workOrderId, RepairActionCommand command) {
        UserContext ctx = requireRole(GOVERNANCE_ROLES, "当前角色无权归档工单");
        RepairWorkOrder order = loadVisible(ctx, workOrderId);
        requireStatus(order, RepairWorkOrderStatus.COMPLETED, RepairWorkOrderStatus.EVALUATED);
        return transition(order, order.withStatus(RepairWorkOrderStatus.ARCHIVED, false, true, false),
                "ARCHIVE", command.remark());
    }

    private LocalDecisionTally tallyLocalDecision(CompleteRepairLocalDecisionCommand command,
                                                  RepairLocalDecision decision) {
        List<CompleteRepairLocalDecisionCommand.Entry> submitted = command.entries() == null
                ? List.of()
                : List.copyOf(command.entries());
        return tallyLocalDecisionEntries(submitted, decision);
    }

    private LocalDecisionTally tallyLocalDecisionEntries(
            List<CompleteRepairLocalDecisionCommand.Entry> submitted,
            RepairLocalDecision decision) {
        List<RepairDecisionRoom> rooms = repository.listDecisionRooms(
                decision.tenantId(), decision.buildingId(), decision.unitName());
        if (rooms.size() != decision.totalOwnerCount()) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "接龙分母已变化，请重新发起接龙");
        }
        Map<Long, CompleteRepairLocalDecisionCommand.Entry> byRoom = new LinkedHashMap<>();
        for (CompleteRepairLocalDecisionCommand.Entry entry : submitted) {
            if (entry == null || entry.roomId() == null || byRoom.putIfAbsent(entry.roomId(), entry) != null) {
                throw new RepairWorkOrderApplicationException(PARAM_INVALID, "表决逐产权人明细不能为空或重复");
            }
        }
        if (byRoom.size() != rooms.size() || rooms.stream().anyMatch(room -> !byRoom.containsKey(room.roomId()))) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID,
                    "必须完整登记范围内每个产权人的表决选择，包括未参与人");
        }

        int participatedCount = 0;
        BigDecimal participatedArea = BigDecimal.ZERO;
        int agreeCount = 0;
        BigDecimal agreeArea = BigDecimal.ZERO;
        int disagreeCount = 0;
        BigDecimal disagreeArea = BigDecimal.ZERO;
        int abstainCount = 0;
        BigDecimal abstainArea = BigDecimal.ZERO;
        int invalidCount = 0;
        BigDecimal invalidArea = BigDecimal.ZERO;
        List<RepairSolitaireEntry> verifiedEntries = new ArrayList<>();

        for (RepairDecisionRoom room : rooms) {
            CompleteRepairLocalDecisionCommand.Entry submittedEntry = byRoom.get(room.roomId());
            RepairVoteChoice choice = parseEnum(submittedEntry.choice(), RepairVoteChoice.class, null, "choice");
            if (choice == null || choice == RepairVoteChoice.CONFLICTED) {
                throw new RepairWorkOrderApplicationException(PARAM_INVALID, "存在冲突选择时必须先核验，不能完成接龙");
            }
            verifiedEntries.add(new RepairSolitaireEntry(
                    room.roomId(), null, choice, room.buildArea(), trim(submittedEntry.originalText())));
            if (choice == RepairVoteChoice.NOT_VOTED) {
                continue;
            }
            participatedCount++;
            participatedArea = participatedArea.add(room.buildArea());
            switch (choice) {
                case AGREE -> {
                    agreeCount++;
                    agreeArea = agreeArea.add(room.buildArea());
                }
                case DISAGREE -> {
                    disagreeCount++;
                    disagreeArea = disagreeArea.add(room.buildArea());
                }
                case ABSTAIN -> {
                    abstainCount++;
                    abstainArea = abstainArea.add(room.buildArea());
                }
                case INVALID -> {
                    invalidCount++;
                    invalidArea = invalidArea.add(room.buildArea());
                }
                default -> throw new RepairWorkOrderApplicationException(PARAM_INVALID, "接龙选择未核验");
            }
        }
        return new LocalDecisionTally(participatedCount, participatedArea, agreeCount, agreeArea,
                disagreeCount, disagreeArea, abstainCount, abstainArea, invalidCount, invalidArea,
                verifiedEntries);
    }

    private record LocalDecisionTally(
            int participatedOwnerCount,
            BigDecimal participatedArea,
            int agreeOwnerCount,
            BigDecimal agreeArea,
            int disagreeOwnerCount,
            BigDecimal disagreeArea,
            int abstainOwnerCount,
            BigDecimal abstainArea,
            int invalidOwnerCount,
            BigDecimal invalidArea,
            List<RepairSolitaireEntry> entries
    ) {
    }

    private void validateApprovalAttachments(List<RepairApprovalAttachment> attachments,
                                             boolean requireSolitaireEvidence) {
        if (attachments.isEmpty()) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "报审文件包至少包含一份附件");
        }
        Set<Integer> sortOrders = new HashSet<>();
        boolean hasSolitaireEvidence = false;
        for (RepairApprovalAttachment attachment : attachments) {
            if (attachment == null || trim(attachment.attachmentType()) == null
                    || trim(attachment.attachmentHash()) == null || attachment.sortOrder() < 1
                    || !sortOrders.add(attachment.sortOrder())) {
                throw new RepairWorkOrderApplicationException(PARAM_INVALID, "报审附件类型、哈希和顺序必须完整且不可重复");
            }
            if ("SOLITAIRE_SCREENSHOT".equals(attachment.attachmentType())) {
                hasSolitaireEvidence = true;
            }
        }
        if (requireSolitaireEvidence && !hasSolitaireEvidence) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "楼栋维修报审包必须包含接龙截图附件");
        }
    }

    private String attachmentHash(RepairAttachment attachment) {
        return "attachment:%d:%s".formatted(attachment.attachmentId(), attachment.etag());
    }

    private void validateContractSignatures(List<RepairContractSignature> signatures) {
        Set<String> requiredParties = Set.of("OWNERS_ASSEMBLY_OR_GROUP", "PROPERTY", "SUPPLIER");
        Set<String> parties = new HashSet<>();
        for (RepairContractSignature signature : signatures) {
            if (signature == null || !requiredParties.contains(signature.partyType())
                    || !parties.add(signature.partyType()) || trim(signature.signerName()) == null
                    || !Set.of("ELECTRONIC", "PAPER_SCAN").contains(signature.signatureMethod())
                    || trim(signature.signatureFileHash()) == null || signature.signedAt() == null) {
                throw new RepairWorkOrderApplicationException(PARAM_INVALID, "三方合同签署记录不完整或重复");
            }
        }
        if (!parties.equals(requiredParties)) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "业主组织、物业、供应商三方必须全部签署");
        }
    }

    private void assertAcceptanceParticipantRole(UserContext ctx, String participantType) {
        if ("OWNER_REPRESENTATIVE".equals(ctx.roleKey())
                && !"OWNER_REPRESENTATIVE".equals(participantType)) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, "楼组长只能提交本人的验收记录");
        }
        if (("COMMITTEE_DIRECTOR".equals(ctx.roleKey()) || "COMMITTEE_MEMBER".equals(ctx.roleKey()))
                && !"COMMITTEE_REPRESENTATIVE".equals(participantType)) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, "业委会成员只能作为可选见证人验收");
        }
    }

    private String acceptanceSummaryPayload(RepairAcceptanceSummary summary) {
        return jsonPayload(Map.of(
                "affectedRoomCount", summary.affectedRoomCount(),
                "passedAffectedRoomCount", summary.passedAffectedRoomCount(),
                "rectificationCount", summary.rectificationCount(),
                "unreachableCount", summary.unreachableCount(),
                "ownerRepresentativePassed", summary.ownerRepresentativePassed(),
                "propertyRepresentativePassed", summary.propertyRepresentativePassed()));
    }

    private String governanceConfirmPosition(UserContext ctx, RepairWorkOrder order) {
        String position = repository.findActiveCommitteePosition(order.tenantId(), ctx.userId()).orElse(null);
        if ("DIRECTOR".equals(position) || "VICE_DIRECTOR".equals(position)) {
            return position;
        }
        if ("COMMITTEE_DIRECTOR".equals(ctx.roleKey())) {
            return "DIRECTOR";
        }
        throw new RepairWorkOrderApplicationException(FORBIDDEN, "仅业委会主任或副主任可确认维修报批");
    }

    private String jsonPayload(Map<String, ?> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "审计载荷序列化失败");
        }
    }

    private RepairWorkOrder duplicate(UserContext ctx, RepairSpaceScope scope, Long roomId, Long buildingId, String title) {
        return repository.findDuplicate(ctx.tenantId(), ctx.accountId(), scope, roomId, buildingId, title,
                LocalDateTime.now().minusMinutes(DEDUP_MINUTES)).orElse(null);
    }

    private RepairWorkOrder transition(RepairWorkOrder before, RepairWorkOrder after, String action, String remark) {
        return transition(before, after, action, remark, "{}");
    }

    private RepairWorkOrder transition(RepairWorkOrder before,
                                       RepairWorkOrder after,
                                       String action,
                                       String remark,
                                       String payloadJson) {
        int updated = repository.update(after);
        if (updated != 1) {
            throw new RepairWorkOrderApplicationException(INVALID_STATUS, "工单已被并发修改，请刷新后重试");
        }
        RepairWorkOrder persisted = repository.findById(before.workOrderId())
                .orElseThrow(() -> new RepairWorkOrderApplicationException(NOT_FOUND, "工单不存在"));
        event(persisted, action, before.status(), persisted.status(), trim(remark), payloadJson);
        return persisted;
    }

    private void event(RepairWorkOrder order,
                       String action,
                       RepairWorkOrderStatus fromStatus,
                       RepairWorkOrderStatus toStatus,
                       String remark) {
        event(order, action, fromStatus, toStatus, remark, "{}");
    }

    private void event(RepairWorkOrder order,
                       String action,
                       RepairWorkOrderStatus fromStatus,
                       RepairWorkOrderStatus toStatus,
                       String remark,
                       String payloadJson) {
        UserContext ctx = userContextHolder.current();
        repository.insertEvent(new RepairWorkOrderEvent(
                null,
                order.workOrderId(),
                order.tenantId(),
                action,
                fromStatus,
                toStatus,
                ctx == null ? null : ctx.accountId(),
                ctx == null || ctx.identityType() == null ? null : ctx.identityType().name(),
                ctx == null ? null : ctx.activeIdentityId(),
                remark,
                trim(payloadJson) == null ? "{}" : payloadJson,
                null));
    }

    private String fieldEvidencePayload(String fieldSupplement, List<String> evidenceImagesBase64) {
        String supplement = trim(fieldSupplement);
        Map<String, Object> payload = new LinkedHashMap<>();
        if (supplement != null) {
            payload.put("fieldSupplement", supplement);
        }
        return evidencePayload(payload, evidenceImagesBase64);
    }

    private String attachmentEvidencePayload(String fieldSupplement, List<RepairAttachment> attachments) {
        Map<String, Object> payload = new LinkedHashMap<>();
        String supplement = trim(fieldSupplement);
        if (supplement != null) {
            payload.put("fieldSupplement", supplement);
        }
        payload.put("attachments", attachmentAuditItems(attachments));
        return jsonPayload(payload);
    }

    private String surveyEvidencePayload(String surveySummary,
                                         String riskLevel,
                                         List<RepairAttachment> attachments) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("surveySummary", surveySummary);
        payload.put("riskLevel", riskLevel);
        payload.put("attachments", attachmentAuditItems(attachments));
        return jsonPayload(payload);
    }

    private List<Map<String, Object>> attachmentAuditItems(List<RepairAttachment> attachments) {
        return attachments.stream().map(attachment -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("attachmentId", attachment.attachmentId());
            item.put("kind", attachment.kind().name());
            item.put("objectKey", attachment.objectKey());
            item.put("contentType", attachment.contentType());
            item.put("size", attachment.actualSize());
            item.put("etag", attachment.etag());
            return item;
        }).toList();
    }

    private List<RepairAttachment> readyAttachments(RepairWorkOrder order,
                                                     List<Long> attachmentIds,
                                                     RepairAttachmentKind expectedKind,
                                                     int minimum,
                                                     int maximum) {
        List<Long> ids = attachmentIds == null
                ? List.of()
                : attachmentIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.size() < minimum || ids.size() > maximum) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID,
                    expectedKind == RepairAttachmentKind.SURVEY_VIDEO
                            ? "现场初勘最多上传 1 段视频"
                            : "现场证据图片数量必须在 " + minimum + " 至 " + maximum + " 张之间");
        }
        List<RepairAttachment> attachments = attachmentRepository.findByIds(
                ids, order.workOrderId(), order.tenantId());
        if (attachments.size() != ids.size()) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "存在不属于当前工单的现场附件");
        }
        for (RepairAttachment attachment : attachments) {
            if (attachment.kind() != expectedKind || attachment.status() != RepairAttachmentStatus.READY
                    || attachment.actualSize() == null || trim(attachment.etag()) == null) {
                throw new RepairWorkOrderApplicationException(PARAM_INVALID, "现场附件类型或上传状态无效");
            }
        }
        return attachments;
    }

    private void bindAttachments(RepairWorkOrder order, List<RepairAttachment> attachments, String action) {
        if (attachments.isEmpty()) {
            return;
        }
        List<Long> ids = attachments.stream().map(RepairAttachment::attachmentId).toList();
        if (attachmentRepository.markBound(ids, order.workOrderId(), order.tenantId(), action) != ids.size()) {
            throw new RepairWorkOrderApplicationException(INVALID_STATUS, "附件已被其他业务动作绑定，请刷新后重试");
        }
    }

    private String evidencePayload(Map<String, Object> payload, List<String> evidenceImagesBase64) {
        List<String> images = normalizeEvidenceImages(evidenceImagesBase64);
        if (!images.isEmpty()) {
            List<Map<String, String>> evidenceImages = new ArrayList<>();
            for (String image : images) {
                Map<String, String> item = new LinkedHashMap<>();
                item.put("sha256", sha256(image));
                item.put("base64", image);
                evidenceImages.add(item);
            }
            payload.put("evidenceImages", evidenceImages);
        }
        if (payload.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "现场证据序列化失败");
        }
    }

    private List<String> normalizeEvidenceImages(List<String> images) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String image : images) {
            String value = trim(image);
            if (value == null) {
                continue;
            }
            if (value.length() > MAX_EVIDENCE_IMAGE_BASE64_LENGTH) {
                throw new RepairWorkOrderApplicationException(PARAM_INVALID, "单张现场证据图片过大");
            }
            normalized.add(value);
        }
        if (normalized.size() > MAX_EVIDENCE_IMAGE_COUNT) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "现场证据图片最多上传 3 张");
        }
        return normalized;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private record GovernanceSealResult(
            Long electronicSealId,
            String sealName,
            String sealType,
            Long sourceAttachmentId,
            RepairAttachment sealedAttachment,
            String sourceFileHash,
            String sealedFileHash,
            String providerTransactionId,
            String certificateSerial,
            String verificationStatus,
            boolean simulated,
            String generatedObjectKey
    ) {
    }

    private void assertHandoverUnlocked(RepairWorkOrder order) {
        tenantTermLockGuard.lockedElectionForLargeAmount(order.tenantId(), order.planBudget())
                .ifPresent(electionId -> {
                    throw new RepairWorkOrderApplicationException(HANDOVER_LOCKED,
                            "换届锁定中，大额维修方案已熔断 electionSubjectId=" + electionId
                                    + " amount=" + order.planBudget());
                });
    }

    private RepairPlanningPolicy planningPolicy(Long tenantId) {
        return communitySettingsRepository.findCommunity(tenantId)
                .map(community -> new RepairPlanningPolicy(
                        community.repairEstimateRequired(),
                        community.buildingRepairDefaultDecisionChannel()))
                .orElseGet(() -> new RepairPlanningPolicy(false, RepairLocalDecisionChannel.WECHAT.name()));
    }

    private RepairWorkOrder loadVisible(UserContext ctx, Long workOrderId) {
        RepairWorkOrder order = repository.findById(workOrderId)
                .orElseThrow(() -> new RepairWorkOrderApplicationException(NOT_FOUND,
                        "工单不存在 workOrderId=" + workOrderId));
        assertVisible(ctx, order);
        return order;
    }

    private RepairWorkOrder loadForSupplier(UserContext ctx, Long workOrderId) {
        if (ctx.deptId() == null) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, "供应商账号未绑定企业组织");
        }
        RepairWorkOrder order = repository.findById(workOrderId)
                .orElseThrow(() -> new RepairWorkOrderApplicationException(NOT_FOUND,
                        "工单不存在 workOrderId=" + workOrderId));
        if (!repository.supplierCanAccess(order.workOrderId(), order.tenantId(), ctx.deptId())) {
            throw new RepairWorkOrderApplicationException(NOT_FOUND, "供应商未收到该工单邀价");
        }
        return order;
    }

    private void assertVisible(UserContext ctx, RepairWorkOrder order) {
        boolean ownerGroup = "OWNER_GROUP".equals(ctx.dataScopeType() == null ? null : ctx.dataScopeType().getValue());
        if (!ownerGroup && ctx.tenantId() != null && !ctx.tenantId().equals(order.tenantId())) {
            throw new RepairWorkOrderApplicationException(NOT_FOUND, "工单不在当前租户范围内");
        }
        if (ownerGroup) {
            boolean assigned = ctx.userId() != null && ctx.userId().equals(order.assignedUserId());
            boolean buildingAllowed = order.buildingId() != null
                    && ctx.authorizedBuildingScopes().contains(
                            new WorkIdentityBuildingScope(order.tenantId(), order.buildingId()));
            if (!assigned && !buildingAllowed) {
                throw new RepairWorkOrderApplicationException(NOT_FOUND, "工单不在当前责任田范围内");
            }
        }
    }

    private UserContext requireOwnerContext() {
        UserContext ctx = userContextHolder.current();
        if (ctx == null || !ctx.isCUser() || ctx.uid() == null || ctx.tenantId() == null) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, "未识别到业主身份，禁止访问报修");
        }
        if (ctx.authLevel() == null || ctx.authLevel().getValue() < 2) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, "业主实名等级不足，禁止提交报修");
        }
        return ctx;
    }

    private UserContext requireSysContext() {
        UserContext ctx = userContextHolder.current();
        if (ctx == null || !ctx.isSysUser() || ctx.roleKey() == null) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, "未识别到管理端工作身份");
        }
        return ctx;
    }

    private UserContext requireRole(Set<String> roles, String message) {
        UserContext ctx = requireSysContext();
        if (!roles.contains(ctx.roleKey())) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, message + " roleKey=" + ctx.roleKey());
        }
        return ctx;
    }

    private Long requireTenant(UserContext ctx) {
        if (ctx.tenantId() == null) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, "未识别到租户上下文");
        }
        return ctx.tenantId();
    }

    private void requireStatus(RepairWorkOrder order, RepairWorkOrderStatus... allowed) {
        for (RepairWorkOrderStatus status : allowed) {
            if (order.status() == status) {
                return;
            }
        }
        throw new RepairWorkOrderApplicationException(INVALID_STATUS,
                "当前状态不允许该动作 status=" + order.status());
    }

    private String requireText(String value, String field) {
        String trimmed = trim(value);
        if (trimmed == null) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, field + " 必填");
        }
        return trimmed;
    }

    private String requireAllowed(String value, String field, Set<String> allowed) {
        String normalized = requireText(value, field).toUpperCase();
        if (!allowed.contains(normalized)) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, field + " 取值不合法");
        }
        return normalized;
    }

    private String normalizeCategory(String value) {
        String category = trim(value);
        if (category == null) {
            return null;
        }
        try {
            return RepairCategory.fromInput(category).name();
        } catch (IllegalArgumentException ex) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "不支持的维修专业：" + category);
        }
    }

    private RepairPublicAreaScope resolvePublicAreaScope(String value,
                                                         Long buildingId,
                                                         String locationText) {
        String normalized = trim(value);
        if (normalized == null) {
            return buildingId == null ? null : RepairPublicAreaScope.BUILDING;
        }
        RepairPublicAreaScope scope;
        try {
            scope = RepairPublicAreaScope.valueOf(normalized.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID,
                    "publicAreaScope 仅支持 BUILDING、COMMUNITY");
        }
        if (scope == RepairPublicAreaScope.BUILDING && buildingId == null) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID,
                    "楼栋公共部位报修必须选择维修楼栋");
        }
        if (scope == RepairPublicAreaScope.COMMUNITY && buildingId != null) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID,
                    "小区公共区域报修不能绑定具体楼栋");
        }
        if (scope == RepairPublicAreaScope.COMMUNITY && trim(locationText) == null) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID,
                    "小区公共区域报修必须填写具体位置");
        }
        return scope;
    }

    private void validateInspectionLocation(RepairWorkOrder order,
                                            Long buildingId,
                                            Long roomId,
                                            String locationText,
                                            RepairPublicAreaScope publicAreaScope) {
        if (order.spaceScope() == RepairSpaceScope.PUBLIC && publicAreaScope == null) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID,
                    "公共区域勘验必须确认楼栋公共部位或小区公共区域");
        }
        if (order.spaceScope() == RepairSpaceScope.PRIVATE && buildingId == null && roomId == null) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID,
                    "buildingId 或 roomId 至少填写一个");
        }
        if (buildingId != null && !repository.buildingExists(order.tenantId(), buildingId)) {
            throw new RepairWorkOrderApplicationException(BUILDING_NOT_IN_SCOPE,
                    "楼栋不在当前小区范围内 buildingId=" + buildingId);
        }
        if (roomId != null && !repository.roomExists(order.tenantId(), buildingId, roomId)) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID,
                    "房号不在当前小区或楼栋范围内 roomId=" + roomId);
        }
        if (publicAreaScope == RepairPublicAreaScope.COMMUNITY && trim(locationText) == null) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID,
                    "小区公共区域勘验必须填写具体位置");
        }
    }

    private void assertLocationComplete(RepairWorkOrder order) {
        if (order.needManualLocation()) {
            throw new RepairWorkOrderApplicationException(LOCATION_NOT_VERIFIED,
                    "工单位置仍需现场补充，禁止提交勘验");
        }
        boolean communityLocationComplete = order.spaceScope() == RepairSpaceScope.PUBLIC
                && order.publicAreaScope() == RepairPublicAreaScope.COMMUNITY
                && trim(order.locationText()) != null;
        if (!communityLocationComplete && order.buildingId() == null && order.roomId() == null) {
            throw new RepairWorkOrderApplicationException(LOCATION_NOT_VERIFIED,
                    "工单未绑定楼栋或房屋，禁止提交勘验");
        }
    }

    private void validateFundSourceForPublicArea(RepairWorkOrder order, String fundSource) {
        if (order.spaceScope() != RepairSpaceScope.PUBLIC || order.publicAreaScope() == null) {
            return;
        }
        if (order.publicAreaScope() == RepairPublicAreaScope.BUILDING
                && FUND_COMMUNITY_MAINTENANCE.equals(fundSource)) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID,
                    "楼栋公共部位维修不能使用小区公共维修资金");
        }
        if (order.publicAreaScope() == RepairPublicAreaScope.COMMUNITY
                && FUND_BUILDING_MAINTENANCE.equals(fundSource)) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID,
                    "小区公共区域维修不能使用楼栋维修资金");
        }
    }

    private <E extends Enum<E>> E parseEnum(String value,
                                            Class<E> enumType,
                                            E defaultValue,
                                            String field) {
        String normalized = trim(value);
        if (normalized == null) {
            return defaultValue;
        }
        try {
            return Enum.valueOf(enumType, normalized.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, field + " 取值不合法");
        }
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
