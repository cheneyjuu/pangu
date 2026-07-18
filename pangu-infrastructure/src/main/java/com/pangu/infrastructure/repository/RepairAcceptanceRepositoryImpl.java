// 关联业务：实现楼栋维修和全小区公共维修的独立验收持久化边界。
package com.pangu.infrastructure.repository;

import com.pangu.domain.model.repair.RepairAcceptanceAffectedOwner;
import com.pangu.domain.model.repair.RepairAcceptanceParty;
import com.pangu.domain.model.repair.RepairAcceptancePolicySnapshot;
import com.pangu.domain.model.repair.RepairAcceptanceRound;
import com.pangu.domain.model.repair.RepairAcceptanceSummary;
import com.pangu.domain.model.repair.RepairWorkflowType;
import com.pangu.domain.repository.RepairAcceptanceRepository;
import com.pangu.infrastructure.persistence.entity.RepairAcceptancePolicyRow;
import com.pangu.infrastructure.persistence.entity.RepairAcceptanceRoundRow;
import com.pangu.infrastructure.persistence.mapper.RepairAcceptanceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RepairAcceptanceRepositoryImpl implements RepairAcceptanceRepository {

    private final RepairAcceptanceMapper mapper;

    @Override
    public boolean ownerOwnsRoom(Long tenantId, Long buildingId, Long roomId, Long ownerUid) {
        return mapper.ownerOwnsRoom(tenantId, buildingId, roomId, ownerUid);
    }

    @Override
    public RepairAcceptancePolicySnapshot lockPolicy(
            RepairAcceptancePolicySnapshot snapshot,
            List<RepairAcceptanceAffectedOwner> affectedOwners) {
        mapper.supersedeActivePolicy(snapshot.workOrderId(), snapshot.tenantId());
        Long policyId = mapper.insertPolicy(
                snapshot.workOrderId(), snapshot.tenantId(), snapshot.workflowType().name(),
                snapshot.policyHash(), snapshot.affectedOwnerCount(),
                snapshot.minimumAffectedOwnerParticipants(), snapshot.minimumAffectedOwnerApprovals(),
                snapshot.lockedByUserId());
        for (RepairAcceptanceAffectedOwner affectedOwner : affectedOwners) {
            mapper.insertAffectedOwner(
                    policyId, snapshot.tenantId(), affectedOwner.roomId(), affectedOwner.ownerUid(),
                    affectedOwner.affectedReason());
        }
        return findPolicy(snapshot.workOrderId(), snapshot.tenantId()).orElseThrow();
    }

    @Override
    public Optional<RepairAcceptancePolicySnapshot> findPolicy(Long workOrderId, Long tenantId) {
        return Optional.ofNullable(mapper.findPolicy(workOrderId, tenantId)).map(this::toDomain);
    }

    @Override
    public boolean ownerIncluded(Long policyId, Long tenantId, Long roomId, Long ownerUid) {
        return mapper.ownerIncluded(policyId, tenantId, roomId, ownerUid);
    }

    @Override
    public List<Long> listOwnerRooms(Long workOrderId, Long tenantId, Long ownerUid) {
        return mapper.listOwnerRooms(workOrderId, tenantId, ownerUid);
    }

    @Override
    public RepairAcceptanceRound startRound(
            Long workOrderId,
            Long tenantId,
            Long policyId,
            Long submittedByUserId) {
        Long acceptanceId = mapper.insertRound(workOrderId, tenantId, policyId, submittedByUserId);
        return toDomain(mapper.findRoundById(acceptanceId, tenantId));
    }

    @Override
    public Optional<RepairAcceptanceRound> findCollectingRound(Long workOrderId, Long tenantId) {
        return Optional.ofNullable(mapper.findCollectingRound(workOrderId, tenantId)).map(this::toDomain);
    }

    @Override
    public void insertParty(Long acceptanceId, Long tenantId, RepairAcceptanceParty party) {
        mapper.insertParty(acceptanceId, tenantId, party);
    }

    @Override
    public RepairAcceptanceSummary summarize(Long acceptanceId, Long tenantId) {
        var row = mapper.summarize(acceptanceId, tenantId);
        return new RepairAcceptanceSummary(
                value(row.getAffectedOwnerCount()),
                value(row.getParticipatingAffectedOwnerCount()),
                value(row.getPassedAffectedOwnerCount()),
                value(row.getRectificationCount()),
                Boolean.TRUE.equals(row.getBuildingLeaderPassed()),
                Boolean.TRUE.equals(row.getCommitteeExecutivePassed()),
                Boolean.TRUE.equals(row.getCommitteeSealApplied()),
                Boolean.TRUE.equals(row.getPropertyTechnicalCosigned()),
                Boolean.TRUE.equals(row.getThirdPartyTechnicalCosigned()));
    }

    @Override
    public int completeRound(
            Long acceptanceId,
            Long tenantId,
            String status,
            Long completedByUserId,
            String completionRemark) {
        return mapper.completeRound(
                acceptanceId, tenantId, status, completedByUserId, completionRemark);
    }

    private RepairAcceptancePolicySnapshot toDomain(RepairAcceptancePolicyRow row) {
        return new RepairAcceptancePolicySnapshot(
                row.getPolicyId(), row.getWorkOrderId(), row.getTenantId(),
                RepairWorkflowType.valueOf(row.getWorkflowType()), row.getPolicyHash(),
                value(row.getAffectedOwnerCount()), row.getMinimumAffectedOwnerParticipants(),
                row.getMinimumAffectedOwnerApprovals(), row.getLockedByUserId(), row.getLockedAt());
    }

    private RepairAcceptanceRound toDomain(RepairAcceptanceRoundRow row) {
        return new RepairAcceptanceRound(
                row.getAcceptanceId(), row.getWorkOrderId(), row.getTenantId(), row.getPolicyId(),
                value(row.getRoundNo()), row.getStatus());
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }
}
