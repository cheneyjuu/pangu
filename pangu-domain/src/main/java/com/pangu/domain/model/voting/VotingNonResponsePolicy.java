// 关联业务：表达冻结议事规则对有效送达但截止未反馈表决权的计票认定方式。
package com.pangu.domain.model.voting;

/** 通用表决内核可执行的未反馈认定方式。 */
public enum VotingNonResponsePolicy {
    NOT_PARTICIPATED,
    FOLLOW_MAJORITY,
    ABSTAIN
}
