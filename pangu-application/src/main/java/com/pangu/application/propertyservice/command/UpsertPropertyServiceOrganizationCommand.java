// 关联业务：承载小区登记物业服务企业、服务依据、合同期限和本小区项目部名称的输入。
package com.pangu.application.propertyservice.command;

import com.pangu.domain.model.propertyservice.PropertyServiceContractBasis;

import java.time.LocalDate;

/**
 * 新建或修改物业服务组织登记命令。
 */
public record UpsertPropertyServiceOrganizationCommand(
        String legalName,
        String unifiedSocialCreditCode,
        String projectDeptName,
        String serviceContactName,
        String serviceContactPhone,
        PropertyServiceContractBasis serviceBasis,
        LocalDate serviceStartDate,
        LocalDate serviceEndDate,
        Integer expectedVersion
) {
}
