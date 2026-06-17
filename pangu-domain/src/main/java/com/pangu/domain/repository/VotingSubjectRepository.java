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
}
