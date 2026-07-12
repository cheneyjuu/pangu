// 关联业务：定义小区房屋与产权基础名册、房产绑定及名册空间汇总的持久化边界。
package com.pangu.domain.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface PropertyBindingRepository {

    List<RosterOption> findRosterOptions(Long tenantId);

    List<RosterTopology> findRosterTopology(Long tenantId);

    String findTenantName(Long tenantId);

    Roster findRosterById(Long rosterId);

    Long findBuildingIdByName(Long tenantId, String buildingName);

    Long nextBuildingId(Long tenantId);

    Long findRoomIdByPath(Long tenantId, String buildingName, String unitName, String roomName);

    Long nextRoomId(Long buildingId);

    int upsertRoster(RosterImportDraft row);

    Long insertClaim(ClaimDraft row);

    Claim findClaimById(Long claimId);

    List<Claim> findClaimsForUid(Long uid);

    List<Claim> findAdminClaims(Long tenantId, String status, int limit, int offset);

    long countAdminClaims(Long tenantId, String status);

    int supersedeOtherOwnerships(Long uid, Long tenantId, Long roomId, String reason);

    int clearOtherVotingDelegates(Long uid, Long tenantId, Long roomId);

    int upsertOwnership(OwnershipDraft row);

    Long findOwnershipOpid(Long uid, Long tenantId, Long roomId);

    int markClaimApproved(Long claimId, String status, Long opid, Long reviewedBy);

    int markClaimRejected(Long claimId, String rejectReasonCode, String rejectReason, Long reviewedBy);

    record RosterOption(
            Long rosterId,
            Long tenantId,
            String communityName,
            Long buildingId,
            String buildingName,
            String unitName,
            Long roomId,
            String roomName,
            BigDecimal buildArea) {
    }

    /**
     * 当前已激活房屋产权基础名册的楼栋/单元汇总。
     *
     * <p>该汇总仅用于管理端展示已录入的房屋结构，不能替代经核验、对账和发布的法定计票基数。
     */
    record RosterTopology(
            Long buildingId,
            String buildingName,
            String unitName,
            Long householdCount,
            BigDecimal totalArea) {
    }

    record Roster(
            Long rosterId,
            Long tenantId,
            String communityName,
            Long buildingId,
            String buildingName,
            String unitName,
            Long roomId,
            String roomName,
            BigDecimal buildArea,
            String registeredOwnerName,
            String registeredOwnerPhone,
            String status) {
    }

    record RosterImportDraft(
            Long tenantId,
            String communityName,
            Long buildingId,
            String buildingName,
            String unitName,
            Long roomId,
            String roomName,
            BigDecimal buildArea,
            String registeredOwnerName,
            String registeredOwnerPhone,
            String importBatchNo,
            Long importedBy) {
    }

    record ClaimDraft(
            Long accountId,
            Long uid,
            Long tenantId,
            Long rosterId,
            Long buildingId,
            String buildingName,
            String unitName,
            Long roomId,
            String roomName,
            String applicantRealName,
            String applicantPhone,
            String rosterOwnerName,
            String rosterOwnerPhone,
            String matchResult,
            String claimStatus,
            boolean jointOwnership,
            boolean votingDelegate,
            String proofType,
            String proofMaterialJson,
            Long boundOpid) {
    }

    record Claim(
            Long claimId,
            Long accountId,
            Long uid,
            Long tenantId,
            Long rosterId,
            Long buildingId,
            String buildingName,
            String unitName,
            Long roomId,
            String roomName,
            String applicantRealName,
            String applicantPhone,
            String rosterOwnerName,
            String rosterOwnerPhone,
            String matchResult,
            String claimStatus,
            boolean jointOwnership,
            boolean votingDelegate,
            String proofType,
            String proofMaterialJson,
            String rejectReasonCode,
            String rejectReason,
            Long reviewedBy,
            LocalDateTime reviewedAt,
            Long boundOpid,
            LocalDateTime createTime) {
    }

    record OwnershipDraft(
            Long uid,
            Long tenantId,
            Long buildingId,
            Long roomId,
            BigDecimal buildArea,
            boolean jointOwnership,
            boolean votingDelegate,
            String verifyType,
            String verifySource,
            Long verifiedBy) {
    }
}
