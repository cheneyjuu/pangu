// 关联业务：定义小区物业管理模式的互斥法定运行态。
package com.pangu.domain.model.community;

/**
 * 小区物业管理模式。
 *
 * <p>同一小区在任一时点只能处于一种模式。模式不是前端展示偏好，必须由注册审核或
 * 经业主大会决议、属地执行的变更流程写入租户事实。
 */
public enum PropertyManagementMode {
    /** 包干制。 */
    LUMP_SUM,
    /** 酬金制。 */
    FUND_RAISING,
    /** 信托制。 */
    TRUST
}
