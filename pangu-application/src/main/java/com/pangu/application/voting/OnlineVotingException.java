// 关联业务：向接口层表达线上实名表决中的资格、状态、重复和并发失败。
package com.pangu.application.voting;

import lombok.Getter;

@Getter
public class OnlineVotingException extends RuntimeException {

    private final Reason reason;

    public OnlineVotingException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public OnlineVotingException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public enum Reason {
        NOT_FOUND,
        FORBIDDEN,
        AUTHENTICATION_REQUIRED,
        INVALID_STATUS,
        INVALID_ARGUMENT,
        ALREADY_SUBMITTED,
        CONCURRENT_MODIFICATION
    }
}
