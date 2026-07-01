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
     * 状态翻转 + 追加 review_history JSONB 审计记录。
     */
    int updateStatusWithReviewHistory(@Param("subjectId") Long subjectId,
                                      @Param("newStatus") int newStatus,
                                      @Param("expectedVersion") long expectedVersion,
                                      @Param("reviewEntryJson") String reviewEntryJson);

    /**
     * 调度器扫描：{@code status = 3 (VOTING) AND vote_end_at < now()} 的议题，
     * 按 vote_end_at 升序返回前 {@code limit} 条。
     */
    List<VotingSubjectRow> selectExpiredVoting(@Param("now") Instant now,
                                                 @Param("limit") int limit);

    // ============= M3-2 新增 =============

    /** 立项写入。回填自增 subject_id 到 row。 */
    int insert(VotingSubjectRow row);

    /** 撤回：把 status 翻 6 + 落 cancelled_* 三件套，乐观锁 + 版本递增。 */
    int cancel(@Param("subjectId") Long subjectId,
                @Param("cancelledAt") Instant cancelledAt,
                @Param("cancelledByUserId") Long cancelledByUserId,
                @Param("cancelReason") String cancelReason,
                @Param("expectedVersion") long expectedVersion);

    /**
     * Scheduler 扫描：{@code status = 2 (PUBLISHED) AND vote_start_at <= now()}，
     * 按 vote_start_at 升序返回前 {@code limit} 条。
     */
    List<VotingSubjectRow> selectPublishedReadyForOpen(@Param("now") Instant now,
                                                        @Param("limit") int limit);

    /**
     * HANDOVER_LOCK 生效时暂停非 ELECTION 的 PUBLISHED/VOTING 议题倒计时。
     */
    int suspendVotingClocksForHandover(@Param("tenantId") Long tenantId,
                                       @Param("electionSubjectId") Long electionSubjectId);

    /**
     * HANDOVER_LOCK 解除时按暂停时长顺延 vote_start_at/vote_end_at 并清空暂停标记。
     */
    int resumeVotingClocksAfterHandover(@Param("tenantId") Long tenantId);

    /**
     * "我的议题"：tenant + status 范围 + scope 过滤。当 buildingIds 为空时仅返回 COMMUNITY 议题。
     */
    List<VotingSubjectRow> selectVisibleForOwner(@Param("tenantId") Long tenantId,
                                                  @Param("buildingIds") List<Long> buildingIds,
                                                  @Param("limit") int limit,
                                                  @Param("offset") int offset);

    // ============= M4-1 管理端分页查询 =============

    /**
     * 管理端议题分页：tenant 恒在 + status/type 可选筛选，按 create_time 倒序。
     * status/type 为 {@code null} 时该条件省略。
     */
    List<VotingSubjectRow> selectAdminPage(@Param("tenantId") Long tenantId,
                                            @Param("status") Integer status,
                                            @Param("type") Integer type,
                                            @Param("limit") int limit,
                                            @Param("offset") int offset);

    /** 管理端议题分页计数：where 条件与 {@link #selectAdminPage} 一致。 */
    long countAdminPage(@Param("tenantId") Long tenantId,
                        @Param("status") Integer status,
                        @Param("type") Integer type);

    // ============= HANDOVER_LOCK 换届熔断 =============

    /**
     * 换届熔断检测：该租户任一在途换届选举（{@code subject_type = 1 AND status IN (2,3,4)}）的
     * subject_id，无则 {@code null}。{@code ORDER BY subject_id LIMIT 1}。
     */
    Long selectActiveElectionSubjectId(@Param("tenantId") Long tenantId);
}
