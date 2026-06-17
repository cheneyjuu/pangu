package com.pangu.domain.model.voting;

/**
 * 投票分母解析端口（Hexagonal Port）。
 *
 * <p>语义：给定议题，返回经双重去重后的不可变 {@link Denominator}：
 * <ul>
 *   <li>面积分母按 {@code room_id} 去重（防止一户多房 / 共有产权被乘以 N 倍）</li>
 *   <li>人头分母按 {@code primary_owner_uid} 去重
 *       （优先 {@code is_voting_delegate=1}，次按 {@code opid} 升序）</li>
 *   <li>解析过程必须同步落定 {@code t_voting_denominator_snapshot}
 *       与行级 {@code t_voting_denominator_item_snapshot}，并写入 Merkle root</li>
 * </ul>
 *
 * <p>具体实现位于 infrastructure 层
 * （{@code DefaultVotingDenominatorResolver}），domain 不做 SQL 假设。
 */
public interface VotingDenominatorResolver {

    /**
     * 解析并落定议题分母快照。
     *
     * @param subject 议题（依据 {@link VotingSubject#getScope()} 决定空间范围）
     * @return 经强校验的不可变 {@link Denominator}
     */
    Denominator resolve(VotingSubject subject);
}
