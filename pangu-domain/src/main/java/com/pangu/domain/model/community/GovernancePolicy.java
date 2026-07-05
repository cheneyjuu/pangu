package com.pangu.domain.model.community;

import java.time.Instant;

/**
 * 地方议事规则 / 治理策略模板。
 */
public record GovernancePolicy(
        Long policyId,
        String policyCode,
        String policyName,
        String policyVersion,
        String abstentionStrategy,
        String sharedOwnershipStrategy,
        String ownerRepresentativeStrategy,
        String unvotedOwnerStrategy,
        String summaryJson,
        int status,
        Instant effectiveAt
) {
}
