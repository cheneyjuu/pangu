package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.Instant;

/**
 * t_governance_lock 全字段行映射。
 *
 * <p>由 {@code GovernanceLockRepositoryImpl} 在 row ↔ {@code GovernanceLock} 聚合根之间
 * 双向翻译。{@code entityType} 直接保存枚举名 (VARCHAR(32))，与 V2.5 schema 的
 * {@code chk_lock_entity_type} CHECK 约束严格对齐。
 */
@Data
public class GovernanceLockRow {

    private Long lockId;
    private Long tenantId;
    private String entityType;
    private Long entityId;
    private Long lockedByUserId;
    private Instant lockedAt;
    private String lockPayloadHash;

    private Integer status;

    private Long unlockCommitteeUserId;
    private Instant unlockCommitteeAt;
    private String unlockCommitteeSignature;

    private Long unlockStreetUserId;
    private Instant unlockStreetAt;
    private String unlockStreetSignature;

    private Instant unlockAt;

    private long version;
    private Instant createTime;
    private Instant updateTime;
}
