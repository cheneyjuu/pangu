package com.pangu.application.community;

import lombok.Getter;

/**
 * 社区设置应用层异常。
 */
@Getter
public class CommunitySettingsApplicationException extends RuntimeException {

    private final Reason reason;

    public CommunitySettingsApplicationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public CommunitySettingsApplicationException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public enum Reason {
        PARAM_INVALID,
        FORBIDDEN,
        COMMUNITY_NOT_FOUND,
        POLICY_NOT_FOUND,
        REVIEW_NOT_FOUND,
        REVIEW_INVALID_STATUS
    }
}
