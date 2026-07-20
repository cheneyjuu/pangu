// 关联业务：统一表达业主实际提交的有效票和依据议事规则形成的未反馈认定票，并保留计票来源。
package com.pangu.domain.model.voting;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 进入结算引擎的不可变票项。
 *
 * <p>认定票不是业主提交的 {@link VoteItem}，因此必须以独立来源进入计票，避免投票回执、
 * 逐户选择和审计记录把系统认定误写成业主本人选择。
 */
public record CountedVote(
        Long opid,
        Long uid,
        Long targetId,
        BigDecimal propertyArea,
        VoteChoice choice,
        Origin origin,
        String sourceReference
) {

    public CountedVote {
        Objects.requireNonNull(opid, "opid 不能为空");
        Objects.requireNonNull(uid, "uid 不能为空");
        Objects.requireNonNull(propertyArea, "propertyArea 不能为空");
        Objects.requireNonNull(choice, "choice 不能为空");
        Objects.requireNonNull(origin, "origin 不能为空");
        if (propertyArea.signum() <= 0) {
            throw new IllegalArgumentException("propertyArea 必须大于 0");
        }
        sourceReference = sourceReference == null ? null : sourceReference.trim();
    }

    public static CountedVote actual(VoteItem vote) {
        Objects.requireNonNull(vote, "vote 不能为空");
        return new CountedVote(
                vote.getOpid(), vote.getUid(), vote.getTargetId(), vote.getPropertyArea(),
                vote.getChoice(), Origin.ACTUAL_BALLOT, null);
    }

    public static CountedVote deemed(Long opid,
                                     Long uid,
                                     BigDecimal propertyArea,
                                     VoteChoice choice,
                                     String derivationHash) {
        return new CountedVote(
                opid, uid, null, propertyArea, choice, Origin.DEEMED_NON_RESPONSE, derivationHash);
    }

    public enum Origin {
        ACTUAL_BALLOT,
        DEEMED_NON_RESPONSE
    }
}
