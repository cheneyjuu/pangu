// 关联业务：向维修邀价和签约界面展示供应商组织、账号及当前租户企业核验信息。
package com.pangu.domain.model.repair;

import java.time.LocalDateTime;

/** 可参与维修邀价的供应商组织。 */
public record RepairSupplierOrganization(
        Long supplierDeptId,
        String unifiedSocialCreditCode,
        String legalName,
        String contactName,
        String contactPhone,
        String verificationStatus,
        Long verificationId,
        String verificationMethod,
        String verificationProviderCode,
        String verificationSourceCode,
        boolean verificationSimulated,
        Long verifiedByAccountId,
        Long verifiedByUserId,
        LocalDateTime verifiedAt,
        String accountStatus,
        Integer activeAccountCount,
        String loginPhone,
        Long activationInvitationId,
        LocalDateTime activationInvitationExpiresAt
) {
}
