// 关联业务：向接口层返回书面委托登记、核验、撤销和使用校验的稳定失败语义。
package com.pangu.application.voting;

public class VotingProxyAuthorizationException extends RuntimeException {

    private final Reason reason;

    public VotingProxyAuthorizationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public VotingProxyAuthorizationException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    public enum Reason {
        FORBIDDEN,
        NOT_FOUND,
        INVALID_ARGUMENT,
        INVALID_STATUS,
        DUPLICATE,
        CONCURRENT_MODIFICATION,
        STORAGE_UNAVAILABLE
    }
}
