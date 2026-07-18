// 关联业务：以明确的比较关系执行业主大会等表决事项的人数、面积参与和同意门槛。
package com.pangu.domain.model.voting;

import java.math.BigDecimal;

/**
 * 表决比例门槛。
 *
 * <p>比例本身不足以表达“超过”还是“达到及超过”。该区别会直接影响临界票的法律效果，
 * 因此由议事规则版本明确保存，不能从平台默认规则推断。
 */
public record VotingThreshold(Integer numerator, Integer denominator, Comparison comparison) {

    public enum Comparison {
        AT_LEAST,
        GREATER_THAN
    }

    public boolean isSatisfied(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        requireExecutable();
        int compared = numerator.multiply(BigDecimal.valueOf(denominator()))
                .compareTo(denominator.multiply(BigDecimal.valueOf(numerator())));
        return comparison == Comparison.AT_LEAST ? compared >= 0 : compared > 0;
    }

    public boolean isSatisfied(long numerator, long denominator) {
        return isSatisfied(BigDecimal.valueOf(numerator), BigDecimal.valueOf(denominator));
    }

    /**
     * 校验该比例是否可以作为冻结规则直接参与计票。
     *
     * <p>这里不提供任何默认比较关系，避免把“超过”误当成“达到及超过”。
     */
    public void requireExecutable() {
        if (numerator == null || denominator == null || comparison == null
                || numerator < 0 || denominator <= 0 || numerator > denominator) {
            throw new IllegalStateException("表决比例门槛未完成规则确认");
        }
    }
}
