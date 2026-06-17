package com.pangu.infrastructure.persistence.mapper;

import com.pangu.infrastructure.persistence.entity.VotingSubjectRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

/**
 * t_voting_subject Mapper。本期仅暴露 application 流程必需的最小集。
 */
@Mapper
public interface VotingSubjectMapper {

    VotingSubjectRow selectById(@Param("subjectId") Long subjectId);

    VotingSubjectRow selectByIdForUpdate(@Param("subjectId") Long subjectId);

    /**
     * 仅 {@code party_ratio_floor / version} 更新；带乐观锁 {@code WHERE version = #{expectedVersion}}。
     * 0 表示乐观锁失败。
     */
    int updatePartyRatioFloor(@Param("subjectId") Long subjectId,
                               @Param("partyRatioFloor") java.math.BigDecimal partyRatioFloor,
                               @Param("expectedVersion") long expectedVersion);

    /**
     * 状态翻转 + 版本递增。设置 {@code settled_at = now()} 当 newStatus = 5(SETTLED)。
     */
    int updateStatus(@Param("subjectId") Long subjectId,
                      @Param("newStatus") int newStatus,
                      @Param("expectedVersion") long expectedVersion);

    /**
     * 调度器扫描：{@code status = 3 (VOTING) AND vote_end_at < now()} 的议题，
     * 按 vote_end_at 升序返回前 {@code limit} 条。
     */
    List<VotingSubjectRow> selectExpiredVoting(@Param("now") Instant now,
                                                 @Param("limit") int limit);
}
