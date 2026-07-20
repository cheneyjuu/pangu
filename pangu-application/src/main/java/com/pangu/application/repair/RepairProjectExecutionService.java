// 关联业务：编排需业主侧定商的维修工程审价、施工合同、开工、施工取证、材料进场和结构化竣工结算；直接责任履行不进入该合同链路。
package com.pangu.application.repair;

import com.pangu.application.repair.RepairProjectApplicationSupport.Context;
import com.pangu.application.repair.command.RepairProjectExecutionCommands.RecordContract;
import com.pangu.application.repair.command.RepairProjectExecutionCommands.RecordCostReview;
import com.pangu.application.repair.command.RepairProjectExecutionCommands.StartWork;
import com.pangu.application.repair.command.RepairProjectExecutionCommands.SubmitExecutionRecord;
import com.pangu.application.repair.command.RepairProjectExecutionCommands.SubmitMaterialInspection;
import com.pangu.application.repair.command.RepairProjectExecutionCommands.SubmitSettlement;
import com.pangu.application.repair.command.RepairProjectExecutionCommands.VerifyExecutionRecord;
import com.pangu.application.repair.command.RepairProjectExecutionCommands.VerifyMaterialInspection;
import com.pangu.application.repair.command.RepairProjectExecutionCommands.VerifySettlement;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.model.propertyservice.PropertyServiceEnterprise;
import com.pangu.domain.model.propertyservice.PropertyServiceOrganization;
import com.pangu.domain.model.repair.RepairProject.Attachment;
import com.pangu.domain.model.repair.RepairProject.AllocationRoom;
import com.pangu.domain.model.repair.RepairProject.EvidenceRequirement;
import com.pangu.domain.model.repair.RepairProject.EvidenceStage;
import com.pangu.domain.model.repair.RepairAcceptancePartyRole;
import com.pangu.domain.model.repair.RepairProject.ResponsibilityDeterminationStatus;
import com.pangu.domain.model.repair.RepairProject.ResponsibilityPath;
import com.pangu.domain.model.repair.RepairProject.Status;
import com.pangu.domain.model.repair.RepairProjectExecution.AcceptancePolicy;
import com.pangu.domain.model.repair.RepairProjectExecution.AcceptanceRequirement;
import com.pangu.domain.model.repair.RepairProjectExecution.Contract;
import com.pangu.domain.model.repair.RepairProjectExecution.ContractPartyType;
import com.pangu.domain.model.repair.RepairProjectExecution.ContractSignature;
import com.pangu.domain.model.repair.RepairProjectExecution.ContractStatus;
import com.pangu.domain.model.repair.RepairProjectExecution.CostReview;
import com.pangu.domain.model.repair.RepairProjectExecution.Details;
import com.pangu.domain.model.repair.RepairProjectExecution.ExecutionRecord;
import com.pangu.domain.model.repair.RepairProjectExecution.MaterialInspection;
import com.pangu.domain.model.repair.RepairProjectExecution.Settlement;
import com.pangu.domain.model.repair.RepairProjectExecution.SettlementItem;
import com.pangu.domain.model.repair.RepairProjectExecution.SettlementStatus;
import com.pangu.domain.model.repair.RepairProjectExecution.SignatureMethod;
import com.pangu.domain.model.repair.RepairProjectExecution.VerificationStatus;
import com.pangu.domain.model.repair.RepairProjectSourcing.Selection;
import com.pangu.domain.model.repair.RepairProjectGovernance.BuildingProcess;
import com.pangu.domain.model.repair.RepairWorkflowType;
import com.pangu.domain.repository.RepairProjectExecutionRepository;
import com.pangu.domain.repository.RepairProjectSourcingRepository;
import com.pangu.domain.repository.RepairProjectGovernanceRepository;
import com.pangu.domain.repository.RepairProjectRepository;
import com.pangu.domain.repository.RepairWorkOrderRepository;
import com.pangu.domain.repository.PropertyServiceOrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RepairProjectExecutionService {

    private static final Set<String> PROPERTY_ROLES = Set.of("PROPERTY_MANAGER", "PROPERTY_STAFF");
    private static final Set<String> CONTRACT_ROLES = Set.of("PROPERTY_MANAGER");
    private static final Set<String> COMMITTEE_ROLES = Set.of("COMMITTEE_DIRECTOR", "COMMITTEE_MEMBER");
    private static final Set<String> SUPPLIER_ROLES = Set.of("SERVICE_PROVIDER_MANAGER", "SERVICE_PROVIDER_STAFF");
    private static final Set<String> EXECUTION_SUBMIT_ROLES = Set.of(
            "PROPERTY_MANAGER", "PROPERTY_STAFF", "SERVICE_PROVIDER_MANAGER", "SERVICE_PROVIDER_STAFF");
    private static final Set<String> REVIEW_MODES = Set.of(
            "INTERNAL_PRICE_REVIEW", "THIRD_PARTY_AUDIT", "NOT_REQUIRED");

    private final RepairProjectApplicationSupport support;
    private final RepairProjectRepository projectRepository;
    private final RepairProjectExecutionRepository executionRepository;
    private final RepairProjectSourcingRepository sourcingRepository;
    private final RepairProjectGovernanceRepository governanceRepository;
    private final RepairWorkOrderRepository workOrderRepository;
    private final PropertyServiceOrganizationRepository propertyServiceOrganizationRepository;

    @Transactional
    public CostReview recordCostReview(Long projectId, RecordCostReview command) {
        UserContext actor = support.requireSysActor(COMMITTEE_ROLES, "仅业委会可记录全小区维修审价");
        require(command != null && command.expectedProjectVersion() != null,
                "expectedProjectVersion 必填");
        Context context = support.loadForUpdate(projectId, actor.tenantId(), Status.AUTHORIZED);
        requireVersion(context, command.expectedProjectVersion());
        if (context.project().workflowType() != RepairWorkflowType.COMMUNITY_PUBLIC_REPAIR) {
            throw support.invalid("楼栋维修审价必须在楼栋治理流程中完成，不能重复登记项目审价");
        }
        if (executionRepository.findCostReview(projectId, context.plan().planId(), actor.tenantId()).isPresent()) {
            throw support.conflict("当前锁定方案已完成审价");
        }
        String mode = allowed(command.reviewMode(), REVIEW_MODES, "reviewMode");
        requirePositive(command.reviewedAmount(), "reviewedAmount");
        if (command.reviewedAmount().compareTo(context.plan().budgetTotal()) > 0) {
            throw support.invalid("审价金额超过锁定方案预算，必须形成新方案并重新履行业主大会程序");
        }
        if (context.plan().priceReviewRequired() && "NOT_REQUIRED".equals(mode)) {
            throw support.invalid("锁定方案要求审价，不能登记为无需审价");
        }
        if (!"NOT_REQUIRED".equals(mode)) {
            document(support.attachment(context, command.reportAttachmentId(), "审价报告"), "审价报告");
        }
        CostReview review = executionRepository.insertCostReview(new CostReview(
                null, projectId, context.plan().planId(), actor.tenantId(), mode,
                command.reviewedAmount().setScale(2, RoundingMode.HALF_UP),
                command.reportAttachmentId(), actor.userId(), null));
        support.event(context, actor, "PROJECT_COST_REVIEW_RECORDED", Map.of(
                "reviewId", review.reviewId(), "reviewMode", mode,
                "reviewedAmount", review.reviewedAmount()));
        return review;
    }

    @Transactional
    public Contract recordEffectiveContract(Long projectId, RecordContract command) {
        UserContext actor = support.requireSysActor(CONTRACT_ROLES, "仅物业经理可组织并归档维修施工合同");
        require(command != null && command.expectedProjectVersion() != null,
                "expectedProjectVersion 必填");
        Context context = support.loadForUpdate(projectId, actor.tenantId(), Status.AUTHORIZED);
        requireVersion(context, command.expectedProjectVersion());
        rejectDirectResponsibilityContract(context);
        if (executionRepository.findContract(projectId, actor.tenantId()).isPresent()) {
            throw support.conflict("当前项目已有生效合同");
        }
        Selection selection = sourcingRepository.findCurrentSelection(
                        projectId, context.plan().planId(), actor.tenantId())
                .orElseThrow(() -> support.invalid("当前锁定方案缺少有效的中选供应商报价"));
        if (!selection.supplierDeptId().equals(command.supplierDeptId())) {
            throw support.invalid("合同施工单位必须与锁定方案的中选供应商一致");
        }
        if (command.supplierDeptId() == null
                || !workOrderRepository.supplierVerified(actor.tenantId(), command.supplierDeptId())) {
            throw support.invalid("施工单位必须是当前小区已核验的供应商组织");
        }
        String legalName = workOrderRepository.findSupplierLegalName(command.supplierDeptId())
                .orElseThrow(() -> support.invalid("施工单位缺少已核验法定名称"));
        if (text(command.supplierName()) != null && !legalName.equals(text(command.supplierName()))) {
            throw support.invalid("施工单位名称必须使用已核验法定名称");
        }
        requirePositive(command.contractAmount(), "contractAmount");
        BigDecimal priceLimit = priceLimit(context);
        if (command.contractAmount().compareTo(context.plan().budgetTotal()) > 0
                || command.contractAmount().compareTo(priceLimit) > 0
                || command.contractAmount().compareTo(selection.quoteAmount()) > 0) {
            throw support.invalid("合同金额超过中选报价、锁定方案预算或有效审价金额");
        }
        PropertyServiceEnterprise propertyEnterprise = requireActivePropertyContractParty(actor);
        Attachment contractAttachment = support.attachment(
                context, command.contractAttachmentId(), "施工合同文件");
        document(contractAttachment, "施工合同文件");
        List<ContractSignature> signatures = validatedSignatures(
                context, command.signatures(), actor, command.supplierDeptId());
        Contract contract = executionRepository.insertContract(new Contract(
                null, projectId, context.plan().planId(), actor.tenantId(), command.supplierDeptId(),
                legalName, command.contractAmount().setScale(2, RoundingMode.HALF_UP),
                context.plan().snapshotHash(), context.project().fundSource(), signingMethod(signatures),
                contractAttachment.attachmentId(), contractAttachment.sha256(),
                ContractStatus.EFFECTIVE, actor.userId(), null, null));
        List<ContractSignature> bound = signatures.stream()
                .map(signature -> new ContractSignature(
                        null, contract.contractId(), signature.partyType(), signature.signerName(),
                        signature.signerUserId(), signature.signatureMethod(), signature.signatureAttachmentId(),
                        signature.signatureFileHash(), signature.signedAt()))
                .toList();
        executionRepository.insertContractSignatures(actor.tenantId(), bound);
        support.advance(context, Status.CONTRACT_EFFECTIVE);
        support.event(context, actor, "PROJECT_CONTRACT_EFFECTIVE", Map.of(
                "contractId", contract.contractId(), "supplierDeptId", contract.supplierDeptId(),
                "contractAmount", contract.contractAmount(), "partyCount", bound.size(),
                "propertyEnterpriseId", propertyEnterprise.enterpriseId(),
                "propertyEnterpriseName", propertyEnterprise.legalName(),
                "selectionId", selection.selectionId(), "quoteId", selection.quoteId()));
        return contract;
    }

    /**
     * 已确认由合同责任方、保修责任方或第三方直接履行的工程，没有业主侧中选供应商这一事实。
     * 先在服务端阻断，避免把缺少中选报价误报成普通合同资料不全。
     */
    private void rejectDirectResponsibilityContract(Context context) {
        projectRepository.findCurrentResponsibilityDetermination(
                        context.project().projectId(), context.project().tenantId())
                .filter(determination -> determination.status() == ResponsibilityDeterminationStatus.CONFIRMED)
                .filter(determination -> determination.responsibilityPath() != ResponsibilityPath.SHARED_COMMON_REPAIR)
                .ifPresent(determination -> {
                    throw support.invalid("当前工程已确认由直接责任方履行，不适用业主侧施工合同归档");
                });
    }

    @Transactional
    public Contract startWork(Long projectId, StartWork command) {
        UserContext actor = support.requireSysActor(PROPERTY_ROLES, "仅物业项目人员可登记开工");
        require(command != null && command.expectedProjectVersion() != null,
                "expectedProjectVersion 必填");
        Context context = support.loadForUpdate(projectId, actor.tenantId(), Status.CONTRACT_EFFECTIVE);
        requireVersion(context, command.expectedProjectVersion());
        Contract contract = executionRepository.findContract(projectId, actor.tenantId())
                .orElseThrow(() -> support.conflict("项目没有已生效施工合同"));
        support.advance(context, Status.IN_PROGRESS);
        support.event(context, actor, "PROJECT_WORK_STARTED", Map.of("contractId", contract.contractId()));
        return contract;
    }

    @Transactional
    public ExecutionRecord submitExecutionRecord(Long projectId, SubmitExecutionRecord command) {
        UserContext actor = support.requireSysActor(EXECUTION_SUBMIT_ROLES, "当前角色无权提交施工过程记录");
        require(command != null && command.stage() != null, "stage 必填");
        Context context = support.loadForUpdate(projectId, actor.tenantId(), Status.IN_PROGRESS);
        assertSupplierMatchesContract(actor, context);
        support.workPoint(context, command.workPointId());
        String description = requiredText(command.description(), "description");
        LocalDateTime occurredAt = command.occurredAt();
        if (occurredAt == null || occurredAt.isAfter(LocalDateTime.now().plusMinutes(5))) {
            throw support.invalid("occurredAt 必填且不能晚于当前时间");
        }
        support.attachments(context, command.attachmentIds(), "施工阶段原始证据");
        ExecutionRecord record = executionRepository.insertExecutionRecord(new ExecutionRecord(
                null, projectId, context.plan().planId(), command.workPointId(), actor.tenantId(),
                command.stage(), description, occurredAt, actor.userId(), null,
                VerificationStatus.PENDING, null, null, command.attachmentIds(), null));
        executionRepository.insertExecutionAttachments(
                record.recordId(), actor.tenantId(), command.attachmentIds());
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("recordId", record.recordId());
        event.put("workPointId", command.workPointId());
        event.put("stage", command.stage().name());
        event.put("attachmentCount", command.attachmentIds().size());
        support.event(context, actor, "PROJECT_EXECUTION_RECORDED", event);
        return executionRepository.findExecutionRecord(record.recordId(), projectId, actor.tenantId()).orElseThrow();
    }

    @Transactional
    public ExecutionRecord verifyExecutionRecord(
            Long projectId, Long recordId, VerifyExecutionRecord command) {
        UserContext actor = support.requireSysActor(PROPERTY_ROLES, "仅物业项目人员可核验施工过程记录");
        require(command != null && EnumSet.of(VerificationStatus.VERIFIED, VerificationStatus.REJECTED)
                        .contains(command.status()),
                "status 必须为 VERIFIED 或 REJECTED");
        Context context = support.loadForUpdate(projectId, actor.tenantId(), Status.IN_PROGRESS);
        ExecutionRecord record = executionRepository.findExecutionRecord(recordId, projectId, actor.tenantId())
                .orElseThrow(() -> support.notFound("施工过程记录不存在"));
        if (command.status() == VerificationStatus.REJECTED && text(command.opinion()) == null) {
            throw support.invalid("驳回施工记录必须填写原因");
        }
        if (executionRepository.verifyExecutionRecord(
                recordId, projectId, actor.tenantId(), command.status().name(),
                actor.userId(), text(command.opinion())) != 1) {
            throw support.conflict("施工过程记录已被核验，请刷新后重试");
        }
        support.event(context, actor, "PROJECT_EXECUTION_VERIFIED", Map.of(
                "recordId", record.recordId(), "status", command.status().name()));
        return executionRepository.findExecutionRecord(recordId, projectId, actor.tenantId()).orElseThrow();
    }

    @Transactional
    public MaterialInspection submitMaterialInspection(
            Long projectId, SubmitMaterialInspection command) {
        UserContext actor = support.requireSysActor(EXECUTION_SUBMIT_ROLES, "当前角色无权提交材料进场记录");
        require(command != null, "材料进场记录不能为空");
        Context context = support.loadForUpdate(projectId, actor.tenantId(), Status.IN_PROGRESS);
        assertSupplierMatchesContract(actor, context);
        support.workPoint(context, command.workPointId());
        requirePositive(command.quantity(), "quantity");
        Attachment qualification = support.attachment(
                context, command.qualificationAttachmentId(), "材料合格证明");
        document(qualification, "材料合格证明");
        support.attachments(context, command.photoAttachmentIds(), "材料进场照片");
        MaterialInspection inspection = executionRepository.insertMaterialInspection(new MaterialInspection(
                null, projectId, context.plan().planId(), command.workPointId(), actor.tenantId(),
                requiredText(command.materialName(), "materialName"), requiredText(command.brand(), "brand"),
                requiredText(command.model(), "model"), requiredText(command.specification(), "specification"),
                command.quantity(), requiredText(command.unit(), "unit"),
                requiredText(command.manufacturer(), "manufacturer"), qualification.attachmentId(),
                command.photoAttachmentIds(), actor.userId(), VerificationStatus.PENDING,
                null, null, null, null));
        executionRepository.insertMaterialPhotos(
                inspection.inspectionId(), actor.tenantId(), command.photoAttachmentIds());
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("inspectionId", inspection.inspectionId());
        event.put("workPointId", command.workPointId());
        event.put("materialName", inspection.materialName());
        support.event(context, actor, "PROJECT_MATERIAL_SUBMITTED", event);
        return executionRepository.findMaterialInspection(
                inspection.inspectionId(), projectId, actor.tenantId()).orElseThrow();
    }

    @Transactional
    public MaterialInspection verifyMaterialInspection(
            Long projectId, Long inspectionId, VerifyMaterialInspection command) {
        UserContext actor = support.requireSysActor(PROPERTY_ROLES, "仅物业项目人员可核验进场材料");
        require(command != null && EnumSet.of(VerificationStatus.VERIFIED, VerificationStatus.REJECTED)
                        .contains(command.status()),
                "status 必须为 VERIFIED 或 REJECTED");
        Context context = support.loadForUpdate(projectId, actor.tenantId(), Status.IN_PROGRESS);
        MaterialInspection inspection = executionRepository.findMaterialInspection(
                        inspectionId, projectId, actor.tenantId())
                .orElseThrow(() -> support.notFound("材料进场记录不存在"));
        if (command.status() == VerificationStatus.REJECTED && text(command.opinion()) == null) {
            throw support.invalid("驳回材料必须填写原因");
        }
        if (executionRepository.verifyMaterialInspection(
                inspectionId, projectId, actor.tenantId(), command.status().name(),
                actor.userId(), text(command.opinion())) != 1) {
            throw support.conflict("材料进场记录已被核验，请刷新后重试");
        }
        support.event(context, actor, "PROJECT_MATERIAL_VERIFIED", Map.of(
                "inspectionId", inspection.inspectionId(), "status", command.status().name()));
        return executionRepository.findMaterialInspection(
                inspectionId, projectId, actor.tenantId()).orElseThrow();
    }

    @Transactional
    public Settlement submitSettlement(Long projectId, SubmitSettlement command) {
        UserContext actor = support.requireSysActor(EXECUTION_SUBMIT_ROLES, "当前角色无权提交竣工结算");
        require(command != null, "结算命令不能为空");
        Context context = support.loadForUpdate(projectId, actor.tenantId(), Status.IN_PROGRESS);
        assertSupplierMatchesContract(actor, context);
        if (executionRepository.findActiveSettlement(projectId, actor.tenantId()).isPresent()) {
            throw support.conflict("项目已有待核验或已核验结算");
        }
        Contract contract = executionRepository.findContract(projectId, actor.tenantId())
                .orElseThrow(() -> support.conflict("项目没有已生效合同"));
        assertExecutionEvidenceComplete(context);
        Attachment settlementAttachment = support.attachment(
                context, command.settlementAttachmentId(), "竣工结算单");
        document(settlementAttachment, "竣工结算单");
        BigDecimal taxRate = settlementTaxRate(command.taxRate());
        List<SettlementItem> items = settlementItems(context, command.items());
        BigDecimal subtotal = items.stream().map(SettlementItem::amountExcludingTax)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal tax = subtotal.multiply(taxRate).movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.add(tax).setScale(2, RoundingMode.HALF_UP);
        if (total.compareTo(contract.contractAmount()) > 0) {
            throw support.invalid("实际结算超过合同金额，必须先完成变更审批或补充协议");
        }
        Settlement settlement = executionRepository.insertSettlement(new Settlement(
                null, projectId, context.plan().planId(), contract.contractId(), actor.tenantId(),
                executionRepository.nextSettlementVersion(projectId, actor.tenantId()),
                SettlementStatus.SUBMITTED, subtotal, taxRate, tax, total, settlementAttachment.attachmentId(),
                actor.userId(), null, null, null, null, items));
        executionRepository.insertSettlementItems(settlement.settlementId(), items);
        support.event(context, actor, "PROJECT_SETTLEMENT_SUBMITTED", Map.of(
                "settlementId", settlement.settlementId(), "detailCount", items.size(),
                "taxRate", taxRate, "totalAmount", total));
        return executionRepository.findActiveSettlement(projectId, actor.tenantId()).orElseThrow();
    }

    @Transactional
    public Settlement verifySettlement(Long projectId, VerifySettlement command) {
        UserContext actor = support.requireSysActor(PROPERTY_ROLES, "仅物业项目人员可核验竣工结算");
        require(command != null && command.expectedProjectVersion() != null,
                "expectedProjectVersion 必填");
        Context context = support.loadForUpdate(projectId, actor.tenantId(), Status.IN_PROGRESS);
        requireVersion(context, command.expectedProjectVersion());
        Settlement settlement = executionRepository.findActiveSettlement(projectId, actor.tenantId())
                .orElseThrow(() -> support.notFound("待核验竣工结算不存在"));
        if (settlement.status() != SettlementStatus.SUBMITTED) {
            throw support.conflict("竣工结算已经核验");
        }
        if (!command.approved() && text(command.opinion()) == null) {
            throw support.invalid("退回结算必须填写原因");
        }
        String next = command.approved() ? SettlementStatus.VERIFIED.name() : SettlementStatus.REJECTED.name();
        if (executionRepository.verifySettlement(
                settlement.settlementId(), projectId, actor.tenantId(), next,
                actor.userId(), text(command.opinion())) != 1) {
            throw support.conflict("竣工结算状态已变化，请刷新后重试");
        }
        if (command.approved()) {
            openAcceptance(context, settlement, actor);
            support.advance(context, Status.PENDING_ACCEPTANCE);
        }
        support.event(context, actor, "PROJECT_SETTLEMENT_VERIFIED", Map.of(
                "settlementId", settlement.settlementId(), "status", next));
        return command.approved()
                ? executionRepository.findActiveSettlement(projectId, actor.tenantId()).orElseThrow()
                : settlement;
    }

    @Transactional(readOnly = true)
    public Details details(Long projectId) {
        UserContext actor = support.requireActor();
        Context context = support.load(projectId, actor.tenantId());
        Contract contract = executionRepository.findContract(projectId, actor.tenantId()).orElse(null);
        var acceptance = executionRepository.findLatestAcceptance(projectId, actor.tenantId()).orElse(null);
        return new Details(
                contract,
                contract == null ? List.of() : executionRepository.listContractSignatures(contract.contractId()),
                executionRepository.findCostReview(projectId, context.plan().planId(), actor.tenantId()).orElse(null),
                executionRepository.listExecutionRecords(projectId, actor.tenantId()),
                executionRepository.listMaterialInspections(projectId, actor.tenantId()),
                executionRepository.findActiveSettlement(projectId, actor.tenantId()).orElse(null),
                executionRepository.findAcceptancePolicy(projectId, actor.tenantId()).orElse(null),
                acceptance,
                acceptance == null ? List.of() : executionRepository.listAcceptanceParties(
                        acceptance.acceptanceId(), actor.tenantId()),
                executionRepository.listPaymentRequests(projectId, actor.tenantId()),
                executionRepository.findCompletionDisclosure(projectId, actor.tenantId()).orElse(null));
    }

    private void openAcceptance(Context context, Settlement settlement, UserContext actor) {
        boolean affectedOwnersParticipate = context.plan().acceptanceRequirements().stream()
                .map(AcceptanceRequirement::eligibleRoles)
                .anyMatch(roles -> roles.contains(RepairAcceptancePartyRole.AFFECTED_OWNER));
        List<AllocationRoom> allocationRooms = affectedOwnersParticipate
                ? projectRepository.listAllocationRooms(context.plan().planId(), context.project().tenantId())
                : List.of();
        int affectedOwnerCount = Math.toIntExact(allocationRooms.stream()
                .map(AllocationRoom::ownerUid).distinct().count());
        int minimumParticipants = affectedOwnersParticipate
                ? context.plan().minimumAffectedOwnerAcceptors() : 0;
        if (affectedOwnersParticipate && (affectedOwnerCount == 0 || minimumParticipants > affectedOwnerCount)) {
            throw support.invalid("实施方案约定受影响业主参与验收，但费用承担房屋中没有足够的已核验业主");
        }
        int minimumApprovals = minimumAffectedOwnerApprovals(context, minimumParticipants);
        String policyHash = sha256(String.join("|",
                context.plan().snapshotHash(), settlement.settlementId().toString(),
                context.project().workflowType().name(), context.plan().acceptanceMethod(),
                acceptanceRequirementHashSource(context.plan().acceptanceRequirements()),
                context.plan().acceptanceFinalizerRoles().stream().map(Enum::name).sorted().toList().toString(),
                context.plan().acceptanceBasisAttachmentIds().toString(), context.plan().acceptanceBasisSummary(),
                Integer.toString(affectedOwnerCount),
                Integer.toString(minimumParticipants),
                String.valueOf(context.plan().affectedOwnerPassRule()),
                String.valueOf(context.plan().affectedOwnerApprovalRatio())));
        AcceptancePolicy policy = executionRepository.insertAcceptancePolicy(new AcceptancePolicy(
                null, context.project().projectId(), context.plan().planId(), context.project().tenantId(),
                context.project().workflowType(), policyHash, context.plan().acceptanceMethod(),
                context.plan().acceptanceRequirements(), context.plan().acceptanceFinalizerRoles(),
                context.plan().acceptanceBasisAttachmentIds(), context.plan().acceptanceBasisSummary(),
                affectedOwnerCount, minimumParticipants,
                context.plan().affectedOwnerPassRule(), context.plan().affectedOwnerApprovalRatio()),
                minimumApprovals, actor.userId());
        if (affectedOwnersParticipate) {
            executionRepository.snapshotAcceptanceAffectedOwners(
                    policy.policyId(), context.plan().planId(), context.project().tenantId());
        }
        executionRepository.startAcceptance(
                context.project().projectId(), context.project().tenantId(), policy.policyId(),
                settlement.settlementId(), actor.userId());
    }

    private int minimumAffectedOwnerApprovals(Context context, int minimumParticipants) {
        if (minimumParticipants == 0) {
            return 0;
        }
        if (context.plan().affectedOwnerPassRule()
                == com.pangu.domain.model.repair.RepairProject.AffectedOwnerPassRule.ALL) {
            return minimumParticipants;
        }
        return BigDecimal.valueOf(minimumParticipants)
                .multiply(context.plan().affectedOwnerApprovalRatio())
                .setScale(0, RoundingMode.CEILING).intValueExact();
    }

    private String acceptanceRequirementHashSource(List<AcceptanceRequirement> requirements) {
        return requirements.stream()
                .sorted(Comparator.comparing(AcceptanceRequirement::requirementCode))
                .map(requirement -> requirement.requirementCode() + ":" + requirement.businessName() + ":"
                        + requirement.eligibleRoles().stream().map(Enum::name).sorted().toList()
                        + ":" + requirement.minimumPassingCount() + ":" + requirement.evidenceRequired())
                .toList().toString();
    }

    private void assertExecutionEvidenceComplete(Context context) {
        List<ExecutionRecord> records = executionRepository.listExecutionRecords(
                context.project().projectId(), context.project().tenantId());
        Set<EvidenceStage> verifiedStages = new LinkedHashSet<>();
        records.stream()
                .filter(record -> record.verificationStatus() == VerificationStatus.VERIFIED)
                .map(ExecutionRecord::stage)
                .forEach(verifiedStages::add);
        for (EvidenceRequirement requirement : context.plan().evidenceRequirements()) {
            if (requirement.required()
                    && requirement.stage() != EvidenceStage.ACCEPTANCE
                    && !verifiedStages.contains(requirement.stage())) {
                throw support.invalid("缺少已核验施工证据 stage=" + requirement.stage());
            }
        }
        if (context.plan().evidenceRequirements().stream()
                .anyMatch(requirement -> requirement.required()
                        && requirement.stage() == EvidenceStage.MATERIAL_ENTRY)
                && executionRepository.listMaterialInspections(
                context.project().projectId(), context.project().tenantId()).stream()
                .noneMatch(item -> item.status() == VerificationStatus.VERIFIED)) {
            throw support.invalid("缺少已核验材料进场记录");
        }
    }

    private List<SettlementItem> settlementItems(Context context, List<SubmitSettlement.Item> submitted) {
        require(submitted != null && !submitted.isEmpty(), "结算明细不能为空");
        List<SettlementItem> result = new ArrayList<>();
        for (SubmitSettlement.Item item : submitted) {
            require(item != null, "结算明细不能为空");
            support.workPoint(context, item.workPointId());
            requireNonNegative(item.actualQuantity(), "actualQuantity");
            requireNonNegative(item.actualUnitPrice(), "actualUnitPrice");
            BigDecimal excludingTax = item.actualQuantity().multiply(item.actualUnitPrice())
                    .setScale(2, RoundingMode.HALF_UP);
            result.add(new SettlementItem(
                    null, null, item.workPointId(), item.actualQuantity(), requiredText(item.unit(), "unit"),
                    item.actualUnitPrice(), excludingTax, text(item.varianceReason())));
        }
        return result;
    }

    private BigDecimal settlementTaxRate(BigDecimal value) {
        require(value != null && value.compareTo(BigDecimal.ZERO) >= 0
                && value.compareTo(new BigDecimal("100")) <= 0, "taxRate 必须在 0 到 100 之间");
        try {
            return value.setScale(3, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw support.invalid("taxRate 最多保留 3 位小数");
        }
    }

    private List<ContractSignature> validatedSignatures(
            Context context, List<RecordContract.Signature> submitted,
            UserContext actor, Long supplierDeptId) {
        Set<ContractPartyType> requiredParties = requiredContractParties(context.project().workflowType());
        require(submitted != null, "合同签署方不能为空");
        Map<ContractPartyType, ContractSignature> signatures = new LinkedHashMap<>();
        for (RecordContract.Signature source : submitted) {
            require(source != null && source.partyType() != null && source.signatureMethod() != null,
                    "合同签署方和签署方式必填");
            Attachment attachment = support.attachment(
                    context, source.signatureAttachmentId(), "合同签署页");
            document(attachment, "合同签署页");
            LocalDateTime signedAt = source.signedAt();
            if (signedAt == null || signedAt.isAfter(LocalDateTime.now().plusMinutes(5))) {
                throw support.invalid("signedAt 必填且不能晚于当前时间");
            }
            assertSignerIdentity(source, actor, supplierDeptId);
            ContractSignature signature = new ContractSignature(
                    null, null, source.partyType(), requiredText(source.signerName(), "signerName"),
                    source.signerUserId(), source.signatureMethod(), attachment.attachmentId(),
                    attachment.sha256(), signedAt);
            if (signatures.put(source.partyType(), signature) != null) {
                throw support.invalid("合同签署方不能重复 partyType=" + source.partyType());
            }
        }
        if (!signatures.keySet().equals(requiredParties)) {
            throw support.invalid("合同签署方不符合当前维修流程");
        }
        return List.copyOf(signatures.values());
    }

    private Set<ContractPartyType> requiredContractParties(RepairWorkflowType workflowType) {
        // 楼栋维修由物业服务企业以自身名义与施工单位签约；业委会授权留在治理环节，不构成合同签约方。
        if (workflowType == RepairWorkflowType.BUILDING_REPAIR) {
            return EnumSet.of(ContractPartyType.PROPERTY, ContractPartyType.SUPPLIER);
        }
        return EnumSet.allOf(ContractPartyType.class);
    }

    /**
     * 合同中的物业方必须来自当前小区已启用的物业服务企业及其项目部，不能由前端手填或仅按角色推定。
     */
    private PropertyServiceEnterprise requireActivePropertyContractParty(UserContext actor) {
        PropertyServiceOrganization organization = propertyServiceOrganizationRepository
                .findActiveByTenant(actor.tenantId())
                .orElseThrow(() -> support.invalid("当前小区未启用物业服务组织，不能以物业服务企业名义签署合同"));
        if (actor.deptId() == null || !actor.deptId().equals(organization.projectDeptId())) {
            throw support.invalid("当前物业经理未挂接本小区已启用物业项目部，不能以物业服务企业名义签署合同");
        }
        return propertyServiceOrganizationRepository.findEnterpriseById(organization.enterpriseId())
                .orElseThrow(() -> support.invalid("当前物业服务组织缺少已核验企业主体，不能签署合同"));
    }

    private void assertSignerIdentity(
            RecordContract.Signature signature, UserContext actor, Long supplierDeptId) {
        Long signerUserId = signature.signerUserId();
        // 纸质合同以已归档的签署页为凭证，办理人的系统身份已由合同和事件审计记录；
        // 只有电子签署才需要把签约自然人与可核验的系统工作身份绑定。
        if (signerUserId == null) {
            if (signature.signatureMethod() == SignatureMethod.ELECTRONIC) {
                throw support.invalid("电子签署必须绑定系统工作身份 partyType=" + signature.partyType());
            }
            return;
        }
        switch (signature.partyType()) {
            case OWNERS_ASSEMBLY_OR_GROUP -> {
                if (workOrderRepository.findActiveCommitteePosition(actor.tenantId(), signerUserId)
                        .filter(position -> Set.of("DIRECTOR", "VICE_DIRECTOR").contains(position))
                        .isEmpty()) {
                    throw support.invalid("业主大会或相关业主方必须由在任主任或副主任代表签署");
                }
            }
            case PROPERTY -> {
                if (actor.deptId() == null
                        || !workOrderRepository.activeUserBelongsToDept(signerUserId, actor.deptId())) {
                    throw support.invalid("物业签署人必须是当前物业组织的在职人员");
                }
            }
            case SUPPLIER -> {
                if (signerUserId != null
                        && !workOrderRepository.activeUserBelongsToDept(signerUserId, supplierDeptId)) {
                    throw support.invalid("施工单位签署人不属于合同供应商组织");
                }
            }
        }
    }

    private String signingMethod(List<ContractSignature> signatures) {
        long electronicCount = signatures.stream()
                .filter(signature -> signature.signatureMethod()
                        == SignatureMethod.ELECTRONIC)
                .count();
        if (electronicCount == signatures.size()) {
            return "ONLINE";
        }
        return electronicCount == 0 ? "OFFLINE" : "MIXED";
    }

    private BigDecimal priceLimit(Context context) {
        if (!context.plan().priceReviewRequired()) {
            return context.plan().budgetTotal();
        }
        if (context.project().workflowType() == RepairWorkflowType.BUILDING_REPAIR) {
            BuildingProcess process = governanceRepository.findBuildingProcess(
                            context.project().projectId(), context.plan().planId(), context.project().tenantId())
                    .orElseThrow(() -> support.conflict("楼栋维修缺少已授权治理流程"));
            if (process.reviewedAmount() == null) {
                throw support.conflict("楼栋维修缺少有效审价金额");
            }
            return process.reviewedAmount();
        }
        return executionRepository.findCostReview(
                        context.project().projectId(), context.plan().planId(), context.project().tenantId())
                .map(CostReview::reviewedAmount)
                .orElseThrow(() -> support.conflict("全小区维修尚未完成锁定方案要求的审价"));
    }

    private void assertSupplierMatchesContract(UserContext actor, Context context) {
        if (!SUPPLIER_ROLES.contains(actor.roleKey())) {
            return;
        }
        Contract contract = executionRepository.findContract(
                        context.project().projectId(), context.project().tenantId())
                .orElseThrow(() -> support.conflict("项目没有已生效合同"));
        if (actor.deptId() == null || !actor.deptId().equals(contract.supplierDeptId())) {
            throw support.forbidden("当前供应商不是本项目合同施工单位");
        }
    }

    private void requireVersion(Context context, Integer expectedVersion) {
        if (!context.project().version().equals(expectedVersion)) {
            throw support.conflict("项目版本已变化，请刷新后重试");
        }
    }

    private void document(Attachment attachment, String label) {
        String contentType = attachment.contentType();
        if (contentType == null || !(contentType.startsWith("image/")
                || "application/pdf".equals(contentType)
                || contentType.contains("word") || contentType.contains("spreadsheet")
                || contentType.contains("excel"))) {
            throw support.invalid(label + "文件类型不合法");
        }
    }

    private String allowed(String value, Set<String> allowed, String field) {
        String normalized = requiredText(value, field).toUpperCase();
        if (!allowed.contains(normalized)) {
            throw support.invalid(field + " 取值不合法");
        }
        return normalized;
    }

    private String requiredText(String value, String field) {
        String normalized = text(value);
        if (normalized == null) {
            throw support.invalid(field + " 必填");
        }
        return normalized;
    }

    private String text(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private void requirePositive(BigDecimal value, String field) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw support.invalid(field + " 必须大于 0");
        }
    }

    private void requireNonNegative(BigDecimal value, String field) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            throw support.invalid(field + " 不能小于 0");
        }
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw support.invalid(message);
        }
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JVM 缺少 SHA-256", ex);
        }
    }
}
