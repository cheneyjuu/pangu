package com.pangu.domain.model.user;

import java.util.List;

/**
 * 自然人账号及其管理端工作身份聚合视图。
 *
 * @param accountId        t_account.account_id
 * @param phone            登录手机号
 * @param realName         容错解密后的真实姓名；开发期 MOCK_ 明文保持原样
 * @param realNameVerified 0=未实名, 1=已实名
 * @param status           t_account.status：1=正常, 2=禁用, 3=注销
 * @param shadows          当前已启用的 SYS_USER 工作身份
 */
public record WorkIdentityAccount(
        Long accountId,
        String phone,
        String realName,
        int realNameVerified,
        int status,
        List<WorkIdentityShadow> shadows) {

    public WorkIdentityAccount {
        shadows = shadows == null ? List.of() : List.copyOf(shadows);
    }
}
