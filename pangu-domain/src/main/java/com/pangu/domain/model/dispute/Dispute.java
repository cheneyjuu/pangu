package com.pangu.domain.model.dispute;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 业主异议聚合根。
 *
 * <p>核心职责：
 * <ul>
 *   <li>承载 5 级行政升级状态机（业委会/街道办/区政府/市政府/行政诉讼）；</li>
 *   <li>校验状态机非法流转、不可逆 / 不可跳级（与 V2.8 trigger 10 双层防护）；</li>
 *   <li>聚合 4 类 dispute_kind 的起始 level 差异（{@link DisputeKind#initialLevel()}）；</li>
 *   <li>不持久化（由 application 层调用 repository 完成）；不引入 Spring/Lombok 依赖（与 {@code GovernanceLock} 一致）。</li>
 * </ul>
 *
 * <p>状态机不允许 {@code DECIDED_LEVEL_4_REJECTED → UNDER_REVIEW_LEVEL_5}：Level 5 = 行政诉讼，
 * 必须通过 {@link #gotoLitigation()} 显式声明（与 ADR-0004 锁定的"诉讼路径独立"边界一致）。
 */
public class Dispute {

    /** 合法状态流转表（来源 → 允许的目标集）。 */
    private static final Map<DisputeStatus, Set<DisputeStatus>> ALLOWED_TRANSITIONS;

    static {
        ALLOWED_TRANSITIONS = new EnumMap<>(DisputeStatus.class);

        // RAISED：可启动审查、可直接走诉讼、可撤回
        ALLOWED_TRANSITIONS.put(DisputeStatus.RAISED, EnumSet.of(
                DisputeStatus.UNDER_REVIEW_LEVEL_1,
                DisputeStatus.UNDER_REVIEW_LEVEL_2,
                DisputeStatus.LITIGATION_FILED,
                DisputeStatus.WITHDRAWN
        ));

        // 各级 UNDER_REVIEW → DECIDED_LEVEL_N_(UPHELD|REJECTED|PARTIAL) 或 WITHDRAWN
        ALLOWED_TRANSITIONS.put(DisputeStatus.UNDER_REVIEW_LEVEL_1, EnumSet.of(
                DisputeStatus.DECIDED_LEVEL_1_UPHELD,
                DisputeStatus.DECIDED_LEVEL_1_REJECTED,
                DisputeStatus.DECIDED_LEVEL_1_PARTIAL,
                DisputeStatus.WITHDRAWN
        ));
        ALLOWED_TRANSITIONS.put(DisputeStatus.UNDER_REVIEW_LEVEL_2, EnumSet.of(
                DisputeStatus.DECIDED_LEVEL_2_UPHELD,
                DisputeStatus.DECIDED_LEVEL_2_REJECTED,
                DisputeStatus.DECIDED_LEVEL_2_PARTIAL,
                DisputeStatus.WITHDRAWN
        ));
        ALLOWED_TRANSITIONS.put(DisputeStatus.UNDER_REVIEW_LEVEL_3, EnumSet.of(
                DisputeStatus.DECIDED_LEVEL_3_UPHELD,
                DisputeStatus.DECIDED_LEVEL_3_REJECTED,
                DisputeStatus.DECIDED_LEVEL_3_PARTIAL,
                DisputeStatus.WITHDRAWN
        ));
        ALLOWED_TRANSITIONS.put(DisputeStatus.UNDER_REVIEW_LEVEL_4, EnumSet.of(
                DisputeStatus.DECIDED_LEVEL_4_UPHELD,
                DisputeStatus.DECIDED_LEVEL_4_REJECTED,
                DisputeStatus.DECIDED_LEVEL_4_PARTIAL,
                DisputeStatus.WITHDRAWN
        ));

        // DECIDED_REJECTED：可升级到下一级 UNDER_REVIEW；Level 4 REJECTED 仅可走诉讼
        ALLOWED_TRANSITIONS.put(DisputeStatus.DECIDED_LEVEL_1_REJECTED, EnumSet.of(
                DisputeStatus.UNDER_REVIEW_LEVEL_2
        ));
        ALLOWED_TRANSITIONS.put(DisputeStatus.DECIDED_LEVEL_2_REJECTED, EnumSet.of(
                DisputeStatus.UNDER_REVIEW_LEVEL_3
        ));
        ALLOWED_TRANSITIONS.put(DisputeStatus.DECIDED_LEVEL_3_REJECTED, EnumSet.of(
                DisputeStatus.UNDER_REVIEW_LEVEL_4
        ));
        ALLOWED_TRANSITIONS.put(DisputeStatus.DECIDED_LEVEL_4_REJECTED, EnumSet.of(
                DisputeStatus.LITIGATION_FILED
        ));

        // DECIDED_UPHELD / DECIDED_PARTIAL：业主接受 → CLOSED_FINAL
        for (DisputeStatus d : new DisputeStatus[]{
                DisputeStatus.DECIDED_LEVEL_1_UPHELD, DisputeStatus.DECIDED_LEVEL_1_PARTIAL,
                DisputeStatus.DECIDED_LEVEL_2_UPHELD, DisputeStatus.DECIDED_LEVEL_2_PARTIAL,
                DisputeStatus.DECIDED_LEVEL_3_UPHELD, DisputeStatus.DECIDED_LEVEL_3_PARTIAL,
                DisputeStatus.DECIDED_LEVEL_4_UPHELD, DisputeStatus.DECIDED_LEVEL_4_PARTIAL
        }) {
            ALLOWED_TRANSITIONS.put(d, EnumSet.of(DisputeStatus.CLOSED_FINAL));
        }

        // 终态：LITIGATION_FILED 等 M3-4 判决回流；CLOSED_FINAL / WITHDRAWN 不再流转
        ALLOWED_TRANSITIONS.put(DisputeStatus.LITIGATION_FILED, EnumSet.of(DisputeStatus.CLOSED_FINAL));
        ALLOWED_TRANSITIONS.put(DisputeStatus.CLOSED_FINAL, EnumSet.noneOf(DisputeStatus.class));
        ALLOWED_TRANSITIONS.put(DisputeStatus.WITHDRAWN, EnumSet.noneOf(DisputeStatus.class));
    }

    private Long disputeId;
    private Long tenantId;
    private Long raisedByOwnerId;
    private DisputeKind disputeKind;
    private String relatedEntityType;
    private Long relatedEntityId;
    private int currentReviewLevel;
    private DisputeStatus status;
    private String businessPayloadJson;
    private Instant raisedAt;
    private Instant escalatedAt;
    private Instant closedAt;
    private String litigationOutcome;
    private String litigationJudgementUrl;
    private long version;

    private Dispute() {
    }

    /**
     * 提起新异议：状态 RAISED，level = kind.initialLevel()。
     *
     * <p>businessPayloadJson 为已序列化好的 JSON 串（NULL → 空对象 "{}"）。
     */
    public static Dispute open(Long tenantId, Long raisedByOwnerId, DisputeKind kind,
                                String relatedEntityType, Long relatedEntityId,
                                String businessPayloadJson) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId must not be null");
        }
        if (raisedByOwnerId == null) {
            throw new IllegalArgumentException("raisedByOwnerId must not be null");
        }
        if (kind == null) {
            throw new IllegalArgumentException("disputeKind must not be null");
        }
        Dispute d = new Dispute();
        d.tenantId = tenantId;
        d.raisedByOwnerId = raisedByOwnerId;
        d.disputeKind = kind;
        d.relatedEntityType = relatedEntityType;
        d.relatedEntityId = relatedEntityId;
        d.currentReviewLevel = kind.initialLevel();
        d.status = DisputeStatus.RAISED;
        d.businessPayloadJson = businessPayloadJson == null || businessPayloadJson.isBlank()
                ? "{}" : businessPayloadJson;
        d.raisedAt = Instant.now();
        return d;
    }

    /**
     * 启动审查：RAISED → UNDER_REVIEW_LEVEL_<currentLevel>。
     */
    public void startReview() {
        if (status != DisputeStatus.RAISED) {
            throw new IllegalStateException(
                    "Only RAISED can be moved to UNDER_REVIEW, current=" + status);
        }
        transitionTo(DisputeStatus.underReviewFor(currentReviewLevel));
    }

    /**
     * 出具决议：UNDER_REVIEW_LEVEL_N → DECIDED_LEVEL_N_<KIND>。
     *
     * @return 新的 {@link Decision} 值对象（disputeId 与 decisionId 由 repository 落库时填充）。
     */
    public Decision decide(DecisionKind kind, Long decidedByUserId, String content, String docUrl) {
        if (kind == null) {
            throw new IllegalArgumentException("decisionKind must not be null");
        }
        if (decidedByUserId == null) {
            throw new IllegalArgumentException("decidedByUserId must not be null");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("decision content must not be blank");
        }
        if (!status.isUnderReview()) {
            throw new IllegalStateException(
                    "Only UNDER_REVIEW_LEVEL_N can be decided, current=" + status);
        }
        if (status.levelOrZero() != currentReviewLevel) {
            throw new IllegalStateException(
                    "status level=" + status.levelOrZero() + " mismatches currentReviewLevel=" + currentReviewLevel);
        }
        DisputeStatus target = DisputeStatus.decidedFor(currentReviewLevel, kind);
        transitionTo(target);
        return new Decision(null, disputeId, currentReviewLevel, decidedByUserId,
                kind, content, docUrl, Instant.now());
    }

    /**
     * 升级到下一级：DECIDED_LEVEL_N_REJECTED → UNDER_REVIEW_LEVEL_N+1（N=4 时拒绝，必须走 gotoLitigation）。
     */
    public void escalate() {
        if (!status.isRejected()) {
            throw new IllegalStateException(
                    "Only DECIDED_LEVEL_N_REJECTED can escalate, current=" + status);
        }
        if (currentReviewLevel == 4) {
            throw new IllegalStateException(
                    "Level 4 REJECTED must go to LITIGATION via gotoLitigation()");
        }
        int nextLevel = currentReviewLevel + 1;
        DisputeStatus target = DisputeStatus.underReviewFor(nextLevel);
        transitionTo(target);
        this.currentReviewLevel = nextLevel;
        this.escalatedAt = Instant.now();
    }

    /**
     * 走 Level 5 行政诉讼：RAISED 或 DECIDED_LEVEL_4_REJECTED → LITIGATION_FILED。
     *
     * <p>terminalOutcome 不在此处填充，等 M3-4 阶段 {@code concludeLitigation} 回流判决。
     */
    public void gotoLitigation() {
        if (status != DisputeStatus.RAISED && status != DisputeStatus.DECIDED_LEVEL_4_REJECTED) {
            throw new IllegalStateException(
                    "Only RAISED or DECIDED_LEVEL_4_REJECTED can go to litigation, current=" + status);
        }
        transitionTo(DisputeStatus.LITIGATION_FILED);
        this.currentReviewLevel = 5;
        this.escalatedAt = Instant.now();
    }

    /**
     * 业主撤回：RAISED / UNDER_REVIEW_* → WITHDRAWN。
     */
    public void withdraw() {
        if (status != DisputeStatus.RAISED && !status.isUnderReview()) {
            throw new IllegalStateException(
                    "Only RAISED or UNDER_REVIEW can withdraw, current=" + status);
        }
        transitionTo(DisputeStatus.WITHDRAWN);
        this.closedAt = Instant.now();
    }

    /**
     * 业主接受最终决议：DECIDED_LEVEL_N_(UPHELD|PARTIAL) → CLOSED_FINAL。
     */
    public void concludeFinal() {
        if (!status.isDecided() || status.isRejected()) {
            throw new IllegalStateException(
                    "Only DECIDED_*_UPHELD or DECIDED_*_PARTIAL can be concluded, current=" + status);
        }
        transitionTo(DisputeStatus.CLOSED_FINAL);
        this.closedAt = Instant.now();
    }

    private void transitionTo(DisputeStatus target) {
        if (target == null) {
            throw new IllegalArgumentException("target status must not be null");
        }
        Set<DisputeStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(status,
                EnumSet.noneOf(DisputeStatus.class));
        if (!allowed.contains(target)) {
            throw new IllegalStateException(
                    "Illegal status transition: " + status + " -> " + target);
        }
        this.status = target;
    }

    // === 属性访问 ===

    public Long getDisputeId() { return disputeId; }
    public void setDisputeId(Long disputeId) { this.disputeId = disputeId; }
    public Long getTenantId() { return tenantId; }
    public Long getRaisedByOwnerId() { return raisedByOwnerId; }
    public DisputeKind getDisputeKind() { return disputeKind; }
    public String getRelatedEntityType() { return relatedEntityType; }
    public Long getRelatedEntityId() { return relatedEntityId; }
    public int getCurrentReviewLevel() { return currentReviewLevel; }
    public DisputeStatus getStatus() { return status; }
    public String getBusinessPayloadJson() { return businessPayloadJson; }
    public Instant getRaisedAt() { return raisedAt; }
    public Instant getEscalatedAt() { return escalatedAt; }
    public Instant getClosedAt() { return closedAt; }
    public String getLitigationOutcome() { return litigationOutcome; }
    public String getLitigationJudgementUrl() { return litigationJudgementUrl; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }

    /** Repository 重建用：仅供持久化层使用。 */
    public static Dispute rehydrate(
            Long disputeId, Long tenantId, Long raisedByOwnerId, DisputeKind kind,
            String relatedEntityType, Long relatedEntityId, int currentReviewLevel,
            DisputeStatus status, String businessPayloadJson,
            Instant raisedAt, Instant escalatedAt, Instant closedAt,
            String litigationOutcome, String litigationJudgementUrl, long version) {
        Dispute d = new Dispute();
        d.disputeId = disputeId;
        d.tenantId = tenantId;
        d.raisedByOwnerId = raisedByOwnerId;
        d.disputeKind = kind;
        d.relatedEntityType = relatedEntityType;
        d.relatedEntityId = relatedEntityId;
        d.currentReviewLevel = currentReviewLevel;
        d.status = status;
        d.businessPayloadJson = businessPayloadJson;
        d.raisedAt = raisedAt;
        d.escalatedAt = escalatedAt;
        d.closedAt = closedAt;
        d.litigationOutcome = litigationOutcome;
        d.litigationJudgementUrl = litigationJudgementUrl;
        d.version = version;
        return d;
    }
}
