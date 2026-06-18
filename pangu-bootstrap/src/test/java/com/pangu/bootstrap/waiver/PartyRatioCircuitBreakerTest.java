package com.pangu.bootstrap.waiver;

import com.pangu.domain.model.voting.RatioCheckTrigger;
import com.pangu.domain.model.waiver.SnapshotComparison;
import com.pangu.domain.model.waiver.WaiverStatus;
import com.pangu.infrastructure.persistence.entity.CandidatePoolCount;
import com.pangu.infrastructure.persistence.entity.WaiverPolicyRow;
import com.pangu.infrastructure.persistence.entity.WaiverSnapshotComparisonRow;
import com.pangu.infrastructure.persistence.mapper.PartyRatioPolicyMapper;
import com.pangu.infrastructure.voting.DefaultPartyRatioPolicyResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * {@link DefaultPartyRatioPolicyResolver} 党员比例断路器三动作回归测试（Mockito 单元测试）。
 *
 * <p>三个核心场景：
 * <ol>
 *   <li>自然 ratio 已 ≥ 50% → REVOKED_BY_SYSTEM，撤销 waiver，返回 empty（引擎走默认 0.50）；</li>
 *   <li>自然 ratio < 50% 且党员池稳定 → NONE，返回 requestedRatio；</li>
 *   <li>自然 ratio < 50% 但党员人数较记录时减少 → WARN_REGRESSION，返回 requestedRatio 但写审计行；</li>
 * </ol>
 *
 * <p>额外 edge：
 * <ul>
 *   <li>无 APPROVED 状态 waiver → 返回 empty 且不写 comparison；</li>
 *   <li>对账行强制写入 audit_hash（即使 NONE 动作）。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class PartyRatioCircuitBreakerTest {

    @Mock
    private PartyRatioPolicyMapper policyMapper;

    @InjectMocks
    private DefaultPartyRatioPolicyResolver resolver;

    private static final Long SUBJECT_ID = 1001L;
    private static final Long WAIVER_ID = 5001L;

    private WaiverPolicyRow approvedWaiverWithRecord(long recordedParty, long recordedEligible) {
        WaiverPolicyRow row = new WaiverPolicyRow();
        row.setWaiverId(WAIVER_ID);
        row.setRequestedRatio(new BigDecimal("0.30"));
        row.setRecordedPartyCount(recordedParty);
        row.setRecordedEligibleCount(recordedEligible);
        row.setStatus(WaiverStatus.APPROVED.getDbValue());
        return row;
    }

    private CandidatePoolCount currentPool(long party, long eligible) {
        CandidatePoolCount c = new CandidatePoolCount();
        c.setPartyCount(party);
        c.setEligibleCount(eligible);
        return c;
    }

    @Test
    public void naturalRatioReachesHalf_revokedBySystem_returnsEmpty() {
        // 申请时：5/20 = 25%；现在：12/20 = 60% → ≥ 50% → 自动撤销
        when(policyMapper.selectEffectiveWaiver(eq(SUBJECT_ID), eq(WaiverStatus.APPROVED.getDbValue())))
                .thenReturn(approvedWaiverWithRecord(5L, 20L));
        when(policyMapper.countCurrentCandidatePool(SUBJECT_ID))
                .thenReturn(currentPool(12L, 20L));
        when(policyMapper.revokeWaiverBySystem(anyLong(), anyInt(), anyInt())).thenReturn(1);

        Optional<BigDecimal> result = resolver.resolveRatio(SUBJECT_ID, RatioCheckTrigger.SETTLE);

        assertTrue(result.isEmpty(), "撤销后应返回 empty 让引擎走默认 0.50");
        verify(policyMapper).revokeWaiverBySystem(
                eq(WAIVER_ID),
                eq(WaiverStatus.REVOKED_BY_SYSTEM.getDbValue()),
                eq(WaiverStatus.APPROVED.getDbValue()));

        ArgumentCaptor<WaiverSnapshotComparisonRow> captor =
                ArgumentCaptor.forClass(WaiverSnapshotComparisonRow.class);
        verify(policyMapper).insertComparison(captor.capture());
        WaiverSnapshotComparisonRow row = captor.getValue();
        assertEquals(SnapshotComparison.SnapshotAction.REVOKED_BY_SYSTEM.getDbValue(),
                row.getActionTaken().intValue());
        assertEquals(WAIVER_ID, row.getWaiverId());
        assertEquals(SUBJECT_ID, row.getSubjectId());
        assertEquals(RatioCheckTrigger.SETTLE.getDbValue(), row.getTriggerPhase().intValue());
        assertEquals(7L, row.getDeltaParty(), "delta = current 12 - recorded 5 = 7");
        assertNotNull(row.getAuditHash());
        assertEquals(64, row.getAuditHash().length(), "auditHash 必须是 64-hex SHA256");
    }

    @Test
    public void naturalRatioStillBelow_andPoolStable_returnsRequestedRatio_actionNone() {
        // 申请时：5/20 = 25%；现在：6/20 = 30% < 50%，党员人数从 5 → 6（无倒退）→ NONE
        when(policyMapper.selectEffectiveWaiver(eq(SUBJECT_ID), eq(WaiverStatus.APPROVED.getDbValue())))
                .thenReturn(approvedWaiverWithRecord(5L, 20L));
        when(policyMapper.countCurrentCandidatePool(SUBJECT_ID))
                .thenReturn(currentPool(6L, 20L));

        Optional<BigDecimal> result = resolver.resolveRatio(SUBJECT_ID, RatioCheckTrigger.PUBLISH_DAY);

        assertTrue(result.isPresent());
        assertEquals(0, new BigDecimal("0.30").compareTo(result.get()));
        verify(policyMapper, never()).revokeWaiverBySystem(anyLong(), anyInt(), anyInt());

        ArgumentCaptor<WaiverSnapshotComparisonRow> captor =
                ArgumentCaptor.forClass(WaiverSnapshotComparisonRow.class);
        verify(policyMapper).insertComparison(captor.capture());
        WaiverSnapshotComparisonRow row = captor.getValue();
        assertEquals(SnapshotComparison.SnapshotAction.NONE.getDbValue(), row.getActionTaken().intValue(),
                "无回退、未达 50% → NONE");
        assertEquals(RatioCheckTrigger.PUBLISH_DAY.getDbValue(), row.getTriggerPhase().intValue());
        assertEquals(1L, row.getDeltaParty(), "delta party = 6 - 5 = +1");
        assertNotNull(row.getAuditHash());
    }

    @Test
    public void naturalRatioBelowButPartyDecreased_warnsRegression_stillReturnsRatio() {
        // 申请时：8/30；现在：6/30 → 党员人数从 8 → 6（减少 2）；ratio 6/30=20% < 50%
        when(policyMapper.selectEffectiveWaiver(eq(SUBJECT_ID), eq(WaiverStatus.APPROVED.getDbValue())))
                .thenReturn(approvedWaiverWithRecord(8L, 30L));
        when(policyMapper.countCurrentCandidatePool(SUBJECT_ID))
                .thenReturn(currentPool(6L, 30L));

        Optional<BigDecimal> result = resolver.resolveRatio(SUBJECT_ID, RatioCheckTrigger.VOTE_END);

        assertTrue(result.isPresent(), "回退仅警告，仍按 requestedRatio 放行");
        assertEquals(0, new BigDecimal("0.30").compareTo(result.get()));
        verify(policyMapper, never()).revokeWaiverBySystem(anyLong(), anyInt(), anyInt());

        ArgumentCaptor<WaiverSnapshotComparisonRow> captor =
                ArgumentCaptor.forClass(WaiverSnapshotComparisonRow.class);
        verify(policyMapper).insertComparison(captor.capture());
        WaiverSnapshotComparisonRow row = captor.getValue();
        assertEquals(SnapshotComparison.SnapshotAction.WARN_REGRESSION.getDbValue(),
                row.getActionTaken().intValue(),
                "党员人数减少应记录 WARN_REGRESSION");
        assertEquals(-2L, row.getDeltaParty(), "delta party = 6 - 8 = -2");
    }

    @Test
    public void noApprovedWaiver_returnsEmpty_skipsComparison() {
        // 没有任何 APPROVED 状态的 waiver（可能仍在审批 / 已驳回 / 已 APPLIED）
        when(policyMapper.selectEffectiveWaiver(eq(SUBJECT_ID), eq(WaiverStatus.APPROVED.getDbValue())))
                .thenReturn(null);

        Optional<BigDecimal> result = resolver.resolveRatio(SUBJECT_ID, RatioCheckTrigger.SETTLE);

        assertTrue(result.isEmpty(), "无 APPROVED waiver → 引擎走默认 0.50 floor");
        verify(policyMapper, never()).countCurrentCandidatePool(anyLong());
        verify(policyMapper, never()).revokeWaiverBySystem(anyLong(), anyInt(), anyInt());
        verify(policyMapper, never()).insertComparison(any());
    }

    @Test
    public void exactlyHalfNaturalRatio_alsoTriggersRevoke() {
        // 边界：恰好 50% 时（>= 比较）也应触发自动撤销
        when(policyMapper.selectEffectiveWaiver(eq(SUBJECT_ID), eq(WaiverStatus.APPROVED.getDbValue())))
                .thenReturn(approvedWaiverWithRecord(3L, 20L));
        when(policyMapper.countCurrentCandidatePool(SUBJECT_ID))
                .thenReturn(currentPool(10L, 20L));  // 10/20 = 0.50 等号
        when(policyMapper.revokeWaiverBySystem(anyLong(), anyInt(), anyInt())).thenReturn(1);

        Optional<BigDecimal> result = resolver.resolveRatio(SUBJECT_ID, RatioCheckTrigger.SETTLE);

        assertTrue(result.isEmpty());
        verify(policyMapper).revokeWaiverBySystem(eq(WAIVER_ID), anyInt(), anyInt());
    }

    @Test
    public void rejectsNullArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolveRatio(null, RatioCheckTrigger.SETTLE));
        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolveRatio(SUBJECT_ID, null));
    }

    @Test
    public void zeroEligibleCount_doesNotDivideByZero() {
        // 极端：候选人池为空（eligible=0）→ ratio 应为 0，行为按 NONE 处理
        when(policyMapper.selectEffectiveWaiver(eq(SUBJECT_ID), eq(WaiverStatus.APPROVED.getDbValue())))
                .thenReturn(approvedWaiverWithRecord(0L, 0L));
        when(policyMapper.countCurrentCandidatePool(SUBJECT_ID))
                .thenReturn(currentPool(0L, 0L));

        Optional<BigDecimal> result = resolver.resolveRatio(SUBJECT_ID, RatioCheckTrigger.SETTLE);

        assertTrue(result.isPresent(), "0/0 不应触发撤销，按 NONE 处理");
        verify(policyMapper, never()).revokeWaiverBySystem(anyLong(), anyInt(), anyInt());

        ArgumentCaptor<WaiverSnapshotComparisonRow> captor =
                ArgumentCaptor.forClass(WaiverSnapshotComparisonRow.class);
        verify(policyMapper).insertComparison(captor.capture());
        assertEquals(0, BigDecimal.ZERO.compareTo(captor.getValue().getCurrentNaturalRatio().stripTrailingZeros()));
    }
}
