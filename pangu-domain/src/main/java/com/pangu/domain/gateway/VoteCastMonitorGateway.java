package com.pangu.domain.gateway;

import com.pangu.domain.model.voting.SubjectType;
import com.pangu.domain.model.voting.VoteChoice;
import com.pangu.domain.model.voting.VoteChannel;

import java.time.Instant;

/**
 * 投票写入侧监控基线端口。
 *
 * <p>用于在成功写入 {@code t_vote_item} 后记录可重放的监控信号：Bloom 去重基线、
 * 增量计数、快速连续投票等。监控失败不得影响投票主事务。
 */
public interface VoteCastMonitorGateway {

    void recordCast(VoteCastEvent event);

    VoteCastCounters loadCounters(Long subjectId);

    record VoteCastEvent(
            Long subjectId,
            Long tenantId,
            Long uid,
            Long opid,
            Long targetId,
            SubjectType subjectType,
            VoteChoice choice,
            String signatureHash,
            VoteChannel voteChannel,
            Instant castAt
    ) {
        public VoteCastEvent(
                Long subjectId,
                Long tenantId,
                Long uid,
                Long opid,
                Long targetId,
                SubjectType subjectType,
                VoteChoice choice,
                String signatureHash,
                Instant castAt) {
            this(subjectId, tenantId, uid, opid, targetId, subjectType, choice, signatureHash,
                    VoteChannel.ONLINE, castAt);
        }

        public VoteCastEvent {
            voteChannel = VoteChannel.defaultIfNull(voteChannel);
        }

        public String voteKey() {
            return opid + ":" + (targetId == null ? 0 : targetId);
        }

        public boolean unsignedLikePaper() {
            return voteChannel.paperLike();
        }
    }

    record VoteCastCounters(
            Long subjectId,
            long totalCount,
            long unsignedCount,
            long rapidIntervalCount
    ) {
        public static VoteCastCounters empty(Long subjectId) {
            return new VoteCastCounters(subjectId, 0L, 0L, 0L);
        }
    }
}
