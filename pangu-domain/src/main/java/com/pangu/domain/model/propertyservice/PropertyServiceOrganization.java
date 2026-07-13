// 关联业务：保存小区与物业服务企业之间的登记、核验和项目部启用关系。
package com.pangu.domain.model.propertyservice;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 小区物业服务组织登记。
 */
public record PropertyServiceOrganization(
        Long organizationId,
        Long tenantId,
        Long enterpriseId,
        Long projectDeptId,
        String projectDeptName,
        String serviceContactName,
        String serviceContactPhone,
        PropertyServiceContractBasis serviceBasis,
        LocalDate serviceStartDate,
        LocalDate serviceEndDate,
        PropertyServiceOrganizationStatus status,
        Long submittedByAccountId,
        Long submittedByUserId,
        Instant submittedAt,
        Long verifiedByAccountId,
        Long verifiedByUserId,
        Instant verifiedAt,
        String rejectionReason,
        int version,
        Instant createdAt,
        Instant updatedAt) {
}
