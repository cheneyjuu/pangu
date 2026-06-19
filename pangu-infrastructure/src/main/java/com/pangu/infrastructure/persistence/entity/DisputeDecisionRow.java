package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.Instant;

/** t_dispute_review_decision 全字段行映射。 */
@Data
public class DisputeDecisionRow {

    private Long decisionId;
    private Long disputeId;
    private Integer reviewLevel;
    private Long decidedByUserId;
    private String decisionKind;
    private String decisionContent;
    private String decisionDocUrl;
    private Instant decidedAt;
    private Instant createTime;
}
