package com.pangu.infrastructure.voting;

import com.pangu.domain.model.voting.PartyRatioPolicyResolver;
import com.pangu.domain.model.voting.RatioCheckTrigger;
import com.pangu.domain.model.waiver.SnapshotComparison;
import com.pangu.domain.model.waiver.WaiverStatus;
import com.pangu.infrastructure.crypto.MerkleHashCalculator;
import com.pangu.infrastructure.persistence.entity.CandidatePoolCount;
import com.pangu.infrastructure.persistence.entity.WaiverPolicyRow;
import com.pangu.infrastructure.persistence.entity.WaiverSnapshotComparisonRow;
import com.pangu.infrastructure.persistence.mapper.PartyRatioPolicyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * {@link PartyRatioPolicyResolver} 默认实现：临门一脚跑党员池 COUNT，
 * 自然达标自动 REVOKED_BY_SYSTEM；无论是否撤销都强制写一条 t_waiver_snapshot_comparison 审计。
 *
 * <p>三种动作：
 * <ul>
 *   <li>{@code NONE}：自然 ratio 仍 < 50% 且党员池未倒退，正常返回 requestedRatio；</li>
 *   <li>{@code REVOKED_BY_SYSTEM}：自然 ratio 已 ≥ 50%，撤销 waiver 并返回 empty（走默认 50%）；</li>
 *   <li>{@code WARN_REGRESSION}：党员池较申请时减少（候选人退出），告警但仍按 requestedRatio 放行。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultPartyRatioPolicyResolver implements PartyRatioPolicyResolver {

    private static final BigDecimal DEFAULT_RATIO_FLOOR = new BigDecimal("0.50");
    private static final int RATIO_SCALE = 4;

    private final PartyRatioPolicyMapper policyMapper;

    @Override
    @Transactional
    public Optional<BigDecimal> resolveRatio(Long subjectId, RatioCheckTrigger trigger) {
        if (subjectId == null) {
            throw new IllegalArgumentException("subjectId 不可为空");
        }
        if (trigger == null) {
            throw new IllegalArgumentException("trigger 不可为空");
        }

        WaiverPolicyRow waiver = policyMapper.selectEffectiveWaiver(
                subjectId,
                WaiverStatus.APPROVED.getDbValue());
        if (waiver == null) {
            // 无 APPROVED 状态 waiver（可能尚未审批通过、已驳回、已撤销，或已 APPLIED 终态）
            // 返回 empty → 引擎走默认 50% floor。APPLIED 终态由 ApplicationService 已写入
            // subject.partyRatioFloor，settle 时直接消费该字段，不需要本路径再次解析。
            return Optional.empty();
        }

        CandidatePoolCount current = policyMapper.countCurrentCandidatePool(subjectId);
        if (current == null) {
            current = new CandidatePoolCount();
        }
        BigDecimal currentNaturalRatio = computeRatio(current.getPartyCount(), current.getEligibleCount());
        BigDecimal recordedRatio = computeRatio(waiver.getRecordedPartyCount(), waiver.getRecordedEligibleCount());

        SnapshotComparison.SnapshotAction action =
                resolveAction(waiver, currentNaturalRatio, current);

        if (action == SnapshotComparison.SnapshotAction.REVOKED_BY_SYSTEM) {
            int revoked = policyMapper.revokeWaiverBySystem(
                    waiver.getWaiverId(),
                    WaiverStatus.REVOKED_BY_SYSTEM.getDbValue(),
                    WaiverStatus.APPROVED.getDbValue());
            log.warn("党员比例自然达标，自动撤销 waiver waiverId={} subjectId={} naturalRatio={} affected={}",
                    waiver.getWaiverId(), subjectId, currentNaturalRatio, revoked);
        } else if (action == SnapshotComparison.SnapshotAction.WARN_REGRESSION) {
            log.warn("党员池较申请时倒退（人数减少），保留 waiver 但记录告警 waiverId={} subjectId={} recorded={} current={}",
                    waiver.getWaiverId(), subjectId, waiver.getRecordedPartyCount(), current.getPartyCount());
        }

        WaiverSnapshotComparisonRow comparison =
                buildComparisonRow(waiver, subjectId, trigger, recordedRatio, current, currentNaturalRatio, action);
        policyMapper.insertComparison(comparison);

        if (action == SnapshotComparison.SnapshotAction.REVOKED_BY_SYSTEM) {
            return Optional.empty();
        }
        return Optional.of(waiver.getRequestedRatio());
    }

    private SnapshotComparison.SnapshotAction resolveAction(WaiverPolicyRow waiver,
                                                            BigDecimal currentNaturalRatio,
                                                            CandidatePoolCount current) {
        // 设计裁决（已与 Codex 对辩并最终保留此顺序）：
        // 1) 自然 ratio ≥ 50% → 立即撤销，让引擎按默认 0.50 floor 复核。
        //    即使党员人数减少+候选池萎缩同时发生，回归默认 floor 后引擎 settle 时
        //    会按 ceil(0.50 * winners) 显式校验党员席位是否足够；不够则结果不通过，
        //    强制业务侧手动干预（重审/重选）。这是合规审计角度更稳妥的保守路线，
        //    优于「用低 floor 偷偷放行」。
        // 2) 仅当自然 ratio 仍 < 50% 但党员人数较 record 时点减少（候选人退出/资格被撤）→
        //    记录 WARN_REGRESSION 但保留 waiver；放宽门槛仍然合理，只是需要审计可见。
        if (currentNaturalRatio.compareTo(DEFAULT_RATIO_FLOOR) >= 0) {
            return SnapshotComparison.SnapshotAction.REVOKED_BY_SYSTEM;
        }
        if (current.getPartyCount() < waiver.getRecordedPartyCount()) {
            return SnapshotComparison.SnapshotAction.WARN_REGRESSION;
        }
        return SnapshotComparison.SnapshotAction.NONE;
    }

    private WaiverSnapshotComparisonRow buildComparisonRow(WaiverPolicyRow waiver,
                                                            Long subjectId,
                                                            RatioCheckTrigger trigger,
                                                            BigDecimal recordedRatio,
                                                            CandidatePoolCount current,
                                                            BigDecimal currentNaturalRatio,
                                                            SnapshotComparison.SnapshotAction action) {
        WaiverSnapshotComparisonRow row = new WaiverSnapshotComparisonRow();
        row.setWaiverId(waiver.getWaiverId());
        row.setSubjectId(subjectId);
        row.setTriggerPhase(trigger.getDbValue());
        row.setRecordedPartyCount(waiver.getRecordedPartyCount());
        row.setRecordedEligibleCount(waiver.getRecordedEligibleCount());
        row.setRecordedRatio(recordedRatio);
        row.setCurrentPartyCount(current.getPartyCount());
        row.setCurrentEligibleCount(current.getEligibleCount());
        row.setCurrentNaturalRatio(currentNaturalRatio);
        row.setDeltaParty(current.getPartyCount() - waiver.getRecordedPartyCount());
        row.setDeltaEligible(current.getEligibleCount() - waiver.getRecordedEligibleCount());
        row.setActionTaken(action.getDbValue());
        row.setAuditHash(computeAuditHash(row));
        return row;
    }

    private BigDecimal computeRatio(long partyCount, long eligibleCount) {
        if (eligibleCount <= 0) {
            return BigDecimal.ZERO.setScale(RATIO_SCALE, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(partyCount)
                .divide(BigDecimal.valueOf(eligibleCount), RATIO_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 行级审计 hash：仅含可重放业务字段，不含 wall-clock 时间。
     * 时间由列 {@code snapshot_at DEFAULT CURRENT_TIMESTAMP} 单独记录，
     * hash 因此可被事后重新计算用于防篡改验证。
     */
    private String computeAuditHash(WaiverSnapshotComparisonRow row) {
        String input = row.getWaiverId()
                + "|" + row.getSubjectId()
                + "|" + row.getTriggerPhase()
                + "|" + row.getRecordedPartyCount()
                + "|" + row.getRecordedEligibleCount()
                + "|" + row.getCurrentPartyCount()
                + "|" + row.getCurrentEligibleCount()
                + "|" + row.getCurrentNaturalRatio().toPlainString()
                + "|" + row.getDeltaParty()
                + "|" + row.getDeltaEligible()
                + "|" + row.getActionTaken();
        return MerkleHashCalculator.sha256Hex(input);
    }
}
