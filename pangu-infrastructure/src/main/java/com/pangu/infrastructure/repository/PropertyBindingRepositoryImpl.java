package com.pangu.infrastructure.repository;

import com.pangu.domain.repository.PropertyBindingRepository;
import com.pangu.infrastructure.persistence.mapper.PropertyBindingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class PropertyBindingRepositoryImpl implements PropertyBindingRepository {

    private final PropertyBindingMapper mapper;

    @Override
    public List<RosterOption> findRosterOptions(Long tenantId) {
        return mapper.selectRosterOptions(tenantId).stream()
                .map(this::toRosterOption)
                .toList();
    }

    @Override
    public String findTenantName(Long tenantId) {
        return mapper.selectTenantName(tenantId);
    }

    @Override
    public Roster findRosterById(Long rosterId) {
        return toRoster(mapper.selectRosterById(rosterId));
    }

    @Override
    public Long findBuildingIdByName(Long tenantId, String buildingName) {
        return mapper.selectBuildingIdByName(tenantId, buildingName);
    }

    @Override
    public Long nextBuildingId(Long tenantId) {
        return mapper.selectNextBuildingId(tenantId);
    }

    @Override
    public Long findRoomIdByPath(Long tenantId, String buildingName, String unitName, String roomName) {
        return mapper.selectRoomIdByPath(tenantId, buildingName, unitName, roomName);
    }

    @Override
    public Long nextRoomId(Long buildingId) {
        return mapper.selectNextRoomId(buildingId);
    }

    @Override
    public int upsertRoster(RosterImportDraft row) {
        return mapper.upsertRoster(toRosterImportRow(row));
    }

    @Override
    public Long insertClaim(ClaimDraft row) {
        PropertyBindingMapper.ClaimInsertRow insertRow = toClaimInsertRow(row);
        mapper.insertClaim(insertRow);
        return insertRow.getClaimId();
    }

    @Override
    public Claim findClaimById(Long claimId) {
        return toClaim(mapper.selectClaimById(claimId));
    }

    @Override
    public List<Claim> findClaimsForUid(Long uid) {
        return mapper.selectClaimsForUid(uid).stream()
                .map(this::toClaim)
                .toList();
    }

    @Override
    public List<Claim> findAdminClaims(Long tenantId, String status, int limit, int offset) {
        return mapper.selectAdminClaims(tenantId, status, limit, offset).stream()
                .map(this::toClaim)
                .toList();
    }

    @Override
    public long countAdminClaims(Long tenantId, String status) {
        return mapper.countAdminClaims(tenantId, status);
    }

    @Override
    public int supersedeOtherOwnerships(Long uid, Long tenantId, Long roomId, String reason) {
        return mapper.supersedeOtherOwnerships(uid, tenantId, roomId, reason);
    }

    @Override
    public int clearOtherVotingDelegates(Long uid, Long tenantId, Long roomId) {
        return mapper.clearOtherVotingDelegates(uid, tenantId, roomId);
    }

    @Override
    public int upsertOwnership(OwnershipDraft row) {
        return mapper.upsertOwnership(toOwnershipUpsertRow(row));
    }

    @Override
    public Long findOwnershipOpid(Long uid, Long tenantId, Long roomId) {
        return mapper.selectOwnershipOpid(uid, tenantId, roomId);
    }

    @Override
    public int markClaimApproved(Long claimId, String status, Long opid, Long reviewedBy) {
        return mapper.updateClaimApproved(claimId, status, opid, reviewedBy);
    }

    @Override
    public int markClaimRejected(Long claimId, String rejectReasonCode, String rejectReason, Long reviewedBy) {
        return mapper.updateClaimRejected(claimId, rejectReasonCode, rejectReason, reviewedBy);
    }

    private RosterOption toRosterOption(PropertyBindingMapper.RosterOptionRow row) {
        if (row == null) {
            return null;
        }
        return new RosterOption(
                row.getRosterId(),
                row.getTenantId(),
                row.getCommunityName(),
                row.getBuildingId(),
                row.getBuildingName(),
                row.getUnitName(),
                row.getRoomId(),
                row.getRoomName(),
                row.getBuildArea());
    }

    private Roster toRoster(PropertyBindingMapper.RosterRow row) {
        if (row == null) {
            return null;
        }
        return new Roster(
                row.getRosterId(),
                row.getTenantId(),
                row.getCommunityName(),
                row.getBuildingId(),
                row.getBuildingName(),
                row.getUnitName(),
                row.getRoomId(),
                row.getRoomName(),
                row.getBuildArea(),
                row.getRegisteredOwnerName(),
                row.getRegisteredOwnerPhone(),
                row.getStatus());
    }

    private PropertyBindingMapper.RosterImportRow toRosterImportRow(RosterImportDraft draft) {
        PropertyBindingMapper.RosterImportRow row = new PropertyBindingMapper.RosterImportRow();
        row.setTenantId(draft.tenantId());
        row.setCommunityName(draft.communityName());
        row.setBuildingId(draft.buildingId());
        row.setBuildingName(draft.buildingName());
        row.setUnitName(draft.unitName());
        row.setRoomId(draft.roomId());
        row.setRoomName(draft.roomName());
        row.setBuildArea(draft.buildArea());
        row.setRegisteredOwnerName(draft.registeredOwnerName());
        row.setRegisteredOwnerPhone(draft.registeredOwnerPhone());
        row.setImportBatchNo(draft.importBatchNo());
        row.setImportedBy(draft.importedBy());
        return row;
    }

    private PropertyBindingMapper.ClaimInsertRow toClaimInsertRow(ClaimDraft draft) {
        PropertyBindingMapper.ClaimInsertRow row = new PropertyBindingMapper.ClaimInsertRow();
        row.setAccountId(draft.accountId());
        row.setUid(draft.uid());
        row.setTenantId(draft.tenantId());
        row.setRosterId(draft.rosterId());
        row.setBuildingId(draft.buildingId());
        row.setBuildingName(draft.buildingName());
        row.setUnitName(draft.unitName());
        row.setRoomId(draft.roomId());
        row.setRoomName(draft.roomName());
        row.setApplicantRealName(draft.applicantRealName());
        row.setApplicantPhone(draft.applicantPhone());
        row.setRosterOwnerName(draft.rosterOwnerName());
        row.setRosterOwnerPhone(draft.rosterOwnerPhone());
        row.setMatchResult(draft.matchResult());
        row.setClaimStatus(draft.claimStatus());
        row.setJointOwnership(draft.jointOwnership() ? 1 : 0);
        row.setVotingDelegate(draft.votingDelegate() ? 1 : 0);
        row.setProofType(draft.proofType());
        row.setProofMaterialJson(draft.proofMaterialJson());
        row.setBoundOpid(draft.boundOpid());
        return row;
    }

    private PropertyBindingMapper.OwnershipUpsertRow toOwnershipUpsertRow(OwnershipDraft draft) {
        PropertyBindingMapper.OwnershipUpsertRow row = new PropertyBindingMapper.OwnershipUpsertRow();
        row.setUid(draft.uid());
        row.setTenantId(draft.tenantId());
        row.setBuildingId(draft.buildingId());
        row.setRoomId(draft.roomId());
        row.setBuildArea(draft.buildArea());
        row.setJointOwnership(draft.jointOwnership() ? 1 : 0);
        row.setVotingDelegate(draft.votingDelegate() ? 1 : 0);
        row.setVerifyType(draft.verifyType());
        row.setVerifySource(draft.verifySource());
        row.setVerifiedBy(draft.verifiedBy());
        return row;
    }

    private Claim toClaim(PropertyBindingMapper.ClaimRow row) {
        if (row == null) {
            return null;
        }
        return new Claim(
                row.getClaimId(),
                row.getAccountId(),
                row.getUid(),
                row.getTenantId(),
                row.getRosterId(),
                row.getBuildingId(),
                row.getBuildingName(),
                row.getUnitName(),
                row.getRoomId(),
                row.getRoomName(),
                row.getApplicantRealName(),
                row.getApplicantPhone(),
                row.getRosterOwnerName(),
                row.getRosterOwnerPhone(),
                row.getMatchResult(),
                row.getClaimStatus(),
                row.getJointOwnership() == 1,
                row.getVotingDelegate() == 1,
                row.getProofType(),
                row.getProofMaterialJson(),
                row.getRejectReasonCode(),
                row.getRejectReason(),
                row.getReviewedBy(),
                row.getReviewedAt(),
                row.getBoundOpid(),
                row.getCreateTime());
    }
}
