package com.pangu.interfaces.web.controller.dto.voting;

import com.pangu.domain.model.asset.OwnerSummary;

/**
 * 业主检索视图（提名候选人「按手机号 / 姓名 / 拼音关联业主」）。
 *
 * <p>手机号脱敏回显（{@code 138****0012}），{@code uid} 保留供提名调用自动带入。
 * 姓名 {@code name} 在姓名/拼音搜索链路由 application 解密后填入；手机号 fast-path 下为 {@code null}。
 * 候选人最终公示后姓名本就公开，提名管理端展示明文姓名的隐私敞口与公示一致。
 */
public record OwnerSearchResponse(
        Long uid,
        String name,
        String phoneMasked,
        Long buildingId,
        Long roomId
) {
    public static OwnerSearchResponse from(OwnerSummary owner) {
        return new OwnerSearchResponse(
                owner.getUid(),
                owner.getRealName(),
                maskPhone(owner.getPhone()),
                owner.getBuildingId(),
                owner.getRoomId());
    }

    /** 手机号脱敏：保留前 3 位与后 4 位，中间以 {@code ****} 代替；不足 7 位则全部以 * 替换中段。 */
    private static String maskPhone(String phone) {
        if (phone == null) {
            return null;
        }
        int len = phone.length();
        if (len <= 4) {
            return "*".repeat(len);
        }
        if (len < 7) {
            return phone.charAt(0) + "*".repeat(len - 1);
        }
        return phone.substring(0, 3) + "****" + phone.substring(len - 4);
    }
}
