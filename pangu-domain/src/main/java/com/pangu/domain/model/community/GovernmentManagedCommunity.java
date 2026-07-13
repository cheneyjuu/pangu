// 关联业务：向街道办返回辖区小区及其已生效物业管理模式，支持受控切换租户上下文。
package com.pangu.domain.model.community;

import java.math.BigDecimal;

/**
 * 关联业务：表达街镇或平台根组织在辖区内可切换监管的小区摘要。
 *
 * <p>该对象只承载组织授权后可见的小区基础信息，不承载前端自行推断的租赁、物业模式或
 * 角色权限；实际业务数据仍以当前 JWT 中的 tenant 上下文为准。
 */
public record GovernmentManagedCommunity(
        Long tenantId,
        String tenantName,
        Integer plannedHouseholdCount,
        BigDecimal totalExclusiveArea,
        String governanceStatus,
        PropertyManagementMode propertyManagementMode) {
}
