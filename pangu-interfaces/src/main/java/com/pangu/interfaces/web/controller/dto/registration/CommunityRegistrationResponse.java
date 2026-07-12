// 关联业务：输出小区注册申请、审核历史和冷启动就绪状态。
package com.pangu.interfaces.web.controller.dto.registration;

import com.pangu.application.registration.CommunityRegistrationDetails;
import com.pangu.domain.model.registration.CommunityOnboardingWorkspace;
import com.pangu.domain.model.registration.CommunityRegistrationApplication;
import com.pangu.domain.model.registration.CommunityRegistrationReview;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 小区注册申请响应。
 */
public record CommunityRegistrationResponse(
        Long applicationId,
        String applicationNo,
        Long applicantAccountId,
        String applicantName,
        String applicantPhone,
        String claimedIdentity,
        String provinceCode,
        String provinceName,
        String cityCode,
        String cityName,
        String districtCode,
        String districtName,
        String communityName,
        String communityAddress,
        int declaredHouseholdCount,
        Set<String> housingTags,
        String status,
        String reviewMode,
        Long reviewerUserId,
        Long reviewerDeptId,
        String reviewComment,
        String fallbackReason,
        Long provisionedTenantId,
        int version,
        Instant submittedAt,
        Instant reviewedAt,
        Instant createdAt,
        Instant updatedAt,
        List<CommunityRegistrationMaterialResponse> materials,
        List<ReviewResponse> reviews,
        OnboardingResponse onboarding
) {
    public static CommunityRegistrationResponse from(CommunityRegistrationDetails details) {
        CommunityRegistrationApplication application = details.application();
        return new CommunityRegistrationResponse(
                application.applicationId(), application.applicationNo(), application.applicantAccountId(),
                application.applicantName(), application.applicantPhone(), application.claimedIdentity().name(),
                application.provinceCode(), application.provinceName(), application.cityCode(), application.cityName(),
                application.districtCode(), application.districtName(), application.communityName(),
                application.communityAddress(), application.declaredHouseholdCount(),
                application.housingTags().stream().map(Enum::name).collect(Collectors.toUnmodifiableSet()),
                application.status().name(),
                application.reviewMode() == null ? null : application.reviewMode().name(),
                application.reviewerUserId(), application.reviewerDeptId(), application.reviewComment(),
                application.fallbackReason(), application.provisionedTenantId(), application.version(),
                application.submittedAt(), application.reviewedAt(), application.createdAt(), application.updatedAt(),
                details.materials().stream().map(CommunityRegistrationMaterialResponse::from).toList(),
                details.reviews().stream().map(ReviewResponse::from).toList(),
                OnboardingResponse.from(details.onboardingWorkspace()));
    }

    public record ReviewResponse(
            Long reviewId,
            String decision,
            String reviewMode,
            Long reviewerUserId,
            Long reviewerDeptId,
            String reviewComment,
            String fallbackReason,
            Instant createdAt
    ) {
        static ReviewResponse from(CommunityRegistrationReview review) {
            return new ReviewResponse(
                    review.reviewId(), review.decision().name(), review.reviewMode().name(),
                    review.reviewerUserId(), review.reviewerDeptId(), review.reviewComment(),
                    review.fallbackReason(), review.createdAt());
        }
    }

    public record OnboardingResponse(
            Long onboardingId,
            Long tenantId,
            String status,
            String officialAffiliationStatus,
            String spaceLedgerStatus,
            String propertyRosterStatus,
            String denominatorStatus,
            String ownerAccessQrStatus,
            Long initializationDeptId,
            Long committeeDeptId,
            Long applicantWorkUserId
    ) {
        static OnboardingResponse from(CommunityOnboardingWorkspace workspace) {
            if (workspace == null) {
                return null;
            }
            return new OnboardingResponse(
                    workspace.onboardingId(), workspace.tenantId(), workspace.status(),
                    workspace.officialAffiliationStatus(), workspace.spaceLedgerStatus(),
                    workspace.propertyRosterStatus(), workspace.denominatorStatus(),
                    workspace.ownerAccessQrStatus(), workspace.initializationDeptId(),
                    workspace.committeeDeptId(), workspace.applicantWorkUserId());
        }
    }
}
