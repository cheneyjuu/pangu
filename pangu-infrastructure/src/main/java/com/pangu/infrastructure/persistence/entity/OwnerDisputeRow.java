package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.Instant;

/**
 * t_owner_dispute 全字段行映射。
 *
 * <p>由 {@code DisputeRepositoryImpl} 在 row ↔ {@code Dispute} 聚合根之间双向翻译。
 * {@code disputeKind} / {@code status} 直接保存枚举名 (VARCHAR)，与 V2.8 schema 的 CHECK 约束一致。
 * {@code businessPayload} 在 SQL 端用 {@code CAST(? AS JSONB)}（见 mapper XML）。
 */
@Data
public class OwnerDisputeRow {

    private Long disputeId;
    private Long tenantId;
    private Long raisedByOwnerId;
    private String disputeKind;
    private String relatedEntityType;
    private Long relatedEntityId;
    private Integer currentReviewLevel;
    private String status;
    private String businessPayload;
    private Instant raisedAt;
    private Instant escalatedAt;
    private Instant closedAt;
    private String litigationOutcome;
    private String litigationJudgementUrl;
    private long version;
    private Instant createTime;
    private Instant updateTime;
}
