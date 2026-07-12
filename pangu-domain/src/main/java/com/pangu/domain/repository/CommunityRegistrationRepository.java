// 关联业务：定义小区注册申请、审核材料、审核记录与审计事件的持久化端口。
package com.pangu.domain.repository;

import com.pangu.domain.model.registration.CommunityOnboardingWorkspace;
import com.pangu.domain.model.registration.CommunityRegistrationApplication;
import com.pangu.domain.model.registration.CommunityRegistrationMaterial;
import com.pangu.domain.model.registration.CommunityRegistrationReview;
import com.pangu.domain.model.registration.CommunityRegistrationStatus;

import java.util.List;
import java.util.Optional;

/**
 * 小区注册聚合仓储。
 */
public interface CommunityRegistrationRepository {

    CommunityRegistrationApplication insert(CommunityRegistrationApplication application);

    int update(CommunityRegistrationApplication application, int expectedVersion);

    Optional<CommunityRegistrationApplication> findById(Long applicationId);

    List<CommunityRegistrationApplication> listByApplicant(Long applicantAccountId, int limit);

    List<CommunityRegistrationApplication> listForReview(CommunityRegistrationStatus status, int limit);

    boolean existsActiveFingerprint(String fingerprint, Long excludeApplicationId);

    boolean existsProvisionedCommunity(String fingerprint,
                                       String districtCode,
                                       String communityName,
                                       String communityAddress);

    CommunityRegistrationMaterial insertMaterial(CommunityRegistrationMaterial material);

    Optional<CommunityRegistrationMaterial> findMaterial(Long applicationId, Long materialId);

    List<CommunityRegistrationMaterial> listMaterials(Long applicationId);

    int deactivateMaterial(Long applicationId, Long materialId, Long applicantAccountId);

    long countActiveMaterials(Long applicationId);

    CommunityRegistrationReview insertReview(CommunityRegistrationReview review);

    List<CommunityRegistrationReview> listReviews(Long applicationId);

    Optional<CommunityOnboardingWorkspace> findOnboardingWorkspace(Long applicationId);

    void insertAudit(Long applicationId,
                     Long actorAccountId,
                     Long actorUserId,
                     Long actorDeptId,
                     String eventType,
                     CommunityRegistrationStatus fromStatus,
                     CommunityRegistrationStatus toStatus,
                     String payloadJson);

    class DuplicateRegistrationException extends RuntimeException {
        public DuplicateRegistrationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
