package com.pangu.infrastructure.validator;

import com.pangu.domain.policy.ReasonTextPolicy;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Waiver 申请理由水文检测器（纯 Java，不依赖 LLM）—— {@link ReasonTextPolicy} 默认实现。
 *
 * <p>三层防线（任一不达标即拒绝）：
 * <ol>
 *   <li>实质字符占比 ≥ {@value #MIN_SUBSTANTIVE_RATIO}（去除空白/标点后字符数与原长度比）</li>
 *   <li>香农熵 ≥ {@value #MIN_SHANNON_ENTROPY}（衡量字符分布多样性）</li>
 *   <li>3-gram 重复率 ≤ {@value #MAX_TRIGRAM_REPETITION}（衡量短语重复）</li>
 * </ol>
 *
 * <p>设计原则：
 * <ul>
 *   <li>只做检测，不抛业务异常——异常类型由调用方决定（保持 infrastructure 不依赖 interfaces）</li>
 *   <li>阈值参数化：未来可改为可配置 properties，本期固化常量便于审计</li>
 * </ul>
 */
@Component
public class ReasonTextValidator implements ReasonTextPolicy {

    /** 最少实质字符数（去除空白与标点）。 */
    public static final int MIN_SUBSTANTIVE_CHARS = 50;

    /** 实质字符占比下限。 */
    public static final double MIN_SUBSTANTIVE_RATIO = 0.80;

    /** 香农熵下限（中文常态文本 ~ 3.5；纯重复短语 < 2.5）。 */
    public static final double MIN_SHANNON_ENTROPY = 2.5;

    /** 3-gram 重复率上限。 */
    public static final double MAX_TRIGRAM_REPETITION = 0.30;

    @Override
    public ValidationResult validate(String text) {
        if (text == null || text.isBlank()) {
            return ValidationResult.fail(FailureReason.NULL_OR_EMPTY, "理由不能为空");
        }
        // 1. 实质字符占比
        String substantive = stripNonSubstantive(text);
        if (substantive.length() < MIN_SUBSTANTIVE_CHARS) {
            return ValidationResult.fail(FailureReason.TOO_SHORT,
                    "实质字符不足：" + substantive.length() + " < " + MIN_SUBSTANTIVE_CHARS);
        }
        double substantiveRatio = (double) substantive.length() / text.length();
        if (substantiveRatio < MIN_SUBSTANTIVE_RATIO) {
            return ValidationResult.fail(FailureReason.LOW_SUBSTANTIVE_RATIO,
                    String.format("实质字符占比 %.2f 低于阈值 %.2f", substantiveRatio, MIN_SUBSTANTIVE_RATIO));
        }
        // 2. 香农熵
        double entropy = shannonEntropy(substantive);
        if (entropy < MIN_SHANNON_ENTROPY) {
            return ValidationResult.fail(FailureReason.LOW_ENTROPY,
                    String.format("字符多样性不足（熵=%.2f）", entropy));
        }
        // 3. 3-gram 重复率
        double trigramRepetition = trigramRepetitionRate(substantive);
        if (trigramRepetition > MAX_TRIGRAM_REPETITION) {
            return ValidationResult.fail(FailureReason.HIGH_REPETITION,
                    String.format("内容重复率过高（%.2f）", trigramRepetition));
        }
        return ValidationResult.success();
    }

    private static String stripNonSubstantive(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        text.codePoints().forEach(cp -> {
            if (!Character.isWhitespace(cp) && !isPunctuation(cp)) {
                sb.appendCodePoint(cp);
            }
        });
        return sb.toString();
    }

    private static boolean isPunctuation(int cp) {
        // ASCII 标点 + CJK 标点常用区
        if (cp < 128) {
            return ",.!?;:'\"()[]{}<>-_+=*/\\|@#$%^&~`".indexOf(cp) >= 0;
        }
        return (cp >= 0x3000 && cp <= 0x303F)   // CJK Symbols and Punctuation
                || (cp >= 0xFF00 && cp <= 0xFFEF); // Halfwidth and Fullwidth Forms
    }

    private static double shannonEntropy(String text) {
        if (text.isEmpty()) {
            return 0;
        }
        Map<Integer, Integer> counts = new HashMap<>();
        text.codePoints().forEach(cp -> counts.merge(cp, 1, Integer::sum));
        int total = (int) text.codePoints().count();
        double entropy = 0;
        for (int c : counts.values()) {
            double p = (double) c / total;
            entropy -= p * (Math.log(p) / Math.log(2));
        }
        return entropy;
    }

    private static double trigramRepetitionRate(String text) {
        int[] cps = text.codePoints().toArray();
        if (cps.length < 3) {
            return 0;
        }
        int totalTrigrams = cps.length - 2;
        Map<String, Integer> trigramCount = new HashMap<>();
        for (int i = 0; i < totalTrigrams; i++) {
            String key = new StringBuilder()
                    .appendCodePoint(cps[i])
                    .appendCodePoint(cps[i + 1])
                    .appendCodePoint(cps[i + 2])
                    .toString();
            trigramCount.merge(key, 1, Integer::sum);
        }
        // 重复率 = (出现次数 > 1 的 trigram 总占比)
        int repeated = 0;
        for (int count : trigramCount.values()) {
            if (count > 1) {
                repeated += count;
            }
        }
        return (double) repeated / totalTrigrams;
    }
}
