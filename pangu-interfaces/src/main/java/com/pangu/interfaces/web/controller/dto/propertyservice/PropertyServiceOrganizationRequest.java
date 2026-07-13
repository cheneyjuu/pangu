// 关联业务：校验小区登记物业服务企业、服务依据、期限及本小区项目部信息。
package com.pangu.interfaces.web.controller.dto.propertyservice;

import com.pangu.application.propertyservice.command.UpsertPropertyServiceOrganizationCommand;
import com.pangu.domain.model.propertyservice.PropertyServiceContractBasis;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 物业服务组织登记表单。
 */
public record PropertyServiceOrganizationRequest(
        @NotBlank @Size(max = 120) String legalName,
        @NotBlank @Pattern(regexp = "[0-9A-HJ-NPQRTUWXY]{18}") String unifiedSocialCreditCode,
        @Size(max = 50) String projectDeptName,
        @NotBlank @Size(max = 50) String serviceContactName,
        @NotBlank @Pattern(regexp = "1[3-9]\\d{9}") String serviceContactPhone,
        @NotNull PropertyServiceContractBasis serviceBasis,
        @NotNull LocalDate serviceStartDate,
        LocalDate serviceEndDate,
        @PositiveOrZero Integer expectedVersion
) {
    public UpsertPropertyServiceOrganizationCommand toCommand() {
        return new UpsertPropertyServiceOrganizationCommand(
                legalName, unifiedSocialCreditCode, projectDeptName, serviceContactName, serviceContactPhone,
                serviceBasis, serviceStartDate, serviceEndDate, expectedVersion);
    }
}
