// 关联业务：表示一次共同决定中已绑定方案、规则、范围、名册和投票窗口的通用表决包。
package com.pangu.domain.model.voting;

import java.time.Instant;
import java.util.Objects;

/**
 * 通用表决包聚合根。
 *
 * <p>表决包冻结后，方案、规则、决定范围、收集方式、时间窗口和表决人名册均不得再变更。
 */
public final class VotingExecutionPackage {

    public enum BusinessType {
        OWNERS_ASSEMBLY,
        REPAIR_PROJECT
    }

    /**
     * 本次实际收集方式。线上为主时仍保留纸质协助路径，不存在完全排除纸质办理的“纯线上”模式。
     */
    public enum CollectionMode {
        PAPER,
        ONLINE_WITH_PAPER_ASSISTANCE,
        PAPER_AND_ONLINE
    }

    /** 本次冻结的跨渠道重复票处理方式；与收票方式是两个独立维度。 */
    public enum DuplicateBallotPolicy {
        NOT_APPLICABLE,
        FIRST_VALID_WINS,
        PAPER_PREVAILS,
        ONLINE_PREVAILS
    }

    public enum Status {
        DRAFT,
        FROZEN,
        VOTING,
        CLOSED,
        SETTLED,
        VOIDED
    }

    private Long packageId;
    private final Long tenantId;
    private final BusinessType businessType;
    private final Long businessReferenceId;
    private final String proposalSnapshotType;
    private final Long proposalSnapshotId;
    private final String proposalSnapshotHash;
    private final String ruleSnapshotType;
    private final Long ruleSnapshotId;
    private final String ruleSnapshotHash;
    private final VotingScope scope;
    private final Long scopeReferenceId;
    private final CollectionMode collectionMode;
    private final DuplicateBallotPolicy duplicateBallotPolicy;
    private Status status;
    private final Instant voteStartAt;
    private final Instant voteEndAt;
    private String packageHash;
    private Long electorateSnapshotId;
    private final Long createdByUserId;
    private Long frozenByUserId;
    private Instant frozenAt;
    private long version;

    private VotingExecutionPackage(Long packageId,
                                   Long tenantId,
                                   BusinessType businessType,
                                   Long businessReferenceId,
                                   String proposalSnapshotType,
                                   Long proposalSnapshotId,
                                   String proposalSnapshotHash,
                                   String ruleSnapshotType,
                                   Long ruleSnapshotId,
                                   String ruleSnapshotHash,
                                   VotingScope scope,
                                   Long scopeReferenceId,
                                   CollectionMode collectionMode,
                                   DuplicateBallotPolicy duplicateBallotPolicy,
                                   Status status,
                                   Instant voteStartAt,
                                   Instant voteEndAt,
                                   String packageHash,
                                   Long electorateSnapshotId,
                                   Long createdByUserId,
                                   Long frozenByUserId,
                                   Instant frozenAt,
                                   long version) {
        this.packageId = packageId;
        this.tenantId = tenantId;
        this.businessType = businessType;
        this.businessReferenceId = businessReferenceId;
        this.proposalSnapshotType = proposalSnapshotType;
        this.proposalSnapshotId = proposalSnapshotId;
        this.proposalSnapshotHash = proposalSnapshotHash;
        this.ruleSnapshotType = ruleSnapshotType;
        this.ruleSnapshotId = ruleSnapshotId;
        this.ruleSnapshotHash = ruleSnapshotHash;
        this.scope = scope;
        this.scopeReferenceId = scopeReferenceId;
        this.collectionMode = collectionMode;
        this.duplicateBallotPolicy = duplicateBallotPolicy;
        this.status = status;
        this.voteStartAt = voteStartAt;
        this.voteEndAt = voteEndAt;
        this.packageHash = packageHash;
        this.electorateSnapshotId = electorateSnapshotId;
        this.createdByUserId = createdByUserId;
        this.frozenByUserId = frozenByUserId;
        this.frozenAt = frozenAt;
        this.version = version;
    }

