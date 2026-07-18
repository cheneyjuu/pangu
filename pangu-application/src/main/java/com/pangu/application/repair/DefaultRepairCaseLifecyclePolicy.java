// 关联业务：关闭共有部分旧工单的项目级写入，并把已勘验事项路由到楼栋或全小区工程项目。
package com.pangu.application.repair;

import com.pangu.domain.model.repair.RepairAttachmentKind;
import com.pangu.domain.model.repair.RepairPublicAreaScope;
import com.pangu.domain.model.repair.RepairSpaceScope;
import com.pangu.domain.model.repair.RepairWorkOrder;
import com.pangu.domain.model.repair.RepairWorkOrderStatus;
import com.pangu.domain.model.repair.RepairWorkflowType;
import com.pangu.domain.policy.RepairCaseLifecyclePolicy;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class DefaultRepairCaseLifecyclePolicy implements RepairCaseLifecyclePolicy {

    private static final String PROJECT_CUTOVER_MESSAGE =
            "共有部分维修已切换到维修工程项目；勘验完成后请在工程项目台账关联本工单，旧工单项目级写接口仅保留历史只读";
    private static final Set<RepairWorkOrderStatus> CASE_STATUSES = Set.of(
            RepairWorkOrderStatus.SUBMITTED,
            RepairWorkOrderStatus.PENDING_VERIFY,
            RepairWorkOrderStatus.NEED_MANUAL_LOCATION,
            RepairWorkOrderStatus.VERIFIED,
            RepairWorkOrderStatus.ASSIGNED,
            RepairWorkOrderStatus.SURVEYING,
            RepairWorkOrderStatus.SURVEY_COMPLETED,
            RepairWorkOrderStatus.PROJECT_LINKED);
    private static final Set<RepairAttachmentKind> CASE_ATTACHMENTS = Set.of(
            RepairAttachmentKind.OWNER_REPORT_IMAGE,
            RepairAttachmentKind.INTAKE_ATTACHMENT,
            RepairAttachmentKind.LOCATION_IMAGE,
            RepairAttachmentKind.SURVEY_IMAGE,
            RepairAttachmentKind.SURVEY_VIDEO);

    @Override
    public Decision assessLegacyTransition(RepairWorkOrder order, RepairWorkOrderStatus targetStatus) {
        if (order == null || targetStatus == null) {
            return Decision.reject("报修事项和目标状态不能为空");
        }
        if (order.spaceScope() == RepairSpaceScope.PRIVATE || CASE_STATUSES.contains(targetStatus)) {
            return Decision.allow();
        }
        return Decision.reject(PROJECT_CUTOVER_MESSAGE);
    }

    @Override
    public Decision assessLegacyAttachment(RepairWorkOrder order, RepairAttachmentKind attachmentKind) {
        if (order == null || attachmentKind == null) {
            return Decision.reject("报修事项和附件类型不能为空");
        }
        if (order.spaceScope() == RepairSpaceScope.PRIVATE || CASE_ATTACHMENTS.contains(attachmentKind)) {
            return Decision.allow();
        }
        return Decision.reject(PROJECT_CUTOVER_MESSAGE);
    }

    @Override
    public Decision assessProjectLink(
            RepairWorkOrder order,
            RepairWorkflowType workflowType,
            Long projectBuildingId) {
        if (order == null || workflowType == null) {
            return Decision.reject("关联报修事项和工程流程不能为空");
        }
        if (order.spaceScope() != RepairSpaceScope.PUBLIC) {
            return Decision.reject("维修工程项目只能关联共有部分报修事项");
        }
        if (order.status().isTerminal()
                || order.status() == RepairWorkOrderStatus.COMPLETED
                || order.status() == RepairWorkOrderStatus.EVALUATED
                || !order.locationLocked()
                || order.surveySummary() == null || order.surveySummary().isBlank()) {
            return Decision.reject("报修事项必须完成位置核验和现场勘验后才能关联维修工程项目");
        }
        if (workflowType == RepairWorkflowType.BUILDING_REPAIR) {
            if (order.publicAreaScope() != RepairPublicAreaScope.BUILDING
                    || projectBuildingId == null || !projectBuildingId.equals(order.buildingId())) {
                return Decision.reject("楼栋维修项目只能关联同一楼栋的共有部分报修事项");
            }
            return Decision.allow();
        }
        if (workflowType == RepairWorkflowType.COMMUNITY_PUBLIC_REPAIR) {
            return order.publicAreaScope() == RepairPublicAreaScope.COMMUNITY
                    ? Decision.allow()
                    : Decision.reject("全小区公共维修项目只能关联全体共有区域报修事项");
        }
        return Decision.reject("不支持的维修工程流程");
    }
}
