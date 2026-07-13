// 关联业务：实现小区注册申请、审核材料、审核历史和冷启动工作区持久化。
package com.pangu.infrastructure.repository;

import com.pangu.domain.model.registration.CommunityApplicantIdentity;
import com.pangu.domain.model.registration.CommunityHousingTag;
import com.pangu.domain.model.community.PropertyManagementMode;
import com.pangu.domain.model.registration.CommunityOnboardingWorkspace;
import com.pangu.domain.model.registration.CommunityRegistrationApplication;
import com.pangu.domain.model.registration.CommunityRegistrationDecision;
import com.pangu.domain.model.registration.CommunityRegistrationMaterial;
import com.pangu.domain.model.registration.CommunityRegistrationMaterialType;
import com.pangu.domain.model.registration.CommunityRegistrationReview;
import com.pangu.domain.model.registration.CommunityRegistrationReviewMode;
import com.pangu.domain.model.registration.CommunityRegistrationStatus;
import com.pangu.domain.repository.CommunityRegistrationRepository;
import com.pangu.infrastructure.persistence.mapper.CommunityRegistrationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 小区注册聚合仓储实现。
 */
@Repository
@RequiredArgsConstructor
public class CommunityRegistrationRepositoryImpl implements CommunityRegistrationRepository {

    private final CommunityRegistrationMapper mapper;

    @Override
    public CommunityRegistrationApplication insert(CommunityRegistrationApplication application) {
        CommunityRegistrationMapper.ApplicationRow row = toRow(application);
        try {
            mapper.insertApplication(row);
            replaceTags(row.getApplicationId(), application.housingTags());
        } catch (DuplicateKeyException ex) {
            throw new DuplicateRegistrationException("小区注册申请唯一约束冲突", ex);
        }
        return findById(row.getApplicationId())
                .orElseThrow(() -> new IllegalStateException("小区注册申请创建后未能回读"));
    }

    @Override
    public int update(CommunityRegistrationApplication application, int expectedVersion) {
        try {
            int affected = mapper.updateApplication(toRow(application), expectedVersion);
            if (affected == 1) {
                replaceTags(application.applicationId(), application.housingTags());
            }
            return affected;
        } catch (DuplicateKeyException ex) {
            throw new DuplicateRegistrationException("小区注册申请唯一约束冲突", ex);
        }
    }

    @Override
    public Optional<CommunityRegistrationApplication> findById(Long applicationId) {
        return Optional.ofNullable(mapper.selectApplication(applicationId)).map(this::toApplication);
    }

    @Override
    public List<CommunityRegistrationApplication> listByApplicant(Long applicantAccountId, int limit) {
        return mapper.selectApplicationsByApplicant(applicantAccountId, limit).stream()
                .map(this::toApplication)
                .toList();
    }

    @Override
    public List<CommunityRegistrationApplication> listForReview(CommunityRegistrationStatus status, int limit) {
        return mapper.selectApplicationsForReview(status.name(), limit).stream()
                .map(this::toApplication)
                .toList();
    }

    @Override
    public boolean existsActiveFingerprint(String fingerprint, Long excludeApplicationId) {
        return mapper.existsActiveFingerprint(fingerprint, excludeApplicationId);
    }

    @Override
    public boolean existsProvisionedCommunity(
            String fingerprint,
            String districtCode,
            String communityName,
            String communityAddress) {
        return mapper.existsProvisionedCommunity(
                fingerprint, districtCode, communityName, communityAddress);
    }

    @Override
    public CommunityRegistrationMaterial insertMaterial(CommunityRegistrationMaterial material) {
        CommunityRegistrationMapper.MaterialRow row = toRow(material);
        mapper.insertMaterial(row);
        return toMaterial(row);
    }

    @Override
    public Optional<CommunityRegistrationMaterial> findMaterial(Long applicationId, Long materialId) {
        return Optional.ofNullable(mapper.selectMaterial(applicationId, materialId)).map(this::toMaterial);
    }

    @Override
    public List<CommunityRegistrationMaterial> listMaterials(Long applicationId) {
        return mapper.selectMaterials(applicationId).stream().map(this::toMaterial).toList();
    }

    @Override
    public int deactivateMaterial(Long applicationId, Long materialId, Long applicantAccountId) {
        return mapper.deactivateMaterial(applicationId, materialId, applicantAccountId);
    }

