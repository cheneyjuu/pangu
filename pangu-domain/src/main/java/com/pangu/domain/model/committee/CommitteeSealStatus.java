// 关联业务：业主自治组织电子印章从启用到停用、注销的生命周期。
package com.pangu.domain.model.committee;

public enum CommitteeSealStatus {
    ACTIVE,
    INACTIVE,
    EXPIRED,
    REVOKED
}
