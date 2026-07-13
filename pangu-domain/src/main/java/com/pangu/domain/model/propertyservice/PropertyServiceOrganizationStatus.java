// 关联业务：描述小区物业服务组织从登记到核验启用的业务状态。
package com.pangu.domain.model.propertyservice;

/**
 * 小区物业服务组织登记状态。
 */
public enum PropertyServiceOrganizationStatus {
    DRAFT,
    PENDING_VERIFICATION,
    ACTIVE,
    REJECTED;

    public boolean editable() {
        return this == DRAFT || this == REJECTED;
    }
}
