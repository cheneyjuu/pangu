package com.pangu.domain.repository;

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
}

