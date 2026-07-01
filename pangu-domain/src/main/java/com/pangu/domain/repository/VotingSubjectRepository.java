package com.pangu.domain.repository;

import com.pangu.domain.common.Page;
import com.pangu.domain.model.voting.SubjectStatus;
import com.pangu.domain.model.voting.SubjectType;
import com.pangu.domain.model.voting.VotingSubject;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 议题主表仓储端口（Hexagonal Port）。
 *
 * <p>本期方法集合保持最小：
 * <ul>
 *   <li>{@link #findById(Long)}：读详情；</li>
 *   <li>{@link #findByIdForUpdate(Long)}：审批 / 结算路径加行锁；</li>
 *   <li>{@link #updatePartyRatioFloor(Long, java.math.BigDecimal, long)}：waiver 应用瞬间写入 floor；</li>
 *   <li>{@link #findExpiredVoting(Instant, int)}：调度器扫描已截止待结算议题。</li>
 *   <li>{@link #insert(VotingSubject)}：M3-2 立项写入 DRAFT；</li>
 *   <li>{@link #cancel(VotingSubject, long)}：M3-2 撤回（DRAFT/PUBLISHED → CANCELLED 落 cancel_* 字段）；</li>
 *   <li>{@link #findPublishedReadyForOpen(Instant, int)}：M3-2 scheduler 扫描 PUBLISHED 待开票议题；</li>
 *   <li>{@link #findVisibleForOwner}：M3-2 业主 ABAC 范围内的议题列表。</li>
 * </ul>
 */
public interface VotingSubjectRepository {

    Optional<VotingSubject> findById(Long subjectId);

    Optional<VotingSubject> findByIdForUpdate(Long subjectId);

    /**
     * 仅当 version 与传入相同才更新；返回受影响行数（0 表示乐观锁失败）。
     */
    int updatePartyRatioFloor(Long subjectId, java.math.BigDecimal partyRatioFloor, long expectedVersion);

    /**
     * 标记议题已截止 / 已结算；用于结算流程末端写状态。
     */
    int updateStatus(Long subjectId, int newStatusDbValue, long expectedVersion);

    /**
     * 状态翻转 + 追加审批审计轨迹。{@code reviewEntryJson} 必须是单个 JSON object 字符串。
     */
    int updateStatusWithReviewHistory(Long subjectId,
                                      int newStatusDbValue,
                                      long expectedVersion,
                                      String reviewEntryJson);

    /**
     * 找到所有 {@code status = VOTING AND vote_end_at < now} 的议题，按 vote_end_at 升序，
     * 仅返回首批 {@code limit} 条。供 {@code VotingDeadlineScheduler} 分批结算。
     */
    List<VotingSubject> findExpiredVoting(Instant now, int limit);

    // ============= M3-2 新增 =============

    /**
     * 写入新议题（M3-2 立项）。{@code subject.subjectId} 在返回值中回填。
     */
    VotingSubject insert(VotingSubject subject);

    /**
     * 议题撤回：把 status 翻 CANCELLED 同时落 cancelled_at / cancelled_by_user_id / cancel_reason。
     * 单条 UPDATE 带乐观锁，返回受影响行数（0 表示版本不一致）。
     */
    int cancel(VotingSubject subject, long expectedVersion);

    /**
     * Scheduler 扫描：{@code status = PUBLISHED AND vote_start_at <= now()} 的议题，
     * 按 vote_start_at 升序返回前 {@code limit} 条。
     */
    List<VotingSubject> findPublishedReadyForOpen(Instant now, int limit);

    /**
     * HANDOVER_LOCK 生效时暂停租户内非选举议题的投票倒计时。
     *
     * <p>覆盖 status ∈ {PUBLISHED, VOTING} 且尚未暂停的 GENERAL/MAJOR 议题。
     *
     * @return 本次新暂停的议题数量
     */
    int suspendVotingClocksForHandover(Long tenantId, Long electionSubjectId);

    /**
     * HANDOVER_LOCK 解除时恢复投票倒计时：按 {@code now - clock_suspended_at}
     * 顺延 vote_start_at / vote_end_at，并清空暂停标记。
     *
     * @return 本次恢复的议题数量
     */
    int resumeVotingClocksAfterHandover(Long tenantId);

    /**
     * "我的议题"列表：按租户 + ABAC scope 过滤业主可见的议题。
     *
     * <p>过滤规则（M3-2）：
     * <ol>
     *   <li>{@code tenant_id = #{tenantId}}；</li>
     *   <li>{@code status IN (PUBLISHED, VOTING, CLOSED, SETTLED)}（DRAFT/CANCELLED 业主不可见）；</li>
     *   <li>{@code scope = COMMUNITY} 或 {@code (scope = BUILDING AND scope_reference_id IN buildingIds)}。</li>
     * </ol>
     *
     * @param tenantId    租户 ID
     * @param buildingIds 当前业主拥有房产所在的楼栋 ID 集合（空集表示业主在该租户下没有任何房产，仅返回 COMMUNITY 议题）
     * @param limit       页大小
     * @param offset      偏移
     */
    List<VotingSubject> findVisibleForOwner(Long tenantId, List<Long> buildingIds, int limit, int offset);

    /**
     * B/G 管理端议题分页查询（M4-1）。
     *
     * <p>过滤规则：
     * <ol>
     *   <li>{@code tenant_id = #{tenantId}}（租户隔离，恒在）；</li>
     *   <li>{@code status}：非空时按状态精确筛选，{@code null} 表示不限状态；</li>
     *   <li>{@code type}：非空时按议题类型精确筛选，{@code null} 表示不限类型。</li>
     * </ol>
     *
     * <p>与 {@link #findVisibleForOwner} 的区别：管理端列表覆盖全部状态（含 DRAFT/CANCELLED），
     * 受众是社区级及以上角色，安全边界由租户 + endpoint 的 {@code @PreAuthorize} 共同保证，
     * 不经业主 ABAC scope 过滤。
     *
     * @param tenantId 租户 ID（必填）
     * @param status   状态筛选，{@code null} 不限
     * @param type     类型筛选，{@code null} 不限
     * @param page     页码（1-based）
     * @param size     页大小
     * @return 当前页议题 + 满足条件的总条数
     */
    Page<VotingSubject> pageForAdmin(Long tenantId, SubjectStatus status, SubjectType type, int page, int size);

    // ============= HANDOVER_LOCK 换届熔断 =============

    /**
     * 换届熔断检测：返回该租户任一在途换届选举（{@code subject_type = ELECTION}
     * 且 {@code status ∈ {PUBLISHED, VOTING, CLOSED}}）的 subjectId，无则空。
     *
     * <p>SETTLED / CANCELLED 不计入「在途」——查询自然返回空即自动解除熔断，无需任何手工解锁。
     * 这是「换届进行中」的唯一判定来源，供敏感治理动作（如财务公示发布）发起前查询。
     */
    Optional<Long> findActiveElectionSubjectId(Long tenantId);
}
