package com.pangu.domain.model.voting;

import java.util.Arrays;

/**
 * 选举闭环 G 端客观拒绝理由码。
 */
public enum ElectionRejectReasonCode {
    C1,
    C2,
    C3,
    C4,
    C5;

    public static ElectionRejectReasonCode parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("reject reason code must not be blank");
        }
        return Arrays.stream(values())
                .filter(code -> code.name().equals(value.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("reject reason code must be one of C1-C5"));
    }
}
