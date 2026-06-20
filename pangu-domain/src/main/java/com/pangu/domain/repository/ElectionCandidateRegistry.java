package com.pangu.domain.repository;

import com.pangu.domain.model.voting.Candidate;
import com.pangu.domain.model.voting.CandidatePoolSnapshot;

import java.util.List;
import java.util.Optional;

/**
 * 选举候选人查询/写入端口（Hexagonal Port）。
 *
 * <p>application 层通过本端口操作候选人，避免依赖 infrastructure 的 MyBatis Mapper。
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

    /**
     * 加载议题的 APPROVED 候选人（结算路由用）。
     *
     * @param subjectId 议题 ID
     * @return 仅 qualification_status=APPROVED 的候选人；无则空 list
     */
    List<Candidate> findApprovedCandidates(Long subjectId);

    /**
     * 加载议题全部候选人（含所有状态，管理端列表用）。
     *
     * @param subjectId 议题 ID
     * @return 全部候选人；无则空 list
     */
    List<Candidate> findBySubject(Long subjectId);

    /**
     * 按候选人 ID 加载单条（审查/投票归属校验用）。
     *
     * @param candidateId 候选人 ID
     * @return 候选人；不存在则 empty
     */
    Optional<Candidate> findById(Long candidateId);

    /**
     * 提名候选人（status=PENDING_REVIEW）。
     *
     * @param subjectId   议题 ID
     * @param uid         关联业主 uid
     * @param name        候选人姓名
     * @param partyMember 是否党员
     * @return 新候选人 ID
     * @throws DuplicateCandidateException 同一议题内 uid 已被提名（UNIQUE(subject_id,uid) 冲突）
     */
    Long nominate(Long subjectId, Long uid, String name, boolean partyMember);

    /**
     * 资格审查落库（仅当当前状态仍为 PENDING_REVIEW 时生效）。
     *
     * @param candidateId       候选人 ID
     * @param newStatusDbValue  目标状态 db 值（APPROVED=2 / REJECTED=3）
     * @return affected rows（0 表示已被并发审查，状态非 PENDING_REVIEW）
     */
    int updateQualification(Long candidateId, int newStatusDbValue);

    /**
     * 统计某 opid 在某议题已投出的 SUPPORT 票数（maxWinners 计数门用）。
     *
     * @param subjectId 议题 ID
     * @param opid      业主房产 ID
     * @return 已投票数
     */
    long countSupportByOpid(Long subjectId, Long opid);

    /** 同一议题内重复提名同一 uid 时抛出。application 层捕获翻译为 CANDIDATE_ALREADY_NOMINATED。 */
    class DuplicateCandidateException extends RuntimeException {
        public DuplicateCandidateException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
