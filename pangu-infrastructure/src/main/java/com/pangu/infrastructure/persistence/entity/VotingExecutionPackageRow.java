// 关联业务：映射正式表决统一执行包的冻结依据和状态。
package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.Instant;

@Data
public class VotingExecutionPackageRow {
    private Long packageId;
    private Long tenantId;
    private String businessType;
    private Long businessReferenceId;
    private String proposalSnapshotType;
    private Long proposalSnapshotId;
    private String proposalSnapshotHash;
    private String ruleSnapshotType;
    private Long ruleSnapshotId;
    private String ruleSnapshotHash;
    private Integer scope;
    private Long scopeReferenceId;
    private String collectionMode;
    private String status;
    private Instant voteStartAt;
    private Instant voteEndAt;
    private String packageHash;
    private Long electorateSnapshotId;
    private Long createdByUserId;
    private Long frozenByUserId;
    private Instant frozenAt;
    private Long version;
}
