package com.pangu.infrastructure.persistence.mapper;

import com.pangu.infrastructure.persistence.entity.ElectionCandidateRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * t_election_candidate Mapper（M3-3）。
 *
 * <p>提名写入 / 资格审查 / 候选人列表 / 投票计数门所需的最小集。
 */
@Mapper
public interface ElectionCandidateMapper {

    /** 提名写入（status=PENDING_REVIEW）。回填自增 candidate_id 到 row。 */
    int insertCandidate(ElectionCandidateRow row);

    /**
     * 资格审查：仅当前状态仍为 PENDING_REVIEW(1) 时生效，带条件防并发重复审查。
     * @return affected rows（0 表示已被审查/状态非 PENDING_REVIEW）
     */
    int updateQualification(@Param("candidateId") Long candidateId,
                            @Param("newStatus") int newStatus);

    ElectionCandidateRow selectById(@Param("candidateId") Long candidateId);

    List<ElectionCandidateRow> selectBySubject(@Param("subjectId") Long subjectId);

    /** 仅 qualification_status=APPROVED(2)。 */
    List<ElectionCandidateRow> selectApprovedBySubject(@Param("subjectId") Long subjectId);

    /** 统计某 opid 在某议题已投出的 SUPPORT(choice=1) 票数（maxWinners 计数门用）。 */
    long countSupportByOpid(@Param("subjectId") Long subjectId,
                            @Param("opid") Long opid);
}
