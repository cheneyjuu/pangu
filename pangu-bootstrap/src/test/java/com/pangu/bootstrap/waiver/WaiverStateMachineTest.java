package com.pangu.bootstrap.waiver;

import com.pangu.domain.model.waiver.PartyRatioWaiver;
import com.pangu.domain.model.waiver.WaiverStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link PartyRatioWaiver} 状态机的纯域行为测试。
 *
 * <p>验证：
 * <ul>
 *   <li>正向链路 DRAFT → PENDING_COMMITTEE → PENDING_STREET → APPROVED → APPLIED 全链路允许；</li>
 *   <li>跨级跳转（DRAFT 直接到 APPROVED 等）必须被拒；</li>
 *   <li>终止态（REJECTED / REVOKED / REVOKED_BY_SYSTEM / APPLIED）禁止任何流转；</li>
 *   <li>审批人 dept_type 必须与角色匹配（居委会=2 / 街道办=1）；</li>
 *   <li>同一审批人不可既任初审又任终审（双签强制）；</li>
 *   <li>构造工厂校验（ratio ∈ [0, 0.5)，pool 非负，reasonText 非空）。</li>
 * </ul>
 */
public class WaiverStateMachineTest {

    private static final Long SUBJECT = 1001L;
    private static final Long TENANT = 9001L;
    private static final Long INITIATOR = 7001L;  // 居委会发起人
    private static final Long COMMITTEE_APPROVER = 7002L;  // 居委会审批人（≠ initiator）
    private static final Long STREET_APPROVER = 8001L;     // 街道办审批人

    private PartyRatioWaiver newDraft() {
        return PartyRatioWaiver.draft(SUBJECT, TENANT, INITIATOR,
                new BigDecimal("0.30"), 5L, 20L,
                "本小区党员人数实属严重不足，已多次组织发动仍无候选人，特申请放宽至 30%", null);
    }

    // ===== 工厂校验 =====

    @Test
    public void draft_rejectsInvalidRequestedRatio() {
        // ratio < 0
        assertThrows(IllegalArgumentException.class, () -> PartyRatioWaiver.draft(
                SUBJECT, TENANT, INITIATOR, new BigDecimal("-0.01"), 0L, 0L, "理由 reason text", null));
        // ratio = 0.5
        assertThrows(IllegalArgumentException.class, () -> PartyRatioWaiver.draft(
                SUBJECT, TENANT, INITIATOR, new BigDecimal("0.50"), 0L, 0L, "理由 reason text", null));
        // ratio > 0.5
        assertThrows(IllegalArgumentException.class, () -> PartyRatioWaiver.draft(
                SUBJECT, TENANT, INITIATOR, new BigDecimal("0.60"), 0L, 0L, "理由 reason text", null));
        // ratio = null
        assertThrows(IllegalArgumentException.class, () -> PartyRatioWaiver.draft(
                SUBJECT, TENANT, INITIATOR, null, 0L, 0L, "理由 reason text", null));
    }

    @Test
    public void draft_rejectsBlankReasonAndNullIds() {
        assertThrows(IllegalArgumentException.class, () -> PartyRatioWaiver.draft(
                SUBJECT, TENANT, INITIATOR, new BigDecimal("0.30"), 0L, 0L, "", null));
        assertThrows(IllegalArgumentException.class, () -> PartyRatioWaiver.draft(
                SUBJECT, TENANT, INITIATOR, new BigDecimal("0.30"), 0L, 0L, "   ", null));
        assertThrows(IllegalArgumentException.class, () -> PartyRatioWaiver.draft(
                null, TENANT, INITIATOR, new BigDecimal("0.30"), 0L, 0L, "理由 reason text", null));
        assertThrows(IllegalArgumentException.class, () -> PartyRatioWaiver.draft(
                SUBJECT, null, INITIATOR, new BigDecimal("0.30"), 0L, 0L, "理由 reason text", null));
    }

