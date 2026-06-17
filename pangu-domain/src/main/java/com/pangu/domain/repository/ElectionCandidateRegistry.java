package com.pangu.domain.repository;

import com.pangu.domain.model.voting.CandidatePoolSnapshot;

/**
 * 选举候选人池查询端口（Hexagonal Port）。
 *
 * <p>application 层通过本端口查询当前候选人池快照，避免依赖
 * infrastructure 的 MyBatis Mapper。
 *
 * <p>实现位置：{@code pangu-infrastructure/.../repository/ElectionCandidateRegistryImpl}。
 */
public interface ElectionCandidateRegistry {

    /**
     * 统计议题当前的合格候选人池（仅 qualification_status = APPROVED）。
     *
     * @param subjectId 议题 ID
     * @return 党员人数 + 合格总人数；议题尚无候选人时返回 (0, 0)
     */
    CandidatePoolSnapshot countActivePool(Long subjectId);
}
