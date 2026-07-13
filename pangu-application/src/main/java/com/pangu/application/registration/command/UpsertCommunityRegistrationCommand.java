// 关联业务：承载注册人提交或补充的小区基础信息和申报身份。
package com.pangu.application.registration.command;

import com.pangu.domain.model.registration.CommunityApplicantIdentity;
import com.pangu.domain.model.registration.CommunityHousingTag;
import com.pangu.domain.model.community.PropertyManagementMode;

import java.util.Set;

/**
 * 创建或补充小区注册申请命令。
 */
public record UpsertCommunityRegistrationCommand(
        String applicantName,
        CommunityApplicantIdentity claimedIdentity,
        String provinceCode,
        String provinceName,
        String cityCode,
        String cityName,
        String districtCode,
        String districtName,
        String communityName,
        String communityAddress,
        Integer declaredHouseholdCount,
        Set<CommunityHousingTag> housingTags,
        PropertyManagementMode declaredPropertyMode,
        Integer expectedVersion
) {
}
