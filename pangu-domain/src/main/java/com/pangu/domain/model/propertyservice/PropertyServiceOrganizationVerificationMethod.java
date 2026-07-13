// 关联业务：区分物业服务企业的人工核验和可替换平台核验审计路径。
package com.pangu.domain.model.propertyservice;

/**
 * 企业主体核验方式。
 */
public enum PropertyServiceOrganizationVerificationMethod {
    PROPERTY_MANUAL,
    PLATFORM_API
}
