// 关联业务：读写维修工程项目、不可变实施方案、工程项、费用分摊快照和项目附件。
package com.pangu.infrastructure.persistence.mapper;

import com.pangu.infrastructure.persistence.entity.RepairPlanAllocationRoomRow;
import com.pangu.infrastructure.persistence.entity.RepairPlanAttachmentRow;
import com.pangu.infrastructure.persistence.entity.RepairPlanVersionRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectAttachmentRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectItemRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RepairProjectMapper {

    int insertProject(RepairProjectRow row);

    RepairProjectRow findProject(@Param("projectId") Long projectId, @Param("tenantId") Long tenantId);

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

    int insertItem(RepairProjectItemRow row);

    int linkItemToWorkOrder(@Param("itemId") Long itemId,
                            @Param("workOrderId") Long workOrderId,
                            @Param("tenantId") Long tenantId);

    List<RepairProjectItemRow> listItems(@Param("planId") Long planId, @Param("tenantId") Long tenantId);

    List<Long> listLinkedWorkOrderIds(@Param("itemId") Long itemId);

    int snapshotAllocationRooms(@Param("planId") Long planId,
                                @Param("tenantId") Long tenantId,
                                @Param("scopeType") String scopeType,
                                @Param("buildingId") Long buildingId,
                                @Param("unitName") String unitName);

    List<RepairPlanAllocationRoomRow> listAllocationRooms(@Param("planId") Long planId,
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

    int lockPlan(@Param("planId") Long planId,
                 @Param("projectId") Long projectId,
                 @Param("tenantId") Long tenantId,
                 @Param("snapshotHash") String snapshotHash,
                 @Param("lockedByUserId") Long lockedByUserId);

    int supersedeLockedPlans(@Param("projectId") Long projectId,
                             @Param("tenantId") Long tenantId,
                             @Param("exceptPlanId") Long exceptPlanId);

    int activatePlan(@Param("projectId") Long projectId,
                     @Param("tenantId") Long tenantId,
                     @Param("planId") Long planId,
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
}
