// 关联业务：限定街道办对物业管理模式变更申请的审核和执行动作。
package com.pangu.domain.model.community;

/**
 * 街道办对物业管理模式变更申请的处理决定。
 */
public enum PropertyManagementModeChangeDecision {
    RETURN,
    REJECT,
    EXECUTE
}
