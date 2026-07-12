// 关联业务：区分属地街镇审核与街镇未接入时的平台代审。
package com.pangu.domain.model.registration;

/**
 * 小区注册审核路径。
 */
public enum CommunityRegistrationReviewMode {
    STREET,
    PLATFORM_FALLBACK
}