    @Test
    public void draft_rejectsNegativePoolSizes() {
        assertThrows(IllegalArgumentException.class, () -> PartyRatioWaiver.draft(
                SUBJECT, TENANT, INITIATOR, new BigDecimal("0.30"), -1L, 0L, "理由 reason text", null));
        assertThrows(IllegalArgumentException.class, () -> PartyRatioWaiver.draft(
                SUBJECT, TENANT, INITIATOR, new BigDecimal("0.30"), 0L, -1L, "理由 reason text", null));
    }

    // ===== 正向链路 =====

    @Test
    public void forwardChain_allTransitionsAllowed() {
        PartyRatioWaiver w = newDraft();
        assertEquals(WaiverStatus.DRAFT, w.getStatus());

        w.transitionTo(WaiverStatus.PENDING_COMMITTEE);
        assertEquals(WaiverStatus.PENDING_COMMITTEE, w.getStatus());

        w.approveByCommittee(COMMITTEE_APPROVER, "情况属实");
        assertEquals(WaiverStatus.PENDING_STREET, w.getStatus());
        assertEquals(COMMITTEE_APPROVER, w.getCommitteeApprover());

        w.approveByStreet(STREET_APPROVER, "终审通过");
        assertEquals(WaiverStatus.APPROVED, w.getStatus());
        assertEquals(STREET_APPROVER, w.getStreetApprover());

        w.lockLocalPayloadHash("a".repeat(64));
        assertEquals("a".repeat(64), w.getLocalPayloadHash());
        assertNotNull(w.getLocalPayloadLockedAt());

        w.apply();
        assertEquals(WaiverStatus.APPLIED, w.getStatus());
        assertNotNull(w.getAppliedAt());
        assertTrue(w.getStatus().isTerminal());
    }

    // ===== 跨级跳转 =====

