package com.pangu.domain.model.waiver;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 党员比例放宽申请聚合根。
 *
 * <p>核心职责：
 * <ul>
 *   <li>承载本期申请的所有审批字段（initiator/committee/street）；</li>
 *   <li>维护严格的状态机，禁止跨级跳转 / 终止态再流转 / 同一审批人初审与终审；</li>
 *   <li>不直接持久化（由 application 层调用 repository 完成）；</li>
 *   <li>不引入 Spring/Lombok 之外的框架依赖（保持 domain 框架轻量）。</li>
 * </ul>
 */
public class PartyRatioWaiver {

    /** 合法状态流转（来源 → 允许的目标集）。 */
    private static final Map<WaiverStatus, Set<WaiverStatus>> ALLOWED_TRANSITIONS;
    static {
        ALLOWED_TRANSITIONS = new EnumMap<>(WaiverStatus.class);
        ALLOWED_TRANSITIONS.put(WaiverStatus.DRAFT,
                EnumSet.of(WaiverStatus.PENDING_COMMITTEE, WaiverStatus.REVOKED));
        ALLOWED_TRANSITIONS.put(WaiverStatus.PENDING_COMMITTEE,
                EnumSet.of(WaiverStatus.PENDING_STREET, WaiverStatus.REJECTED, WaiverStatus.REVOKED));
        ALLOWED_TRANSITIONS.put(WaiverStatus.PENDING_STREET,
                EnumSet.of(WaiverStatus.APPROVED, WaiverStatus.REJECTED, WaiverStatus.REVOKED));
        ALLOWED_TRANSITIONS.put(WaiverStatus.APPROVED,
                EnumSet.of(WaiverStatus.APPLIED, WaiverStatus.REVOKED, WaiverStatus.REVOKED_BY_SYSTEM));
        // 终止态不再允许任何流转
        ALLOWED_TRANSITIONS.put(WaiverStatus.REJECTED, EnumSet.noneOf(WaiverStatus.class));
        ALLOWED_TRANSITIONS.put(WaiverStatus.REVOKED, EnumSet.noneOf(WaiverStatus.class));
        ALLOWED_TRANSITIONS.put(WaiverStatus.REVOKED_BY_SYSTEM, EnumSet.noneOf(WaiverStatus.class));
        ALLOWED_TRANSITIONS.put(WaiverStatus.APPLIED, EnumSet.noneOf(WaiverStatus.class));
    }

    private Long waiverId;
    private Long subjectId;
    private Long tenantId;
    private Long initiatorUserId;
    private BigDecimal requestedRatio;
    private long partyPoolSize;
    private long totalEligibleSize;
    private String reasonText;
    private String reasonEvidenceKeys;

    private WaiverStatus status;

    private Long committeeApprover;
    private Instant committeeApprovalAt;
    private String committeeOpinion;
    private String committeeRejectReasonCode;
    private String committeeRejectEvidenceJson;

    private Long streetApprover;
    private Instant streetApprovalAt;
    private String streetOpinion;
    private String streetRejectReasonCode;
    private String streetRejectEvidenceJson;

    private Instant appliedAt;

    private String localPayloadHash;
    private Instant localPayloadLockedAt;
    private String blockchainTxHash;
    private String blockchainChainProvider;
    private int chainAttestStatus = 1; // PENDING
    private int chainAttestAttempts;
    private String chainAttestLastError;
    private Instant chainConfirmedAt;

    private long version;

    private PartyRatioWaiver() {
    }

