package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.Instant;

/**
 * t_disclosure_audit_compare 全字段行映射。
 *
 * <p>由 {@code DisclosureCompareRepositoryImpl} 在 row ↔
 * {@code DisclosureCompareRepository.CompareRecord} record 之间双向翻译。
 * {@code diffJson} 为 canonical JSON 字符串，MyBatis insert 时 {@code CAST(#{diffJson} AS JSONB)}。
 */
@Data
public class DisclosureCompareRow {

    private Long compareId;
    private Long tenantId;
    private Long prevSnapshotId;
    private Long currSnapshotId;
    private String diffJson;
    private Integer writeCount;
    private Integer readCount;
    private Integer noChangeCount;
    private Long auditedByUserId;
    private Instant auditedAt;
    private Instant createTime;
}
