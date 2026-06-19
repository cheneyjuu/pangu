package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * t_voting_result 行映射。
 */
@Data
public class VotingResultRow {

    private Long resultId;
    private Long subjectId;
    private Integer statisticsVersion;

    private BigDecimal totalArea;
    private Long totalOwnerCount;
    private BigDecimal participatingArea;
    private Long participatingOwnerCount;

    /** 0-未达, 1-达。 */
    private Integer quorumSatisfied;

    /** 0-未通过, 1-通过。 */
    private Integer passed;

    /** JSONB 强类型 payload 的 String 表示，由调用方负责序列化。 */
    private String resultPayload;
    private Long denominatorSnapshotId;
    private String attestationTxHash;
    private Instant settledAt;
}
