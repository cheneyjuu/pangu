// 关联业务：把不可变计票快照转换为管理端和业主端可公开的汇总结果，不披露逐户票面。
package com.pangu.application.voting;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.domain.repository.VotingResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 正式共同决定结果的统一公开投影。
 *
 * <p>总量和参与量读取快照强类型字段；同意、反对和弃权读取结算时一并固化的结果载荷。
 * 旧快照尚未保存某项分类统计时返回 {@code null}，不能用零伪装历史事实。
 */
@Component
@RequiredArgsConstructor
public class VotingDecisionResultProjector {

    private final ObjectMapper objectMapper;

    public View project(VotingResultRepository.Snapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        JsonNode payload = parsePayload(snapshot.resultPayloadJson());
        return new View(
                snapshot.quorumSatisfied(), snapshot.passed(),
                snapshot.totalArea(), snapshot.totalOwnerCount(),
                snapshot.participatingArea(), snapshot.participatingOwnerCount(),
                decimal(payload, "supportArea"), integer(payload, "supportOwnerCount"),
                decimal(payload, "againstArea"), integer(payload, "againstOwnerCount"),
                decimal(payload, "abstainArea"), integer(payload, "abstainOwnerCount"),
                nonResponseSummary(payload));
    }

    private JsonNode parsePayload(String payload) {
        if (payload == null || payload.isBlank()) {
            // 历史结果表允许 payload 为空；保留已落库的强类型总量，缺失的选项分类返回 null。
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("正式表决结果的计票明细摘要无法解析", ex);
        }
    }

    private BigDecimal decimal(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        if (value == null || value.isNull() || !value.isValueNode()) {
            return null;
        }
        try {
            return new BigDecimal(value.asText());
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("正式表决结果中的 " + field + " 不是有效数值", ex);
        }
    }

    private Long integer(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        if (value == null || value.isNull() || !value.canConvertToLong()) {
            return null;
        }
        return value.longValue();
    }

    private NonResponseSummary nonResponseSummary(JsonNode payload) {
        String policy = text(payload, "nonResponsePolicy");
        if (policy == null) {
            return null;
        }
        return new NonResponseSummary(
                policy,
                valueOrZero(integer(payload, "nonResponseEligibleOwnerCount")),
                zeroIfMissing(decimal(payload, "nonResponseEligibleArea")),
                text(payload, "majorityChoice"),
                text(payload, "nonResponseDerivationHash"),
                sourceTally(payload, "actual"),
                sourceTally(payload, "deemed"));
    }

    private SourceTally sourceTally(JsonNode payload, String prefix) {
        return new SourceTally(
                zeroIfMissing(decimal(payload, prefix + "ParticipatingArea")),
                valueOrZero(integer(payload, prefix + "ParticipatingOwnerCount")),
                zeroIfMissing(decimal(payload, prefix + "SupportArea")),
                valueOrZero(integer(payload, prefix + "SupportOwnerCount")),
                zeroIfMissing(decimal(payload, prefix + "AgainstArea")),
                valueOrZero(integer(payload, prefix + "AgainstOwnerCount")),
                zeroIfMissing(decimal(payload, prefix + "AbstainArea")),
                valueOrZero(integer(payload, prefix + "AbstainOwnerCount")));
    }

    private String text(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        return value == null || value.isNull() || !value.isValueNode() ? null : value.asText();
    }

    private long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }

    private BigDecimal zeroIfMissing(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public record View(
            boolean quorumSatisfied,
            boolean passed,
            BigDecimal totalArea,
            long totalOwnerCount,
            BigDecimal participatingArea,
            long participatingOwnerCount,
            BigDecimal supportArea,
            Long supportOwnerCount,
            BigDecimal againstArea,
            Long againstOwnerCount,
            BigDecimal abstainArea,
            Long abstainOwnerCount,
            NonResponseSummary nonResponse
    ) {
    }

    /** 公开的分源汇总；不含房屋、业主身份或逐户认定选项。 */
    public record NonResponseSummary(
            String policy,
            long eligibleOwnerCount,
            BigDecimal eligibleArea,
            String majorityChoice,
            String derivationAggregateHash,
            SourceTally actual,
            SourceTally deemed
    ) {
    }

    public record SourceTally(
            BigDecimal participatingArea,
            long participatingOwnerCount,
            BigDecimal supportArea,
            long supportOwnerCount,
            BigDecimal againstArea,
            long againstOwnerCount,
            BigDecimal abstainArea,
            long abstainOwnerCount
    ) {
    }
}
