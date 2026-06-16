package com.pangu.domain.model.user;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 业主电子投票委托关系模型 (VotingDelegation 实体)
 * 处理业主间的投票授权以及防刷票的代理限额校验
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VotingDelegation {

    /** 委托人业主身份ID (OPID) */
    private Long delegatorOpid;

    /** 受托人业主身份ID (OPID) */
    private Long delegateeOpid;

    /** 委托生效时间 */
    private LocalDateTime startTime;

    /** 委托失效时间 */
    private LocalDateTime expireTime;

    /** 委托书电子凭证/协议签名在 Ali OSS 中的文件 Key */
    private String delegationAttachmentKey;

    /**
     * 判断当前委托在给定时间点是否依然有效
     * @param targetTime 目标判断时间点（如投票截止时间）
     * @return 是否有效
     */
    public boolean isValidAt(LocalDateTime targetTime) {
        if (targetTime == null) {
            targetTime = LocalDateTime.now();
        }
        return (startTime == null || !targetTime.isBefore(startTime)) 
            && (expireTime == null || !targetTime.isAfter(expireTime));
    }
}
