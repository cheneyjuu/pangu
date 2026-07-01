package com.pangu.domain.model.voting;

/**
 * 投票写入通道。
 *
 * <p>用于区分 C 端线上票、纸票录入和线下代录，避免用电子签名是否为空推断票源。
 */
public enum VoteChannel {
    ONLINE(1),
    PAPER(2),
    OFFLINE_PROXY(3);

    private final int dbValue;

    VoteChannel(int dbValue) {
        this.dbValue = dbValue;
    }

    public int getDbValue() {
        return dbValue;
    }

    public boolean paperLike() {
        return this == PAPER || this == OFFLINE_PROXY;
    }

    public static VoteChannel defaultIfNull(VoteChannel channel) {
        return channel == null ? ONLINE : channel;
    }

    public static VoteChannel fromDbValue(Integer dbValue) {
        if (dbValue == null) {
            return ONLINE;
        }
        return switch (dbValue) {
            case 1 -> ONLINE;
            case 2 -> PAPER;
            case 3 -> OFFLINE_PROXY;
            default -> throw new IllegalArgumentException("Unknown VoteChannel dbValue: " + dbValue);
        };
    }
}
