// 关联业务：表达维修验收规则校验后的可定案结果及不可跳过的缺项。
package com.pangu.domain.model.repair;

public record RepairAcceptanceDecision(Outcome outcome, String reason) {

    public enum Outcome {
        PASSED,
        INCOMPLETE,
        RECTIFICATION_REQUIRED
    }

    public static RepairAcceptanceDecision passed() {
        return new RepairAcceptanceDecision(Outcome.PASSED, null);
    }

    public static RepairAcceptanceDecision incomplete(String reason) {
        return new RepairAcceptanceDecision(Outcome.INCOMPLETE, reason);
    }

    public static RepairAcceptanceDecision rectificationRequired(String reason) {
        return new RepairAcceptanceDecision(Outcome.RECTIFICATION_REQUIRED, reason);
    }
}
