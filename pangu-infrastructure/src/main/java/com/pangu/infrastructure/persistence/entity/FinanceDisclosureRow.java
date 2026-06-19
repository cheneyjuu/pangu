package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.Instant;

/**
 * t_finance_disclosure_snapshot 全字段行映射。
 *
 * <p>由 {@code FinanceDisclosureRepositoryImpl} 在 row ↔ {@code FinanceDisclosureSnapshot}
 * 聚合根之间双向翻译。{@code disclosureType} 直接保存枚举名 (VARCHAR(32))，与 V2.7
 * {@code chk_disc_type} CHECK 约束严格对齐；{@code dataPayload} 为 canonical JSON 字符串，
 * MyBatis insert 时 {@code CAST(#{dataPayload} AS JSONB)}。
 */
@Data
public class FinanceDisclosureRow {

    private Long snapshotId;
    private Long tenantId;
    private String period;
    private String disclosureType;
    private Integer status;

    private String dataPayload;
    private Integer statisticsVersion;
    private String payloadHash;

    private Long composedByUserId;
    private Instant composedAt;

    private Long governanceLockId;
    private Instant lockedAt;
    private Instant publishedAt;

    private long version;
    private Instant createTime;
    private Instant updateTime;
}
