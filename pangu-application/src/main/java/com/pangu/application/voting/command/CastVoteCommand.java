package com.pangu.application.voting.command;

import com.pangu.domain.model.voting.VoteChoice;
import com.pangu.domain.model.voting.VoteChannel;

/**
 * 业主投票提交命令（M3-2）。
 *
 * @param subjectId     议题 ID
 * @param uid           当前业主自然人 ID（service 与 SecurityUtils.getUid 校验一致）
 * @param tenantId      租户 ID（与议题租户校验一致）
 * @param opid          业主使用的房产身份 ID（前端显式传；service 校验归属 + scope）
 * @param targetId      投票目标：候选人 ID（ELECTION，本期不开放） / 决议时为 null
 * @param choice        投票选择：SUPPORT / OPPOSE / ABSTAIN
 * @param signatureHash 电子签名摘要（可空）
 * @param voteChannel   投票写入通道，缺省为 ONLINE
 */
public record CastVoteCommand(
        Long subjectId,
        Long uid,
        Long tenantId,
        Long opid,
        Long targetId,
        VoteChoice choice,
        String signatureHash,
        VoteChannel voteChannel
) {
    public CastVoteCommand(
            Long subjectId,
            Long uid,
            Long tenantId,
            Long opid,
            Long targetId,
            VoteChoice choice,
            String signatureHash) {
        this(subjectId, uid, tenantId, opid, targetId, choice, signatureHash, VoteChannel.ONLINE);
    }

    public CastVoteCommand {
        voteChannel = VoteChannel.defaultIfNull(voteChannel);
    }
}