    @Test
    public void cannotSkipFromDraftToApproved() {
        PartyRatioWaiver w = newDraft();
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> w.transitionTo(WaiverStatus.APPROVED));
        assertTrue(ex.getMessage().contains("DRAFT -> APPROVED"));
    }

    @Test
    public void cannotSkipFromPendingCommitteeToApproved() {
        PartyRatioWaiver w = newDraft();
        w.transitionTo(WaiverStatus.PENDING_COMMITTEE);
        assertThrows(IllegalStateException.class,
                () -> w.transitionTo(WaiverStatus.APPROVED));
    }

    @Test
    public void cannotTransitionToSameStatus() {
        PartyRatioWaiver w = newDraft();
        w.transitionTo(WaiverStatus.PENDING_COMMITTEE);
        assertThrows(IllegalStateException.class,
                () -> w.transitionTo(WaiverStatus.PENDING_COMMITTEE));
    }

    // ===== 终止态不可流转 =====

    @Test
    public void cannotTransitionFromTerminalRejected() {
        PartyRatioWaiver w = newDraft();
        w.transitionTo(WaiverStatus.PENDING_COMMITTEE);
        w.reject(COMMITTEE_APPROVER, "材料不全");
        assertEquals(WaiverStatus.REJECTED, w.getStatus());
        assertTrue(w.getStatus().isTerminal());
        // 任何后续流转都应被拒
        assertThrows(IllegalStateException.class,
                () -> w.transitionTo(WaiverStatus.PENDING_COMMITTEE));
        assertThrows(IllegalStateException.class,
                () -> w.transitionTo(WaiverStatus.APPROVED));
    }

    @Test
    public void cannotTransitionFromAppliedTerminal() {
        PartyRatioWaiver w = newDraft();
        w.transitionTo(WaiverStatus.PENDING_COMMITTEE);
        w.approveByCommittee(COMMITTEE_APPROVER, "ok");
        w.approveByStreet(STREET_APPROVER, "ok");
        w.apply();
        assertTrue(w.getStatus().isTerminal());
        assertThrows(IllegalStateException.class,
                () -> w.transitionTo(WaiverStatus.REVOKED));
    }

    @Test
    public void cannotRevokeAlreadyRevoked() {
        PartyRatioWaiver w = newDraft();
        w.revokeManually();
        assertTrue(w.getStatus().isTerminal());
        assertThrows(IllegalStateException.class, w::revokeManually);
    }

    // ===== 审批人 dept_type 校验 =====
    // M1 RBAC 重构后，dept_type 校验已下沉至 controller 层 @PreAuthorize
    // (waiver:approve:committee / waiver:approve:street)，聚合根不再校验 dept_type。
    // 原 committeeApprover_mustBeDept2 / streetApprover_mustBeDept1 单测已删除，
    // 由 PreAuthorizeMatrixTest（Task #21）覆盖 RBAC 拒绝路径。

    @Test
    public void approvalRequiresPendingState() {
        PartyRatioWaiver w = newDraft();
        // DRAFT 状态不能直接走 approveByCommittee（必须先 transitionTo PENDING_COMMITTEE）
        assertThrows(IllegalStateException.class,
                () -> w.approveByCommittee(COMMITTEE_APPROVER, "ok"));

        w.transitionTo(WaiverStatus.PENDING_COMMITTEE);
        // PENDING_COMMITTEE 状态不能直接走 approveByStreet
        assertThrows(IllegalStateException.class,
                () -> w.approveByStreet(STREET_APPROVER, "ok"));
    }

    // ===== 双签强制（同一审批人不可初/终签） =====

    @Test
    public void committeeAndStreetApproverCannotBeSamePerson_evenIfDeptOk() {
        PartyRatioWaiver w = newDraft();
        w.transitionTo(WaiverStatus.PENDING_COMMITTEE);
        w.approveByCommittee(COMMITTEE_APPROVER, "ok");
        // 居委会审批人 7002 试图作为街道办终审人（即使 dept_type 传 1）
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> w.approveByStreet(COMMITTEE_APPROVER, "ok"));
        assertTrue(ex.getMessage().contains("终审与初审审批人不能为同一人"));
    }

    @Test
    public void rejectAtStreet_alsoForbidsSameApproverAsCommittee() {
        PartyRatioWaiver w = newDraft();
        w.transitionTo(WaiverStatus.PENDING_COMMITTEE);
        w.approveByCommittee(COMMITTEE_APPROVER, "ok");
        // 终审驳回也走双签校验（防止同一人初审通过又终审驳回）
        assertThrows(IllegalStateException.class,
                () -> w.reject(COMMITTEE_APPROVER, "驳回"));
    }

    // ===== 系统断路器自动撤销 =====

    @Test
    public void revokeBySystem_onlyAllowedFromApproved() {
        PartyRatioWaiver w = newDraft();
        // DRAFT 不能 revokeBySystem
        assertThrows(IllegalStateException.class, w::revokeBySystem);
        w.transitionTo(WaiverStatus.PENDING_COMMITTEE);
        // PENDING_COMMITTEE 不能 revokeBySystem
        assertThrows(IllegalStateException.class, w::revokeBySystem);

        w.approveByCommittee(COMMITTEE_APPROVER, "ok");
        w.approveByStreet(STREET_APPROVER, "ok");
        // APPROVED 阶段允许
        w.revokeBySystem();
        assertEquals(WaiverStatus.REVOKED_BY_SYSTEM, w.getStatus());
        assertTrue(w.getStatus().isTerminal());
    }

    // ===== payload hash 锁定 =====

    @Test
    public void lockLocalPayloadHash_validation() {
        PartyRatioWaiver w = newDraft();
        // 非 APPROVED 不能 lock
        assertThrows(IllegalStateException.class, () -> w.lockLocalPayloadHash("a".repeat(64)));

        w.transitionTo(WaiverStatus.PENDING_COMMITTEE);
        w.approveByCommittee(COMMITTEE_APPROVER, "ok");
        w.approveByStreet(STREET_APPROVER, "ok");

        // hash 长度错误
        assertThrows(IllegalArgumentException.class, () -> w.lockLocalPayloadHash("abc"));
        assertThrows(IllegalArgumentException.class, () -> w.lockLocalPayloadHash(null));

        w.lockLocalPayloadHash("a".repeat(64));
        // 已锁定后不能再次锁定
        assertThrows(IllegalStateException.class, () -> w.lockLocalPayloadHash("b".repeat(64)));
    }
}
