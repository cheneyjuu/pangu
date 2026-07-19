// 关联业务：保存维修授权提案与统一表决包之间的唯一关联和业主可见任务投影。
package com.pangu.domain.model.repair;

import com.pangu.domain.model.voting.VotingExecutionPackage;

import java.time.Instant;

/**
 * 维修工程正式表决关联。
 *
 * <p>选票、名册和结果仍由统一表决内核保存；本对象只负责维修项目状态与表决包状态的衔接。
 */
public record RepairProjectVoting(
        Long linkId,
        Long projectId,
        Long planId,
        Long tenantId,
        Long subjectId,
        Long executionPackageId,
        Long ruleId,
        String ruleConfigurationHash,
        VotingExecutionPackage.CollectionMode collectionMode,
        Status status,
        Result result,
        Long preparedByUserId,
        Instant preparedAt,
        Long openedByUserId,
        Instant openedAt,
        Long settledByUserId,
        Instant settledAt,
        long version
) {

    public enum Status {
        PREPARED,
        VOTING,
        SETTLED,
        VOIDED
    }

    public enum Result {
        PASSED,
        FAILED
    }

    /** 业主本人在冻结名册中的一项维修表决任务，不包含本人选择。 */
    public record OwnerTask(
            Long projectId,
            Long planId,
            String projectNo,
            String projectName,
            Long subjectId,
            Long executionPackageId,
            Long opid,
            Long roomId,
            VotingExecutionPackage.CollectionMode collectionMode,
            Status status,
            Result result,
            String packageHash,
            Instant voteStartAt,
            Instant voteEndAt
    ) {
    }
}