    @Override
    public long countActiveMaterials(Long applicationId) {
        return mapper.countActiveMaterials(applicationId);
    }

    @Override
    public CommunityRegistrationReview insertReview(CommunityRegistrationReview review) {
        CommunityRegistrationMapper.ReviewRow row = toRow(review);
        mapper.insertReview(row);
        return toReview(row);
    }

    @Override
    public List<CommunityRegistrationReview> listReviews(Long applicationId) {
        return mapper.selectReviews(applicationId).stream().map(this::toReview).toList();
    }

    @Override
    public Optional<CommunityOnboardingWorkspace> findOnboardingWorkspace(Long applicationId) {
        return Optional.ofNullable(mapper.selectOnboarding(applicationId)).map(this::toOnboarding);
    }

    @Override
    public void insertAudit(
            Long applicationId,
            Long actorAccountId,
            Long actorUserId,
            Long actorDeptId,
            String eventType,
            CommunityRegistrationStatus fromStatus,
            CommunityRegistrationStatus toStatus,
            String payloadJson) {
        mapper.insertAudit(applicationId, actorAccountId, actorUserId, actorDeptId, eventType,
                fromStatus == null ? null : fromStatus.name(),
                toStatus == null ? null : toStatus.name(), payloadJson);
    }

    private void replaceTags(Long applicationId, Set<CommunityHousingTag> tags) {
        mapper.deleteHousingTags(applicationId);
        for (CommunityHousingTag tag : tags) {
            mapper.insertHousingTag(applicationId, tag.name());
        }
    }

    private CommunityRegistrationApplication toApplication(CommunityRegistrationMapper.ApplicationRow row) {
        Set<CommunityHousingTag> tags = mapper.selectHousingTags(row.getApplicationId()).stream()
                .map(CommunityHousingTag::valueOf)
                .collect(Collectors.toUnmodifiableSet());
        return new CommunityRegistrationApplication(
                row.getApplicationId(), row.getApplicationNo(), row.getApplicantAccountId(),
                row.getApplicantName(), row.getApplicantPhone(),
                CommunityApplicantIdentity.valueOf(row.getClaimedIdentity()),
                row.getProvinceCode(), row.getProvinceName(), row.getCityCode(), row.getCityName(),
                row.getDistrictCode(), row.getDistrictName(), row.getCommunityName(), row.getCommunityAddress(),
                row.getDeclaredHouseholdCount() == null ? 0 : row.getDeclaredHouseholdCount(), tags,
                row.getDeclaredPropertyMode() == null ? null
                        : PropertyManagementMode.valueOf(row.getDeclaredPropertyMode()),
                row.getCommunityFingerprint(), CommunityRegistrationStatus.valueOf(row.getStatus()),
                row.getReviewMode() == null ? null : CommunityRegistrationReviewMode.valueOf(row.getReviewMode()),
                row.getReviewerAccountId(), row.getReviewerUserId(), row.getReviewerDeptId(),
                row.getReviewComment(), row.getFallbackReason(), row.getProvisionedTenantId(),
                row.getVersion() == null ? 0 : row.getVersion(), row.getSubmittedAt(), row.getReviewedAt(),
                row.getCreateTime(), row.getUpdateTime());
    }

