package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * t_party_ratio_waiver 全字段行映射。
 *
 * <p>{@code WaiverPolicyRow} 是断路器路径专用的最小字段视图；本类是聚合根
 * {@code PartyRatioWaiver} 持久化时的完整投影，由 {@code PartyRatioWaiverRepositoryImpl}
 * 在 row ↔ aggregate 之间双向翻译。
 */
@Data
public class PartyRatioWaiverRow {

    private Long waiverId;
    private Long subjectId;
    private Long tenantId;
    private Long initiatorUserId;
    private BigDecimal requestedRatio;
    private long partyPoolSize;
    private long totalEligibleSize;
    private String reasonText;
    private String reasonEvidenceKeys;
    private Integer status;

    private Long committeeApprover;
    private Instant committeeApprovalAt;
    private String committeeOpinion;

    private Long streetApprover;
    private Instant streetApprovalAt;
    private String streetOpinion;

    private Instant appliedAt;

    private String localPayloadHash;
    private Instant localPayloadLockedAt;
    private String blockchainTxHash;
    private String blockchainChainProvider;
    private Integer chainAttestStatus;
    private Integer chainAttestAttempts;
    private String chainAttestLastError;
    private Instant chainConfirmedAt;

    private long version;
    private Instant createTime;
    private Instant updateTime;
}