    /**
     * 构造一条 DRAFT 状态的草稿（尚未提交）。
     */
    public static PartyRatioWaiver draft(Long subjectId, Long tenantId, Long initiatorUserId,
                                          BigDecimal requestedRatio, long partyPoolSize, long totalEligibleSize,
                                          String reasonText, String reasonEvidenceKeys) {
        if (subjectId == null || tenantId == null || initiatorUserId == null) {
            throw new IllegalArgumentException("subjectId / tenantId / initiatorUserId must not be null");
        }
        if (requestedRatio == null
                || requestedRatio.signum() < 0
                || requestedRatio.compareTo(new BigDecimal("0.50")) >= 0) {
            throw new IllegalArgumentException("requestedRatio must be in [0.00, 0.50)");
        }
        if (partyPoolSize < 0 || totalEligibleSize < 0) {
            throw new IllegalArgumentException("pool size must be non-negative");
        }
        if (reasonText == null || reasonText.isBlank()) {
            throw new IllegalArgumentException("reasonText must not be blank");
        }
        PartyRatioWaiver w = new PartyRatioWaiver();
        w.subjectId = subjectId;
        w.tenantId = tenantId;
        w.initiatorUserId = initiatorUserId;
        w.requestedRatio = requestedRatio;
        w.partyPoolSize = partyPoolSize;
        w.totalEligibleSize = totalEligibleSize;
        w.reasonText = reasonText;
        w.reasonEvidenceKeys = reasonEvidenceKeys;
        w.status = WaiverStatus.DRAFT;
        return w;
    }

    /**
     * 不可逆状态机流转校验。
     *
     * @throws IllegalStateException 不允许的流转
     */
    public void transitionTo(WaiverStatus target) {
        if (target == null) {
            throw new IllegalArgumentException("target status must not be null");
        }
        if (status == target) {
            throw new IllegalStateException("Cannot transition to the same status: " + status);
        }
        Set<WaiverStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(status, EnumSet.noneOf(WaiverStatus.class));
        if (!allowed.contains(target)) {
            throw new IllegalStateException(
                    "Illegal status transition: " + status + " -> " + target);
        }
        this.status = target;
    }

    /**
     * 居委会初审通过：流转到 PENDING_STREET。
     *
     * <p>调用方在 controller 层已通过 {@code @PreAuthorize("hasAuthority('waiver:approve:committee')")}
     * 完成权限校验；本聚合仅校验状态机 + 审批人非空。
     *
     * @param approverUserId 审批人 sys_user.user_id
     * @param opinion 意见
     */
    public void approveByCommittee(Long approverUserId, String opinion) {
        requireApprover(approverUserId, "居委会初审人");
        if (status != WaiverStatus.PENDING_COMMITTEE) {
            throw new IllegalStateException("Only PENDING_COMMITTEE can be approved by committee, current=" + status);
        }
        this.committeeApprover = approverUserId;
        this.committeeApprovalAt = Instant.now();
        this.committeeOpinion = opinion;
        transitionTo(WaiverStatus.PENDING_STREET);
    }

    /**
     * 街道办终审通过：流转到 APPROVED。
     *
     * <p>调用方在 controller 层已通过 {@code @PreAuthorize("hasAuthority('waiver:approve:street')")}
     * 完成权限校验；本聚合仅校验状态机 + 审批人不与初审人重合。
     *
     * @param approverUserId 审批人 sys_user.user_id（必须与初审人不同）
     */
    public void approveByStreet(Long approverUserId, String opinion) {
        requireApprover(approverUserId, "街道办终审人");
        if (status != WaiverStatus.PENDING_STREET) {
            throw new IllegalStateException("Only PENDING_STREET can be approved by street, current=" + status);
        }
        if (committeeApprover != null && committeeApprover.equals(approverUserId)) {
            throw new IllegalStateException("终审与初审审批人不能为同一人");
        }
        this.streetApprover = approverUserId;
        this.streetApprovalAt = Instant.now();
        this.streetOpinion = opinion;
        transitionTo(WaiverStatus.APPROVED);
    }

    /**
     * 拒绝：流转到 REJECTED（终止态），可在 PENDING_COMMITTEE / PENDING_STREET 阶段调用。
     *
     * <p>权限由 controller 层 {@code @PreAuthorize} 校验：PENDING_COMMITTEE 阶段
     * 需 {@code waiver:approve:committee}；PENDING_STREET 阶段需 {@code waiver:approve:street}。
     */
    public void reject(Long approverUserId, String opinion) {
        reject(approverUserId, opinion, null, null);
    }

