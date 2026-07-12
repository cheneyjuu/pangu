// 关联业务：汇总小区注册申请、材料、审核历史和冷启动工作区供接口展示。
package com.pangu.application.registration;

import com.pangu.domain.model.registration.CommunityOnboardingWorkspace;
import com.pangu.domain.model.registration.CommunityRegistrationApplication;
import com.pangu.domain.model.registration.CommunityRegistrationMaterial;
import com.pangu.domain.model.registration.CommunityRegistrationReview;

import java.util.List;

/**
 * 小区注册申请详情。
 */
public record CommunityRegistrationDetails(
        CommunityRegistrationApplication application,
        List<CommunityRegistrationMaterial> materials,
        List<CommunityRegistrationReview> reviews,
        CommunityOnboardingWorkspace onboardingWorkspace
) {
    public CommunityRegistrationDetails {
        materials = materials == null ? List.of() : List.copyOf(materials);
        reviews = reviews == null ? List.of() : List.copyOf(reviews);
    }
}
