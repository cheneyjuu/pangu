package com.pangu.interfaces.web.controller.dto.admin;

import com.pangu.domain.model.user.WorkIdentityAccount;

import java.util.List;

/**
 * 自然人账号及管理端工作身份响应。
 */
public record WorkIdentityAccountResponse(
        Long accountId,
        String phone,
        String realName,
        int realNameVerified,
        int status,
        List<WorkIdentityShadowResponse> shadows) {

    public static WorkIdentityAccountResponse from(WorkIdentityAccount account) {
        return new WorkIdentityAccountResponse(
                account.accountId(),
                account.phone(),
                account.realName(),
                account.realNameVerified(),
                account.status(),
                account.shadows().stream().map(WorkIdentityShadowResponse::from).toList());
    }
}
