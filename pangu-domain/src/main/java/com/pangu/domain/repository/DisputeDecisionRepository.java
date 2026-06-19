package com.pangu.domain.repository;

import com.pangu.domain.model.dispute.Decision;

import java.util.List;

/**
 * 行政机关决议附属表仓储端口。
 *
 * <p>实现位置：{@code pangu-infrastructure/.../repository/DisputeDecisionRepositoryImpl}。
 */
public interface DisputeDecisionRepository {

    /**
     * 新增决议；触发 {@code uk_decision_dispute_level} 冲突时抛 {@link DuplicateDecisionException}。
     *
     * <p>V2.8 trigger 11 要求主表 status 已转为 DECIDED_LEVEL_N_<KIND> 时方可 insert，
     * 顺序由 {@code DisputeApplicationService.decide} 保证。
     */
    Decision insert(Decision decision);

    /** 列出某 dispute 的所有决议（按 review_level 升序）。 */
    List<Decision> findByDisputeId(Long disputeId);

    /**
     * 唯一索引冲突信号（同 dispute_id + review_level 已有决议）。
     * 由 application 层映射为业务错误码 {@code DECISION_DUPLICATE}。
     */
    class DuplicateDecisionException extends RuntimeException {
        public DuplicateDecisionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
