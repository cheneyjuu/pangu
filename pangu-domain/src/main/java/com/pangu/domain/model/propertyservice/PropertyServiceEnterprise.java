// 关联业务：保存可跨小区复用的物业服务企业主体与全局组织根节点关联。
package com.pangu.domain.model.propertyservice;

import java.time.Instant;

/**
 * 物业服务企业主体。
 *
 * <p>企业根组织仅用于组织层级关联，不可直接承接本小区物业经理或物业员工工作身份。
 */
public record PropertyServiceEnterprise(
        Long enterpriseId,
        Long enterpriseDeptId,
        String legalName,
        String unifiedSocialCreditCode,
        Instant createdAt,
        Instant updatedAt) {
}
