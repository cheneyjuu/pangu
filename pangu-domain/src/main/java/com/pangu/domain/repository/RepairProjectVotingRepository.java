// 关联业务：持久化维修授权提案与统一表决包的唯一关联及业主任务投影。
package com.pangu.domain.repository;

import com.pangu.domain.model.repair.RepairProjectVoting;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RepairProjectVotingRepository {

    RepairProjectVoting insert(RepairProjectVoting link);

    Optional<RepairProjectVoting> find(Long projectId, Long planId, Long tenantId);

    Optional<RepairProjectVoting> findForUpdate(Long projectId, Long planId, Long tenantId);

    int markVoting(Long linkId, Long tenantId, Long openedByUserId, Instant openedAt, long expectedVersion);

    int settle(Long linkId, Long tenantId, RepairProjectVoting.Result result,
               Long settledByUserId, Instant settledAt, long expectedVersion);

    List<RepairProjectVoting.OwnerTask> listOwnerTasks(Long tenantId, Long ownerUid);
}
