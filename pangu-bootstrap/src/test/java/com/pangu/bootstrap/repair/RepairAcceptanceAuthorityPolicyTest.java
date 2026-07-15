// 关联业务：验证楼栋维修与全小区公共维修使用彼此独立、不可互相替代的验收规则。
package com.pangu.bootstrap.repair;

import com.pangu.domain.model.repair.RepairAcceptanceDecision;
import com.pangu.domain.model.repair.RepairAcceptancePolicySnapshot;
import com.pangu.domain.model.repair.RepairAcceptanceSummary;
import com.pangu.domain.model.repair.RepairWorkflowType;
import com.pangu.domain.policy.repair.BuildingRepairAcceptanceAuthorityPolicy;
import com.pangu.domain.policy.repair.CommunityRepairAcceptanceAuthorityPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepairAcceptanceAuthorityPolicyTest {

    private final BuildingRepairAcceptanceAuthorityPolicy buildingPolicy =
            new BuildingRepairAcceptanceAuthorityPolicy();
    private final CommunityRepairAcceptanceAuthorityPolicy communityPolicy =
            new CommunityRepairAcceptanceAuthorityPolicy();

    @Test
    void buildingRepairRequiresLeaderAndLockedOwnerThresholds() {
        RepairAcceptancePolicySnapshot snapshot = RepairAcceptancePolicySnapshot.building(
                null, 100L, 10001L, "policy-hash", 3, 2, 2, 800201L, null);

        RepairAcceptanceDecision missingOwner = buildingPolicy.evaluate(snapshot,
                summary(3, 1, 1, 0, true, false, false, false, false));
        assertEquals(RepairAcceptanceDecision.Outcome.INCOMPLETE, missingOwner.outcome());
        assertTrue(missingOwner.reason().contains("最低有效验收人数"));

        RepairAcceptanceDecision missingLeader = buildingPolicy.evaluate(snapshot,
                summary(3, 2, 2, 0, false, false, false, false, false));
        assertEquals(RepairAcceptanceDecision.Outcome.INCOMPLETE, missingLeader.outcome());
        assertTrue(missingLeader.reason().contains("楼组长"));

        RepairAcceptanceDecision passed = buildingPolicy.evaluate(snapshot,
                summary(3, 2, 2, 0, true, false, false, false, false));
        assertEquals(RepairAcceptanceDecision.Outcome.PASSED, passed.outcome());
    }

    @Test
    void anyExplicitRectificationBlocksBuildingAcceptance() {
        RepairAcceptancePolicySnapshot snapshot = RepairAcceptancePolicySnapshot.building(
                null, 100L, 10001L, "policy-hash", 2, 1, 1, 800201L, null);

        RepairAcceptanceDecision decision = buildingPolicy.evaluate(snapshot,
                summary(2, 2, 1, 1, true, false, false, false, false));

        assertEquals(RepairAcceptanceDecision.Outcome.RECTIFICATION_REQUIRED, decision.outcome());
    }

    @Test
    void communityRepairRequiresExecutiveApprovalSealAndProfessionalCosign() {
        RepairAcceptancePolicySnapshot snapshot = RepairAcceptancePolicySnapshot.community(
                null, 200L, 10001L, "policy-hash", 800201L, null);

        assertIncompleteCommunity(snapshot,
                summary(0, 0, 0, 0, false, false, true, true, false), "主任或副主任");
        assertIncompleteCommunity(snapshot,
                summary(0, 0, 0, 0, false, true, false, true, false), "业委会公章");
        assertIncompleteCommunity(snapshot,
                summary(0, 0, 0, 0, false, true, true, false, false), "物业或第三方");

        RepairAcceptanceDecision propertyCosigned = communityPolicy.evaluate(snapshot,
                summary(0, 0, 0, 0, false, true, true, true, false));
        assertEquals(RepairAcceptanceDecision.Outcome.PASSED, propertyCosigned.outcome());

        RepairAcceptanceDecision thirdPartyCosigned = communityPolicy.evaluate(snapshot,
                summary(0, 0, 0, 0, false, true, true, false, true));
        assertEquals(RepairAcceptanceDecision.Outcome.PASSED, thirdPartyCosigned.outcome());
    }

    @Test
    void policySnapshotRejectsPlatformDefaultsForBuildingRepair() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new RepairAcceptancePolicySnapshot(
                        null, 100L, 10001L, RepairWorkflowType.BUILDING_REPAIR,
                        "policy-hash", 3, null, null, 800201L, null));
    }

    private void assertIncompleteCommunity(RepairAcceptancePolicySnapshot snapshot,
                                           RepairAcceptanceSummary summary,
                                           String reasonFragment) {
        RepairAcceptanceDecision decision = communityPolicy.evaluate(snapshot, summary);
        assertEquals(RepairAcceptanceDecision.Outcome.INCOMPLETE, decision.outcome());
        assertTrue(decision.reason().contains(reasonFragment));
    }

    private RepairAcceptanceSummary summary(int affectedOwnerCount,
                                             int participatingOwnerCount,
                                             int passedOwnerCount,
                                             int rectificationCount,
                                             boolean buildingLeaderPassed,
                                             boolean executivePassed,
                                             boolean sealApplied,
                                             boolean propertyCosigned,
                                             boolean thirdPartyCosigned) {
        return new RepairAcceptanceSummary(
                affectedOwnerCount,
                participatingOwnerCount,
                passedOwnerCount,
                rectificationCount,
                buildingLeaderPassed,
                executivePassed,
                sealApplied,
                propertyCosigned,
                thirdPartyCosigned);
    }
}