    public static VotingExecutionPackage draft(Long tenantId,
                                                BusinessType businessType,
                                                Long businessReferenceId,
                                                String proposalSnapshotType,
                                                Long proposalSnapshotId,
                                                String proposalSnapshotHash,
                                                String ruleSnapshotType,
                                                Long ruleSnapshotId,
                                                String ruleSnapshotHash,
                                                VotingScope scope,
                                                Long scopeReferenceId,
                                                CollectionMode collectionMode,
                                                DuplicateBallotPolicy duplicateBallotPolicy,
                                                Instant voteStartAt,
                                                Instant voteEndAt,
                                                Long createdByUserId) {
        requirePositive(tenantId, "tenantId");
        Objects.requireNonNull(businessType, "businessType 不能为空");
        requirePositive(businessReferenceId, "businessReferenceId");
        String normalizedProposalType = requireText(proposalSnapshotType, "proposalSnapshotType");
        requirePositive(proposalSnapshotId, "proposalSnapshotId");
        String normalizedProposalHash = requireSha256(proposalSnapshotHash, "proposalSnapshotHash");
        String normalizedRuleType = requireText(ruleSnapshotType, "ruleSnapshotType");
        requirePositive(ruleSnapshotId, "ruleSnapshotId");
        String normalizedRuleHash = requireSha256(ruleSnapshotHash, "ruleSnapshotHash");
        Objects.requireNonNull(scope, "scope 不能为空");
        if ((scope == VotingScope.BUILDING || scope == VotingScope.REPAIR_ALLOCATION)
                && scopeReferenceId == null) {
            throw new IllegalArgumentException(scope + " 范围必须提供 scopeReferenceId");
        }
        if (scope == VotingScope.UNIT) {
            throw new IllegalArgumentException("UNIT 决定范围尚未建模");
        }
        Objects.requireNonNull(collectionMode, "collectionMode 不能为空");
        requireCompatibleDuplicatePolicy(collectionMode, duplicateBallotPolicy);
        Objects.requireNonNull(voteStartAt, "voteStartAt 不能为空");
        Objects.requireNonNull(voteEndAt, "voteEndAt 不能为空");
        if (!voteEndAt.isAfter(voteStartAt)) {
            throw new IllegalArgumentException("voteEndAt 必须晚于 voteStartAt");
        }
        requirePositive(createdByUserId, "createdByUserId");
        return new VotingExecutionPackage(
                null,
                tenantId,
                businessType,
                businessReferenceId,
                normalizedProposalType,
                proposalSnapshotId,
                normalizedProposalHash,
                normalizedRuleType,
                ruleSnapshotId,
                normalizedRuleHash,
                scope,
                scopeReferenceId,
                collectionMode,
                duplicateBallotPolicy,
                Status.DRAFT,
                voteStartAt,
                voteEndAt,
                null,
                null,
                createdByUserId,
                null,
                null,
                0L);
    }

    /** 仅供仓储从持久化记录还原聚合。 */
    public static VotingExecutionPackage restore(Long packageId,
                                                  Long tenantId,
                                                  BusinessType businessType,
                                                  Long businessReferenceId,
                                                  String proposalSnapshotType,
                                                  Long proposalSnapshotId,
                                                  String proposalSnapshotHash,
                                                  String ruleSnapshotType,
                                                  Long ruleSnapshotId,
                                                  String ruleSnapshotHash,
                                                  VotingScope scope,
                                                  Long scopeReferenceId,
                                                  CollectionMode collectionMode,
                                                  DuplicateBallotPolicy duplicateBallotPolicy,
                                                  Status status,
                                                  Instant voteStartAt,
                                                  Instant voteEndAt,
                                                  String packageHash,
                                                  Long electorateSnapshotId,
                                                  Long createdByUserId,
                                                  Long frozenByUserId,
                                                  Instant frozenAt,
                                                  long version) {
        requirePositive(packageId, "packageId");
        return new VotingExecutionPackage(
                packageId, tenantId, businessType, businessReferenceId,
                proposalSnapshotType, proposalSnapshotId, proposalSnapshotHash,
                ruleSnapshotType, ruleSnapshotId, ruleSnapshotHash,
                scope, scopeReferenceId, collectionMode, duplicateBallotPolicy, status,
                voteStartAt, voteEndAt, packageHash, electorateSnapshotId,
                createdByUserId, frozenByUserId, frozenAt, version);
    }

    public void assignId(Long assignedPackageId) {
        if (packageId != null) {
            throw new IllegalStateException("表决包已有 packageId");
        }
        requirePositive(assignedPackageId, "packageId");
        packageId = assignedPackageId;
    }

