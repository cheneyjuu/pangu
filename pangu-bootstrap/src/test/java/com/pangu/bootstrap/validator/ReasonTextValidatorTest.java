package com.pangu.bootstrap.validator;

import com.pangu.domain.policy.ReasonTextPolicy;
import com.pangu.domain.policy.ReasonTextPolicy.FailureReason;
import com.pangu.infrastructure.validator.ReasonTextValidator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ReasonTextValidator} 水文检测纯函数测试。
 *
 * <p>三层防线分别构造典型 fixture：
 * <ul>
 *   <li>NULL_OR_EMPTY / TOO_SHORT：空串、纯空白、过短</li>
 *   <li>LOW_SUBSTANTIVE_RATIO：纯空白填充凑长度</li>
 *   <li>LOW_ENTROPY：极少种字符大量重复</li>
 *   <li>HIGH_REPETITION：3-gram 大量重复</li>
 *   <li>success：真实业务理由（中文 ≥ 50 字符 + 多样字符 + 少重复）</li>
 * </ul>
 *
 * <p>纯 POJO 测试，不需要 Spring 容器。
 */
public class ReasonTextValidatorTest {

    private final ReasonTextPolicy validator = new ReasonTextValidator();

    @Test
    public void rejectsNullAndBlank() {
        assertEquals(FailureReason.NULL_OR_EMPTY, validator.validate(null).failureReason());
        assertEquals(FailureReason.NULL_OR_EMPTY, validator.validate("").failureReason());
        assertEquals(FailureReason.NULL_OR_EMPTY, validator.validate("   \t\n  ").failureReason());
    }

    @Test
    public void rejectsTooShortRealistic() {
        // 仅 10 个实质字符（<50）
        ReasonTextPolicy.ValidationResult result = validator.validate("党员人数太少");
        assertFalse(result.valid());
        assertEquals(FailureReason.TOO_SHORT, result.failureReason());
    }

    @Test
    public void rejectsPureWhitespacePadding() {
        // 实质字符 30 个但被大量空格填充至 200 长 → 实质占比 < 0.8
        StringBuilder sb = new StringBuilder();
        // 60 个实质字符
        sb.append("党员实在严重不足无法满足比例党员实在严重不足无法满足比例党员实在严重不足无法满足比例党员实在严重不足无法满足比例");
        // 加上 200 个空格
        for (int i = 0; i < 200; i++) sb.append(' ');
        ReasonTextPolicy.ValidationResult result = validator.validate(sb.toString());
        assertFalse(result.valid());
        assertEquals(FailureReason.LOW_SUBSTANTIVE_RATIO, result.failureReason());
    }

    @Test
    public void rejectsLowEntropyRepeatedChar() {
        // 200 个 "啊"（实质字符够，占比 100%，但熵 = 0 < 2.5）
        String s = "啊".repeat(200);
        ReasonTextPolicy.ValidationResult result = validator.validate(s);
        assertFalse(result.valid());
        assertEquals(FailureReason.LOW_ENTROPY, result.failureReason());
    }

    @Test
    public void rejectsHighTrigramRepetition() {
        // 「党员不足」短语重复 50 次（4 字符 × 50 = 200 字符；实质字符占比 1.0；
        // 字符多样性熵 ≈ 2.0（仅 4 种字符均匀分布 → log2(4)=2，但仍可能低于 2.5），
        // 因此本测试主要验证 trigram 重复率会否被命中。
        // 改用 5 种字符的短语避免熵阈值竞争：「党员人数实在不足凑数」共 10 字符，重复 30 次。
        String s = "党员人数实在不足凑数".repeat(30);
        ReasonTextPolicy.ValidationResult result = validator.validate(s);
        assertFalse(result.valid());
        assertEquals(FailureReason.HIGH_REPETITION, result.failureReason(),
                "短语完全重复应被 trigram 命中");
    }

    @Test
    public void acceptsRealisticBusinessReason() {
        String reason = """
                本小区共有产权房比例较高，实际居住业主中党员人数仅占 10%，
                经多次组织居民代表协商发动报名，仍无法凑足候选人池所需的党员数量。
                依据《物业管理条例》及上级民政部门关于党员比例放宽的指导意见，
                特申请将本届业委会党员比例下限放宽至 30%，恳请居委会及街道办予以审议批准。
                """;
        ReasonTextPolicy.ValidationResult result = validator.validate(reason);
        assertTrue(result.valid(), "真实业务理由应通过：" + result.message());
        assertNull(result.failureReason());
    }

    @Test
    public void acceptsBoundaryCase_minSubstantiveExact50() {
        // 至少 50 个实质字符的中文文本，多种字符分布
        String s = "党员人数严重不足影响选举进程经过多次发动仍然找不到合适候选人申请放宽比例至百分之三十请审批通过此次申请";
        // 验证字符数（中文每字符 codePoint 单 char）
        assertTrue(s.length() >= 50, "fixture 长度 = " + s.length());
        ReasonTextPolicy.ValidationResult result = validator.validate(s);
        assertTrue(result.valid(),
                "边界 50 字符多样化文本应通过：" + result.failureReason() + " - " + result.message());
    }

    @Test
    public void rejectsAlternatingTwoCharsLowEntropyButSubstantive() {
        // 「ABAB...」200 长，熵 = 1.0 < 2.5，应被熵命中（不是占比，不是 trigram 重复率上限不一定触发）
        String s = "AB".repeat(100);
        ReasonTextPolicy.ValidationResult result = validator.validate(s);
        assertFalse(result.valid());
        assertEquals(FailureReason.LOW_ENTROPY, result.failureReason());
    }
}