    public void reject(Long approverUserId, String opinion, String reasonCode, String evidenceJson) {
        if (status == WaiverStatus.PENDING_COMMITTEE) {
            requireApprover(approverUserId, "居委会初审人");
            this.committeeApprover = approverUserId;
            this.committeeApprovalAt = Instant.now();
            this.committeeOpinion = opinion;
            this.committeeRejectReasonCode = reasonCode;
            this.committeeRejectEvidenceJson = evidenceJson;
        } else if (status == WaiverStatus.PENDING_STREET) {
            requireApprover(approverUserId, "街道办终审人");
            if (committeeApprover != null && committeeApprover.equals(approverUserId)) {
                throw new IllegalStateException("终审与初审审批人不能为同一人");
            }
            this.streetApprover = approverUserId;
            this.streetApprovalAt = Instant.now();
            this.streetOpinion = opinion;
            this.streetRejectReasonCode = reasonCode;
            this.streetRejectEvidenceJson = evidenceJson;
        } else {
            throw new IllegalStateException("Only PENDING_* can be rejected, current=" + status);
        }
        transitionTo(WaiverStatus.REJECTED);
    }

    /**
     * 人工撤销：流转到 REVOKED。允许在 DRAFT / PENDING_* / APPROVED 阶段调用。
     */
    public void revokeManually() {
        transitionTo(WaiverStatus.REVOKED);
    }

    /**
     * 系统断路器自动撤销：仅允许在 APPROVED 阶段（applied 之前）调用。
     */
    public void revokeBySystem() {
        if (status != WaiverStatus.APPROVED) {
            throw new IllegalStateException("Only APPROVED can be revoked by system, current=" + status);
        }
        transitionTo(WaiverStatus.REVOKED_BY_SYSTEM);
    }

    /**
     * 锁定本地 payload hash（APPROVED 流转后由 application 立即调用）。
     */
    public void lockLocalPayloadHash(String payloadHash) {
        if (status != WaiverStatus.APPROVED) {
            throw new IllegalStateException("Only APPROVED waiver can lock local payload hash");
        }
        if (payloadHash == null || payloadHash.length() != 64) {
            throw new IllegalArgumentException("payloadHash must be 64-hex SHA256");
        }
        if (this.localPayloadHash != null) {
            throw new IllegalStateException("localPayloadHash already locked");
        }
        this.localPayloadHash = payloadHash;
        this.localPayloadLockedAt = Instant.now();
    }

    /**
     * 应用到议题：流转到 APPLIED（结算前由 VotingApplicationService 调用）。
     */
    public void apply() {
        if (status != WaiverStatus.APPROVED) {
            throw new IllegalStateException("Only APPROVED can be applied, current=" + status);
        }
        this.appliedAt = Instant.now();
        transitionTo(WaiverStatus.APPLIED);
    }

    private void requireApprover(Long approverUserId, String label) {
        if (approverUserId == null) {
            throw new IllegalArgumentException(label + " 必须提供");
        }
    }

    // === 属性访问（保持框架轻量；application 层 builder 模式手写） ===

