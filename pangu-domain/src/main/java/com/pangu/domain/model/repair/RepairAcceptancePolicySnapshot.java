// 关联业务：在方案征询前冻结维修项目的验收主体、人数门槛和通过门槛。
package com.pangu.domain.model.repair;

import java.time.LocalDateTime;
import java.util.Objects;

public record RepairAcceptancePolicySnapshot(
        Long policyId,
        Long workOrderId,
        Long tenantId,
        RepairWorkflowType workflowType,
        String policyHash,
        int affectedOwnerCount,
        Integer minimumAffectedOwnerParticipants,
        Integer minimumAffectedOwnerApprovals,
        Long lockedByUserId,
        LocalDateTime lockedAt
) {

    public RepairAcceptancePolicySnapshot {
        Objects.requireNonNull(workOrderId, "workOrderId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(workflowType, "workflowType must not be null");
        if (policyHash == null || policyHash.isBlank()) {
            throw new IllegalArgumentException("policyHash must not be blank");
        }
        if (affectedOwnerCount < 0) {
            throw new IllegalArgumentException("affectedOwnerCount must not be negative");
        }
        if (workflowType == RepairWorkflowType.BUILDING_REPAIR) {
            validateBuildingThresholds(
                    affectedOwnerCount,
                    minimumAffectedOwnerParticipants,
                    minimumAffectedOwnerApprovals);
        } else if (affectedOwnerCount != 0
                || minimumAffectedOwnerParticipants != null
                || minimumAffectedOwnerApprovals != null) {
            throw new IllegalArgumentException("community acceptance must not carry building owner thresholds");
        }
    }

    public static RepairAcceptancePolicySnapshot building(
            Long policyId,
            Long workOrderId,
            Long tenantId,
            String policyHash,
            int affectedOwnerCount,
            int minimumAffectedOwnerParticipants,
            int minimumAffectedOwnerApprovals,
            Long lockedByUserId,
            LocalDateTime lockedAt) {
        return new RepairAcceptancePolicySnapshot(
                policyId, workOrderId, tenantId, RepairWorkflowType.BUILDING_REPAIR,
                policyHash, affectedOwnerCount, minimumAffectedOwnerParticipants,
                minimumAffectedOwnerApprovals, lockedByUserId, lockedAt);
    }

    public static RepairAcceptancePolicySnapshot community(
            Long policyId,
            Long workOrderId,
            Long tenantId,
            String policyHash,
            Long lockedByUserId,
            LocalDateTime lockedAt) {
        return new RepairAcceptancePolicySnapshot(
                policyId, workOrderId, tenantId, RepairWorkflowType.COMMUNITY_PUBLIC_REPAIR,
                policyHash, 0, null, null, lockedByUserId, lockedAt);
    }

    private static void validateBuildingThresholds(int affectedOwnerCount,
                                                   Integer minimumParticipants,
                                                   Integer minimumApprovals) {
        if (affectedOwnerCount == 0) {
            throw new IllegalArgumentException("building acceptance requires affected owners");
        }
        if (minimumParticipants == null || minimumApprovals == null) {
            throw new IllegalArgumentException("building acceptance thresholds must be explicitly locked");
        }
        if (minimumParticipants < 1 || minimumParticipants > affectedOwnerCount) {
            throw new IllegalArgumentException("minimum participants must be within affected owner count");
        }
        if (minimumApprovals < 1 || minimumApprovals > minimumParticipants) {
            throw new IllegalArgumentException("minimum approvals must be within minimum participants");
        }
    }
}
