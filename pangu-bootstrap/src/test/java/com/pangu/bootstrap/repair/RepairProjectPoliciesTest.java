// 关联业务：验证方案约定的工程验收门槛及维修资金各付款节点的后端强制规则。
package com.pangu.bootstrap.repair;

import com.pangu.domain.model.repair.RepairAcceptanceDecision;
import com.pangu.domain.model.repair.RepairAcceptancePartyRole;
import com.pangu.domain.model.repair.RepairProject;
import com.pangu.domain.model.repair.RepairProjectExecution.AcceptancePolicy;
import com.pangu.domain.model.repair.RepairProjectExecution.AcceptanceRequirement;
import com.pangu.domain.model.repair.RepairProjectExecution.AcceptanceSummary;
import com.pangu.domain.model.repair.RepairWorkflowType;
import com.pangu.domain.policy.repair.RepairPaymentEligibilityPolicy;
import com.pangu.domain.policy.repair.RepairProjectAcceptancePolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepairProjectPoliciesTest {

    private final RepairProjectAcceptancePolicy acceptance =
            new RepairProjectAcceptancePolicy.Configured();
    private final RepairPaymentEligibilityPolicy paymentEligibility =
            new RepairPaymentEligibilityPolicy.Default();

    @Test
    void communityAcceptanceRequiresExecutiveSealAndOneProfessionalCosigner() {
        AcceptancePolicy policy = communityPolicy();

        assertIncomplete(policy, communitySummary(false, true, true, false), "业委会负责人");
        assertIncomplete(policy, communitySummary(true, false, true, false), "验收文件用印");
        assertIncomplete(policy, communitySummary(true, true, false, false), "专业人员共同验收");

        assertEquals(RepairAcceptanceDecision.Outcome.PASSED,
                acceptance.evaluate(policy, communitySummary(true, true, true, false)).outcome());
        assertEquals(RepairAcceptanceDecision.Outcome.PASSED,
                acceptance.evaluate(policy, communitySummary(true, true, false, true)).outcome());
    }

    @Test
    void buildingAcceptanceUsesLockedMinimumAndApprovalRatio() {
        AcceptancePolicy policy = new AcceptancePolicy(
                1L, 2L, 3L, 10001L, RepairWorkflowType.BUILDING_REPAIR, "hash",
                "楼组长与受影响业主现场验收",
                List.of(
                        requirement("BUILDING_LEADER", "楼组长验收", Set.of(
                                RepairAcceptancePartyRole.BUILDING_LEADER)),
                        requirement("AFFECTED_OWNER", "受影响业主验收", Set.of(
                                RepairAcceptancePartyRole.AFFECTED_OWNER))),
                Set.of(RepairAcceptancePartyRole.BUILDING_LEADER), List.of(10L), "锁定方案约定",
                4, 3, RepairProject.AffectedOwnerPassRule.AT_LEAST_RATIO, new BigDecimal("0.6667"));

        assertEquals(RepairAcceptanceDecision.Outcome.INCOMPLETE,
                acceptance.evaluate(policy, new AcceptanceSummary(2, 2, 0, true,
                        false, false, false, false)).outcome());
        assertEquals(RepairAcceptanceDecision.Outcome.INCOMPLETE,
                acceptance.evaluate(policy, new AcceptanceSummary(3, 2, 0, true,
                        false, false, false, false)).outcome());
        assertEquals(RepairAcceptanceDecision.Outcome.PASSED,
                acceptance.evaluate(policy, new AcceptanceSummary(3, 3, 0, true,
                        false, false, false, false)).outcome());
    }

    @Test
    void projectScopeDoesNotInventAcceptanceParticipants() {
        AcceptancePolicy communityProjectWithPropertyAcceptance = new AcceptancePolicy(
                1L, 2L, 3L, 10001L, RepairWorkflowType.COMMUNITY_PUBLIC_REPAIR, "hash",
                "物业按竣工单现场核验",
                List.of(requirement("PROPERTY", "物业现场验收", Set.of(
                        RepairAcceptancePartyRole.PROPERTY_TECHNICAL_COSIGNER))),
                Set.of(RepairAcceptancePartyRole.PROPERTY_TECHNICAL_COSIGNER),
                List.of(10L), "合同约定", 0, 0, null, null);

        assertEquals(RepairAcceptanceDecision.Outcome.PASSED,
                acceptance.evaluate(communityProjectWithPropertyAcceptance,
                        communitySummary(false, false, true, false)).outcome());
    }

    @Test
    void advanceNeverExceedsThirtyPercentOfContract() {
        RepairPaymentEligibilityPolicy.Decision rejected = paymentEligibility.evaluate(facts(
                RepairProject.PaymentMilestoneType.ADVANCE, "0.80", RepairProject.Status.CONTRACT_EFFECTIVE,
                false, null, null, "0", "300.01", Set.of("SIGNED_CONTRACT"), null, LocalDate.now()));
        RepairPaymentEligibilityPolicy.Decision accepted = paymentEligibility.evaluate(facts(
                RepairProject.PaymentMilestoneType.ADVANCE, "0.80", RepairProject.Status.CONTRACT_EFFECTIVE,
                false, null, null, "0", "300.00", Set.of("SIGNED_CONTRACT"), null, LocalDate.now()));

        assertFalse(rejected.eligible());
        assertEquals(new BigDecimal("300.00"), rejected.upperLimit());
        assertTrue(accepted.eligible());
    }

    @Test
    void progressAndCompletionRespectReviewAndSettlementLimits() {
        RepairPaymentEligibilityPolicy.Decision progressWithoutReview = paymentEligibility.evaluate(facts(
                RepairProject.PaymentMilestoneType.PROGRESS, "1.00", RepairProject.Status.IN_PROGRESS,
                true, null, null, "0", "900.01", Set.of("PROGRESS_RECORD"), null, LocalDate.now()));
        RepairPaymentEligibilityPolicy.Decision progressWithReview = paymentEligibility.evaluate(facts(
                RepairProject.PaymentMilestoneType.PROGRESS, "1.00", RepairProject.Status.IN_PROGRESS,
                true, "850", null, "0", "850", Set.of("PROGRESS_RECORD"), null, LocalDate.now()));
        RepairPaymentEligibilityPolicy.Decision completion = paymentEligibility.evaluate(facts(
                RepairProject.PaymentMilestoneType.COMPLETION, "1.00", RepairProject.Status.COMPLETED,
                true, "880", "900", "300", "580", Set.of("ACCEPTANCE", "SETTLEMENT"),
                null, LocalDate.now()));

        assertFalse(progressWithoutReview.eligible());
        assertEquals(new BigDecimal("900.00"), progressWithoutReview.upperLimit());
        assertTrue(progressWithReview.eligible());
        assertTrue(completion.eligible());
        assertEquals(new BigDecimal("880"), completion.upperLimit());
    }

    @Test
    void warrantyReleaseRequiresResponsibilityPeriodToEnd() {
        LocalDate endDate = LocalDate.of(2026, 8, 14);
        RepairPaymentEligibilityPolicy.Decision beforeEnd = paymentEligibility.evaluate(facts(
                RepairProject.PaymentMilestoneType.WARRANTY_RELEASE, "1.00", RepairProject.Status.WARRANTY,
                false, null, "1000", "900", "100", Set.of("WARRANTY_EXPIRED_CERTIFICATE"),
                endDate, endDate.minusDays(1)));
        RepairPaymentEligibilityPolicy.Decision onEnd = paymentEligibility.evaluate(facts(
                RepairProject.PaymentMilestoneType.WARRANTY_RELEASE, "1.00", RepairProject.Status.WARRANTY,
                false, null, "1000", "900", "100", Set.of("WARRANTY_EXPIRED_CERTIFICATE"),
                endDate, endDate));

        assertFalse(beforeEnd.eligible());
        assertTrue(onEnd.eligible());
    }

    private AcceptancePolicy communityPolicy() {
        return new AcceptancePolicy(1L, 2L, 3L, 10001L,
                RepairWorkflowType.COMMUNITY_PUBLIC_REPAIR, "hash", "多方现场验收",
                List.of(
                        requirement("EXECUTIVE", "业委会负责人验收", Set.of(
                                RepairAcceptancePartyRole.COMMITTEE_EXECUTIVE_APPROVER)),
                        requirement("SEAL", "验收文件用印", Set.of(
                                RepairAcceptancePartyRole.COMMITTEE_SEAL_OPERATOR)),
                        requirement("TECHNICAL", "专业人员共同验收", Set.of(
                                RepairAcceptancePartyRole.PROPERTY_TECHNICAL_COSIGNER,
                                RepairAcceptancePartyRole.THIRD_PARTY_TECHNICAL_COSIGNER))),
                Set.of(RepairAcceptancePartyRole.COMMITTEE_EXECUTIVE_APPROVER),
                List.of(10L), "业主决定和合同约定", 0, 0, null, null);
    }

    private AcceptanceRequirement requirement(
            String code, String businessName, Set<RepairAcceptancePartyRole> roles) {
        return new AcceptanceRequirement(code, businessName, roles, 1, false);
    }

    private AcceptanceSummary communitySummary(
            boolean executive, boolean seal, boolean property, boolean thirdParty) {
        return new AcceptanceSummary(0, 0, 0, false,
                executive, seal, property, thirdParty);
    }

    private void assertIncomplete(AcceptancePolicy policy, AcceptanceSummary summary, String reasonPart) {
        RepairAcceptanceDecision decision = acceptance.evaluate(policy, summary);
        assertEquals(RepairAcceptanceDecision.Outcome.INCOMPLETE, decision.outcome());
        assertTrue(decision.reason().contains(reasonPart));
    }

    private RepairPaymentEligibilityPolicy.Facts facts(
            RepairProject.PaymentMilestoneType type,
            String configuredRatio,
            RepairProject.Status status,
            boolean priceReviewRequired,
            String reviewedAmount,
            String settlementAmount,
            String alreadyRequested,
            String requested,
            Set<String> evidence,
            LocalDate warrantyEndDate,
            LocalDate today) {
        List<String> requiredCodes = switch (type) {
            case ADVANCE -> List.of("SIGNED_CONTRACT");
            case PROGRESS -> List.of("PROGRESS_RECORD");
            case COMPLETION -> List.of("ACCEPTANCE", "SETTLEMENT");
            case WARRANTY_RELEASE -> List.of("WARRANTY_EXPIRED_CERTIFICATE");
        };
        return new RepairPaymentEligibilityPolicy.Facts(
                new RepairProject.PaymentMilestone(type, new BigDecimal(configuredRatio), requiredCodes),
                status,
                new BigDecimal("1000"),
                priceReviewRequired,
                decimal(reviewedAmount),
                decimal(settlementAmount),
                new BigDecimal(alreadyRequested),
                new BigDecimal(requested),
                evidence,
                warrantyEndDate,
                today);
    }

    private BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
