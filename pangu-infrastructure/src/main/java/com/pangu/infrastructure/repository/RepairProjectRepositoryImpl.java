// 关联业务：实现维修工程项目、单一决定范围、维修点位、可信资金切片、实施方案版本及项目附件的数据访问。
package com.pangu.infrastructure.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.domain.model.repair.RepairProject;
import com.pangu.domain.model.repair.RepairProjectProcessEvent;
import com.pangu.domain.model.repair.RepairProject.AllocationRoom;
import com.pangu.domain.model.repair.RepairProject.AllocationBasis;
import com.pangu.domain.model.repair.RepairProject.Attachment;
import com.pangu.domain.model.repair.RepairProject.EvidenceRequirement;
import com.pangu.domain.model.repair.RepairProject.EligibleAffectedOwner;
import com.pangu.domain.model.repair.RepairProject.DecisionScope;
import com.pangu.domain.model.repair.RepairProject.FundingSlice;
import com.pangu.domain.model.repair.RepairProject.PaymentMilestone;
import com.pangu.domain.model.repair.RepairProject.PlanAttachment;
import com.pangu.domain.model.repair.RepairProject.PlanAffectedOwner;
import com.pangu.domain.model.repair.RepairProject.PlanVersion;
import com.pangu.domain.model.repair.RepairProject.Status;
import com.pangu.domain.model.repair.RepairProject.WorkPoint;
import com.pangu.domain.model.repair.RepairSupplierSelectionMethod;
import com.pangu.domain.model.repair.RepairWorkflowType;
import com.pangu.domain.repository.RepairProjectRepository;
import com.pangu.infrastructure.persistence.entity.RepairPlanAllocationRoomRow;
import com.pangu.infrastructure.persistence.entity.RepairAllocationBasisRow;
import com.pangu.infrastructure.persistence.entity.RepairEligibleAffectedOwnerRow;
import com.pangu.infrastructure.persistence.entity.RepairPlanAffectedOwnerRow;
import com.pangu.infrastructure.persistence.entity.RepairPlanAttachmentRow;
import com.pangu.infrastructure.persistence.entity.RepairPlanVersionRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectAttachmentRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectProcessEventRow;
import com.pangu.infrastructure.persistence.entity.RepairDecisionScopeRow;
import com.pangu.infrastructure.persistence.entity.RepairFundingSliceRow;
import com.pangu.infrastructure.persistence.entity.RepairWorkPointRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectRow;
import com.pangu.infrastructure.persistence.mapper.RepairProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RepairProjectRepositoryImpl implements RepairProjectRepository {

    private static final TypeReference<List<EvidenceRequirement>> EVIDENCE_REQUIREMENTS_TYPE =
            new TypeReference<>() { };
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() { };
    private static final TypeReference<List<PaymentMilestone>> PAYMENT_MILESTONES_TYPE =
            new TypeReference<>() { };

    private final RepairProjectMapper mapper;
    private final ObjectMapper objectMapper;

    @Override
    public RepairProject insertProject(RepairProject project) {
        RepairProjectRow row = toRow(project);
        mapper.insertProject(row);
        return findProject(row.getProjectId(), project.tenantId()).orElseThrow();
    }

    @Override
    public PlanVersion insertPlan(PlanVersion plan) {
        RepairPlanVersionRow row = toRow(plan);
        mapper.insertPlan(row);
        return mapper.listPlans(plan.projectId(), plan.tenantId()).stream()
                .filter(candidate -> candidate.getPlanId().equals(row.getPlanId()))
                .findFirst()
                .map(this::toDomain)
                .orElseThrow();
    }

    @Override
    public DecisionScope insertDecisionScope(DecisionScope decisionScope) {
        RepairDecisionScopeRow row = toRow(decisionScope);
        mapper.insertDecisionScope(row);
        return findDecisionScope(decisionScope.projectId(), decisionScope.tenantId()).orElseThrow();
    }

    @Override
    public Optional<DecisionScope> findDecisionScope(Long projectId, Long tenantId) {
        return Optional.ofNullable(mapper.findDecisionScope(projectId, tenantId)).map(this::toDomain);
    }

    @Override
    public int updateDecisionScopeVerification(
            Long projectId,
            Long tenantId,
            RepairProject.DecisionScopeVerificationStatus verificationStatus,
            String verificationBasis) {
        return mapper.updateDecisionScopeVerification(
                projectId, tenantId, verificationStatus.name(), verificationBasis);
    }

    @Override
    public List<FundingSlice> listFundingSlices(Long decisionScopeId, Long tenantId) {
        return mapper.listFundingSlices(decisionScopeId, tenantId).stream().map(this::toDomain).toList();
    }

    @Override
    public WorkPoint insertWorkPoint(WorkPoint workPoint) {
        RepairWorkPointRow row = toRow(workPoint);
        mapper.insertWorkPoint(row);
        row.setLinkedWorkOrderIds(List.of());
        return toDomain(row);
    }

    @Override
    public void linkWorkPointToWorkOrder(Long workPointId, Long workOrderId, Long tenantId) {
        mapper.linkWorkPointToWorkOrder(workPointId, workOrderId, tenantId);
    }

    @Override
    public List<AllocationRoom> snapshotAllocationRooms(
            Long planId, Long tenantId, RepairProject.ScopeType scopeType, Long buildingId, String unitName) {
        mapper.snapshotAllocationRooms(planId, tenantId, scopeType.name(), buildingId, unitName);
        return listAllocationRooms(planId, tenantId);
    }

    @Override
    public Optional<AllocationBasis> findAllocationBasis(
            Long tenantId, RepairProject.ScopeType scopeType, Long buildingId, String unitName) {
        return Optional.ofNullable(mapper.findAllocationBasis(
                        tenantId, scopeType.name(), buildingId, unitName))
                .map(this::toDomain);
    }

    @Override
    public Optional<AllocationBasis> findAllocationSnapshotBasis(Long planId, Long tenantId) {
        return Optional.ofNullable(mapper.findAllocationSnapshotBasis(planId, tenantId))
                .map(this::toDomain);
    }

    @Override
    public void linkPlanAttachment(Long planId, PlanAttachment attachment) {
        mapper.linkPlanAttachment(
                planId, attachment.attachmentId(), attachment.purpose().name(), attachment.sortOrder());
    }

    @Override
    public Optional<RepairProject> findProject(Long projectId, Long tenantId) {
        return Optional.ofNullable(mapper.findProject(projectId, tenantId)).map(this::toDomain);
    }

    @Override
    public Optional<RepairProject> findProjectByActivePlanWorkOrder(Long workOrderId, Long tenantId) {
        return Optional.ofNullable(mapper.findProjectByActivePlanWorkOrder(workOrderId, tenantId))
                .map(this::toDomain);
    }

    @Override
    public Optional<RepairProject> findProjectForUpdate(Long projectId, Long tenantId) {
        return Optional.ofNullable(mapper.findProjectForUpdate(projectId, tenantId)).map(this::toDomain);
    }

    @Override
    public List<RepairProject> listProjects(Long tenantId, Status status, String keyword, int offset, int limit) {
        return mapper.listProjects(tenantId, status == null ? null : status.name(), keyword, offset, limit).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public long countProjects(Long tenantId, Status status, String keyword) {
        return mapper.countProjects(tenantId, status == null ? null : status.name(), keyword);
    }

    @Override
    public List<PlanVersion> listPlans(Long projectId, Long tenantId) {
        return mapper.listPlans(projectId, tenantId).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<PlanVersion> findPlanForUpdate(Long planId, Long projectId, Long tenantId) {
        return Optional.ofNullable(mapper.findPlanForUpdate(planId, projectId, tenantId)).map(this::toDomain);
    }

    @Override
    public List<WorkPoint> listWorkPoints(Long planId, Long tenantId) {
        return mapper.listWorkPoints(planId, tenantId).stream()
                .map(row -> {
                    row.setLinkedWorkOrderIds(mapper.listLinkedWorkOrderIds(row.getWorkPointId()));
                    return toDomain(row);
                })
                .toList();
    }

    @Override
    public List<AllocationRoom> listAllocationRooms(Long planId, Long tenantId) {
        return mapper.listAllocationRooms(planId, tenantId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<EligibleAffectedOwner> listEligibleAffectedOwners(
            Long tenantId, RepairProject.ScopeType scopeType, Long buildingId, String unitName) {
        return mapper.listEligibleAffectedOwners(
                        tenantId, scopeType.name(), buildingId, unitName).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public PlanAffectedOwner insertPlanAffectedOwner(PlanAffectedOwner affectedOwner) {
        RepairPlanAffectedOwnerRow row = toRow(affectedOwner);
        mapper.insertPlanAffectedOwner(row);
        row.setCreateTime(affectedOwner.createTime());
        return toDomain(row);
    }

    @Override
    public List<PlanAffectedOwner> listPlanAffectedOwners(Long planId, Long tenantId) {
        return mapper.listPlanAffectedOwners(planId, tenantId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Attachment> listAttachments(Long projectId, Long tenantId) {
        return mapper.listAttachments(projectId, tenantId).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<Attachment> findAttachment(Long attachmentId, Long projectId, Long tenantId) {
        return Optional.ofNullable(mapper.findAttachment(attachmentId, projectId, tenantId)).map(this::toDomain);
    }

    @Override
    public List<PlanAttachment> listPlanAttachments(Long planId, Long tenantId) {
        return mapper.listPlanAttachments(planId, tenantId).stream().map(this::toDomain).toList();
    }

    @Override
    public Attachment insertAttachment(Attachment attachment) {
        RepairProjectAttachmentRow row = toRow(attachment);
        mapper.insertAttachment(row);
        return findAttachment(row.getAttachmentId(), attachment.projectId(), attachment.tenantId()).orElseThrow();
    }

    @Override
    public int lockPlan(Long planId, Long projectId, Long tenantId, String snapshotHash, Long lockedByUserId) {
        return mapper.lockPlan(planId, projectId, tenantId, snapshotHash, lockedByUserId);
    }

    @Override
    public int supersedeLockedPlans(Long projectId, Long tenantId, Long exceptPlanId) {
        return mapper.supersedeLockedPlans(projectId, tenantId, exceptPlanId);
    }

    @Override
    public int activatePlan(Long projectId, Long tenantId, Long planId, Integer expectedVersion) {
        return mapper.activatePlan(projectId, tenantId, planId, expectedVersion);
    }

    @Override
    public int advanceStatus(Long projectId, Long tenantId, Status expectedStatus,
                             Status nextStatus, Integer expectedVersion) {
        return mapper.advanceStatus(
                projectId, tenantId, expectedStatus.name(), nextStatus.name(), expectedVersion);
    }

    @Override
    public void insertEvent(Long projectId, Long tenantId, String action,
                            Long actorAccountId, Long actorUserId, String payloadJson) {
        mapper.insertEvent(projectId, tenantId, action, actorAccountId, actorUserId, payloadJson);
    }

    @Override
    public void insertOwnerEvent(Long projectId, Long tenantId, String action,
                                 Long actorAccountId, Long actorOwnerUid, String payloadJson) {
        mapper.insertOwnerEvent(
                projectId, tenantId, action, actorAccountId, actorOwnerUid, payloadJson);
    }

    @Override
    public List<RepairProjectProcessEvent> listProcessEvents(Long projectId, Long tenantId) {
        return mapper.listProcessEvents(projectId, tenantId).stream().map(this::toDomain).toList();
    }

    private RepairProject toDomain(RepairProjectRow row) {
        return new RepairProject(
                row.getProjectId(), row.getProjectNo(), row.getTenantId(), row.getProjectName(),
                RepairWorkflowType.valueOf(row.getWorkflowType()), RepairProject.ScopeType.valueOf(row.getScopeType()),
                row.getBuildingId(), row.getUnitName(), enumOrNull(RepairProject.FundSource.class, row.getFundSource()),
                enumOrNull(RepairProject.GovernancePath.class, row.getGovernancePath()),
                RepairProject.Status.valueOf(row.getStatus()), row.getActivePlanId(), row.getVersion(),
                row.getCreatedByAccountId(), row.getCreatedByUserId(), row.getCreateTime(), row.getUpdateTime());
    }

    private RepairProjectProcessEvent toDomain(RepairProjectProcessEventRow row) {
        return new RepairProjectProcessEvent(
                row.getEventId(), row.getProjectId(), row.getTenantId(), row.getAction(), row.getOccurredAt());
    }

    private RepairProjectRow toRow(RepairProject project) {
        RepairProjectRow row = new RepairProjectRow();
        row.setProjectId(project.projectId());
        row.setTenantId(project.tenantId());
        row.setProjectName(project.projectName());
        row.setWorkflowType(project.workflowType().name());
        row.setScopeType(project.scopeType().name());
        row.setBuildingId(project.buildingId());
        row.setUnitName(project.unitName());
        row.setFundSource(nameOrNull(project.fundSource()));
        row.setGovernancePath(nameOrNull(project.governancePath()));
        row.setStatus(project.status().name());
        row.setCreatedByAccountId(project.createdByAccountId());
        row.setCreatedByUserId(project.createdByUserId());
        return row;
    }

    private PlanVersion toDomain(RepairPlanVersionRow row) {
        return new PlanVersion(
                row.getPlanId(), row.getProjectId(), row.getTenantId(), row.getVersionNo(),
                row.getPlanDescription(), row.getBudgetTotal(),
                enumOrNull(RepairProject.FundSource.class, row.getFundSource()),
                enumOrNull(RepairProject.AllocationRuleType.class, row.getAllocationRuleType()),
                row.getAllocationRuleDescription(),
                enumOrNull(RepairSupplierSelectionMethod.class, row.getSupplierSelectionMethod()),
                row.getSupplierSelectionReason(), row.getConstructionManagementRequirements(),
                readJson(row.getEvidenceRequirementsJson(), EVIDENCE_REQUIREMENTS_TYPE),
                row.getSafetyRequirements(), row.getAcceptanceMethod(),
                readJson(row.getRequiredAcceptanceRolesJson(), STRING_LIST_TYPE),
                row.getAffectedOwnerScopeDescription(), row.getMinimumAffectedOwnerAcceptors(),
                enumOrNull(RepairProject.AffectedOwnerPassRule.class, row.getAffectedOwnerPassRule()),
                row.getAffectedOwnerApprovalRatio(),
                enumOrNull(RepairProject.SettlementMethod.class, row.getSettlementMethod()),
                row.getPlannedStartDate(), row.getPlannedCompletionDate(), row.getWarrantyDays(),
                enumOrNull(RepairProject.GovernancePath.class, row.getGovernancePath()),
                Integer.valueOf(1).equals(row.getPriceReviewRequired()),
                readJson(row.getPaymentMilestonesJson(), PAYMENT_MILESTONES_TYPE),
                RepairProject.PlanStatus.valueOf(row.getStatus()), row.getSnapshotHash(),
                row.getCreatedByAccountId(), row.getCreatedByUserId(), row.getLockedByUserId(),
                row.getCreateTime(), row.getLockedAt());
    }

    private RepairPlanVersionRow toRow(PlanVersion plan) {
        RepairPlanVersionRow row = new RepairPlanVersionRow();
        row.setPlanId(plan.planId());
        row.setProjectId(plan.projectId());
        row.setTenantId(plan.tenantId());
        row.setVersionNo(plan.versionNo());
        row.setPlanDescription(plan.planDescription());
        row.setBudgetTotal(plan.budgetTotal());
        row.setFundSource(nameOrNull(plan.fundSource()));
        row.setAllocationRuleType(nameOrNull(plan.allocationRuleType()));
        row.setAllocationRuleDescription(plan.allocationRuleDescription());
        row.setSupplierSelectionMethod(nameOrNull(plan.supplierSelectionMethod()));
        row.setSupplierSelectionReason(plan.supplierSelectionReason());
        row.setConstructionManagementRequirements(plan.constructionManagementRequirements());
        row.setEvidenceRequirementsJson(writeJson(plan.evidenceRequirements()));
        row.setSafetyRequirements(plan.safetyRequirements());
        row.setAcceptanceMethod(plan.acceptanceMethod());
        row.setRequiredAcceptanceRolesJson(writeJson(plan.requiredAcceptanceRoles()));
        row.setAffectedOwnerScopeDescription(plan.affectedOwnerScopeDescription());
        row.setMinimumAffectedOwnerAcceptors(plan.minimumAffectedOwnerAcceptors());
        row.setAffectedOwnerPassRule(nameOrNull(plan.affectedOwnerPassRule()));
        row.setAffectedOwnerApprovalRatio(plan.affectedOwnerApprovalRatio());
        row.setSettlementMethod(nameOrNull(plan.settlementMethod()));
        row.setPlannedStartDate(plan.plannedStartDate());
        row.setPlannedCompletionDate(plan.plannedCompletionDate());
        row.setWarrantyDays(plan.warrantyDays());
        row.setGovernancePath(nameOrNull(plan.governancePath()));
        row.setPriceReviewRequired(plan.priceReviewRequired() ? 1 : 0);
        row.setPaymentMilestonesJson(writeJson(plan.paymentMilestones()));
        row.setStatus(plan.status().name());
        row.setCreatedByAccountId(plan.createdByAccountId());
        row.setCreatedByUserId(plan.createdByUserId());
        return row;
    }

    private DecisionScope toDomain(RepairDecisionScopeRow row) {
        return new DecisionScope(
                row.getDecisionScopeId(), row.getProjectId(), row.getTenantId(),
                RepairProject.ScopeType.valueOf(row.getScopeType()), row.getBuildingId(), row.getUnitName(),
                RepairProject.DecisionScopeVerificationStatus.valueOf(row.getVerificationStatus()),
                row.getVerificationBasis(), Boolean.TRUE.equals(row.getLegacyReadOnly()), row.getCreateTime());
    }

    private FundingSlice toDomain(RepairFundingSliceRow row) {
        return new FundingSlice(
                row.getFundingSliceId(), row.getDecisionScopeId(), row.getProjectId(), row.getTenantId(),
                RepairProject.FundingSourceType.valueOf(row.getSourceType()), row.getSourceRecordType(),
                row.getSourceRecordId(), row.getLedgerReference(), row.getAllocationSnapshotHash(),
                row.getApprovedAmount(), RepairProject.FundingSliceVerificationStatus.valueOf(
                        row.getVerificationStatus()),
                Boolean.TRUE.equals(row.getLegacyReadOnly()), row.getVerifiedAt(), row.getCreateTime());
    }

    private RepairDecisionScopeRow toRow(DecisionScope decisionScope) {
        RepairDecisionScopeRow row = new RepairDecisionScopeRow();
        row.setDecisionScopeId(decisionScope.decisionScopeId());
        row.setProjectId(decisionScope.projectId());
        row.setTenantId(decisionScope.tenantId());
        row.setScopeType(decisionScope.scopeType().name());
        row.setBuildingId(decisionScope.buildingId());
        row.setUnitName(decisionScope.unitName());
        row.setVerificationStatus(decisionScope.verificationStatus().name());
        row.setVerificationBasis(decisionScope.verificationBasis());
        row.setLegacyReadOnly(decisionScope.legacyReadOnly());
        row.setCreateTime(decisionScope.createTime());
        return row;
    }

    private WorkPoint toDomain(RepairWorkPointRow row) {
        return new WorkPoint(
                row.getWorkPointId(), row.getProjectId(), row.getPlanId(), row.getTenantId(),
                row.getBusinessName(), row.getBuildingId(), row.getUnitName(),
                RepairProject.WorkPointLocationType.valueOf(row.getLocationType()),
                row.getReferenceRoomId(), row.getCommonAreaName(), row.getSpaceName(), row.getOrientation(),
                row.getComponent(), row.getSpecificPart(), row.getSymptom(),
                RepairProject.WorkPointCauseStatus.valueOf(row.getCauseStatus()), row.getCauseBasis(),
                row.getProposedMeasure(), row.getTechnicalRequirements(), row.getQuantity(), row.getUnit(),
                row.getPreliminaryEstimatedAmount(), row.getEstimateSource(), row.getSortOrder(),
                Boolean.TRUE.equals(row.getLegacyReadOnly()), row.getLinkedWorkOrderIds(), row.getCreateTime());
    }

    private RepairWorkPointRow toRow(WorkPoint workPoint) {
        RepairWorkPointRow row = new RepairWorkPointRow();
        row.setWorkPointId(workPoint.workPointId());
        row.setProjectId(workPoint.projectId());
        row.setPlanId(workPoint.planId());
        row.setTenantId(workPoint.tenantId());
        row.setBusinessName(workPoint.businessName());
        row.setBuildingId(workPoint.buildingId());
        row.setUnitName(workPoint.unitName());
        row.setLocationType(workPoint.locationType().name());
        row.setReferenceRoomId(workPoint.referenceRoomId());
        row.setCommonAreaName(workPoint.commonAreaName());
        row.setSpaceName(workPoint.spaceName());
        row.setOrientation(workPoint.orientation());
        row.setComponent(workPoint.component());
        row.setSpecificPart(workPoint.specificPart());
        row.setSymptom(workPoint.symptom());
        row.setCauseStatus(workPoint.causeStatus().name());
        row.setCauseBasis(workPoint.causeBasis());
        row.setProposedMeasure(workPoint.proposedMeasure());
        row.setTechnicalRequirements(workPoint.technicalRequirements());
        row.setQuantity(workPoint.quantity());
        row.setUnit(workPoint.unit());
        row.setPreliminaryEstimatedAmount(workPoint.preliminaryEstimatedAmount());
        row.setEstimateSource(workPoint.estimateSource());
        row.setSortOrder(workPoint.sortOrder());
        row.setLegacyReadOnly(workPoint.legacyReadOnly());
        return row;
    }

    private AllocationRoom toDomain(RepairPlanAllocationRoomRow row) {
        return new AllocationRoom(
                row.getAllocationRoomId(), row.getPlanId(), row.getTenantId(), row.getRoomId(),
                row.getBuildingId(), row.getUnitName(), row.getOwnerUid(), row.getBuildArea(), row.getCreateTime());
    }

    private AllocationBasis toDomain(RepairAllocationBasisRow row) {
        return new AllocationBasis(
                row.getScopeLabel(), row.getRoomCount(), row.getOwnerCount(), row.getTotalBuildArea());
    }

    private EligibleAffectedOwner toDomain(RepairEligibleAffectedOwnerRow row) {
        return new EligibleAffectedOwner(
                row.getRoomId(), row.getBuildingId(), row.getBuildingName(), row.getUnitName(),
                row.getRoomName(), row.getOwnerUid());
    }

    private PlanAffectedOwner toDomain(RepairPlanAffectedOwnerRow row) {
        return new PlanAffectedOwner(
                row.getPlanAffectedOwnerId(), row.getPlanId(), row.getTenantId(), row.getRoomId(),
                row.getBuildingId(), row.getBuildingName(), row.getUnitName(), row.getRoomName(),
                row.getOwnerUid(), row.getAffectedReason(),
                RepairProject.AffectedOwnerSourceType.valueOf(row.getSourceType()), row.getCreateTime());
    }

    private RepairPlanAffectedOwnerRow toRow(PlanAffectedOwner affectedOwner) {
        RepairPlanAffectedOwnerRow row = new RepairPlanAffectedOwnerRow();
        row.setPlanAffectedOwnerId(affectedOwner.planAffectedOwnerId());
        row.setPlanId(affectedOwner.planId());
        row.setTenantId(affectedOwner.tenantId());
        row.setRoomId(affectedOwner.roomId());
        row.setBuildingId(affectedOwner.buildingId());
        row.setBuildingName(affectedOwner.buildingName());
        row.setUnitName(affectedOwner.unitName());
        row.setRoomName(affectedOwner.roomName());
        row.setOwnerUid(affectedOwner.ownerUid());
        row.setAffectedReason(affectedOwner.affectedReason());
        row.setSourceType(affectedOwner.sourceType().name());
        row.setCreateTime(affectedOwner.createTime());
        return row;
    }

    private Attachment toDomain(RepairProjectAttachmentRow row) {
        return new Attachment(
                row.getAttachmentId(), row.getProjectId(), row.getTenantId(), row.getObjectKey(),
                row.getOriginalFileName(), row.getContentType(), row.getFileSize(), row.getEtag(), row.getSha256(),
                row.getUploadedByAccountId(), row.getUploadedByUserId(), row.getCreateTime());
    }

    private RepairProjectAttachmentRow toRow(Attachment attachment) {
        RepairProjectAttachmentRow row = new RepairProjectAttachmentRow();
        row.setAttachmentId(attachment.attachmentId());
        row.setProjectId(attachment.projectId());
        row.setTenantId(attachment.tenantId());
        row.setObjectKey(attachment.objectKey());
        row.setOriginalFileName(attachment.originalFileName());
        row.setContentType(attachment.contentType());
        row.setFileSize(attachment.fileSize());
        row.setEtag(attachment.etag());
        row.setSha256(attachment.sha256());
        row.setUploadedByAccountId(attachment.uploadedByAccountId());
        row.setUploadedByUserId(attachment.uploadedByUserId());
        return row;
    }

    private PlanAttachment toDomain(RepairPlanAttachmentRow row) {
        return new PlanAttachment(
                row.getAttachmentId(), RepairProject.AttachmentPurpose.valueOf(row.getPurpose()), row.getSortOrder());
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("维修实施方案 JSON 序列化失败", ex);
        }
    }

    private <T> T readJson(String value, TypeReference<T> type) {
        try {
            return objectMapper.readValue(value == null || value.isBlank() ? "[]" : value, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("维修实施方案 JSON 反序列化失败", ex);
        }
    }

    private <E extends Enum<E>> E enumOrNull(Class<E> type, String value) {
        return value == null ? null : Enum.valueOf(type, value);
    }

    private String nameOrNull(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