    public void freeze(Long snapshotId, String frozenPackageHash, Long actorUserId, Instant now) {
        requireStatus(Status.DRAFT);
        requirePositive(snapshotId, "electorateSnapshotId");
        requirePositive(actorUserId, "actorUserId");
        Objects.requireNonNull(now, "now 不能为空");
        electorateSnapshotId = snapshotId;
        packageHash = requireSha256(frozenPackageHash, "packageHash");
        frozenByUserId = actorUserId;
        frozenAt = now;
        status = Status.FROZEN;
    }

    public void open(Instant now, Long actorUserId) {
        requireStatus(Status.FROZEN);
        requirePositive(actorUserId, "actorUserId");
        Objects.requireNonNull(now, "now 不能为空");
        if (electorateSnapshotId == null || packageHash == null) {
            throw new IllegalStateException("表决人名册和表决包摘要尚未冻结");
        }
        if (now.isBefore(voteStartAt) || !now.isBefore(voteEndAt)) {
            throw new IllegalStateException("当前时间不在表决包投票窗口内");
        }
        status = Status.VOTING;
    }

    public void close(Instant now) {
        requireStatus(Status.VOTING);
        Objects.requireNonNull(now, "now 不能为空");
        if (now.isBefore(voteEndAt)) {
            throw new IllegalStateException("尚未到达投票截止时间");
        }
        status = Status.CLOSED;
    }

    public void settle() {
        requireStatus(Status.CLOSED);
        status = Status.SETTLED;
    }

    public boolean accepts(VoteChannel channel) {
        if (channel == null) {
            return false;
        }
        return switch (collectionMode) {
            case PAPER -> channel.paperLike();
            case ONLINE_WITH_PAPER_ASSISTANCE, PAPER_AND_ONLINE ->
                    channel == VoteChannel.ONLINE || channel.paperLike();
        };
    }

    private void requireStatus(Status expected) {
        if (status != expected) {
            throw new IllegalStateException("表决包状态不允许该操作 status=" + status);
        }
    }

    private static void requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(field + " 必须为正整数");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value.trim();
    }

    private static String requireSha256(String value, String field) {
        String normalized = requireText(value, field).toLowerCase(java.util.Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " 必须为 64 位 SHA-256 摘要");
        }
        return normalized;
    }

    private static void requireCompatibleDuplicatePolicy(
            CollectionMode collectionMode, DuplicateBallotPolicy duplicateBallotPolicy) {
        Objects.requireNonNull(duplicateBallotPolicy, "duplicateBallotPolicy 不能为空");
        if (collectionMode == CollectionMode.PAPER_AND_ONLINE) {
            if (duplicateBallotPolicy == DuplicateBallotPolicy.NOT_APPLICABLE) {
                throw new IllegalArgumentException("纸质与线上并行必须冻结重复票处理方式");
            }
        } else if (duplicateBallotPolicy != DuplicateBallotPolicy.NOT_APPLICABLE) {
            throw new IllegalArgumentException("非并行收票不应设置跨渠道重复票处理方式");
        }
    }

    public Long getPackageId() { return packageId; }
    public Long getTenantId() { return tenantId; }
    public BusinessType getBusinessType() { return businessType; }
    public Long getBusinessReferenceId() { return businessReferenceId; }
    public String getProposalSnapshotType() { return proposalSnapshotType; }
    public Long getProposalSnapshotId() { return proposalSnapshotId; }
    public String getProposalSnapshotHash() { return proposalSnapshotHash; }
    public String getRuleSnapshotType() { return ruleSnapshotType; }
    public Long getRuleSnapshotId() { return ruleSnapshotId; }
    public String getRuleSnapshotHash() { return ruleSnapshotHash; }
    public VotingScope getScope() { return scope; }
    public Long getScopeReferenceId() { return scopeReferenceId; }
    public CollectionMode getCollectionMode() { return collectionMode; }
    public DuplicateBallotPolicy getDuplicateBallotPolicy() { return duplicateBallotPolicy; }
    public Status getStatus() { return status; }
    public Instant getVoteStartAt() { return voteStartAt; }
    public Instant getVoteEndAt() { return voteEndAt; }
    public String getPackageHash() { return packageHash; }
    public Long getElectorateSnapshotId() { return electorateSnapshotId; }
    public Long getCreatedByUserId() { return createdByUserId; }
    public Long getFrozenByUserId() { return frozenByUserId; }
    public Instant getFrozenAt() { return frozenAt; }
    public long getVersion() { return version; }
}
