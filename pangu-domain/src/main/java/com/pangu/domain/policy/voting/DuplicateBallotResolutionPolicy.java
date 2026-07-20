// 关联业务：按照表决包冻结规则裁决纸质与线上渠道的重复有效票。
package com.pangu.domain.policy.voting;

import com.pangu.domain.model.voting.VoteChannel;
import com.pangu.domain.model.voting.VotingExecutionPackage;

import java.util.Objects;

/**
 * 跨渠道重复票裁决。
 *
 * <p>本策略只决定保留还是替代，不读取数据库，也不修改原票；应用层必须在同一事务中完成
 * 原票失效、新票写入和替代审计。
 */
public final class DuplicateBallotResolutionPolicy {

    public Resolution resolve(
            VotingExecutionPackage.DuplicateBallotPolicy policy,
            VoteChannel existingChannel,
            VoteChannel incomingChannel) {
        Objects.requireNonNull(policy, "policy 不能为空");
        Objects.requireNonNull(existingChannel, "existingChannel 不能为空");
        Objects.requireNonNull(incomingChannel, "incomingChannel 不能为空");
        if (existingChannel == incomingChannel
                || existingChannel.paperLike() && incomingChannel.paperLike()) {
            return Resolution.keepExisting("同一渠道已经形成有效票");
        }
        return switch (policy) {
            case NOT_APPLICABLE, FIRST_VALID_WINS ->
                    Resolution.keepExisting("本小区规则约定保留先形成的有效票");
            case ONLINE_PREVAILS -> incomingChannel == VoteChannel.ONLINE
                    ? Resolution.replaceExisting("本小区规则约定线上有效票优先")
                    : Resolution.keepExisting("本小区规则约定线上有效票优先");
            case PAPER_PREVAILS -> incomingChannel.paperLike()
                    ? Resolution.replaceExisting("本小区规则约定纸质有效票优先")
                    : Resolution.keepExisting("本小区规则约定纸质有效票优先");
        };
    }

    public record Resolution(Decision decision, String reason) {
        public Resolution {
            Objects.requireNonNull(decision, "decision 不能为空");
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("reason 不能为空");
            }
            reason = reason.trim();
        }

        public static Resolution keepExisting(String reason) {
            return new Resolution(Decision.KEEP_EXISTING, reason);
        }

        public static Resolution replaceExisting(String reason) {
            return new Resolution(Decision.REPLACE_EXISTING, reason);
        }
    }

    public enum Decision {
        KEEP_EXISTING,
        REPLACE_EXISTING
    }
}
