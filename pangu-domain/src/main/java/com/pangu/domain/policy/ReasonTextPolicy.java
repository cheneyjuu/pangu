package com.pangu.domain.policy;

/**
 * 申请理由文本水文检测策略端口（Hexagonal Port）。
 *
 * <p>application 层通过本端口校验 waiver 申请理由：
 * <ul>
 *   <li>实质字符占比（去除空白与标点）</li>
 *   <li>香农熵（字符分布多样性）</li>
 *   <li>3-gram 重复率（短语重复）</li>
 * </ul>
 *
 * <p>实现位置：{@code pangu-infrastructure/.../validator/ReasonTextValidator}。
 * 纯 Java 实现，不依赖 LLM；阈值定义在实现内部。
 */
public interface ReasonTextPolicy {

    /**
     * 校验文本是否满足实质性要求。
     *
     * @param text 待校验文本
     * @return 校验结果（含失败原因与可读 message）
     */
    ValidationResult validate(String text);

    enum FailureReason {
        /** 文本为空或仅含空白。 */
        NULL_OR_EMPTY,
        /** 实质字符不足。 */
        TOO_SHORT,
        /** 实质字符占比过低（空白/标点填充）。 */
        LOW_SUBSTANTIVE_RATIO,
        /** 香农熵过低（字符多样性不足）。 */
        LOW_ENTROPY,
        /** 3-gram 重复率过高（短语重复填充）。 */
        HIGH_REPETITION
    }

    /**
     * 校验结果（不可变值对象）。
     */
    record ValidationResult(boolean valid, FailureReason failureReason, String message) {
        public static ValidationResult success() {
            return new ValidationResult(true, null, null);
        }

        public static ValidationResult fail(FailureReason reason, String message) {
            return new ValidationResult(false, reason, message);
        }
    }
}
