// 关联业务：从房屋名册和有效产权关系读取表决人名册候选行，不读取欠费或账号状态。
package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class VotingElectorateCandidateRow {
    private Long rosterId;
    private Long roomId;
    private Long buildingId;
    private BigDecimal certifiedArea;
    private Long opid;
    private Long uid;
    private Integer votingDelegate;
}
