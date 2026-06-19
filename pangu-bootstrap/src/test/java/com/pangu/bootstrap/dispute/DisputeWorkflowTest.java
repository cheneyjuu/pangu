package com.pangu.bootstrap.dispute;

import com.pangu.application.dispute.DisputeApplicationException;
import com.pangu.application.dispute.DisputeApplicationService;
import com.pangu.application.dispute.command.AddEvidenceCommand;
import com.pangu.application.dispute.command.ConcludeCommand;
import com.pangu.application.dispute.command.DecideCommand;
import com.pangu.application.dispute.command.EscalateCommand;
import com.pangu.application.dispute.command.GotoLitigationCommand;
import com.pangu.application.dispute.command.OpenCommand;
import com.pangu.application.dispute.command.StartReviewCommand;
import com.pangu.application.dispute.command.WithdrawCommand;
import com.pangu.domain.model.dispute.DecisionKind;
import com.pangu.domain.model.dispute.Dispute;
import com.pangu.domain.model.dispute.DisputeEvidence;
import com.pangu.domain.model.dispute.DisputeKind;
import com.pangu.domain.model.dispute.DisputeStatus;
import com.pangu.domain.model.dispute.EvidenceKind;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DisputeApplicationService} 全链路集成（与
 * {@code FinanceDisclosureWorkflowTest} 同风格）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>EXPENSE_VOUCHER_DISPUTE 完整 4 级链路 open → startReview → decide(REJECTED) → escalate
 *       → ... → decide(UPHELD) → concludeFinal；</li>
 *   <li>诉讼旁路：open → gotoLitigation 直达 LITIGATION_FILED（closed_at 留空等 M3-4）；</li>
 *   <li>业主越权访问 NOT_FOUND（避免存在性泄漏）；</li>
 *   <li>非发起人调 escalate / withdraw 抛 NOT_OWNER；</li>
 *   <li>addEvidence 在终态拒绝；</li>
 *   <li>db row 持久化字段一致（trigger 10/11 兜底通路）。</li>
 * </ul>
 */
@SpringBootTest
public class DisputeWorkflowTest {

    private static final long TEST_TENANT_ID = 99802L;
    private static final long OWNER_A = 70002L;
    private static final long OWNER_B = 70011L;
    private static final long DECIDER_L1 = 800101L; // 业委会主任
    private static final long DECIDER_L2 = 800003L; // 街道居委会
    private static final long DECIDER_L3 = 800001L; // 街道办主任
    private static final long DECIDER_L4 = 800002L; // 党组织书记

    @Autowired
    private DisputeApplicationService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    public void setUp() {
        cleanUp();
    }