    public Long getWaiverId() { return waiverId; }
    public void setWaiverId(Long waiverId) { this.waiverId = waiverId; }
    public Long getSubjectId() { return subjectId; }
    public Long getTenantId() { return tenantId; }
    public Long getInitiatorUserId() { return initiatorUserId; }
    public BigDecimal getRequestedRatio() { return requestedRatio; }
    public long getPartyPoolSize() { return partyPoolSize; }
    public long getTotalEligibleSize() { return totalEligibleSize; }
    public String getReasonText() { return reasonText; }
    public String getReasonEvidenceKeys() { return reasonEvidenceKeys; }
    public WaiverStatus getStatus() { return status; }
    public Long getCommitteeApprover() { return committeeApprover; }
    public Instant getCommitteeApprovalAt() { return committeeApprovalAt; }
    public String getCommitteeOpinion() { return committeeOpinion; }
    public String getCommitteeRejectReasonCode() { return committeeRejectReasonCode; }
    public String getCommitteeRejectEvidenceJson() { return committeeRejectEvidenceJson; }
    public Long getStreetApprover() { return streetApprover; }
    public Instant getStreetApprovalAt() { return streetApprovalAt; }
    public String getStreetOpinion() { return streetOpinion; }
    public String getStreetRejectReasonCode() { return streetRejectReasonCode; }
    public String getStreetRejectEvidenceJson() { return streetRejectEvidenceJson; }
    public Instant getAppliedAt() { return appliedAt; }
    public String getLocalPayloadHash() { return localPayloadHash; }
    public Instant getLocalPayloadLockedAt() { return localPayloadLockedAt; }
    public String getBlockchainTxHash() { return blockchainTxHash; }
    public void setBlockchainTxHash(String blockchainTxHash) { this.blockchainTxHash = blockchainTxHash; }
    public String getBlockchainChainProvider() { return blockchainChainProvider; }
    public void setBlockchainChainProvider(String blockchainChainProvider) {
        this.blockchainChainProvider = blockchainChainProvider;
    }
    public int getChainAttestStatus() { return chainAttestStatus; }
    public void setChainAttestStatus(int chainAttestStatus) { this.chainAttestStatus = chainAttestStatus; }
    public int getChainAttestAttempts() { return chainAttestAttempts; }
    public void setChainAttestAttempts(int chainAttestAttempts) {
        this.chainAttestAttempts = chainAttestAttempts;
    }
    public String getChainAttestLastError() { return chainAttestLastError; }
    public void setChainAttestLastError(String chainAttestLastError) {
        this.chainAttestLastError = chainAttestLastError;
    }
    public Instant getChainConfirmedAt() { return chainConfirmedAt; }
    public void setChainConfirmedAt(Instant chainConfirmedAt) { this.chainConfirmedAt = chainConfirmedAt; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }

    /**
     * Repository 重建用：仅供持久化层使用。
     */
    public static PartyRatioWaiver rehydrate(
            Long waiverId, Long subjectId, Long tenantId, Long initiatorUserId,
            BigDecimal requestedRatio, long partyPoolSize, long totalEligibleSize,
            String reasonText, String reasonEvidenceKeys, WaiverStatus status,
            Long committeeApprover, Instant committeeApprovalAt, String committeeOpinion,
            String committeeRejectReasonCode, String committeeRejectEvidenceJson,
            Long streetApprover, Instant streetApprovalAt, String streetOpinion,
            String streetRejectReasonCode, String streetRejectEvidenceJson,
            Instant appliedAt, String localPayloadHash, Instant localPayloadLockedAt,
            String blockchainTxHash, String blockchainChainProvider,
            int chainAttestStatus, int chainAttestAttempts, String chainAttestLastError,
            Instant chainConfirmedAt, long version) {
        PartyRatioWaiver w = new PartyRatioWaiver();
        w.waiverId = waiverId;
        w.subjectId = subjectId;
        w.tenantId = tenantId;
        w.initiatorUserId = initiatorUserId;
        w.requestedRatio = requestedRatio;
        w.partyPoolSize = partyPoolSize;
        w.totalEligibleSize = totalEligibleSize;
        w.reasonText = reasonText;
        w.reasonEvidenceKeys = reasonEvidenceKeys;
        w.status = status;
        w.committeeApprover = committeeApprover;
        w.committeeApprovalAt = committeeApprovalAt;
        w.committeeOpinion = committeeOpinion;
        w.committeeRejectReasonCode = committeeRejectReasonCode;
        w.committeeRejectEvidenceJson = committeeRejectEvidenceJson;
        w.streetApprover = streetApprover;
        w.streetApprovalAt = streetApprovalAt;
        w.streetOpinion = streetOpinion;
        w.streetRejectReasonCode = streetRejectReasonCode;
        w.streetRejectEvidenceJson = streetRejectEvidenceJson;
        w.appliedAt = appliedAt;
        w.localPayloadHash = localPayloadHash;
        w.localPayloadLockedAt = localPayloadLockedAt;
        w.blockchainTxHash = blockchainTxHash;
        w.blockchainChainProvider = blockchainChainProvider;
        w.chainAttestStatus = chainAttestStatus;
        w.chainAttestAttempts = chainAttestAttempts;
        w.chainAttestLastError = chainAttestLastError;
        w.chainConfirmedAt = chainConfirmedAt;
        w.version = version;
        return w;
    }
}
