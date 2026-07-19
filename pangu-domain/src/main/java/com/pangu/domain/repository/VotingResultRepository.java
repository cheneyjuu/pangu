// 关联业务：保存通用议题结算结果，并追溯正式表决时冻结的包、名册、方案和规则。
package com.pangu.domain.repository;

import com.pangu.domain.model.voting.VotingResult;
import com.pangu.domain.model.voting.VotingExecutionTrace;
import com.pangu.domain.model.voting.VotingSubject;

import java.util.Optional;

/**
 * 表决结果仓储端口（领域定义；实现在 infrastructure）。
 *
 * <p>本期持久化策略：一议题最多一行（{@code subject_id} 唯一）；重复结算时由
 * application 层判断后调用 {@link #upsert} 自增 {@code statistics_version}。
 *
 * <p>{@link Snapshot} record 是领域与持久层之间的纯数据载体，避免领域 {@link VotingResult}
 * 必须感知 JSON 序列化等基础设施细节。
 */
public interface VotingResultRepository {

    /**
     * 写入或更新结果快照（subject_id 唯一）。
     *
     * <p>若已存在记录则递增 {@code statisticsVersion} 并替换 payload；通常应当在
     * {@code SELECT FOR UPDATE} 议题主表后调用，以避免并发重复结算。
     */
    void upsert(Snapshot snapshot);

    /**
     * 加载某议题的最新结算快照（若存在）。
     */
    Optional<Snapshot> findBySubjectId(Long subjectId);

    /**
     * 表决结果持久化快照。
     *
     * @param subjectId               议题 ID
     * @param statisticsVersion       结算版本（首次为 1，重新结算时由仓储递增）
     * @param totalArea               小区计票总面积
     * @param totalOwnerCount         小区总业主数
     * @param participatingArea       参会面积
     * @param participatingOwnerCount 参会人数
     * @param quorumSatisfied         法定门槛是否达成
     * @param passed                  议题是否通过
     * @param resultPayloadJson       强类型 payload 的 JSON 序列化（候选人结果 / 当选人 等）
     * @param denominatorSnapshotId   分母快照 ID（来自 t_voting_denominator_snapshot）
     * @param attestationTxHash       司法链回执 hash（stub 为 STUB-{eventId}）
     */
    record Snapshot(
            Long subjectId,
            int statisticsVersion,
            java.math.BigDecimal totalArea,
            long totalOwnerCount,
            java.math.BigDecimal participatingArea,
            long participatingOwnerCount,
            boolean quorumSatisfied,
            boolean passed,
            String resultPayloadJson,
            Long denominatorSnapshotId,
            String attestationTxHash,
            Long executionPackageId,
            Long electorateSnapshotId,
            String proposalSnapshotHash,
            String ruleSnapshotHash,
            String executionPackageHash
    ) {
        public Snapshot {
            if (subjectId == null) {
                throw new IllegalArgumentException("subjectId must not be null");
            }
            if (statisticsVersion < 1) {
                throw new IllegalArgumentException("statisticsVersion must be >= 1");
            }
            if (totalArea == null) {
                throw new IllegalArgumentException("totalArea must not be null");
            }
            if (participatingArea == null) {
                throw new IllegalArgumentException("participatingArea must not be null");
            }
        }

        /**
         * 静态工厂：从领域 {@link VotingResult} 构造快照。
         * 调用方负责 payloadJson 序列化（避免领域依赖 jackson）。
         */
        public static Snapshot from(VotingResult<? extends VotingSubject> result,
                                     int statisticsVersion,
                                     String payloadJson,
                                     Long denominatorSnapshotId,
                                     String attestationTxHash) {
            return from(result, statisticsVersion, payloadJson, denominatorSnapshotId, attestationTxHash, null);
        }

        public static Snapshot from(VotingResult<? extends VotingSubject> result,
                                     int statisticsVersion,
                                     String payloadJson,
                                     Long denominatorSnapshotId,
                                     String attestationTxHash,
                                     VotingExecutionTrace executionTrace) {
            return new Snapshot(
                    result.getSubject().getSubjectId(),
                    statisticsVersion,
                    result.getTotalArea(),
                    result.getTotalOwnerCount() == null ? 0L : result.getTotalOwnerCount(),
                    result.getParticipatingArea(),
                    result.getParticipatingOwnerCount() == null ? 0L : result.getParticipatingOwnerCount(),
                    result.isQuorumSatisfied(),
                    result.isPassed(),
                    payloadJson,
                    denominatorSnapshotId,
                    attestationTxHash,
                    executionTrace == null ? null : executionTrace.executionPackageId(),
                    executionTrace == null ? null : executionTrace.electorateSnapshotId(),
                    executionTrace == null ? null : executionTrace.proposalSnapshotHash(),
                    executionTrace == null ? null : executionTrace.ruleSnapshotHash(),
                    executionTrace == null ? null : executionTrace.executionPackageHash());
        }
    }
}
