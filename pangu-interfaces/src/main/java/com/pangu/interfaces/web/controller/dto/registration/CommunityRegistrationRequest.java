// 关联业务：校验注册人提交或补充的小区基础信息与申报身份。
package com.pangu.interfaces.web.controller.dto.registration;

import com.pangu.application.registration.command.UpsertCommunityRegistrationCommand;
import com.pangu.domain.model.community.PropertyManagementMode;
import com.pangu.domain.model.registration.CommunityApplicantIdentity;
import com.pangu.domain.model.registration.CommunityHousingTag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * 小区注册申请表单。
 */
public record CommunityRegistrationRequest(
        @NotBlank @Size(min = 2, max = 50) String applicantName,
        @NotNull CommunityApplicantIdentity claimedIdentity,
        @NotBlank @Pattern(regexp = "\\d{6}") String provinceCode,
        @NotBlank @Size(min = 2, max = 64) String provinceName,
        @NotBlank @Pattern(regexp = "\\d{6}") String cityCode,
        @NotBlank @Size(min = 2, max = 64) String cityName,
        @NotBlank @Pattern(regexp = "\\d{6}") String districtCode,
        @NotBlank @Size(min = 2, max = 64) String districtName,
        @NotBlank @Size(min = 2, max = 128) String communityName,
        @NotBlank @Size(min = 5, max = 256) String communityAddress,
        @NotNull @Min(1) @Max(1_000_000) Integer declaredHouseholdCount,
        @NotEmpty @Size(max = 4) Set<CommunityHousingTag> housingTags,
        @NotNull PropertyManagementMode declaredPropertyMode,
        @Min(0) Integer expectedVersion
) {
    public UpsertCommunityRegistrationCommand toCommand() {
        return new UpsertCommunityRegistrationCommand(
                applicantName, claimedIdentity, provinceCode, provinceName, cityCode, cityName,
                districtCode, districtName, communityName, communityAddress,
                declaredHouseholdCount, housingTags, declaredPropertyMode, expectedVersion);
    }
}
