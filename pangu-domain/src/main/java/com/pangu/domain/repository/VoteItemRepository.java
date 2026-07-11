package com.pangu.domain.repository;

import com.pangu.domain.model.voting.VoteItem;
import com.pangu.domain.model.voting.VoteChannel;

import java.util.List;
import java.util.Optional;

/**
 * 投票明细仓储端口（领域定义；实现在 infrastructure）。
 *
 * <p>本期暴露：
 * <ul>
 *   <li>{@link #findValidVotes(Long)}：结算流程读侧；</li>
 *   <li>{@link #insert(Long, VoteItem, String)}：M3-2 业主投票提交写侧，
 *       UNIQUE 索引冲突由实现翻译为 {@link DuplicateVoteException}。</li>
 * </ul>
 */
public interface VoteItemRepository {

    /**
     * 加载某议题的全部有效投票。引擎内部会对 (uid, opid) 与 uid 做双重去重，
     * 因此本方法返回原始多行无需在持久层提前去重。
     */
    List<VoteItem> findValidVotes(Long subjectId);

    /**
     * 写入一票。{@code subjectId} 单独入参（避免污染领域 {@link VoteItem}）；
     * UNIQUE(subject_id, opid, COALESCE(target_id, 0)) 冲突时抛 {@link DuplicateVoteException}。
     *
     * @param subjectId     议题 ID
     * @param item          领域投票项（uid / opid / targetId / propertyArea / choice）
     * @param signatureHash 电子签名摘要（可空）
     * @return 自增主键 vote_id
     */
    long insert(Long subjectId, VoteItem item, String signatureHash);

    Optional<StoredVote> findActiveVote(Long subjectId, Long opid, Long targetId);

    int invalidateVote(Long voteId, String invalidReason);

    record StoredVote(Long voteId, VoteChannel voteChannel) {
    }

    /** 业主对同一议题同一房产同一目标重复投票时抛出。 */
    class DuplicateVoteException extends RuntimeException {
        public DuplicateVoteException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
