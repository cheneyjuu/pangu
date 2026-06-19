package com.pangu.bootstrap.dispute;

import com.pangu.domain.model.dispute.Decision;
import com.pangu.domain.model.dispute.DecisionKind;
import com.pangu.domain.model.dispute.Dispute;
import com.pangu.domain.model.dispute.DisputeKind;
import com.pangu.domain.model.dispute.DisputeStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Dispute} 聚合根纯域行为（与 {@code GovernanceLockStateMachineTest}/{@code DisputeStatus} 对齐）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>{@link Dispute#open(Long, Long, DisputeKind, String, Long, String)} 字段校验 / 起始 level 区分；</li>
 *   <li>正向 4 级链路 RAISED → UNDER_REVIEW → DECIDED_REJECTED → escalate → ... → DECIDED_UPHELD → CLOSED_FINAL；</li>
 *   <li>{@link Dispute#escalate()} 仅在 DECIDED_LEVEL_N_REJECTED 允许；Level 4 必须走诉讼；</li>
 *   <li>{@link Dispute#gotoLitigation()} RAISED / DECIDED_LEVEL_4_REJECTED 单向流转；</li>
 *   <li>{@link Dispute#withdraw()} / {@link Dispute#concludeFinal()} 终态守护；</li>
 *   <li>状态机非法流转抛 {@code IllegalStateException}；rehydrate 完整恢复字段。</li>
 * </ul>
 */
public class DisputeStateMachineTest {

    private static final Long TENANT = 8801L;
    private static final Long OWNER = 70002L;
    private static final Long DECIDER_L2 = 800003L;
    private static final Long DECIDER_L3 = 800001L;
    private static final Long DECIDER_L4 = 800002L;
    private static final String PAYLOAD = "{\"voucher_id\":1234}";

    private Dispute newExpenseVoucherDispute() {
        return Dispute.open(TENANT, OWNER, DisputeKind.EXPENSE_VOUCHER_DISPUTE,
                "EXPENSE_VOUCHER", 9001L, PAYLOAD);
    }

    private Dispute newProposalQualityDispute() {
        return Dispute.open(TENANT, OWNER, DisputeKind.PROPOSAL_QUALITY_DISPUTE,
                "PROPOSAL", 9002L, "{}");
    }

    // ===== open 工厂校验 =====

    @Test
    public void open_rejectsNullArgs() {
        assertThrows(IllegalArgumentException.class, () -> Dispute.open(
                null, OWNER, DisputeKind.EXPENSE_VOUCHER_DISPUTE, "EXPENSE_VOUCHER", 1L, "{}"));
        assertThrows(IllegalArgumentException.class, () -> Dispute.open(
                TENANT, null, DisputeKind.EXPENSE_VOUCHER_DISPUTE, "EXPENSE_VOUCHER", 1L, "{}"));
        assertThrows(IllegalArgumentException.class, () -> Dispute.open(
                TENANT, OWNER, null, "EXPENSE_VOUCHER", 1L, "{}"));
    }

    @Test
    public void open_initialLevelDiffersByKind() {
        // EXPENSE_VOUCHER_DISPUTE 起步 Level 1（业委会一审）
        Dispute expense = newExpenseVoucherDispute();
        assertEquals(1, expense.getCurrentReviewLevel());
        assertEquals(DisputeStatus.RAISED, expense.getStatus());
        assertNotNull(expense.getRaisedAt());
        assertNull(expense.getEscalatedAt());
        assertNull(expense.getClosedAt());
        assertEquals(PAYLOAD, expense.getBusinessPayloadJson());

        // 其他 3 类直接从 Level 2 起（业委会无管辖权）
        Dispute proposal = newProposalQualityDispute();
        assertEquals(2, proposal.getCurrentReviewLevel());

        Dispute offline = Dispute.open(TENANT, OWNER, DisputeKind.OFFLINE_VOTE_DISPUTE,
                null, null, null);
        assertEquals(2, offline.getCurrentReviewLevel());
        assertEquals("{}", offline.getBusinessPayloadJson(),
                "null/blank payload 默认空对象");

        Dispute admin = Dispute.open(TENANT, OWNER, DisputeKind.ADMINISTRATIVE_REJECTION_DISPUTE,
                null, null, "");
        assertEquals(2, admin.getCurrentReviewLevel());
    }

    // ===== 正向 4 级链路 =====

    @Test
    public void forwardChain_level1ToLevel3_upheldThenConcludeFinal() {
        Dispute d = newExpenseVoucherDispute();

        // Level 1：业委会启动审查 → 驳回 → 升级到 Level 2
        d.startReview();
        assertEquals(DisputeStatus.UNDER_REVIEW_LEVEL_1, d.getStatus());
        Decision dec1 = d.decide(DecisionKind.REJECTED, 800101L, "维持物业账单", null);
        assertEquals(DisputeStatus.DECIDED_LEVEL_1_REJECTED, d.getStatus());
        assertEquals(1, dec1.reviewLevel());
        assertEquals(DecisionKind.REJECTED, dec1.kind());

        d.escalate();
        assertEquals(DisputeStatus.UNDER_REVIEW_LEVEL_2, d.getStatus());
        assertEquals(2, d.getCurrentReviewLevel());
        assertNotNull(d.getEscalatedAt());

        // Level 2：街道办驳回 → 升级到 Level 3
        d.decide(DecisionKind.REJECTED, DECIDER_L2, "街道办认为证据不足", null);
        assertEquals(DisputeStatus.DECIDED_LEVEL_2_REJECTED, d.getStatus());
        d.escalate();
        assertEquals(3, d.getCurrentReviewLevel());

        // Level 3：区政府支持业主诉求（UPHELD）→ 业主接受 → CLOSED_FINAL
        Decision dec3 = d.decide(DecisionKind.UPHELD, DECIDER_L3, "支持业主诉求，物业重出账单", "url");
        assertEquals(DisputeStatus.DECIDED_LEVEL_3_UPHELD, d.getStatus());
        assertEquals(3, dec3.reviewLevel());

        d.concludeFinal();
        assertEquals(DisputeStatus.CLOSED_FINAL, d.getStatus());
        assertNotNull(d.getClosedAt());
    }

    @Test
    public void partialUpheld_concludeFinalAlsoAllowed() {
        Dispute d = newProposalQualityDispute();
        d.startReview();
        d.decide(DecisionKind.PARTIAL_UPHELD, DECIDER_L2, "部分支持业主诉求", null);
        assertEquals(DisputeStatus.DECIDED_LEVEL_2_PARTIAL, d.getStatus());
        d.concludeFinal();
        assertEquals(DisputeStatus.CLOSED_FINAL, d.getStatus());
    }

    // ===== escalate 防御 =====

    @Test
    public void escalate_rejectedNonRejectedStatus() {
        Dispute d = newExpenseVoucherDispute();
        d.startReview();
        d.decide(DecisionKind.UPHELD, 800101L, "支持业主", null);
        // UPHELD 状态调 escalate 应被拒
        IllegalStateException ex = assertThrows(IllegalStateException.class, d::escalate);
        assertTrue(ex.getMessage().contains("REJECTED"));
    }

    @Test
    public void escalate_rejectsLevel4_mustGoLitigation() {
        Dispute d = newProposalQualityDispute(); // 从 Level 2 起步
        // 串到 Level 4 REJECTED
        d.startReview();
        d.decide(DecisionKind.REJECTED, DECIDER_L2, "L2 驳回", null);
        d.escalate();
        d.decide(DecisionKind.REJECTED, DECIDER_L3, "L3 驳回", null);
        d.escalate();
        d.decide(DecisionKind.REJECTED, DECIDER_L4, "L4 驳回", null);
        assertEquals(4, d.getCurrentReviewLevel());
        assertEquals(DisputeStatus.DECIDED_LEVEL_4_REJECTED, d.getStatus());

        // Level 4 escalate 必须改走诉讼
        IllegalStateException ex = assertThrows(IllegalStateException.class, d::escalate);
        assertTrue(ex.getMessage().contains("LITIGATION"));

        // 改走 gotoLitigation 通过
        d.gotoLitigation();
        assertEquals(DisputeStatus.LITIGATION_FILED, d.getStatus());
        assertEquals(5, d.getCurrentReviewLevel());
    }

    // ===== gotoLitigation =====

    @Test
    public void gotoLitigation_directFromRaised() {
        // 业主可以越过 1-4 级直接走诉讼（行政不作为场景）
        Dispute d = newExpenseVoucherDispute();
        d.gotoLitigation();
        assertEquals(DisputeStatus.LITIGATION_FILED, d.getStatus());
        assertEquals(5, d.getCurrentReviewLevel());
        assertNotNull(d.getEscalatedAt());
        // LITIGATION_FILED 不算 closed（等 M3-4 判决回流）
        assertNull(d.getClosedAt());
    }

    @Test
    public void gotoLitigation_rejectsNonRaisedNonLevel4Rejected() {
        Dispute d = newExpenseVoucherDispute();
        d.startReview();
        // UNDER_REVIEW 状态不允许直接走诉讼
        assertThrows(IllegalStateException.class, d::gotoLitigation);
    }

    // ===== withdraw =====

    @Test
    public void withdraw_fromRaisedAndUnderReview() {
        Dispute fromRaised = newExpenseVoucherDispute();
        fromRaised.withdraw();
        assertEquals(DisputeStatus.WITHDRAWN, fromRaised.getStatus());
        assertNotNull(fromRaised.getClosedAt());

        Dispute fromUnderReview = newProposalQualityDispute();
        fromUnderReview.startReview();
        fromUnderReview.withdraw();
        assertEquals(DisputeStatus.WITHDRAWN, fromUnderReview.getStatus());
    }

    @Test
    public void withdraw_rejectedFromDecidedOrClosed() {
        Dispute d = newExpenseVoucherDispute();
        d.startReview();
        d.decide(DecisionKind.UPHELD, 800101L, "支持", null);
        // DECIDED 状态不允许撤回
        assertThrows(IllegalStateException.class, d::withdraw);

        d.concludeFinal();
        // CLOSED_FINAL 不允许任何流转
        assertThrows(IllegalStateException.class, d::withdraw);
    }

    // ===== decide 守护 =====

    @Test
    public void decide_rejectsNonUnderReview() {
        Dispute d = newExpenseVoucherDispute();
        // RAISED 状态调 decide 应被拒
        assertThrows(IllegalStateException.class,
                () -> d.decide(DecisionKind.UPHELD, 800101L, "x", null));
    }

    @Test
    public void decide_rejectsBlankContent() {
        Dispute d = newExpenseVoucherDispute();
        d.startReview();
        assertThrows(IllegalArgumentException.class,
                () -> d.decide(DecisionKind.UPHELD, 800101L, "  ", null));
        assertThrows(IllegalArgumentException.class,
                () -> d.decide(null, 800101L, "x", null));
        assertThrows(IllegalArgumentException.class,
                () -> d.decide(DecisionKind.UPHELD, null, "x", null));
    }

    // ===== concludeFinal 守护 =====

    @Test
    public void concludeFinal_rejectsRejectedDecision() {
        Dispute d = newExpenseVoucherDispute();
        d.startReview();
        d.decide(DecisionKind.REJECTED, 800101L, "驳回", null);
        // DECIDED_LEVEL_1_REJECTED 不允许 concludeFinal（只能 escalate）
        assertThrows(IllegalStateException.class, d::concludeFinal);
    }

    // ===== 状态机一般化反例 =====

    @Test
    public void rehydrate_restoresAllFields() {
        Instant t = Instant.parse("2026-06-01T08:00:00Z");
        Dispute d = Dispute.rehydrate(
                42L, TENANT, OWNER, DisputeKind.EXPENSE_VOUCHER_DISPUTE,
                "EXPENSE_VOUCHER", 9001L, 3,
                DisputeStatus.UNDER_REVIEW_LEVEL_3, "{\"x\":1}",
                t.minusSeconds(120), t.minusSeconds(60), null,
                null, null, 7L);
        assertEquals(42L, d.getDisputeId());
        assertEquals(TENANT, d.getTenantId());
        assertEquals(OWNER, d.getRaisedByOwnerId());
        assertEquals(DisputeKind.EXPENSE_VOUCHER_DISPUTE, d.getDisputeKind());
        assertEquals(3, d.getCurrentReviewLevel());
        assertEquals(DisputeStatus.UNDER_REVIEW_LEVEL_3, d.getStatus());
        assertEquals(7L, d.getVersion());
        assertNotNull(d.getEscalatedAt());
        assertNull(d.getClosedAt());
        // 已 rehydrate 的对象仍可流转
        d.decide(DecisionKind.UPHELD, DECIDER_L3, "支持", "url");
        assertEquals(DisputeStatus.DECIDED_LEVEL_3_UPHELD, d.getStatus());
    }
}
