// 关联业务：依据合同、审价、验收结算、付款节点和结构化材料计算维修资金付款资格。
package com.pangu.domain.policy.repair;

import com.pangu.domain.model.repair.RepairProject.PaymentMilestone;
import com.pangu.domain.model.repair.RepairProject.PaymentMilestoneType;
import com.pangu.domain.model.repair.RepairProject.Status;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

public interface RepairPaymentEligibilityPolicy {

    Decision evaluate(Facts facts);

    record Facts(
            PaymentMilestone milestone,
            Status projectStatus,
            BigDecimal contractAmount,
            boolean priceReviewRequired,
            BigDecimal reviewedAmount,
            BigDecimal verifiedSettlementAmount,
            BigDecimal alreadyRequestedAmount,
            BigDecimal requestedAmount,
            Set<String> suppliedEvidenceCodes,
            LocalDate warrantyEndDate,
            LocalDate today
    ) {
        public Facts {
            suppliedEvidenceCodes = suppliedEvidenceCodes == null
                    ? Set.of()
                    : Set.copyOf(suppliedEvidenceCodes);
        }
    }

    record Decision(boolean eligible, BigDecimal cumulativeAmount, BigDecimal upperLimit, Set<String> missingCodes,
                    String reason) {
        public Decision {
            missingCodes = missingCodes == null ? Set.of() : Set.copyOf(missingCodes);
        }

        public static Decision rejected(Set<String> missingCodes, String reason) {
            return new Decision(false, null, null, missingCodes, reason);
        }
    }

    final class Default implements RepairPaymentEligibilityPolicy {

        private static final BigDecimal LEGAL_ADVANCE_RATIO = new BigDecimal("0.30");
        private static final BigDecimal LEGAL_PRE_REVIEW_PROGRESS_RATIO = new BigDecimal("0.90");

        @Override
        public Decision evaluate(Facts facts) {
            Set<String> missing = new LinkedHashSet<>(facts.milestone().requiredEvidenceCodes());
            missing.removeAll(facts.suppliedEvidenceCodes());
            if (!missing.isEmpty()) {
                return Decision.rejected(missing, "付款节点缺少结构化必需材料");
            }
            BigDecimal cumulative = facts.alreadyRequestedAmount().add(facts.requestedAmount());
            BigDecimal configuredLimit = facts.contractAmount()
                    .multiply(facts.milestone().maximumContractRatio())
                    .setScale(2, RoundingMode.DOWN);
            BigDecimal upperLimit = switch (facts.milestone().type()) {
                case ADVANCE -> advanceLimit(facts, configuredLimit);
                case PROGRESS -> progressLimit(facts, configuredLimit);
                case COMPLETION -> completionLimit(facts, configuredLimit);
                case WARRANTY_RELEASE -> warrantyLimit(facts, configuredLimit);
            };
            if (upperLimit == null) {
                return Decision.rejected(Set.of(), "当前项目状态或证明不满足付款节点条件");
            }
            if (cumulative.compareTo(upperLimit) > 0) {
                return new Decision(false, cumulative, upperLimit, Set.of(), "累计付款申请超过当前可申请上限");
            }
            return new Decision(true, cumulative, upperLimit, Set.of(), null);
        }

        private BigDecimal advanceLimit(Facts facts, BigDecimal configuredLimit) {
            if (facts.projectStatus() != Status.CONTRACT_EFFECTIVE
                    && facts.projectStatus() != Status.IN_PROGRESS) {
                return null;
            }
            return configuredLimit.min(facts.contractAmount()
                    .multiply(LEGAL_ADVANCE_RATIO).setScale(2, RoundingMode.DOWN));
        }

        private BigDecimal progressLimit(Facts facts, BigDecimal configuredLimit) {
            if (facts.projectStatus() != Status.IN_PROGRESS
                    && facts.projectStatus() != Status.PENDING_ACCEPTANCE) {
                return null;
            }
            if (!facts.priceReviewRequired()) {
                return configuredLimit;
            }
            return facts.reviewedAmount() == null
                    ? configuredLimit.min(facts.contractAmount()
                    .multiply(LEGAL_PRE_REVIEW_PROGRESS_RATIO).setScale(2, RoundingMode.DOWN))
                    : configuredLimit.min(facts.reviewedAmount());
        }

        private BigDecimal completionLimit(Facts facts, BigDecimal configuredLimit) {
            if (facts.projectStatus() != Status.COMPLETED
                    && facts.projectStatus() != Status.WARRANTY
                    && facts.projectStatus() != Status.ARCHIVED) {
                return null;
            }
            if (facts.verifiedSettlementAmount() == null) {
                return null;
            }
            BigDecimal limit = configuredLimit.min(facts.verifiedSettlementAmount());
            return facts.reviewedAmount() == null ? limit : limit.min(facts.reviewedAmount());
        }

        private BigDecimal warrantyLimit(Facts facts, BigDecimal configuredLimit) {
            if (facts.warrantyEndDate() == null || facts.today().isBefore(facts.warrantyEndDate())) {
                return null;
            }
            return completionLimit(facts, configuredLimit);
        }
    }
}
