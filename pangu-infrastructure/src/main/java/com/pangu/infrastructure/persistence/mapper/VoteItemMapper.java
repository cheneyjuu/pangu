package com.pangu.infrastructure.persistence.mapper;

import com.pangu.infrastructure.persistence.entity.VoteDetailRow;
import com.pangu.infrastructure.persistence.entity.VoteItemRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * t_vote_item Mapper。
 */
@Mapper
public interface VoteItemMapper {

    /**
     * 加载某议题的全部投票（不在 SQL 内做去重，去重交由领域引擎）。
     */
    List<VoteItemRow> selectBySubjectId(@Param("subjectId") Long subjectId);

    /**
     * M3-2 业主投票提交：写入一票。useGeneratedKeys 回填 vote_id 到 row.voteId。
     * UNIQUE(subject_id, opid, COALESCE(target_id, 0)) 冲突由 Spring 翻 DuplicateKeyException。
     */
    int insert(VoteItemRow row);

    VoteItemRow selectActiveVote(@Param("subjectId") Long subjectId,
                                 @Param("opid") Long opid,
                                 @Param("targetId") Long targetId);

    int invalidateVote(@Param("voteId") Long voteId,
                       @Param("invalidReason") String invalidReason);

    /**
     * M4-2 逐户投票明细分页：以分母范围内应投房产为左表全量铺开，左连接投票表，
     * 未投房产以 {@code voteId/choice/votedAt = null} 出现。每户（opid）一行
     * （{@code DISTINCT ON} 兜底 ELECTION 一房多票）。
     *
     * @param tenantId         租户 ID
     * @param subjectId        议题 ID（左连接条件）
     * @param scope            分母范围 dbValue（1=COMMUNITY/2=BUILDING；UNIT 由调用方拒绝）
     * @param scopeReferenceId BUILDING 时为 building_id；COMMUNITY 可为 null
     * @param limit            页大小
     * @param offset           偏移
     */
    List<VoteDetailRow> selectVoteDetailPage(@Param("tenantId") Long tenantId,
                                             @Param("subjectId") Long subjectId,
                                             @Param("scope") int scope,
                                             @Param("scopeReferenceId") Long scopeReferenceId,
                                             @Param("limit") int limit,
                                             @Param("offset") int offset);

    /**
     * M4-2 逐户投票明细总数：范围内应投房产（opid）数，与 {@link #selectVoteDetailPage} 口径一致。
     */
    long countVoteDetailPage(@Param("tenantId") Long tenantId,
                             @Param("scope") int scope,
                             @Param("scopeReferenceId") Long scopeReferenceId);
}
