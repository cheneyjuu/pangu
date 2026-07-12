// 关联业务：读写租户级供应商企业主体核验结论及不可变审计历史。
package com.pangu.infrastructure.persistence.mapper;

import com.pangu.infrastructure.persistence.entity.SupplierEnterpriseVerificationRow;
import com.pangu.infrastructure.persistence.entity.SupplierEnterpriseVerificationTargetRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SupplierEnterpriseVerificationMapper {

    SupplierEnterpriseVerificationTargetRow findTarget(@Param("tenantId") Long tenantId,
                                                        @Param("supplierDeptId") Long supplierDeptId);

    SupplierEnterpriseVerificationTargetRow findTargetForUpdate(@Param("tenantId") Long tenantId,
                                                                 @Param("supplierDeptId") Long supplierDeptId);

    Long findSupplierDeptIdByUscc(@Param("unifiedSocialCreditCode") String unifiedSocialCreditCode);

    int insert(SupplierEnterpriseVerificationRow row);

    int completeSupplierUscc(@Param("supplierDeptId") Long supplierDeptId,
                             @Param("unifiedSocialCreditCode") String unifiedSocialCreditCode);

    int applyCurrentResult(@Param("tenantId") Long tenantId,
                           @Param("supplierDeptId") Long supplierDeptId,
                           @Param("verificationId") Long verificationId,
                           @Param("verificationStatus") String verificationStatus,
                           @Param("verifiedByUserId") Long verifiedByUserId,
                           @Param("verifiedAt") LocalDateTime verifiedAt);

    List<SupplierEnterpriseVerificationRow> list(@Param("tenantId") Long tenantId,
                                                 @Param("supplierDeptId") Long supplierDeptId);
}
