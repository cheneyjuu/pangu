// 关联业务：映射小区房屋产权基础名册、结构汇总和业主房产绑定的持久化操作。
package com.pangu.infrastructure.persistence.mapper;

import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PropertyBindingMapper {

    List<RosterOptionRow> selectRosterOptions(@Param("tenantId") Long tenantId);

    List<RosterTopologyRow> selectRosterTopology(@Param("tenantId") Long tenantId);

    List<RosterRow> selectActiveRosters(@Param("tenantId") Long tenantId);

    String selectTenantName(@Param("tenantId") Long tenantId);

    RosterRow selectRosterById(@Param("rosterId") Long rosterId);

    Long selectBuildingIdByName(@Param("tenantId") Long tenantId,
                                @Param("buildingName") String buildingName);

    Long selectNextBuildingId(@Param("tenantId") Long tenantId);

    Long selectRoomIdByPath(@Param("tenantId") Long tenantId,
                            @Param("buildingName") String buildingName,
                            @Param("unitName") String unitName,
                            @Param("roomName") String roomName);

    Long selectNextRoomId(@Param("buildingId") Long buildingId);

    int upsertRoster(RosterImportRow row);

    int insertClaim(ClaimInsertRow row);

    ClaimRow selectClaimById(@Param("claimId") Long claimId);

    List<ClaimRow> selectClaimsForUid(@Param("uid") Long uid);

    List<ClaimRow> selectAdminClaims(@Param("tenantId") Long tenantId,
                                     @Param("status") String status,
                                     @Param("limit") int limit,
                                     @Param("offset") int offset);

    long countAdminClaims(@Param("tenantId") Long tenantId,
                          @Param("status") String status);

    int supersedeOtherOwnerships(@Param("uid") Long uid,
                                 @Param("tenantId") Long tenantId,
                                 @Param("roomId") Long roomId,
                                 @Param("reason") String reason);

    int clearOtherVotingDelegates(@Param("uid") Long uid,
                                  @Param("tenantId") Long tenantId,
                                  @Param("roomId") Long roomId);

    int upsertOwnership(OwnershipUpsertRow row);

    Long selectOwnershipOpid(@Param("uid") Long uid,
                             @Param("tenantId") Long tenantId,
                             @Param("roomId") Long roomId);

    int updateClaimApproved(@Param("claimId") Long claimId,
                            @Param("status") String status,
                            @Param("opid") Long opid,
                            @Param("reviewedBy") Long reviewedBy);

    int updateClaimRejected(@Param("claimId") Long claimId,
                            @Param("rejectReasonCode") String rejectReasonCode,
                            @Param("rejectReason") String rejectReason,
                            @Param("reviewedBy") Long reviewedBy);

    @Data
    public static class RosterOptionRow {
        private Long rosterId;
        private Long tenantId;
        private String communityName;
        private Long buildingId;
        private String buildingName;
        private String unitName;
        private Long roomId;
        private String roomName;
        private BigDecimal buildArea;
    }

    @Data
    public static class RosterTopologyRow {
        private Long buildingId;
        private String buildingName;
        private String unitName;
        private Long householdCount;
        private BigDecimal totalArea;
    }

    @Data
    public static class RosterRow extends RosterOptionRow {
        private String registeredOwnerName;
        private String registeredOwnerPhone;
        private String status;
    }

    @Data
    public static class RosterImportRow {
        private Long rosterId;
        private Long tenantId;
        private String communityName;
        private Long buildingId;
        private String buildingName;
        private String unitName;
        private Long roomId;
        private String roomName;
        private BigDecimal buildArea;
        private String registeredOwnerName;
        private String registeredOwnerPhone;
        private String importBatchNo;
        private Long importedBy;
    }

    @Data
    public static class ClaimInsertRow {
        private Long claimId;
        private Long accountId;
        private Long uid;
        private Long tenantId;
        private Long rosterId;
        private Long buildingId;
        private String buildingName;
        private String unitName;
        private Long roomId;
        private String roomName;
        private String applicantRealName;
        private String applicantPhone;
        private String rosterOwnerName;
        private String rosterOwnerPhone;
        private String matchResult;
        private String claimStatus;
        private int jointOwnership;
        private int votingDelegate;
        private String proofType;
        private String proofMaterialJson;
        private Long boundOpid;
    }

    @Data
    public static class ClaimRow {
        private Long claimId;
        private Long accountId;
        private Long uid;
        private Long tenantId;
        private Long rosterId;
        private Long buildingId;
        private String buildingName;
        private String unitName;
        private Long roomId;
        private String roomName;
        private String applicantRealName;
        private String applicantPhone;
        private String rosterOwnerName;
        private String rosterOwnerPhone;
        private String matchResult;
        private String claimStatus;
        private int jointOwnership;
        private int votingDelegate;
        private String proofType;
        private String proofMaterialJson;
        private String rejectReasonCode;
        private String rejectReason;
        private Long reviewedBy;
        private LocalDateTime reviewedAt;
        private Long boundOpid;
        private LocalDateTime createTime;
    }

    @Data
    public static class OwnershipUpsertRow {
        private Long opid;
        private Long uid;
        private Long tenantId;
        private Long buildingId;
        private Long roomId;
        private BigDecimal buildArea;
        private int jointOwnership;
        private int votingDelegate;
        private String verifyType;
        private String verifySource;
        private Long verifiedBy;
    }
}
