// 关联业务：读写维修工程项目、责任认定、单一决定范围、可信资金切片、不可变实施方案及项目附件。
package com.pangu.infrastructure.persistence.mapper;

import com.pangu.infrastructure.persistence.entity.RepairPlanAllocationRoomRow;
import com.pangu.infrastructure.persistence.entity.RepairEligibleAffectedOwnerRow;
import com.pangu.infrastructure.persistence.entity.RepairPlanAffectedOwnerRow;
import com.pangu.infrastructure.persistence.entity.RepairAllocationBasisRow;
import com.pangu.infrastructure.persistence.entity.RepairPlanAttachmentRow;
import com.pangu.infrastructure.persistence.entity.RepairPlanVersionRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectAttachmentRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectProcessEventRow;
import com.pangu.infrastructure.persistence.entity.RepairDecisionScopeRow;
import com.pangu.infrastructure.persistence.entity.RepairFundingSliceRow;
import com.pangu.infrastructure.persistence.entity.RepairResponsibilityDeterminationRow;
import com.pangu.infrastructure.persistence.entity.RepairWorkPointRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RepairProjectMapper {

    int insertProject(RepairProjectRow row);

    RepairProjectRow findProject(@Param("projectId") Long projectId, @Param("tenantId") Long tenantId);

    RepairProjectRow findProjectByActivePlanWorkOrder(@Param("workOrderId") Long workOrderId,
                                                      @Param("tenantId") Long tenantId);

    RepairProjectRow findProjectForUpdate(@Param("projectId") Long projectId, @Param("tenantId") Long tenantId);

    List<RepairProjectRow> listProjects(@Param("tenantId") Long tenantId,
                                        @Param("status") String status,
                                        @Param("keyword") String keyword,
                                        @Param("offset") int offset,
                                        @Param("limit") int limit);

    long countProjects(@Param("tenantId") Long tenantId,
                       @Param("status") String status,
                       @Param("keyword") String keyword);

    int insertPlan(RepairPlanVersionRow row);

    List<RepairPlanVersionRow> listPlans(@Param("projectId") Long projectId, @Param("tenantId") Long tenantId);

    RepairPlanVersionRow findPlanForUpdate(@Param("planId") Long planId,
                                           @Param("projectId") Long projectId,
                                           @Param("tenantId") Long tenantId);

    int insertDecisionScope(RepairDecisionScopeRow row);

    RepairDecisionScopeRow findDecisionScope(@Param("projectId") Long projectId,
                                             @Param("tenantId") Long tenantId);

    int updateDecisionScopeVerification(@Param("projectId") Long projectId,
                                        @Param("tenantId") Long tenantId,
                                        @Param("verificationStatus") String verificationStatus,
                                        @Param("verificationBasis") String verificationBasis);

    int insertResponsibilityDetermination(RepairResponsibilityDeterminationRow row);

    RepairResponsibilityDeterminationRow findCurrentResponsibilityDetermination(
            @Param("projectId") Long projectId,
            @Param("tenantId") Long tenantId);

    List<RepairResponsibilityDeterminationRow> listResponsibilityDeterminations(
            @Param("projectId") Long projectId,
            @Param("tenantId") Long tenantId);

    int supersedeCurrentResponsibilityDeterminations(@Param("projectId") Long projectId,
                                                     @Param("tenantId") Long tenantId);

    int confirmResponsibilityDetermination(@Param("determinationId") Long determinationId,
                                           @Param("projectId") Long projectId,
                                           @Param("tenantId") Long tenantId,
                                           @Param("confirmedByAccountId") Long confirmedByAccountId,
                                           @Param("confirmedByUserId") Long confirmedByUserId,
                                           @Param("confirmationNote") String confirmationNote);

    List<RepairFundingSliceRow> listFundingSlices(@Param("decisionScopeId") Long decisionScopeId,
                                                   @Param("tenantId") Long tenantId);

    int insertFundingSlice(RepairFundingSliceRow row);

    int insertWorkPoint(RepairWorkPointRow row);

    int linkWorkPointToWorkOrder(@Param("workPointId") Long workPointId,
                                 @Param("workOrderId") Long workOrderId,
                                 @Param("tenantId") Long tenantId);

    List<RepairWorkPointRow> listWorkPoints(@Param("planId") Long planId, @Param("tenantId") Long tenantId);

    List<Long> listLinkedWorkOrderIds(@Param("workPointId") Long workPointId);

    int snapshotAllocationRooms(@Param("planId") Long planId,
                                @Param("tenantId") Long tenantId,
                                @Param("scopeType") String scopeType,
                                @Param("buildingId") Long buildingId,
                                @Param("unitName") String unitName);

    RepairAllocationBasisRow findAllocationBasis(@Param("tenantId") Long tenantId,
                                                  @Param("scopeType") String scopeType,
                                                  @Param("buildingId") Long buildingId,
                                                  @Param("unitName") String unitName);

    RepairAllocationBasisRow findAllocationSnapshotBasis(@Param("planId") Long planId,
                                                          @Param("tenantId") Long tenantId);

    List<RepairPlanAllocationRoomRow> listAllocationRooms(@Param("planId") Long planId,
                                                          @Param("tenantId") Long tenantId);

    List<RepairEligibleAffectedOwnerRow> listEligibleAffectedOwners(
            @Param("tenantId") Long tenantId,
            @Param("scopeType") String scopeType,
            @Param("buildingId") Long buildingId,
            @Param("unitName") String unitName);

    int insertPlanAffectedOwner(RepairPlanAffectedOwnerRow row);

    List<RepairPlanAffectedOwnerRow> listPlanAffectedOwners(@Param("planId") Long planId,
                                                            @Param("tenantId") Long tenantId);

    int insertAttachment(RepairProjectAttachmentRow row);

    RepairProjectAttachmentRow findAttachment(@Param("attachmentId") Long attachmentId,
                                               @Param("projectId") Long projectId,
                                               @Param("tenantId") Long tenantId);

    List<RepairProjectAttachmentRow> listAttachments(@Param("projectId") Long projectId,
                                                     @Param("tenantId") Long tenantId);

    int linkPlanAttachment(@Param("planId") Long planId,
                           @Param("attachmentId") Long attachmentId,
                           @Param("purpose") String purpose,
                           @Param("sortOrder") Integer sortOrder);

    List<RepairPlanAttachmentRow> listPlanAttachments(@Param("planId") Long planId,
                                                      @Param("tenantId") Long tenantId);

    int freezePlanForAuthorization(@Param("planId") Long planId,
                                   @Param("projectId") Long projectId,
                                   @Param("tenantId") Long tenantId,
                                   @Param("authorizationSnapshotHash") String authorizationSnapshotHash,
                                   @Param("frozenByUserId") Long frozenByUserId);

    int activateAuthorizationProposal(@Param("projectId") Long projectId,
                                      @Param("tenantId") Long tenantId,
                                      @Param("planId") Long planId,
                                      @Param("expectedVersion") Integer expectedVersion);

    int reopenAfterAuthorizationFailure(@Param("projectId") Long projectId,
                                        @Param("tenantId") Long tenantId,
                                        @Param("expectedStatus") String expectedStatus,
                                        @Param("expectedVersion") Integer expectedVersion);

    int lockPlan(@Param("planId") Long planId,
                 @Param("projectId") Long projectId,
                 @Param("tenantId") Long tenantId,
                 @Param("snapshotHash") String snapshotHash,
                 @Param("lockedByUserId") Long lockedByUserId);

    int supersedeLockedPlans(@Param("projectId") Long projectId,
                             @Param("tenantId") Long tenantId,
                             @Param("exceptPlanId") Long exceptPlanId);

    int activateExecutionPlan(@Param("projectId") Long projectId,
                              @Param("tenantId") Long tenantId,
                              @Param("planId") Long planId,
                              @Param("expectedStatus") String expectedStatus,
                              @Param("nextStatus") String nextStatus,
                              @Param("expectedVersion") Integer expectedVersion);

    int advanceVersion(@Param("projectId") Long projectId,
                       @Param("tenantId") Long tenantId,
                       @Param("expectedVersion") Integer expectedVersion);

    int advanceStatus(@Param("projectId") Long projectId,
                      @Param("tenantId") Long tenantId,
                      @Param("expectedStatus") String expectedStatus,
                      @Param("nextStatus") String nextStatus,
                      @Param("expectedVersion") Integer expectedVersion);

    int insertEvent(@Param("projectId") Long projectId,
                    @Param("tenantId") Long tenantId,
                    @Param("action") String action,
                    @Param("actorAccountId") Long actorAccountId,
                    @Param("actorUserId") Long actorUserId,
                    @Param("payloadJson") String payloadJson);

    int insertOwnerEvent(@Param("projectId") Long projectId,
                         @Param("tenantId") Long tenantId,
                         @Param("action") String action,
                         @Param("actorAccountId") Long actorAccountId,
                         @Param("actorOwnerUid") Long actorOwnerUid,
                         @Param("payloadJson") String payloadJson);

    List<RepairProjectProcessEventRow> listProcessEvents(@Param("projectId") Long projectId,
                                                         @Param("tenantId") Long tenantId);
}
