package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.Instant;

@Data
public class GovernancePolicyRow {
    private Long policyId;
    private String policyCode;
    private String policyName;
    private String policyVersion;
    private String abstentionStrategy;
    private String sharedOwnershipStrategy;
    private String ownerRepresentativeStrategy;
    private String unvotedOwnerStrategy;
    private String summaryJson;
    private Integer status;
    private Instant effectiveAt;
}
