// 关联业务：持久化不可变验收规则、整改复验轮次和各验收角色的独立签署记录。
package com.pangu.domain.repository;

import com.pangu.domain.model.repair.RepairAcceptanceAffectedOwner;
import com.pangu.domain.model.repair.RepairAcceptanceParty;
import com.pangu.domain.model.repair.RepairAcceptancePolicySnapshot;
import com.pangu.domain.model.repair.RepairAcceptanceRound;
import com.pangu.domain.model.repair.RepairAcceptanceSummary;

import java.util.List;
import java.util.Optional;

public interface RepairAcceptanceRepository {

    boolean ownerOwnsRoom(Long tenantId, Long buildingId, Long roomId, Long ownerUid);

    RepairAcceptancePolicySnapshot lockPolicy(
            RepairAcceptancePolicySnapshot snapshot,
            List<RepairAcceptanceAffectedOwner> affectedOwners);

    Optional<RepairAcceptancePolicySnapshot> findPolicy(Long workOrderId, Long tenantId);

    boolean ownerIncluded(Long policyId, Long tenantId, Long roomId, Long ownerUid);

    List<Long> listOwnerRooms(Long workOrderId, Long tenantId, Long ownerUid);

    RepairAcceptanceRound startRound(
            Long workOrderId,
            Long tenantId,
            Long policyId,
            Long submittedByUserId);

    Optional<RepairAcceptanceRound> findCollectingRound(Long workOrderId, Long tenantId);

    void insertParty(Long acceptanceId, Long tenantId, RepairAcceptanceParty party);

    RepairAcceptanceSummary summarize(Long acceptanceId, Long tenantId);

    int completeRound(
            Long acceptanceId,
            Long tenantId,
            String status,
            Long completedByUserId,
            String completionRemark);
}
