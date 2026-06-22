package com.pangu.interfaces.web.controller.dto.owner;

/**
 * 手机号脱敏工具（{@code 138****0012}）。
 *
 * <p>与 {@link com.pangu.interfaces.web.controller.dto.voting.OwnerSearchResponse} 同款规则，
 * 提到独立类后续可统一收口（PII 脱敏跨多个 owner/voting/dispute DTO）。
 */
final class PhoneMasker {

    private PhoneMasker() {
    }

    /** 保留前 3 位与后 4 位，中间以 {@code ****} 代替；不足 7 位降级。 */
    static String mask(String phone) {
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