    @AfterEach
    public void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        jdbcTemplate.update(
                "DELETE FROM t_dispute_review_decision WHERE dispute_id IN "
                        + "(SELECT dispute_id FROM t_owner_dispute WHERE tenant_id = ?)",
                TEST_TENANT_ID);
        jdbcTemplate.update(
                "DELETE FROM t_dispute_evidence WHERE dispute_id IN "
                        + "(SELECT dispute_id FROM t_owner_dispute WHERE tenant_id = ?)",
                TEST_TENANT_ID);
        jdbcTemplate.update(
                "DELETE FROM t_owner_dispute WHERE tenant_id = ?", TEST_TENANT_ID);
    }

    // ===== 全链路：业主 → 业委会驳回 → 街道办驳回 → 区政府支持 → CLOSED_FINAL =====

    @Test
    public void fullWorkflow_expenseVoucher_chain1To3_upheld() {
        // 1. 业主 OWNER_A 提起 EXPENSE_VOUCHER_DISPUTE
        Dispute opened = service.open(new OpenCommand(
                TEST_TENANT_ID, OWNER_A, DisputeKind.EXPENSE_VOUCHER_DISPUTE,
                "EXPENSE_VOUCHER", 9001L, "{\"voucher_id\":9001,\"disputed_amount\":1200}"));
        assertEquals(DisputeStatus.RAISED, opened.getStatus());
        assertEquals(1, opened.getCurrentReviewLevel());
        assertNotNull(opened.getDisputeId());
        Long id = opened.getDisputeId();

        // 2. Level 1：业委会启动审查 + 驳回
        Dispute reviewing1 = service.startReview(new StartReviewCommand(id));
        assertEquals(DisputeStatus.UNDER_REVIEW_LEVEL_1, reviewing1.getStatus());
        service.decide(new DecideCommand(
                id, DecisionKind.REJECTED, DECIDER_L1, "维持原账单，业主诉求未成立", "url://l1"));
        // 验证主表 status 与 decision 行均落
        Integer decCount1 = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_dispute_review_decision WHERE dispute_id = ? AND review_level = 1",
                Integer.class, id);
        assertEquals(1, decCount1);

        // 3. 业主升级到 Level 2
        Dispute escalated2 = service.escalate(new EscalateCommand(id, OWNER_A));
        assertEquals(DisputeStatus.UNDER_REVIEW_LEVEL_2, escalated2.getStatus());
        assertEquals(2, escalated2.getCurrentReviewLevel());
        assertNotNull(escalated2.getEscalatedAt());

        // 4. Level 2：街道办驳回 + 业主升级到 Level 3
        service.decide(new DecideCommand(
                id, DecisionKind.REJECTED, DECIDER_L2, "街道办认为证据不足", null));
        service.escalate(new EscalateCommand(id, OWNER_A));

        // 5. Level 3：区政府支持业主 (UPHELD)
        service.decide(new DecideCommand(
                id, DecisionKind.UPHELD, DECIDER_L3, "认定物业账单存在虚增，应退还差额", "url://l3"));

        // 6. 业主接受最终决议
        Dispute concluded = service.concludeFinal(new ConcludeCommand(id, OWNER_A));
        assertEquals(DisputeStatus.CLOSED_FINAL, concluded.getStatus());
        assertNotNull(concluded.getClosedAt());

        // 7. 验证 3 条 decision 全部落库（每级一条；UK uk_decision_dispute_level）
        Integer totalDecisions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_dispute_review_decision WHERE dispute_id = ?",
                Integer.class, id);
        assertEquals(3, totalDecisions);
    }

    // ===== 诉讼旁路：直接 RAISED → LITIGATION_FILED =====

    @Test
    public void litigationBypass_fromRaised_remainsOpenForJudgmentReplay() {
        Dispute opened = service.open(new OpenCommand(
                TEST_TENANT_ID, OWNER_A, DisputeKind.PROPOSAL_QUALITY_DISPUTE,
                "PROPOSAL", 8001L, "{}"));
        // PROPOSAL_QUALITY_DISPUTE 起步 Level 2
        assertEquals(2, opened.getCurrentReviewLevel());

        Dispute litigation = service.gotoLitigation(new GotoLitigationCommand(
                opened.getDisputeId(), OWNER_A));
        assertEquals(DisputeStatus.LITIGATION_FILED, litigation.getStatus());
        assertEquals(5, litigation.getCurrentReviewLevel());
        // LITIGATION_FILED 不是终态：closed_at 必须 NULL（trigger 10-c 守护）
        assertNull(litigation.getClosedAt());
        assertNotNull(litigation.getEscalatedAt());
    }

    // ===== 业主越权 NOT_FOUND（隐藏存在性） =====

    @Test
    public void getDispute_byNonOwner_returnsNotFound() {
        Dispute opened = service.open(new OpenCommand(
                TEST_TENANT_ID, OWNER_A, DisputeKind.EXPENSE_VOUCHER_DISPUTE,
                "EXPENSE_VOUCHER", 9002L, "{}"));
        // OWNER_B 不是 raisedBy，应返回 NOT_FOUND（不是 NOT_OWNER）
        DisputeApplicationException ex = assertThrows(DisputeApplicationException.class,
                () -> service.getDispute(opened.getDisputeId(), OWNER_B));
        assertEquals(DisputeApplicationException.Reason.DISPUTE_NOT_FOUND, ex.getReason());
    }

    @Test
    public void escalate_byNonOwner_throwsNotOwner() {
        Dispute opened = service.open(new OpenCommand(
                TEST_TENANT_ID, OWNER_A, DisputeKind.EXPENSE_VOUCHER_DISPUTE,
                "EXPENSE_VOUCHER", 9003L, "{}"));
        service.startReview(new StartReviewCommand(opened.getDisputeId()));
        service.decide(new DecideCommand(
                opened.getDisputeId(), DecisionKind.REJECTED, DECIDER_L1, "驳回", null));

        // 非 owner 调 escalate 抛 NOT_OWNER（mutating endpoint 不需隐藏存在性）
        DisputeApplicationException ex = assertThrows(DisputeApplicationException.class,
                () -> service.escalate(new EscalateCommand(opened.getDisputeId(), OWNER_B)));
        assertEquals(DisputeApplicationException.Reason.DISPUTE_NOT_OWNER, ex.getReason());
    }

    // ===== escalate 守护：UPHELD 不允许升级 =====

    @Test
    public void escalate_fromUpheld_throwsEscalateRequiresRejected() {
        Dispute opened = service.open(new OpenCommand(
                TEST_TENANT_ID, OWNER_A, DisputeKind.EXPENSE_VOUCHER_DISPUTE,
                "EXPENSE_VOUCHER", 9004L, "{}"));
        service.startReview(new StartReviewCommand(opened.getDisputeId()));
        service.decide(new DecideCommand(
                opened.getDisputeId(), DecisionKind.UPHELD, DECIDER_L1, "支持业主", null));
        // UPHELD 调 escalate → 状态机拒
        DisputeApplicationException ex = assertThrows(DisputeApplicationException.class,
                () -> service.escalate(new EscalateCommand(opened.getDisputeId(), OWNER_A)));
        assertEquals(DisputeApplicationException.Reason.DISPUTE_ESCALATE_REQUIRES_REJECTED,
                ex.getReason());
    }

    // ===== addEvidence 在终态拒绝 =====

    @Test
    public void addEvidence_afterWithdraw_rejectedAsClosed() {
        Dispute opened = service.open(new OpenCommand(
                TEST_TENANT_ID, OWNER_A, DisputeKind.OFFLINE_VOTE_DISPUTE,
                "VOTE", 7001L, "{}"));
        service.withdraw(new WithdrawCommand(opened.getDisputeId(), OWNER_A));

        DisputeApplicationException ex = assertThrows(DisputeApplicationException.class,
                () -> service.addEvidence(new AddEvidenceCommand(
                        opened.getDisputeId(), OWNER_A,
                        EvidenceKind.IMAGE, "http://oss/x.jpg", "现场照片")));
        assertEquals(DisputeApplicationException.Reason.EVIDENCE_DISPUTE_CLOSED, ex.getReason());
    }

    // ===== addEvidence 在 RAISED / UNDER_REVIEW 通过 =====

    @Test
    public void addEvidence_inLiveDispute_persistsRow() {
        Dispute opened = service.open(new OpenCommand(
                TEST_TENANT_ID, OWNER_A, DisputeKind.EXPENSE_VOUCHER_DISPUTE,
                "EXPENSE_VOUCHER", 9005L, "{}"));
        DisputeEvidence ev = service.addEvidence(new AddEvidenceCommand(
                opened.getDisputeId(), OWNER_A,
                EvidenceKind.PDF, "http://oss/contract.pdf", "原始物业服务合同"));
        assertNotNull(ev.evidenceId());

        Integer rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_dispute_evidence WHERE dispute_id = ?",
                Integer.class, opened.getDisputeId());
        assertEquals(1, rowCount);
    }

    // ===== Level 4 REJECTED → escalate 抛 LEVEL_EXCEEDED =====

    @Test
    public void escalate_fromLevel4Rejected_throwsLevelExceeded() {
        Dispute opened = service.open(new OpenCommand(
                TEST_TENANT_ID, OWNER_A, DisputeKind.PROPOSAL_QUALITY_DISPUTE,
                "PROPOSAL", 8002L, "{}"));
        Long id = opened.getDisputeId();
        // 串到 Level 4 REJECTED：起步 L2 → REJ → L3 REJ → L4 REJ
        service.startReview(new StartReviewCommand(id));
        service.decide(new DecideCommand(id, DecisionKind.REJECTED, DECIDER_L2, "L2 驳回", null));
        service.escalate(new EscalateCommand(id, OWNER_A));
        service.decide(new DecideCommand(id, DecisionKind.REJECTED, DECIDER_L3, "L3 驳回", null));
        service.escalate(new EscalateCommand(id, OWNER_A));
        service.decide(new DecideCommand(id, DecisionKind.REJECTED, DECIDER_L4, "L4 驳回", null));

        // L4 REJECTED 调 escalate → LEVEL_EXCEEDED
        DisputeApplicationException ex = assertThrows(DisputeApplicationException.class,
                () -> service.escalate(new EscalateCommand(id, OWNER_A)));
        assertEquals(DisputeApplicationException.Reason.DISPUTE_LEVEL_EXCEEDED, ex.getReason());

        // 改走 gotoLitigation 通过
        Dispute lit = service.gotoLitigation(new GotoLitigationCommand(id, OWNER_A));
        assertEquals(DisputeStatus.LITIGATION_FILED, lit.getStatus());
        assertEquals(5, lit.getCurrentReviewLevel());

        // 验证主表落库一致：status / level / closed_at NULL
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_owner_dispute "
                        + "WHERE dispute_id = ? AND status = 'LITIGATION_FILED' "
                        + "AND current_review_level = 5 AND closed_at IS NULL",
                Integer.class, id);
        assertEquals(1, rows, "LITIGATION_FILED 不算 closed，等 M3-4 判决回流");
    }

    // ===== load 不存在的 disputeId =====

    @Test
    public void startReview_disputeNotFound_throwsNotFound() {
        DisputeApplicationException ex = assertThrows(DisputeApplicationException.class,
                () -> service.startReview(new StartReviewCommand(99999999L)));
        assertEquals(DisputeApplicationException.Reason.DISPUTE_NOT_FOUND, ex.getReason());
    }

    // ===== listOwnerDisputes / listJurisdiction 命中本租户 =====

    @Test
    public void listEndpoints_returnTenantScopedRows() {
        // OWNER_A 开 2 单
        service.open(new OpenCommand(
                TEST_TENANT_ID, OWNER_A, DisputeKind.EXPENSE_VOUCHER_DISPUTE,
                "EXPENSE_VOUCHER", 9006L, "{}"));
        Dispute second = service.open(new OpenCommand(
                TEST_TENANT_ID, OWNER_A, DisputeKind.PROPOSAL_QUALITY_DISPUTE,
                "PROPOSAL", 9007L, "{}"));
        service.startReview(new StartReviewCommand(second.getDisputeId()));

        // 业主自查：能看到自己 2 单
        assertTrue(service.listOwnerDisputes(TEST_TENANT_ID, OWNER_A, 50, 0).size() >= 2);

        // 仲裁工作台 全 status：本租户 2 单
        assertTrue(service.listJurisdiction(TEST_TENANT_ID, null, null, 50, 0).size() >= 2);

        // 按 level=2 过滤：仅 PROPOSAL_QUALITY_DISPUTE 命中
        assertEquals(1, service.listJurisdiction(TEST_TENANT_ID, 2, null, 50, 0).size());
    }
}
