// 关联业务：读写维修验收规则快照、受影响业主、验收轮次和签署角色。
package com.pangu.infrastructure.persistence.mapper;

import com.pangu.domain.model.repair.RepairAcceptanceParty;
import com.pangu.infrastructure.persistence.entity.RepairAcceptanceFactsRow;
import com.pangu.infrastructure.persistence.entity.RepairAcceptancePolicyRow;
import com.pangu.infrastructure.persistence.entity.RepairAcceptanceRoundRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RepairAcceptanceMapper {

    boolean ownerOwnsRoom(@Param("tenantId") Long tenantId,
                          @Param("buildingId") Long buildingId,
                          @Param("roomId") Long roomId,
                          @Param("ownerUid") Long ownerUid);

    Long insertPolicy(@Param("workOrderId") Long workOrderId,
                      @Param("tenantId") Long tenantId,
                      @Param("workflowType") String workflowType,
                      @Param("policyHash") String policyHash,
                      @Param("affectedOwnerCount") int affectedOwnerCount,
                      @Param("minimumParticipants") Integer minimumParticipants,
                      @Param("minimumApprovals") Integer minimumApprovals,
                      @Param("lockedByUserId") Long lockedByUserId);

    int supersedeActivePolicy(@Param("workOrderId") Long workOrderId,
                              @Param("tenantId") Long tenantId);

    int insertAffectedOwner(@Param("policyId") Long policyId,
                            @Param("tenantId") Long tenantId,
                            @Param("roomId") Long roomId,
                            @Param("ownerUid") Long ownerUid,
                            @Param("affectedReason") String affectedReason);

    RepairAcceptancePolicyRow findPolicy(@Param("workOrderId") Long workOrderId,
                                         @Param("tenantId") Long tenantId);

    boolean ownerIncluded(@Param("policyId") Long policyId,
                          @Param("tenantId") Long tenantId,
                          @Param("roomId") Long roomId,
                          @Param("ownerUid") Long ownerUid);

    List<Long> listOwnerRooms(@Param("workOrderId") Long workOrderId,
                              @Param("tenantId") Long tenantId,
                              @Param("ownerUid") Long ownerUid);

    Long insertRound(@Param("workOrderId") Long workOrderId,
                     @Param("tenantId") Long tenantId,
                     @Param("policyId") Long policyId,
                     @Param("submittedByUserId") Long submittedByUserId);

    RepairAcceptanceRoundRow findRoundById(@Param("acceptanceId") Long acceptanceId,
                                           @Param("tenantId") Long tenantId);

    RepairAcceptanceRoundRow findCollectingRound(@Param("workOrderId") Long workOrderId,
                                                 @Param("tenantId") Long tenantId);

    int insertParty(@Param("acceptanceId") Long acceptanceId,
                    @Param("tenantId") Long tenantId,
                    @Param("party") RepairAcceptanceParty party);

    RepairAcceptanceFactsRow summarize(@Param("acceptanceId") Long acceptanceId,
                                       @Param("tenantId") Long tenantId);

    int completeRound(@Param("acceptanceId") Long acceptanceId,
                      @Param("tenantId") Long tenantId,
                      @Param("status") String status,
                      @Param("completedByUserId") Long completedByUserId,
                      @Param("completionRemark") String completionRemark);
}
