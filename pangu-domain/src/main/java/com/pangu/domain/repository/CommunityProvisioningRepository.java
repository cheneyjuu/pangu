// 关联业务：定义小区注册审核通过后的事务性租户、组织和初始身份开通端口。
package com.pangu.domain.repository;

import com.pangu.domain.context.UserContext;
import com.pangu.domain.model.registration.CommunityRegistrationApplication;
import com.pangu.domain.model.registration.CommunityRegistrationReviewMode;

/**
 * 小区冷启动开通端口。
 */
public interface CommunityProvisioningRepository {

    ProvisioningResult provision(CommunityRegistrationApplication application,
                                 UserContext reviewer,
                                 CommunityRegistrationReviewMode reviewMode);

    record ProvisioningResult(
            Long tenantId,
            Long initializationDeptId,
            Long committeeDeptId,
            Long applicantWorkUserId,
            String officialAffiliationStatus) {
    }

    class ProvisioningConsistencyException extends RuntimeException {
        public ProvisioningConsistencyException(String message) {
            super(message);
        }

        public ProvisioningConsistencyException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
