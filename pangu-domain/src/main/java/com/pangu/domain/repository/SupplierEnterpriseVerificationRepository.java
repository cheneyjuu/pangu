// 关联业务：持久化租户级供应商企业主体核验当前结论与不可变历史审计记录。
package com.pangu.domain.repository;

import com.pangu.domain.model.repair.SupplierEnterpriseVerificationRecord;
import com.pangu.domain.model.repair.SupplierEnterpriseVerificationTarget;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SupplierEnterpriseVerificationRepository {

    Optional<SupplierEnterpriseVerificationTarget> findTarget(Long tenantId, Long supplierDeptId);

    Optional<SupplierEnterpriseVerificationTarget> findTargetForUpdate(Long tenantId, Long supplierDeptId);

    Optional<Long> findSupplierDeptIdByUscc(String unifiedSocialCreditCode);

    SupplierEnterpriseVerificationRecord insert(SupplierEnterpriseVerificationRecord record);

    int applyCurrentResult(Long tenantId,
                           Long supplierDeptId,
                           String unifiedSocialCreditCode,
                           Long verificationId,
                           String verificationStatus,
                           Long verifiedByUserId,
                           LocalDateTime verifiedAt);

    List<SupplierEnterpriseVerificationRecord> list(Long tenantId, Long supplierDeptId);
}
