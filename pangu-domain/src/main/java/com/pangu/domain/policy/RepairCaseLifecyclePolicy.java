// 关联业务：划分报修事项与维修工程项目的写入边界，并校验报修事项能否关联目标工程流程。
package com.pangu.domain.policy;

import com.pangu.domain.model.repair.RepairAttachmentKind;
import com.pangu.domain.model.repair.RepairWorkOrder;
import com.pangu.domain.model.repair.RepairWorkOrderStatus;
import com.pangu.domain.model.repair.RepairWorkflowType;

/**
 * 报修事项只负责登记、定位和勘验；共有部分进入工程项目后，治理、合同、施工、验收和付款
 * 均由项目聚合承载，旧工单接口只能读取历史记录。
 */
public interface RepairCaseLifecyclePolicy {

    Decision assessLegacyTransition(RepairWorkOrder order, RepairWorkOrderStatus targetStatus);

    Decision assessLegacyAttachment(RepairWorkOrder order, RepairAttachmentKind attachmentKind);

    Decision assessProjectLink(
            RepairWorkOrder order,
            RepairWorkflowType workflowType,
            Long projectBuildingId);

    record Decision(boolean allowed, String reason) {

        public static Decision allow() {
            return new Decision(true, null);
        }

        public static Decision reject(String reason) {
            return new Decision(false, reason);
        }
    }
}
