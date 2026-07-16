// 关联业务：维修工程项目、不可变实施方案、工程项、费用分摊及受影响业主快照与项目附件。
package com.pangu.domain.model.repair;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 维修工程项目聚合的领域读模型。
 *
 * <p>报修工单只表达问题来源；治理、合同、施工和付款以项目为业务单元。
 */
public record RepairProject(
        Long projectId,
        String projectNo,
        Long tenantId,
        String projectName,
        RepairWorkflowType workflowType,
        ScopeType scopeType,
        Long buildingId,
        String unitName,
        FundSource fundSource,
        GovernancePath governancePath,
        Status status,
        Long activePlanId,
        Integer version,
        Long createdByAccountId,
        Long createdByUserId,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {

    public enum ScopeType {
        BUILDING,
        BUILDING_UNIT,
        COMMUNITY
    }

    public enum FundSource {
        BUILDING_MAINTENANCE_FUND,
        COMMUNITY_MAINTENANCE_FUND
    }

    public enum GovernancePath {
        BUILDING_REPAIR_DECISION,
        COMMUNITY_ASSEMBLY_DECISION
    }

    public enum Status {
        DRAFT,
        PLAN_LOCKED,
        GOVERNANCE_IN_PROGRESS,
        AUTHORIZED,
        CONTRACT_EFFECTIVE,
        IN_PROGRESS,
        PENDING_ACCEPTANCE,
        COMPLETED,
        WARRANTY,
        ARCHIVED,
        CANCELLED
    }

    public enum PlanStatus {
        DRAFT,
        LOCKED,
        SUPERSEDED
    }

    public enum AllocationRuleType {
        BY_BUILDING_AREA,
        EQUAL_BY_ROOM
    }

    public enum SettlementMethod {
        ACTUAL_QUANTITY,
        FIXED_TOTAL
    }

    public enum EvidenceStage {
        BEFORE_CONSTRUCTION,
        MATERIAL_ENTRY,
        DURING_CONSTRUCTION,
        CONCEALED_WORK,
        COMPLETION,
        ACCEPTANCE
    }

    public enum PaymentMilestoneType {
        ADVANCE,
        PROGRESS,
        COMPLETION,
        WARRANTY_RELEASE
    }

    public enum AttachmentPurpose {
        ORIGINAL_QUOTE,
        SITE_PHOTO,
        OFFICIAL_DOCUMENT,
        OTHER
    }

    public enum AffectedOwnerPassRule {
        ALL,
        AT_LEAST_RATIO
    }

    public enum AffectedOwnerSourceType {
        SYSTEM_RECOMMENDED,
        PROPERTY_ADJUSTED
    }

    public record EvidenceRequirement(
            EvidenceStage stage,
            String description,
            boolean required
    ) {
    }

    public record PaymentMilestone(
            PaymentMilestoneType type,
            BigDecimal maximumContractRatio,
            List<String> requiredEvidenceCodes
    ) {
        public PaymentMilestone {
            requiredEvidenceCodes = requiredEvidenceCodes == null
                    ? List.of()
                    : List.copyOf(requiredEvidenceCodes);
        }
    }

    public record PlanVersion(
            Long planId,
            Long projectId,
            Long tenantId,
            Integer versionNo,
            String planDescription,
            BigDecimal budgetTotal,
            FundSource fundSource,
            AllocationRuleType allocationRuleType,
            String allocationRuleDescription,
            RepairSupplierSelectionMethod supplierSelectionMethod,
            String supplierSelectionReason,
            String constructionManagementRequirements,
            List<EvidenceRequirement> evidenceRequirements,
            String safetyRequirements,
            String acceptanceMethod,
            List<String> requiredAcceptanceRoles,
            String affectedOwnerScopeDescription,
            Integer minimumAffectedOwnerAcceptors,
            AffectedOwnerPassRule affectedOwnerPassRule,
            BigDecimal affectedOwnerApprovalRatio,
            SettlementMethod settlementMethod,
            LocalDate plannedStartDate,
            LocalDate plannedCompletionDate,
            Integer warrantyDays,
            GovernancePath governancePath,
            boolean priceReviewRequired,
            List<PaymentMilestone> paymentMilestones,
            PlanStatus status,
            String snapshotHash,
            Long createdByAccountId,
            Long createdByUserId,
            Long lockedByUserId,
            LocalDateTime createTime,
            LocalDateTime lockedAt
    ) {
        public PlanVersion {
            evidenceRequirements = evidenceRequirements == null ? List.of() : List.copyOf(evidenceRequirements);
            requiredAcceptanceRoles = requiredAcceptanceRoles == null
                    ? List.of()
                    : List.copyOf(requiredAcceptanceRoles);
            paymentMilestones = paymentMilestones == null ? List.of() : List.copyOf(paymentMilestones);
        }

        public PlanVersion withPlanDescription(String resolvedPlanDescription) {
            return new PlanVersion(
                    planId, projectId, tenantId, versionNo, resolvedPlanDescription,
                    budgetTotal, fundSource, allocationRuleType, allocationRuleDescription,
                    supplierSelectionMethod, supplierSelectionReason, constructionManagementRequirements,
                    evidenceRequirements, safetyRequirements, acceptanceMethod, requiredAcceptanceRoles,
                    affectedOwnerScopeDescription, minimumAffectedOwnerAcceptors, affectedOwnerPassRule,
                    affectedOwnerApprovalRatio, settlementMethod, plannedStartDate, plannedCompletionDate,
                    warrantyDays, governancePath, priceReviewRequired, paymentMilestones, status, snapshotHash,
                    createdByAccountId, createdByUserId, lockedByUserId, createTime, lockedAt);
        }
    }

    public record Item(
            Long itemId,
            Long projectId,
            Long planId,
            Long tenantId,
            String itemNo,
            Long buildingId,
            String unitName,
            Long roomId,
            String locationText,
            String workContent,
            BigDecimal quantity,
            String unit,
            BigDecimal estimatedUnitPrice,
            BigDecimal estimatedAmount,
            Integer sortOrder,
            List<Long> linkedWorkOrderIds,
            LocalDateTime createTime
    ) {
        public Item {
            linkedWorkOrderIds = linkedWorkOrderIds == null ? List.of() : List.copyOf(linkedWorkOrderIds);
        }
    }

    public record AllocationRoom(
            Long allocationRoomId,
            Long planId,
            Long tenantId,
            Long roomId,
            Long buildingId,
            String unitName,
            Long ownerUid,
            BigDecimal buildArea,
            LocalDateTime createTime
    ) {
    }

    /** 项目创建前从已核验产权名册汇总出的费用承担范围，不包含逐户身份信息。 */
    public record AllocationBasis(
            String scopeLabel,
            long roomCount,
            long ownerCount,
            BigDecimal totalBuildArea
    ) {
    }

    /** 管理端只读展示的法定分摊预览，创建方案时由后端再次解析并固化。 */
    public record AllocationPreview(
            ScopeType scopeType,
            FundSource fundSource,
            String scopeLabel,
            long roomCount,
            long ownerCount,
            BigDecimal totalBuildArea,
            AllocationRuleType allocationRuleType,
            String allocationRuleDescription,
            String legalBasis
    ) {
    }

    /** 后端从已核验产权名册解析出的可选受影响业主房屋，包含持久化所需身份但不直接返回管理端。 */
    public record EligibleAffectedOwner(
            Long roomId,
            Long buildingId,
            String buildingName,
            String unitName,
            String roomName,
            Long ownerUid
    ) {
    }

    /** 管理端可确认或调整的受影响业主候选房屋，不披露业主身份。 */
    public record AffectedOwnerCandidate(
            Long roomId,
            Long buildingId,
            String buildingName,
            String unitName,
            String roomName,
            String affectedReason
    ) {
    }

    /** 项目创建前由系统根据维修范围生成的受影响业主候选名单。 */
    public record AffectedOwnerPreview(
            String scopeLabel,
            long recommendedOwnerCount,
            List<AffectedOwnerCandidate> candidates
    ) {
        public AffectedOwnerPreview {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
        }
    }

    /** 实施方案内独立于费用分摊范围固化的楼栋维修受影响业主。 */
    public record PlanAffectedOwner(
            Long planAffectedOwnerId,
            Long planId,
            Long tenantId,
            Long roomId,
            Long buildingId,
            String buildingName,
            String unitName,
            String roomName,
            Long ownerUid,
            String affectedReason,
            AffectedOwnerSourceType sourceType,
            LocalDateTime createTime
    ) {
    }

    public record Attachment(
            Long attachmentId,
            Long projectId,
            Long tenantId,
            String objectKey,
            String originalFileName,
            String contentType,
            Long fileSize,
            String etag,
            String sha256,
            Long uploadedByAccountId,
            Long uploadedByUserId,
            LocalDateTime createTime
    ) {
    }

    public record PlanAttachment(
            Long attachmentId,
            AttachmentPurpose purpose,
            Integer sortOrder
    ) {
    }

    public record Details(
            RepairProject project,
            List<PlanVersion> plans,
            List<Item> currentPlanItems,
            List<AllocationRoom> currentPlanAllocationRooms,
            List<PlanAffectedOwner> currentPlanAffectedOwners,
            List<Attachment> attachments,
            List<PlanAttachment> currentPlanAttachments
    ) {
        public Details {
            plans = plans == null ? List.of() : List.copyOf(plans);
            currentPlanItems = currentPlanItems == null ? List.of() : List.copyOf(currentPlanItems);
            currentPlanAllocationRooms = currentPlanAllocationRooms == null
                    ? List.of()
                    : List.copyOf(currentPlanAllocationRooms);
            currentPlanAffectedOwners = currentPlanAffectedOwners == null
                    ? List.of()
                    : List.copyOf(currentPlanAffectedOwners);
            attachments = attachments == null ? List.of() : List.copyOf(attachments);
            currentPlanAttachments = currentPlanAttachments == null
                    ? List.of()
                    : List.copyOf(currentPlanAttachments);
        }
    }
}
