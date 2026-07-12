// 关联业务：承载小区注册申请状态机、申报信息与审核结果。
package com.pangu.domain.model.registration;

import java.time.Instant;
import java.util.Set;

/**
 * 小区注册申请聚合。
 *
 * <p>手机号控制权、小区真实性和注册人业务身份是三个独立事实。本聚合只在明确的
 * 状态转换中记录审核结果，不能把短信验证直接等同于高权限身份授权。
 */
public record CommunityRegistrationApplication(
        Long applicationId,
        String applicationNo,
        Long applicantAccountId,
        String applicantName,
        String applicantPhone,
        CommunityApplicantIdentity claimedIdentity,
        String provinceCode,
        String provinceName,
        String cityCode,
        String cityName,
        String districtCode,
        String districtName,
        String communityName,
        String communityAddress,
        int declaredHouseholdCount,
        Set<CommunityHousingTag> housingTags,
        String communityFingerprint,
        CommunityRegistrationStatus status,
        CommunityRegistrationReviewMode reviewMode,
        Long reviewerAccountId,
        Long reviewerUserId,
        Long reviewerDeptId,
        String reviewComment,
        String fallbackReason,
        Long provisionedTenantId,
        int version,
        Instant submittedAt,
        Instant reviewedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public CommunityRegistrationApplication {
        housingTags = housingTags == null ? Set.of() : Set.copyOf(housingTags);
    }

    public CommunityRegistrationApplication submit(Instant now) {
        requireStatus(CommunityRegistrationStatus.DRAFT, CommunityRegistrationStatus.RETURNED);
        return copy(CommunityRegistrationStatus.SUBMITTED, null, null, null, null,
                null, null, null, version, now, null, now);
    }

    public CommunityRegistrationApplication revise(
            String revisedApplicantName,
            CommunityApplicantIdentity revisedIdentity,
            String revisedProvinceCode,
            String revisedProvinceName,
            String revisedCityCode,
            String revisedCityName,
            String revisedDistrictCode,
            String revisedDistrictName,
            String revisedCommunityName,
            String revisedCommunityAddress,
            int revisedHouseholdCount,
            Set<CommunityHousingTag> revisedTags,
            String revisedFingerprint,
            Instant now) {
        requireStatus(CommunityRegistrationStatus.DRAFT, CommunityRegistrationStatus.RETURNED);
        return new CommunityRegistrationApplication(
                applicationId, applicationNo, applicantAccountId, revisedApplicantName, applicantPhone,
                revisedIdentity, revisedProvinceCode, revisedProvinceName, revisedCityCode, revisedCityName,
                revisedDistrictCode, revisedDistrictName, revisedCommunityName, revisedCommunityAddress,
                revisedHouseholdCount, revisedTags, revisedFingerprint, status, reviewMode,
                reviewerAccountId, reviewerUserId, reviewerDeptId, reviewComment, fallbackReason,
                provisionedTenantId, version, submittedAt, reviewedAt, createdAt, now);
    }

    public CommunityRegistrationApplication returnForSupplement(
            CommunityRegistrationReviewMode mode,
            Long reviewerAccount,
            Long reviewerUser,
            Long reviewerDept,
            String comment,
            String platformFallbackReason,
            Instant now) {
        requireStatus(CommunityRegistrationStatus.SUBMITTED);
        return copy(CommunityRegistrationStatus.RETURNED, mode, reviewerAccount, reviewerUser, reviewerDept,
                comment, platformFallbackReason, null, version, submittedAt, now, now);
    }

    public CommunityRegistrationApplication reject(
            CommunityRegistrationReviewMode mode,
            Long reviewerAccount,
            Long reviewerUser,
            Long reviewerDept,
            String comment,
            String platformFallbackReason,
            Instant now) {
        requireStatus(CommunityRegistrationStatus.SUBMITTED);
        return copy(CommunityRegistrationStatus.REJECTED, mode, reviewerAccount, reviewerUser, reviewerDept,
                comment, platformFallbackReason, null, version, submittedAt, now, now);
    }

    public CommunityRegistrationApplication approve(
            CommunityRegistrationReviewMode mode,
            Long reviewerAccount,
            Long reviewerUser,
            Long reviewerDept,
            String comment,
            String platformFallbackReason,
            Long tenantId,
            Instant now) {
        requireStatus(CommunityRegistrationStatus.SUBMITTED);
        if (tenantId == null) {
            throw new IllegalArgumentException("审核通过必须关联已开通租户");
        }
        return copy(CommunityRegistrationStatus.APPROVED, mode, reviewerAccount, reviewerUser, reviewerDept,
                comment, platformFallbackReason, tenantId, version, submittedAt, now, now);
    }

    public CommunityRegistrationApplication withdraw(Instant now) {
        requireStatus(CommunityRegistrationStatus.DRAFT,
                CommunityRegistrationStatus.SUBMITTED,
                CommunityRegistrationStatus.RETURNED);
        return copy(CommunityRegistrationStatus.WITHDRAWN, reviewMode, reviewerAccountId, reviewerUserId,
                reviewerDeptId, reviewComment, fallbackReason, provisionedTenantId,
                version, submittedAt, reviewedAt, now);
    }

    private CommunityRegistrationApplication copy(
            CommunityRegistrationStatus nextStatus,
            CommunityRegistrationReviewMode nextReviewMode,
            Long nextReviewerAccountId,
            Long nextReviewerUserId,
            Long nextReviewerDeptId,
            String nextReviewComment,
            String nextFallbackReason,
            Long nextProvisionedTenantId,
            int nextVersion,
            Instant nextSubmittedAt,
            Instant nextReviewedAt,
            Instant nextUpdatedAt) {
        return new CommunityRegistrationApplication(
                applicationId, applicationNo, applicantAccountId, applicantName, applicantPhone,
                claimedIdentity, provinceCode, provinceName, cityCode, cityName, districtCode, districtName,
                communityName, communityAddress, declaredHouseholdCount, housingTags, communityFingerprint,
                nextStatus, nextReviewMode, nextReviewerAccountId, nextReviewerUserId, nextReviewerDeptId,
                nextReviewComment, nextFallbackReason, nextProvisionedTenantId, nextVersion,
                nextSubmittedAt, nextReviewedAt, createdAt, nextUpdatedAt);
    }

    private void requireStatus(CommunityRegistrationStatus... allowed) {
        for (CommunityRegistrationStatus candidate : allowed) {
            if (status == candidate) {
                return;
            }
        }
        throw new IllegalStateException("当前申请状态不允许该操作：" + status);
    }
}
