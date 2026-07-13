// 关联业务：表达物业管理模式变更申请从业委会提交到街道办执行的受控状态机。
package com.pangu.domain.model.community;

/**
 * 物业管理模式变更申请状态。
 *
 * <p>只有草稿和退回补正状态可以由业委会主任修改材料；审核通过的执行不是申请人动作，
 * 必须由街道办以当前租户模式为并发前提写入。
 */
public enum PropertyManagementModeChangeStatus {
    DRAFT,
    SUBMITTED,
    RETURNED,
    REJECTED,
    EXECUTED;

    public boolean editable() {
        return this == DRAFT || this == RETURNED;
    }

    public boolean active() {
        return this == DRAFT || this == SUBMITTED || this == RETURNED;
    }
}
