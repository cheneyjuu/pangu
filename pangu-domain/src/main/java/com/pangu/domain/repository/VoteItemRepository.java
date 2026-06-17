package com.pangu.domain.repository;

import com.pangu.domain.model.voting.VoteItem;

import java.util.List;

/**
 * 投票明细仓储端口（领域定义；实现在 infrastructure）。
 *
 * <p>本期仅暴露结算流程所需的最小读侧 API：从 t_vote_item 加载某议题的全部
 * 有效投票（已通过 ABAC 鉴权 + 双重去重前的原始投票）。
 */
public interface VoteItemRepository {

    /**
     * 加载某议题的全部有效投票。引擎内部会对 (uid, opid) 与 uid 做双重去重，
     * 因此本方法返回原始多行无需在持久层提前去重。
     */
    List<VoteItem> findValidVotes(Long subjectId);
}
