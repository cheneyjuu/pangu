// 关联业务：实现维修工程合同、施工、材料、结算、项目验收、付款和完工披露仓储端口。
package com.pangu.infrastructure.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.pangu.domain.model.repair.RepairAcceptanceConclusion;
import com.pangu.domain.model.repair.RepairAcceptancePartyRole;
import com.pangu.domain.model.repair.RepairProject;
import com.pangu.domain.model.repair.RepairProjectExecution.AcceptanceParty;
import com.pangu.domain.model.repair.RepairProjectExecution.AcceptancePolicy;
import com.pangu.domain.model.repair.RepairProjectExecution.AcceptanceRequirement;
import com.pangu.domain.model.repair.RepairProjectExecution.AcceptanceRound;
import com.pangu.domain.model.repair.RepairProjectExecution.AcceptanceStatus;
import com.pangu.domain.model.repair.RepairProjectExecution.AcceptanceSummary;
import com.pangu.domain.model.repair.RepairProjectExecution.CompletionDisclosure;
import com.pangu.domain.model.repair.RepairProjectExecution.Contract;
import com.pangu.domain.model.repair.RepairProjectExecution.ContractPartyType;
import com.pangu.domain.model.repair.RepairProjectExecution.ContractSignature;
import com.pangu.domain.model.repair.RepairProjectExecution.ContractStatus;
import com.pangu.domain.model.repair.RepairProjectExecution.CostReview;
import com.pangu.domain.model.repair.RepairProjectExecution.ExecutionRecord;
import com.pangu.domain.model.repair.RepairProjectExecution.MaterialInspection;
import com.pangu.domain.model.repair.RepairProjectExecution.PaymentEvidence;
import com.pangu.domain.model.repair.RepairProjectExecution.PaymentRequest;
import com.pangu.domain.model.repair.RepairProjectExecution.PaymentStatus;
import com.pangu.domain.model.repair.RepairProjectExecution.Settlement;
import com.pangu.domain.model.repair.RepairProjectExecution.SettlementItem;
import com.pangu.domain.model.repair.RepairProjectExecution.SettlementStatus;
import com.pangu.domain.model.repair.RepairProjectExecution.SignatureMethod;
import com.pangu.domain.model.repair.RepairProjectExecution.VerificationStatus;
import com.pangu.domain.model.repair.RepairWorkflowType;
import com.pangu.domain.repository.RepairProjectExecutionRepository;
import com.pangu.infrastructure.persistence.entity.RepairProjectExecutionRows.AcceptancePartyRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectExecutionRows.AcceptancePolicyRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectExecutionRows.AcceptanceRoundRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectExecutionRows.AcceptanceSummaryRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectExecutionRows.CompletionDisclosureRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectExecutionRows.ContractRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectExecutionRows.ContractSignatureRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectExecutionRows.CostReviewRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectExecutionRows.ExecutionRecordRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectExecutionRows.MaterialInspectionRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectExecutionRows.PaymentRequestRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectExecutionRows.SettlementItemRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectExecutionRows.SettlementRow;
import com.pangu.infrastructure.persistence.mapper.RepairProjectExecutionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class RepairProjectExecutionRepositoryImpl implements RepairProjectExecutionRepository {

    private final RepairProjectExecutionMapper mapper;
    private final ObjectMapper objectMapper;

    private static final TypeReference<List<AcceptanceRequirement>> ACCEPTANCE_REQUIREMENTS =
            new TypeReference<>() { };
    private static final TypeReference<Set<RepairAcceptancePartyRole>> ACCEPTANCE_FINALIZER_ROLES =
            new TypeReference<>() { };
    private static final TypeReference<List<Long>> LONG_LIST = new TypeReference<>() { };

    @Override
    public CostReview insertCostReview(CostReview review) {
        CostReviewRow row = costReviewRow(review);
        mapper.insertCostReview(row);
        return costReview(row);
    }

    @Override
    public Optional<CostReview> findCostReview(Long projectId, Long planId, Long tenantId) {
        return Optional.ofNullable(mapper.findCostReview(projectId, planId, tenantId)).map(this::costReview);
    }

    @Override
    public Contract insertContract(Contract contract) {
        ContractRow row = contractRow(contract);
        mapper.insertContract(row);
        return contract(row);
    }

    @Override
    public void insertContractSignatures(Long tenantId, List<ContractSignature> signatures) {
        signatures.forEach(signature -> mapper.insertContractSignature(contractSignatureRow(signature)));
    }

    @Override
    public Optional<Contract> findContract(Long projectId, Long tenantId) {
        return Optional.ofNullable(mapper.findContract(projectId, tenantId)).map(this::contract);
    }

    @Override
    public List<Long> listSupplierProjectIds(Long tenantId, Long supplierDeptId) {
        return mapper.listSupplierProjectIds(tenantId, supplierDeptId);
    }

    @Override
    public List<ContractSignature> listContractSignatures(Long contractId) {
        return mapper.listContractSignatures(contractId).stream().map(this::contractSignature).toList();
    }

    @Override
    public ExecutionRecord insertExecutionRecord(ExecutionRecord record) {
        ExecutionRecordRow row = executionRow(record);
        mapper.insertExecutionRecord(row);
        return execution(row, List.of());
    }

    @Override
    public void insertExecutionAttachments(Long recordId, Long tenantId, List<Long> attachmentIds) {
        for (int index = 0; index < attachmentIds.size(); index++) {
            mapper.insertExecutionAttachment(recordId, tenantId, attachmentIds.get(index), index + 1);
        }
    }

    @Override
    public int verifyExecutionRecord(
            Long recordId, Long projectId, Long tenantId, String status,
            Long verifiedByUserId, String opinion) {
        return mapper.verifyExecutionRecord(
                recordId, projectId, tenantId, status, verifiedByUserId, opinion);
    }

    @Override
    public Optional<ExecutionRecord> findExecutionRecord(Long recordId, Long projectId, Long tenantId) {
        ExecutionRecordRow row = mapper.findExecutionRecord(recordId, projectId, tenantId);
        return row == null ? Optional.empty() : Optional.of(execution(
                row, mapper.listExecutionAttachmentIds(recordId, tenantId)));
    }

    @Override
    public List<ExecutionRecord> listExecutionRecords(Long projectId, Long tenantId) {
        return mapper.listExecutionRecords(projectId, tenantId).stream()
                .map(row -> execution(row, mapper.listExecutionAttachmentIds(row.getRecordId(), tenantId)))
                .toList();
    }

    @Override
    public MaterialInspection insertMaterialInspection(MaterialInspection inspection) {
        MaterialInspectionRow row = materialRow(inspection);
        mapper.insertMaterialInspection(row);
        return material(row, List.of());
    }

    @Override
    public void insertMaterialPhotos(Long inspectionId, Long tenantId, List<Long> attachmentIds) {
        for (int index = 0; index < attachmentIds.size(); index++) {
            mapper.insertMaterialPhoto(inspectionId, tenantId, attachmentIds.get(index), index + 1);
        }
    }

    @Override
    public int verifyMaterialInspection(
            Long inspectionId, Long projectId, Long tenantId, String status,
            Long verifiedByUserId, String opinion) {
        return mapper.verifyMaterialInspection(
                inspectionId, projectId, tenantId, status, verifiedByUserId, opinion);
    }

    @Override
    public Optional<MaterialInspection> findMaterialInspection(
            Long inspectionId, Long projectId, Long tenantId) {
        MaterialInspectionRow row = mapper.findMaterialInspection(inspectionId, projectId, tenantId);
        return row == null ? Optional.empty() : Optional.of(material(
                row, mapper.listMaterialPhotoIds(inspectionId, tenantId)));
    }

    @Override
    public List<MaterialInspection> listMaterialInspections(Long projectId, Long tenantId) {
        return mapper.listMaterialInspections(projectId, tenantId).stream()
                .map(row -> material(row, mapper.listMaterialPhotoIds(row.getInspectionId(), tenantId)))
                .toList();
    }

    @Override
    public Settlement insertSettlement(Settlement settlement) {
        SettlementRow row = settlementRow(settlement);
        mapper.insertSettlement(row);
        return settlement(row, List.of());
    }

    @Override
    public int nextSettlementVersion(Long projectId, Long tenantId) {
        return mapper.nextSettlementVersion(projectId, tenantId);
    }

    @Override
    public void insertSettlementItems(Long settlementId, List<SettlementItem> items) {
        items.forEach(item -> {
            SettlementItemRow row = settlementItemRow(item);
            row.setSettlementId(settlementId);
            mapper.insertSettlementItem(row);
        });
    }

    @Override
    public Optional<Settlement> findActiveSettlement(Long projectId, Long tenantId) {
        SettlementRow row = mapper.findActiveSettlement(projectId, tenantId);
        return row == null ? Optional.empty() : Optional.of(settlement(
                row, mapper.listSettlementItems(row.getSettlementId()).stream()
                        .map(this::settlementItem).toList()));
    }

    @Override
    public int verifySettlement(
            Long settlementId, Long projectId, Long tenantId,
            String status, Long verifiedByUserId, String opinion) {
        return mapper.verifySettlement(
                settlementId, projectId, tenantId, status, verifiedByUserId, opinion);
    }

    @Override
    public int invalidateVerifiedSettlement(
            Long settlementId, Long projectId, Long tenantId, String opinion) {
        return mapper.invalidateVerifiedSettlement(settlementId, projectId, tenantId, opinion);
    }

    @Override
    public AcceptancePolicy insertAcceptancePolicy(
            AcceptancePolicy policy, int minimumApprovals, Long lockedByUserId) {
        AcceptancePolicyRow row = acceptancePolicyRow(policy);
        mapper.insertAcceptancePolicy(row, minimumApprovals, lockedByUserId);
        return acceptancePolicy(row);
    }

    @Override
    public void snapshotAcceptanceAffectedOwners(Long policyId, Long planId, Long tenantId) {
        mapper.snapshotAcceptanceAffectedOwners(policyId, planId, tenantId);
    }

    @Override
    public AcceptanceRound startAcceptance(
            Long projectId, Long tenantId, Long policyId, Long settlementId, Long submittedByUserId) {
        AcceptanceRoundRow row = new AcceptanceRoundRow();
        row.setProjectId(projectId);
        row.setTenantId(tenantId);
        row.setPolicyId(policyId);
        row.setSettlementId(settlementId);
        mapper.insertAcceptanceRound(row, submittedByUserId);
        return mapper.findCollectingAcceptance(projectId, tenantId) == null
                ? acceptanceRound(row)
                : acceptanceRound(mapper.findCollectingAcceptance(projectId, tenantId));
    }

    @Override
    public Optional<AcceptancePolicy> findAcceptancePolicy(Long projectId, Long tenantId) {
        return Optional.ofNullable(mapper.findAcceptancePolicy(projectId, tenantId)).map(this::acceptancePolicy);
    }

    @Override
    public Optional<AcceptanceRound> findCollectingAcceptance(Long projectId, Long tenantId) {
        return Optional.ofNullable(mapper.findCollectingAcceptance(projectId, tenantId)).map(this::acceptanceRound);
    }

    @Override
    public Optional<AcceptanceRound> findLatestAcceptance(Long projectId, Long tenantId) {
        return Optional.ofNullable(mapper.findLatestAcceptance(projectId, tenantId)).map(this::acceptanceRound);
    }

    @Override
    public boolean affectedOwnerIncluded(Long policyId, Long tenantId, Long roomId, Long ownerUid) {
        return mapper.affectedOwnerIncluded(policyId, tenantId, roomId, ownerUid) > 0;
    }

    @Override
    public List<Long> listAffectedOwnerRoomIds(Long policyId, Long tenantId, Long ownerUid) {
        return mapper.listAffectedOwnerRoomIds(policyId, tenantId, ownerUid);
    }

    @Override
    public List<Long> listOpenAcceptanceProjectIds(Long tenantId, Long ownerUid) {
        return mapper.listOpenAcceptanceProjectIds(tenantId, ownerUid);
    }

    @Override
    public void insertAcceptanceParty(Long tenantId, AcceptanceParty party) {
        mapper.insertAcceptanceParty(acceptancePartyRow(party), tenantId);
    }

    @Override
    public List<AcceptanceParty> listAcceptanceParties(Long acceptanceId, Long tenantId) {
        return mapper.listAcceptanceParties(acceptanceId, tenantId).stream()
                .map(this::acceptanceParty).toList();
    }

    @Override
    public AcceptanceSummary summarizeAcceptance(Long acceptanceId, Long tenantId) {
        AcceptanceSummaryRow row = mapper.summarizeAcceptance(acceptanceId, tenantId);
        return new AcceptanceSummary(
                value(row.getParticipatingAffectedOwnerCount()), value(row.getPassedAffectedOwnerCount()),
                value(row.getRectificationCount()), value(row.getBuildingLeaderPassed()),
                value(row.getCommitteeExecutivePassed()), value(row.getCommitteeSealApplied()),
                value(row.getPropertyTechnicalCosigned()), value(row.getThirdPartyTechnicalCosigned()));
    }

    @Override
    public int completeAcceptance(
            Long acceptanceId, Long tenantId, String status, Long resultAttachmentId,
            Long completedByUserId, String remark) {
        return mapper.completeAcceptance(
                acceptanceId, tenantId, status, resultAttachmentId, completedByUserId, remark);
    }

    @Override
    public int supersedeAcceptancePolicy(Long policyId, Long projectId, Long tenantId) {
        return mapper.supersedeAcceptancePolicy(policyId, projectId, tenantId);
    }

    @Override
    public BigDecimal sumActivePaymentRequests(Long projectId, Long contractId, Long tenantId) {
        BigDecimal total = mapper.sumActivePaymentRequests(projectId, contractId, tenantId);
        return total == null ? BigDecimal.ZERO : total;
    }

    @Override
    public PaymentRequest insertPaymentRequest(PaymentRequest request, String eligibilityResultJson) {
        PaymentRequestRow row = paymentRow(request);
        mapper.insertPaymentRequest(row, eligibilityResultJson);
        return payment(row, request.evidence());
    }

    @Override
    public void insertPaymentEvidence(Long paymentRequestId, Long tenantId, List<PaymentEvidence> evidence) {
        evidence.forEach(item -> mapper.insertPaymentEvidence(
                paymentRequestId, tenantId, item.evidenceCode(), item.attachmentId()));
    }

    @Override
    public List<PaymentRequest> listPaymentRequests(Long projectId, Long tenantId) {
        return mapper.listPaymentRequests(projectId, tenantId).stream()
                .map(row -> payment(row, mapper.listPaymentEvidence(row.getPaymentRequestId()).stream()
                        .map(item -> new PaymentEvidence(item.getEvidenceCode(), item.getAttachmentId()))
                        .toList()))
                .toList();
    }

    @Override
    public CompletionDisclosure insertCompletionDisclosure(CompletionDisclosure disclosure) {
        CompletionDisclosureRow row = disclosureRow(disclosure);
        mapper.insertCompletionDisclosure(row);
        return disclosure(row, List.of());
    }

    @Override
    public void insertCompletionDisclosurePhotos(Long disclosureId, Long tenantId, List<Long> attachmentIds) {
        for (int index = 0; index < attachmentIds.size(); index++) {
            mapper.insertCompletionDisclosurePhoto(
                    disclosureId, tenantId, attachmentIds.get(index), index + 1);
        }
    }

    @Override
    public Optional<CompletionDisclosure> findCompletionDisclosure(Long projectId, Long tenantId) {
        CompletionDisclosureRow row = mapper.findCompletionDisclosure(projectId, tenantId);
        return row == null ? Optional.empty() : Optional.of(disclosure(
                row, mapper.listCompletionDisclosurePhotoIds(row.getDisclosureId(), tenantId)));
    }

    private CostReviewRow costReviewRow(CostReview value) {
        CostReviewRow row = new CostReviewRow();
        row.setReviewId(value.reviewId());
        row.setProjectId(value.projectId());
        row.setPlanId(value.planId());
        row.setTenantId(value.tenantId());
        row.setReviewMode(value.reviewMode());
        row.setReviewedAmount(value.reviewedAmount());
        row.setReportAttachmentId(value.reportAttachmentId());
        row.setReviewedByUserId(value.reviewedByUserId());
        row.setReviewedAt(value.reviewedAt());
        return row;
    }

    private CostReview costReview(CostReviewRow row) {
        return new CostReview(row.getReviewId(), row.getProjectId(), row.getPlanId(), row.getTenantId(),
                row.getReviewMode(), row.getReviewedAmount(), row.getReportAttachmentId(),
                row.getReviewedByUserId(), row.getReviewedAt());
    }

    private ContractRow contractRow(Contract value) {
        ContractRow row = new ContractRow();
        row.setContractId(value.contractId());
        row.setProjectId(value.projectId());
        row.setPlanId(value.planId());
        row.setTenantId(value.tenantId());
        row.setSupplierDeptId(value.supplierDeptId());
        row.setSupplierName(value.supplierName());
        row.setContractAmount(value.contractAmount());
        row.setRepairScopeHash(value.repairScopeHash());
        row.setFundSource(value.fundSource().name());
        row.setSigningMethod(value.signingMethod());
        row.setContractAttachmentId(value.contractAttachmentId());
        row.setContractFileHash(value.contractFileHash());
        row.setStatus(value.status().name());
        row.setCreatedByUserId(value.createdByUserId());
        row.setEffectiveAt(value.effectiveAt());
        row.setCreateTime(value.createTime());
        return row;
    }

    private Contract contract(ContractRow row) {
        return new Contract(row.getContractId(), row.getProjectId(), row.getPlanId(), row.getTenantId(),
                row.getSupplierDeptId(), row.getSupplierName(), row.getContractAmount(),
                row.getRepairScopeHash(), RepairProject.FundSource.valueOf(row.getFundSource()),
                row.getSigningMethod(), row.getContractAttachmentId(), row.getContractFileHash(),
                ContractStatus.valueOf(row.getStatus()),
                row.getCreatedByUserId(), row.getEffectiveAt(), row.getCreateTime());
    }

    private ContractSignatureRow contractSignatureRow(ContractSignature value) {
        ContractSignatureRow row = new ContractSignatureRow();
        row.setSignatureId(value.signatureId());
        row.setContractId(value.contractId());
        row.setPartyType(value.partyType().name());
        row.setSignerName(value.signerName());
        row.setSignerUserId(value.signerUserId());
        row.setSignatureMethod(value.signatureMethod().name());
        row.setSignatureAttachmentId(value.signatureAttachmentId());
        row.setSignatureFileHash(value.signatureFileHash());
        row.setSignedAt(value.signedAt());
        return row;
    }

    private ContractSignature contractSignature(ContractSignatureRow row) {
        return new ContractSignature(row.getSignatureId(), row.getContractId(),
                ContractPartyType.valueOf(row.getPartyType()), row.getSignerName(), row.getSignerUserId(),
                SignatureMethod.valueOf(row.getSignatureMethod()), row.getSignatureAttachmentId(),
                row.getSignatureFileHash(), row.getSignedAt());
    }

    private ExecutionRecordRow executionRow(ExecutionRecord value) {
        ExecutionRecordRow row = new ExecutionRecordRow();
        row.setRecordId(value.recordId());
        row.setProjectId(value.projectId());
        row.setPlanId(value.planId());
        row.setWorkPointId(value.workPointId());
        row.setTenantId(value.tenantId());
        row.setStage(value.stage().name());
        row.setDescription(value.description());
        row.setOccurredAt(value.occurredAt());
        row.setSubmittedByUserId(value.submittedByUserId());
        row.setVerificationStatus(value.verificationStatus().name());
        row.setVerifiedByUserId(value.verifiedByUserId());
        row.setVerificationOpinion(value.verificationOpinion());
        row.setVerifiedAt(value.verifiedAt());
        row.setCreateTime(value.createTime());
        return row;
    }

    private ExecutionRecord execution(ExecutionRecordRow row, List<Long> attachmentIds) {
        return new ExecutionRecord(row.getRecordId(), row.getProjectId(), row.getPlanId(), row.getWorkPointId(),
                row.getTenantId(), RepairProject.EvidenceStage.valueOf(row.getStage()), row.getDescription(),
                row.getOccurredAt(), row.getSubmittedByUserId(), row.getVerifiedByUserId(),
                VerificationStatus.valueOf(row.getVerificationStatus()), row.getVerificationOpinion(),
                row.getVerifiedAt(), attachmentIds, row.getCreateTime());
    }

    private MaterialInspectionRow materialRow(MaterialInspection value) {
        MaterialInspectionRow row = new MaterialInspectionRow();
        row.setInspectionId(value.inspectionId());
        row.setProjectId(value.projectId());
        row.setPlanId(value.planId());
        row.setWorkPointId(value.workPointId());
        row.setTenantId(value.tenantId());
        row.setMaterialName(value.materialName());
        row.setBrand(value.brand());
        row.setModel(value.model());
        row.setSpecification(value.specification());
        row.setQuantity(value.quantity());
        row.setUnit(value.unit());
        row.setManufacturer(value.manufacturer());
        row.setQualificationAttachmentId(value.qualificationAttachmentId());
        row.setSubmittedByUserId(value.submittedByUserId());
        row.setStatus(value.status().name());
        row.setVerifiedByUserId(value.verifiedByUserId());
        row.setVerificationOpinion(value.verificationOpinion());
        row.setVerifiedAt(value.verifiedAt());
        row.setCreateTime(value.createTime());
        return row;
    }

    private MaterialInspection material(MaterialInspectionRow row, List<Long> photoIds) {
        return new MaterialInspection(row.getInspectionId(), row.getProjectId(), row.getPlanId(), row.getWorkPointId(),
                row.getTenantId(), row.getMaterialName(), row.getBrand(), row.getModel(), row.getSpecification(),
                row.getQuantity(), row.getUnit(), row.getManufacturer(), row.getQualificationAttachmentId(),
                photoIds, row.getSubmittedByUserId(), VerificationStatus.valueOf(row.getStatus()),
                row.getVerifiedByUserId(), row.getVerificationOpinion(), row.getVerifiedAt(), row.getCreateTime());
    }

    private SettlementRow settlementRow(Settlement value) {
        SettlementRow row = new SettlementRow();
        row.setSettlementId(value.settlementId());
        row.setProjectId(value.projectId());
        row.setPlanId(value.planId());
        row.setContractId(value.contractId());
        row.setTenantId(value.tenantId());
        row.setVersionNo(value.versionNo());
        row.setStatus(value.status().name());
        row.setSubtotalAmount(value.subtotalAmount());
        row.setTaxRate(value.taxRate());
        row.setTaxAmount(value.taxAmount());
        row.setTotalAmount(value.totalAmount());
        row.setSettlementAttachmentId(value.settlementAttachmentId());
        row.setSubmittedByUserId(value.submittedByUserId());
        row.setVerifiedByUserId(value.verifiedByUserId());
        row.setVerificationOpinion(value.verificationOpinion());
        row.setSubmittedAt(value.submittedAt());
        row.setVerifiedAt(value.verifiedAt());
        return row;
    }

    private Settlement settlement(SettlementRow row, List<SettlementItem> items) {
        return new Settlement(row.getSettlementId(), row.getProjectId(), row.getPlanId(), row.getContractId(),
                row.getTenantId(), row.getVersionNo(), SettlementStatus.valueOf(row.getStatus()),
                row.getSubtotalAmount(), row.getTaxRate(), row.getTaxAmount(), row.getTotalAmount(),
                row.getSettlementAttachmentId(), row.getSubmittedByUserId(), row.getVerifiedByUserId(),
                row.getVerificationOpinion(), row.getSubmittedAt(), row.getVerifiedAt(), items);
    }

    private SettlementItemRow settlementItemRow(SettlementItem value) {
        SettlementItemRow row = new SettlementItemRow();
        row.setSettlementItemId(value.settlementItemId());
        row.setSettlementId(value.settlementId());
        row.setWorkPointId(value.workPointId());
        row.setActualQuantity(value.actualQuantity());
        row.setUnit(value.unit());
        row.setActualUnitPrice(value.actualUnitPrice());
        row.setAmountExcludingTax(value.amountExcludingTax());
        row.setVarianceReason(value.varianceReason());
        return row;
    }

    private SettlementItem settlementItem(SettlementItemRow row) {
        return new SettlementItem(row.getSettlementItemId(), row.getSettlementId(), row.getWorkPointId(),
                row.getActualQuantity(), row.getUnit(), row.getActualUnitPrice(), row.getAmountExcludingTax(),
                row.getVarianceReason());
    }

    private AcceptancePolicyRow acceptancePolicyRow(AcceptancePolicy value) {
        AcceptancePolicyRow row = new AcceptancePolicyRow();
        row.setPolicyId(value.policyId());
        row.setProjectId(value.projectId());
        row.setPlanId(value.planId());
        row.setTenantId(value.tenantId());
        row.setWorkflowType(value.workflowType().name());
        row.setPolicyHash(value.policyHash());
        row.setAcceptanceMethod(value.acceptanceMethod());
        row.setRequirementsJson(writeJson(value.requirements()));
        row.setFinalizerRolesJson(writeJson(value.finalizerRoles()));
        row.setBasisAttachmentIdsJson(writeJson(value.basisAttachmentIds()));
        row.setBasisSummary(value.basisSummary());
        row.setAffectedOwnerCount(value.affectedOwnerCount());
        row.setMinimumAffectedOwnerParticipants(value.minimumAffectedOwnerParticipants());
        row.setAffectedOwnerPassRule(value.affectedOwnerPassRule() == null
                ? null : value.affectedOwnerPassRule().name());
        row.setAffectedOwnerApprovalRatio(value.affectedOwnerApprovalRatio());
        return row;
    }

    private AcceptancePolicy acceptancePolicy(AcceptancePolicyRow row) {
        return new AcceptancePolicy(row.getPolicyId(), row.getProjectId(), row.getPlanId(), row.getTenantId(),
                RepairWorkflowType.valueOf(row.getWorkflowType()), row.getPolicyHash(),
                row.getAcceptanceMethod(), readJson(row.getRequirementsJson(), ACCEPTANCE_REQUIREMENTS),
                readJson(row.getFinalizerRolesJson(), ACCEPTANCE_FINALIZER_ROLES),
                readJson(row.getBasisAttachmentIdsJson(), LONG_LIST), row.getBasisSummary(),
                value(row.getAffectedOwnerCount()), value(row.getMinimumAffectedOwnerParticipants()),
                row.getAffectedOwnerPassRule() == null ? null
                        : RepairProject.AffectedOwnerPassRule.valueOf(row.getAffectedOwnerPassRule()),
                row.getAffectedOwnerApprovalRatio());
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("维修工程验收规则序列化失败", ex);
        }
    }

    private <T> T readJson(String value, TypeReference<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("维修工程验收规则读取失败", ex);
        }
    }

    private AcceptanceRound acceptanceRound(AcceptanceRoundRow row) {
        return new AcceptanceRound(row.getAcceptanceId(), row.getProjectId(), row.getPolicyId(),
                row.getSettlementId(), row.getTenantId(), value(row.getRoundNo()),
                row.getStatus() == null ? AcceptanceStatus.COLLECTING : AcceptanceStatus.valueOf(row.getStatus()),
                row.getResultProjectAttachmentId(), row.getSubmittedAt(), row.getCompletedAt());
    }

    private AcceptancePartyRow acceptancePartyRow(AcceptanceParty value) {
        AcceptancePartyRow row = new AcceptancePartyRow();
        row.setPartyId(value.partyId());
        row.setAcceptanceId(value.acceptanceId());
        row.setParticipantKey(value.participantKey());
        row.setPartyRole(value.partyRole().name());
        row.setRoomId(value.roomId());
        row.setOwnerUid(value.ownerUid());
        row.setParticipantAccountId(value.participantAccountId());
        row.setParticipantUserId(value.participantUserId());
        row.setParticipantName(value.participantName());
        row.setParticipantOrganization(value.participantOrganization());
        row.setCommitteePosition(value.committeePosition());
        row.setConclusion(value.conclusion().name());
        row.setOpinion(value.opinion());
        row.setSubmissionMethod(value.submissionMethod());
        row.setEvidenceProjectAttachmentId(value.evidenceAttachmentId());
        row.setSealUsageId(value.sealUsageId());
        row.setSubmittedByUserId(value.submittedByUserId());
        row.setSubmittedAt(value.submittedAt());
        return row;
    }

    private AcceptanceParty acceptanceParty(AcceptancePartyRow row) {
        return new AcceptanceParty(row.getPartyId(), row.getAcceptanceId(), row.getParticipantKey(),
                RepairAcceptancePartyRole.valueOf(row.getPartyRole()), row.getRoomId(), row.getOwnerUid(),
                row.getParticipantAccountId(), row.getParticipantUserId(), row.getParticipantName(),
                row.getParticipantOrganization(), row.getCommitteePosition(),
                RepairAcceptanceConclusion.valueOf(row.getConclusion()), row.getOpinion(),
                row.getSubmissionMethod(), row.getEvidenceProjectAttachmentId(), row.getSealUsageId(),
                row.getSubmittedByUserId(), row.getSubmittedAt());
    }

    private PaymentRequestRow paymentRow(PaymentRequest value) {
        PaymentRequestRow row = new PaymentRequestRow();
        row.setPaymentRequestId(value.paymentRequestId());
        row.setProjectId(value.projectId());
        row.setContractId(value.contractId());
        row.setTenantId(value.tenantId());
        row.setMilestoneType(value.milestoneType().name());
        row.setRequestedAmount(value.requestedAmount());
        row.setCumulativeRequestedAmount(value.cumulativeRequestedAmount());
        row.setEligibleUpperLimit(value.eligibleUpperLimit());
        row.setStatus(value.status().name());
        row.setRequestedByUserId(value.requestedByUserId());
        row.setCreateTime(value.createTime());
        return row;
    }

    private PaymentRequest payment(PaymentRequestRow row, List<PaymentEvidence> evidence) {
        return new PaymentRequest(row.getPaymentRequestId(), row.getProjectId(), row.getContractId(),
                row.getTenantId(), RepairProject.PaymentMilestoneType.valueOf(row.getMilestoneType()),
                row.getRequestedAmount(), row.getCumulativeRequestedAmount(), row.getEligibleUpperLimit(),
                PaymentStatus.valueOf(row.getStatus()), evidence, row.getRequestedByUserId(), row.getCreateTime());
    }

    private CompletionDisclosureRow disclosureRow(CompletionDisclosure value) {
        CompletionDisclosureRow row = new CompletionDisclosureRow();
        row.setDisclosureId(value.disclosureId());
        row.setProjectId(value.projectId());
        row.setTenantId(value.tenantId());
        row.setNoticeStartDate(value.noticeStartDate());
        row.setNoticeEndDate(value.noticeEndDate());
        row.setPostingScope(value.postingScope());
        row.setNoticeAttachmentId(value.noticeAttachmentId());
        row.setPropertyReportAttachmentId(value.propertyReportAttachmentId());
        row.setWarrantyStartDate(value.warrantyStartDate());
        row.setWarrantyEndDate(value.warrantyEndDate());
        row.setCreatedByUserId(value.createdByUserId());
        row.setCreateTime(value.createTime());
        return row;
    }

    private CompletionDisclosure disclosure(CompletionDisclosureRow row, List<Long> photos) {
        return new CompletionDisclosure(row.getDisclosureId(), row.getProjectId(), row.getTenantId(),
                row.getNoticeStartDate(), row.getNoticeEndDate(), row.getPostingScope(),
                row.getNoticeAttachmentId(), row.getPropertyReportAttachmentId(), photos,
                row.getWarrantyStartDate(), row.getWarrantyEndDate(), row.getCreatedByUserId(),
                row.getCreateTime());
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private boolean value(Boolean value) {
        return Boolean.TRUE.equals(value);
    }
}
