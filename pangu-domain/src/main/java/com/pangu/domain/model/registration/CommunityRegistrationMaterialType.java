// 关联业务：分类保存小区注册人与小区真实性审核材料。
package com.pangu.domain.model.registration;

/**
 * 注册审核材料类型。
 */
public enum CommunityRegistrationMaterialType {
    COMMUNITY_EXISTENCE_PROOF,
    COMMITTEE_FILING,
    POSITION_PROOF,
    OWNER_IDENTITY_PROOF,
    COMMUNITY_STAFF_PROOF,
    OTHER
}
