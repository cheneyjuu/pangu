package com.pangu.interfaces.web.controller.dto.voting;

import com.pangu.domain.model.asset.OwnerSummary;

/**
 * 业主检索视图（提名候选人「按手机号关联业主」）。
 *
 * <p>手机号脱敏回显（{@code 138****0012}）用于辨识；{@code uid} 保留供提名调用自动带入。
 * 不含姓名（{@code real_name} 为 SM4 加密列，检索链路不解密）。
 */
public record OwnerSearchResponse(
        Long uid,
        String phoneMasked,
        Long buildingId,
        Long roomId
) {
    public static OwnerSearchResponse from(OwnerSummary owner) {
        return new OwnerSearchResponse(
                owner.getUid(),
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