    private CommunityRegistrationMapper.ApplicationRow toRow(CommunityRegistrationApplication application) {
        CommunityRegistrationMapper.ApplicationRow row = new CommunityRegistrationMapper.ApplicationRow();
        row.setApplicationId(application.applicationId());
        row.setApplicationNo(application.applicationNo());
        row.setApplicantAccountId(application.applicantAccountId());
        row.setApplicantName(application.applicantName());
        row.setApplicantPhone(application.applicantPhone());
        row.setClaimedIdentity(application.claimedIdentity().name());
        row.setProvinceCode(application.provinceCode());
        row.setProvinceName(application.provinceName());
        row.setCityCode(application.cityCode());
        row.setCityName(application.cityName());
        row.setDistrictCode(application.districtCode());
        row.setDistrictName(application.districtName());
        row.setCommunityName(application.communityName());
        row.setCommunityAddress(application.communityAddress());
        row.setDeclaredHouseholdCount(application.declaredHouseholdCount());
        row.setDeclaredPropertyMode(application.declaredPropertyMode() == null
                ? null : application.declaredPropertyMode().name());
        row.setCommunityFingerprint(application.communityFingerprint());
        row.setStatus(application.status().name());
        row.setReviewMode(application.reviewMode() == null ? null : application.reviewMode().name());
        row.setReviewerAccountId(application.reviewerAccountId());
        row.setReviewerUserId(application.reviewerUserId());
        row.setReviewerDeptId(application.reviewerDeptId());
        row.setReviewComment(application.reviewComment());
        row.setFallbackReason(application.fallbackReason());
        row.setProvisionedTenantId(application.provisionedTenantId());
        row.setVersion(application.version());
        row.setSubmittedAt(application.submittedAt());
        row.setReviewedAt(application.reviewedAt());
        row.setCreateTime(application.createdAt());
        row.setUpdateTime(application.updatedAt());
        return row;
    }

    private CommunityRegistrationMapper.MaterialRow toRow(CommunityRegistrationMaterial material) {
        CommunityRegistrationMapper.MaterialRow row = new CommunityRegistrationMapper.MaterialRow();
        row.setMaterialId(material.materialId());
        row.setApplicationId(material.applicationId());
        row.setMaterialType(material.materialType().name());
        row.setObjectKey(material.objectKey());
        row.setOriginalFileName(material.originalFileName());
        row.setContentType(material.contentType());
        row.setFileSize(material.fileSize());
        row.setEtag(material.etag());
        row.setSha256(material.sha256());
        row.setUploadedByAccountId(material.uploadedByAccountId());
        row.setStatus(material.status());
        row.setCreateTime(material.createdAt());
        return row;
    }

    private CommunityRegistrationMaterial toMaterial(CommunityRegistrationMapper.MaterialRow row) {
        return new CommunityRegistrationMaterial(
                row.getMaterialId(), row.getApplicationId(),
                CommunityRegistrationMaterialType.valueOf(row.getMaterialType()),
                row.getObjectKey(), row.getOriginalFileName(), row.getContentType(),
                row.getFileSize() == null ? 0 : row.getFileSize(), row.getEtag(), row.getSha256(),
                row.getUploadedByAccountId(), row.getStatus(), row.getCreateTime());
    }

    private CommunityRegistrationMapper.ReviewRow toRow(CommunityRegistrationReview review) {
        CommunityRegistrationMapper.ReviewRow row = new CommunityRegistrationMapper.ReviewRow();
        row.setReviewId(review.reviewId());
        row.setApplicationId(review.applicationId());
        row.setDecision(review.decision().name());
        row.setReviewMode(review.reviewMode().name());
        row.setReviewerAccountId(review.reviewerAccountId());
        row.setReviewerUserId(review.reviewerUserId());
        row.setReviewerDeptId(review.reviewerDeptId());
        row.setReviewComment(review.reviewComment());
        row.setFallbackReason(review.fallbackReason());
        row.setCreateTime(review.createdAt());
        return row;
    }

    private CommunityRegistrationReview toReview(CommunityRegistrationMapper.ReviewRow row) {
        return new CommunityRegistrationReview(
                row.getReviewId(), row.getApplicationId(),
                CommunityRegistrationDecision.valueOf(row.getDecision()),
                CommunityRegistrationReviewMode.valueOf(row.getReviewMode()),
                row.getReviewerAccountId(), row.getReviewerUserId(), row.getReviewerDeptId(),
                row.getReviewComment(), row.getFallbackReason(), row.getCreateTime());
    }

    private CommunityOnboardingWorkspace toOnboarding(CommunityRegistrationMapper.OnboardingRow row) {
        return new CommunityOnboardingWorkspace(
                row.getOnboardingId(), row.getApplicationId(), row.getTenantId(), row.getStatus(),
                row.getOfficialAffiliationStatus(), row.getSpaceLedgerStatus(), row.getPropertyRosterStatus(),
                row.getDenominatorStatus(), row.getOwnerAccessQrStatus(), row.getInitializationDeptId(),
                row.getCommitteeDeptId(), row.getApplicantWorkUserId(), row.getCreatedByUserId(),
                row.getCreateTime(), row.getUpdateTime());
    }
}
