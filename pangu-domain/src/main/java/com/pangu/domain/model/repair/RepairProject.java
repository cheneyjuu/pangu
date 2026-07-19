// 关联业务：维修工程项目、责任认定、单一决定范围、资金切片、不可变实施方案及项目附件。
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

    /**
     * 资金切片的真实来源类型。项目范围不是资金来源，只有来自可信账簿、责任认定或有效决定的切片才能进入冻结校验。
     */
    public enum FundingSourceType {
        SPECIAL_MAINTENANCE_LEDGER,
        PUBLIC_REVENUE_LEDGER,
        PROPERTY_SERVICE_CONTRACT,
        LIABLE_PARTY,
        DEVELOPER_WARRANTY,
        OWNER_SELF_FUNDING
    }

    /**
     * 本次工程由谁承担的认定路径。它不由楼栋、设备名称或报修来源自动推导。
     */
    public enum ResponsibilityPath {
        PROPERTY_SERVICE_CONTRACT,
        DEVELOPER_WARRANTY,
        LIABLE_PARTY,
        SHARED_COMMON_REPAIR
    }

    /**
     * 由已确认责任路径派生的执行状态。共有维修只表示尚需取得相关业主决定，
     * 既有授权和紧急维修必须先接入各自的可信事实链路，不能由本工程初判创建。
     */
    public enum ExecutionAuthorityType {
        CONTRACTUAL_EXECUTION,
        WARRANTY_EXECUTION,
        LIABILITY_EXECUTION,
        OWNER_DECISION
    }

    /** 项目责任认定的确认生命周期；新认定不能覆盖已确认历史。 */
    public enum ResponsibilityDeterminationStatus {
        PENDING_CONFIRMATION,
        CONFIRMED,
        SUPERSEDED,
        REJECTED
    }

    /** 资金来源、承担范围和金额快照的核验状态。 */
    public enum FundingSliceVerificationStatus {
        CONFIRMED,
        PENDING_VERIFICATION,
        LEGACY_READ_ONLY
    }

    public enum GovernancePath {
        BUILDING_REPAIR_DECISION,
        COMMUNITY_ASSEMBLY_DECISION
    }

    public enum Status {
        DRAFT,
        /**
         * 已冻结供相关业主决定审查的方案，但尚未形成可施工、可定商或可付款的执行快照。
         */
        AUTHORIZATION_IN_PROGRESS,
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
        /**
         * 为相关业主决定固定的提案版本；它不是实施锁定，不能据此签约、定商或付款。
         */
        AUTHORIZATION_FROZEN,
        LOCKED,
        SUPERSEDED
    }

    /** 决定范围经勘验和权利边界核验后的状态；待核验范围只能保留草稿。 */
    public enum DecisionScopeVerificationStatus {
        CONFIRMED,
        PENDING_VERIFICATION,
        LEGACY_READ_ONLY
    }

    /** 维修点位的结构化参照对象，公共部位不得伪造房屋编号。 */
    public enum WorkPointLocationType {
        REFERENCE_ROOM,
        COMMON_AREA
    }

    /** 原因结论必须与可核验依据一起保存，不能把推测写成既成事实。 */
    public enum WorkPointCauseStatus {
        PENDING_INVESTIGATION,
        CONFIRMED,
        UNCONFIRMED
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
            String authorizationSnapshotHash,
            Long authorizationFrozenByUserId,
            LocalDateTime authorizationFrozenAt,
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
                    warrantyDays, governancePath, priceReviewRequired, paymentMilestones, status,
                    authorizationSnapshotHash, authorizationFrozenByUserId, authorizationFrozenAt, snapshotHash,
                    createdByAccountId, createdByUserId, lockedByUserId, createTime, lockedAt);
        }
    }

    /** 一个工程只能有一个既有共有与决定范围，不承载跨范围合并。 */
    public record DecisionScope(
            Long decisionScopeId,
            Long projectId,
            Long tenantId,
            ScopeType scopeType,
            Long buildingId,
            String unitName,
            DecisionScopeVerificationStatus verificationStatus,
            String verificationBasis,
            boolean legacyReadOnly,
            LocalDateTime createTime
    ) {
    }

    /**
     * 工程责任、资金承担和派生执行状态的版本化事实。物业只能提出，具有治理权限的主体确认后才可参与锁定。
     */
    public record ResponsibilityDetermination(
            Long determinationId,
            Long projectId,
            Long tenantId,
            Integer versionNo,
            ResponsibilityDeterminationStatus status,
            ResponsibilityPath responsibilityPath,
            FundingSourceType fundingSourceType,
            ExecutionAuthorityType executionAuthorityType,
            Long basisAttachmentId,
            String basisReference,
            String responsiblePartyName,
            String responsiblePartyReference,
            BigDecimal approvedAmount,
            Long proposedByAccountId,
            Long proposedByUserId,
            LocalDateTime proposedAt,
            Long confirmedByAccountId,
            Long confirmedByUserId,
            LocalDateTime confirmedAt,
            String confirmationNote,
            LocalDateTime createTime
    ) {
    }

    /**
     * 资金承担的最小不可伪造关系。来源记录由可信账簿/责任认定/有效决定适配器写入，建项表单不得直接写入。
     */
    public record FundingSlice(
            Long fundingSliceId,
            Long responsibilityDeterminationId,
            Long decisionScopeId,
            Long projectId,
            Long tenantId,
            FundingSourceType sourceType,
            String sourceRecordType,
            String sourceRecordId,
            String ledgerReference,
            String allocationSnapshotHash,
            BigDecimal approvedAmount,
            FundingSliceVerificationStatus verificationStatus,
            boolean legacyReadOnly,
            LocalDateTime verifiedAt,
            LocalDateTime createTime
    ) {
    }

    /**
     * 可勘验的维修对象；报价、合同和结算明细可引用点位，但并不与点位一一等同。
     */
    public record WorkPoint(
            Long workPointId,
            Long projectId,
            Long planId,
            Long tenantId,
            String businessName,
            Long buildingId,
            String unitName,
            WorkPointLocationType locationType,
            Long referenceRoomId,
            String commonAreaName,
            String spaceName,
            String orientation,
            String component,
            String specificPart,
            String symptom,
            WorkPointCauseStatus causeStatus,
            String causeBasis,
            String proposedMeasure,
            String technicalRequirements,
            BigDecimal quantity,
            String unit,
            BigDecimal preliminaryEstimatedAmount,
            String estimateSource,
            Integer sortOrder,
            boolean legacyReadOnly,
            List<Long> linkedWorkOrderIds,
            LocalDateTime createTime
    ) {
        public WorkPoint {
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
            DecisionScope decisionScope,
            ResponsibilityDetermination responsibilityDetermination,
            List<ResponsibilityDetermination> responsibilityDeterminationHistory,
            List<PlanVersion> plans,
            List<WorkPoint> currentPlanWorkPoints,
            List<FundingSlice> fundingSlices,
            List<PlanAffectedOwner> currentPlanAffectedOwners,
            List<Attachment> attachments,
            List<PlanAttachment> currentPlanAttachments
    ) {
        public Details {
            responsibilityDeterminationHistory = responsibilityDeterminationHistory == null
                    ? List.of()
                    : List.copyOf(responsibilityDeterminationHistory);
            plans = plans == null ? List.of() : List.copyOf(plans);
            currentPlanWorkPoints = currentPlanWorkPoints == null
                    ? List.of()
                    : List.copyOf(currentPlanWorkPoints);
            fundingSlices = fundingSlices == null ? List.of() : List.copyOf(fundingSlices);
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
