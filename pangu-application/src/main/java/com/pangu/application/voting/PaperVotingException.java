// 关联业务：向上层业务返回纸质送达、纸票回收和录入复核的稳定失败原因。
package com.pangu.application.voting;

public class PaperVotingException extends RuntimeException {

    private final Reason reason;

    public PaperVotingException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public PaperVotingException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    public enum Reason {
        NOT_FOUND,
        INVALID_STATUS,
        INVALID_ARGUMENT,
        DUPLICATE,
        CONCURRENT_MODIFICATION
    }
}
