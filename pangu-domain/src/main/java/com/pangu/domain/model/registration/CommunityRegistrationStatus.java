// 关联业务：定义小区注册申请从草稿到审核终态的可信状态。
package com.pangu.domain.model.registration;

/**
 * 小区注册申请状态。
 */
public enum CommunityRegistrationStatus {
    DRAFT,
    SUBMITTED,
    RETURNED,
    APPROVED,
    REJECTED,
    WITHDRAWN
}
