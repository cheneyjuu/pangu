package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.Instant;

/** t_dispute_evidence 全字段行映射。 */
@Data
public class DisputeEvidenceRow {

    private Long evidenceId;
    private Long disputeId;
    private String evidenceKind;
    private String contentUrl;
    private String description;
    private Instant uploadedAt;
    private Instant createTime;
}
