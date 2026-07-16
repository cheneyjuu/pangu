// 关联业务：实现维修工程项目、实施方案版本、工程项、费用分摊快照与项目附件的数据访问。
package com.pangu.infrastructure.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.domain.model.repair.RepairProject;
import com.pangu.domain.model.repair.RepairProject.AllocationRoom;
import com.pangu.domain.model.repair.RepairProject.AllocationBasis;
import com.pangu.domain.model.repair.RepairProject.Attachment;
import com.pangu.domain.model.repair.RepairProject.EvidenceRequirement;
import com.pangu.domain.model.repair.RepairProject.Item;
import com.pangu.domain.model.repair.RepairProject.PaymentMilestone;
import com.pangu.domain.model.repair.RepairProject.PlanAttachment;
import com.pangu.domain.model.repair.RepairProject.PlanVersion;
import com.pangu.domain.model.repair.RepairProject.Status;
import com.pangu.domain.model.repair.RepairSupplierSelectionMethod;
import com.pangu.domain.model.repair.RepairWorkflowType;
import com.pangu.domain.repository.RepairProjectRepository;
import com.pangu.infrastructure.persistence.entity.RepairPlanAllocationRoomRow;
import com.pangu.infrastructure.persistence.entity.RepairAllocationBasisRow;
import com.pangu.infrastructure.persistence.entity.RepairPlanAttachmentRow;
import com.pangu.infrastructure.persistence.entity.RepairPlanVersionRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectAttachmentRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectItemRow;
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
    public Item insertItem(Item item) {
        RepairProjectItemRow row = toRow(item);
        mapper.insertItem(row);
        row.setLinkedWorkOrderIds(List.of());
        return toDomain(row);
    }

    @Override
    public void linkItemToWorkOrder(Long itemId, Long workOrderId, Long tenantId) {
        mapper.linkItemToWorkOrder(itemId, workOrderId, tenantId);
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
    public List<Item> listItems(Long planId, Long tenantId) {
        return mapper.listItems(planId, tenantId).stream()
                .map(row -> {
                    row.setLinkedWorkOrderIds(mapper.listLinkedWorkOrderIds(row.getItemId()));
                    return toDomain(row);
                })
                .toList();
    }

    @Override
    public List<AllocationRoom> listAllocationRooms(Long planId, Long tenantId) {
        return mapper.listAllocationRooms(planId, tenantId).stream().map(this::toDomain).toList();
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

    private RepairProject toDomain(RepairProjectRow row) {
        return new RepairProject(
                row.getProjectId(), row.getProjectNo(), row.getTenantId(), row.getProjectName(),
                RepairWorkflowType.valueOf(row.getWorkflowType()), RepairProject.ScopeType.valueOf(row.getScopeType()),
                row.getBuildingId(), row.getUnitName(), RepairProject.FundSource.valueOf(row.getFundSource()),
                RepairProject.GovernancePath.valueOf(row.getGovernancePath()),
                RepairProject.Status.valueOf(row.getStatus()), row.getActivePlanId(), row.getVersion(),
                row.getCreatedByAccountId(), row.getCreatedByUserId(), row.getCreateTime(), row.getUpdateTime());
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
        row.setFundSource(project.fundSource().name());
        row.setGovernancePath(project.governancePath().name());
        row.setStatus(project.status().name());
        row.setCreatedByAccountId(project.createdByAccountId());
        row.setCreatedByUserId(project.createdByUserId());
        return row;
    }

    private PlanVersion toDomain(RepairPlanVersionRow row) {
        return new PlanVersion(
                row.getPlanId(), row.getProjectId(), row.getTenantId(), row.getVersionNo(),
                row.getPlanDescription(), row.getBudgetTotal(),
                RepairProject.FundSource.valueOf(row.getFundSource()),
                RepairProject.AllocationRuleType.valueOf(row.getAllocationRuleType()),
                row.getAllocationRuleDescription(),
                RepairSupplierSelectionMethod.valueOf(row.getSupplierSelectionMethod()),
                row.getSupplierSelectionReason(), row.getConstructionManagementRequirements(),
                readJson(row.getEvidenceRequirementsJson(), EVIDENCE_REQUIREMENTS_TYPE),
                row.getSafetyRequirements(), row.getAcceptanceMethod(),
                readJson(row.getRequiredAcceptanceRolesJson(), STRING_LIST_TYPE),
                row.getAffectedOwnerScopeDescription(), row.getMinimumAffectedOwnerAcceptors(),
                enumOrNull(RepairProject.AffectedOwnerPassRule.class, row.getAffectedOwnerPassRule()),
                row.getAffectedOwnerApprovalRatio(),
                RepairProject.SettlementMethod.valueOf(row.getSettlementMethod()),
                row.getPlannedStartDate(), row.getPlannedCompletionDate(), row.getWarrantyDays(),
                RepairProject.GovernancePath.valueOf(row.getGovernancePath()),
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
        row.setFundSource(plan.fundSource().name());
        row.setAllocationRuleType(plan.allocationRuleType().name());
        row.setAllocationRuleDescription(plan.allocationRuleDescription());
        row.setSupplierSelectionMethod(plan.supplierSelectionMethod().name());
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
        row.setSettlementMethod(plan.settlementMethod().name());
        row.setPlannedStartDate(plan.plannedStartDate());
        row.setPlannedCompletionDate(plan.plannedCompletionDate());
        row.setWarrantyDays(plan.warrantyDays());
        row.setGovernancePath(plan.governancePath().name());
        row.setPriceReviewRequired(plan.priceReviewRequired() ? 1 : 0);
        row.setPaymentMilestonesJson(writeJson(plan.paymentMilestones()));
        row.setStatus(plan.status().name());
        row.setCreatedByAccountId(plan.createdByAccountId());
        row.setCreatedByUserId(plan.createdByUserId());
        return row;
    }

    private Item toDomain(RepairProjectItemRow row) {
        return new Item(
                row.getItemId(), row.getProjectId(), row.getPlanId(), row.getTenantId(), row.getItemNo(),
                row.getBuildingId(), row.getUnitName(), row.getRoomId(), row.getLocationText(),
                row.getWorkContent(), row.getQuantity(), row.getUnit(), row.getEstimatedUnitPrice(),
                row.getEstimatedAmount(), row.getSortOrder(), row.getLinkedWorkOrderIds(), row.getCreateTime());
    }

    private RepairProjectItemRow toRow(Item item) {
        RepairProjectItemRow row = new RepairProjectItemRow();
        row.setItemId(item.itemId());
        row.setProjectId(item.projectId());
        row.setPlanId(item.planId());
        row.setTenantId(item.tenantId());
        row.setItemNo(item.itemNo());
        row.setBuildingId(item.buildingId());
        row.setUnitName(item.unitName());
        row.setRoomId(item.roomId());
        row.setLocationText(item.locationText());
        row.setWorkContent(item.workContent());
        row.setQuantity(item.quantity());
        row.setUnit(item.unit());
        row.setEstimatedUnitPrice(item.estimatedUnitPrice());
        row.setEstimatedAmount(item.estimatedAmount());
        row.setSortOrder(item.sortOrder());
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
            return objectMapper.readValue(value, type);
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
