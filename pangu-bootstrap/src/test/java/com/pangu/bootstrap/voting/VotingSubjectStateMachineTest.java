package com.pangu.bootstrap.voting;

import com.pangu.domain.model.voting.SubjectStatus;
import com.pangu.domain.model.voting.SubjectType;
import com.pangu.domain.model.voting.VotingScope;
import com.pangu.domain.model.voting.VotingSubject;
import com.pangu.domain.model.voting.VotingSubjectActions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link VotingSubjectActions} 聚合根纯域行为（无 Spring / 持久化依赖，与
 * {@code DisputeStateMachineTest} 同风格）。
 *
 * <p>覆盖（M3-2 锁定的状态机矩阵）：
 * <ul>
 *   <li>{@link VotingSubjectActions#open} 工厂字段校验 / 默认 DRAFT 起步；</li>
 *   <li>正向链路 DRAFT → PUBLISHED →（scheduler）VOTING；</li>
 *   <li>{@link VotingSubjectActions#openVoting} 要求 now ≥ voteStartAt；</li>
 *   <li>{@link VotingSubjectActions#cancelByProposer} 仅 DRAFT + 本人；
 *       {@link VotingSubjectActions#cancelByGovernment} 仅 PUBLISHED；</li>
 *   <li>CANCELLED 终态落 cancel_* 三件套 + reason 边界；</li>
 *   <li>跨级 / 终态非法跳转抛 {@code IllegalSubjectTransitionException}。</li>
 * </ul>
 */
public class VotingSubjectStateMachineTest {

    private static final Long TENANT = 10001L;
    private static final Long PROPOSER = 800101L;
    private static final Long GOV_USER = 800001L;
    private static final Instant START = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-07-15T00:00:00Z");

    private VotingSubject newCommunityDraft() {
        return VotingSubjectActions.open(TENANT, SubjectType.GENERAL, VotingScope.COMMUNITY,
                null, "求是小区 2026 年度维修资金动用议案", START, END, PROPOSER, new BigDecimal("0.50"));
    }

    // ===== open 工厂校验 =====

    @Test
    public void open_buildsDraftWithProposer() {
        VotingSubject draft = newCommunityDraft();
        assertEquals(SubjectStatus.DRAFT, draft.getStatus());
        assertEquals(TENANT, draft.getTenantId());
        assertEquals(SubjectType.GENERAL, draft.getSubjectType());
        assertEquals(VotingScope.COMMUNITY, draft.getScope());
        assertEquals(PROPOSER, draft.getProposedByUserId());
        assertEquals(0L, draft.getVersion());
        assertNull(draft.getCancelledAt(), "DRAFT 不应有 cancel_* 字段");
        assertNull(draft.getCancelledByUserId());
        assertNull(draft.getCancelReason());
    }

    @Test
    public void open_rejectsNullAndBlankAndBadTime() {
        // 关键入参为空
        assertThrows(NullPointerException.class, () -> VotingSubjectActions.open(
                null, SubjectType.GENERAL, VotingScope.COMMUNITY, null, "x", START, END, PROPOSER, null));
        assertThrows(NullPointerException.class, () -> VotingSubjectActions.open(
                TENANT, null, VotingScope.COMMUNITY, null, "x", START, END, PROPOSER, null));
        assertThrows(NullPointerException.class, () -> VotingSubjectActions.open(
                TENANT, SubjectType.GENERAL, VotingScope.COMMUNITY, null, "x", START, END, null, null));
        // 标题空白
        assertThrows(IllegalArgumentException.class, () -> VotingSubjectActions.open(
                TENANT, SubjectType.GENERAL, VotingScope.COMMUNITY, null, "   ", START, END, PROPOSER, null));
        // voteEndAt 不晚于 voteStartAt
        assertThrows(IllegalArgumentException.class, () -> VotingSubjectActions.open(
                TENANT, SubjectType.GENERAL, VotingScope.COMMUNITY, null, "x", END, START, PROPOSER, null));
    }

    @Test
    public void open_scopeReferenceRules() {
        // BUILDING 缺 scopeReferenceId
        assertThrows(IllegalArgumentException.class, () -> VotingSubjectActions.open(
                TENANT, SubjectType.MAJOR, VotingScope.BUILDING, null, "x", START, END, PROPOSER, null));
        // UNIT 暂未实现
        assertThrows(IllegalArgumentException.class, () -> VotingSubjectActions.open(
                TENANT, SubjectType.GENERAL, VotingScope.UNIT, 1L, "x", START, END, PROPOSER, null));
        // BUILDING + scopeReferenceId 合法
        VotingSubject building = VotingSubjectActions.open(
                TENANT, SubjectType.MAJOR, VotingScope.BUILDING, 30001L, "1 栋专项", START, END, PROPOSER, null);
        assertEquals(VotingScope.BUILDING, building.getScope());
        assertEquals(30001L, building.getScopeReferenceId());
    }

    // ===== 正向链路 =====

    @Test
    public void forwardChain_draftToPublishedToVoting() {
        VotingSubject s = newCommunityDraft();
        VotingSubjectActions.publish(s);
        assertEquals(SubjectStatus.PUBLISHED, s.getStatus());

        // scheduler 在 voteStartAt 之后开票
        VotingSubjectActions.openVoting(s, START.plusSeconds(60));
        assertEquals(SubjectStatus.VOTING, s.getStatus());
    }

    @Test
    public void openVoting_rejectsBeforeVoteStart() {
        VotingSubject s = newCommunityDraft();
        VotingSubjectActions.publish(s);
        // 尚未到 voteStartAt
        VotingSubjectActions.IllegalSubjectTransitionException ex = assertThrows(
                VotingSubjectActions.IllegalSubjectTransitionException.class,
                () -> VotingSubjectActions.openVoting(s, START.minusSeconds(1)));
        assertTrue(ex.getMessage().contains("voteStartAt"));
        // 状态未变
        assertEquals(SubjectStatus.PUBLISHED, s.getStatus());
    }

    // ===== cancelByProposer =====

    @Test
    public void cancelByProposer_draftBySelf_landsCancelledWithAudit() {
        VotingSubject s = newCommunityDraft();
        Instant now = Instant.parse("2026-06-25T08:00:00Z");
        VotingSubjectActions.cancelByProposer(s, PROPOSER, "重复立项，撤回", now);
        assertEquals(SubjectStatus.CANCELLED, s.getStatus());
        assertEquals(now, s.getCancelledAt());
        assertEquals(PROPOSER, s.getCancelledByUserId());
        assertEquals("重复立项，撤回", s.getCancelReason());
    }

    @Test
    public void cancelByProposer_rejectsNonProposerAndNonDraft() {
        // 非本人撤 DRAFT
        VotingSubject s1 = newCommunityDraft();
        assertThrows(VotingSubjectActions.IllegalSubjectTransitionException.class,
                () -> VotingSubjectActions.cancelByProposer(s1, 999999L, "非本人", Instant.now()));

        // PUBLISHED 阶段不允许发起者本人撤（应走 government）
        VotingSubject s2 = newCommunityDraft();
        VotingSubjectActions.publish(s2);
        assertThrows(VotingSubjectActions.IllegalSubjectTransitionException.class,
                () -> VotingSubjectActions.cancelByProposer(s2, PROPOSER, "已公示", Instant.now()));
    }

    // ===== cancelByGovernment =====

    @Test
    public void cancelByGovernment_onlyPublished() {
        // PUBLISHED 阶段政府强撤
        VotingSubject published = newCommunityDraft();
        VotingSubjectActions.publish(published);
        VotingSubjectActions.cancelByGovernment(published, GOV_USER, "存在违规内容，街道办强撤", Instant.now());
        assertEquals(SubjectStatus.CANCELLED, published.getStatus());
        assertEquals(GOV_USER, published.getCancelledByUserId());

        // DRAFT 阶段不允许政府强撤
        VotingSubject draft = newCommunityDraft();
        assertThrows(VotingSubjectActions.IllegalSubjectTransitionException.class,
                () -> VotingSubjectActions.cancelByGovernment(draft, GOV_USER, "草稿不强撤", Instant.now()));
    }

    @Test
    public void cancel_reasonBoundary() {
        VotingSubject s1 = newCommunityDraft();
        // 空白 reason
        assertThrows(IllegalArgumentException.class,
                () -> VotingSubjectActions.cancelByProposer(s1, PROPOSER, "  ", Instant.now()));
        // 超 500 字
        VotingSubject s2 = newCommunityDraft();
        String tooLong = "理".repeat(501);
        assertThrows(IllegalArgumentException.class,
                () -> VotingSubjectActions.cancelByProposer(s2, PROPOSER, tooLong, Instant.now()));
    }

    // ===== 非法跳转 / 终态 =====

    @Test
    public void illegalTransitions_rejected() {
        // VOTING 状态调 publish 非法
        VotingSubject voting = newCommunityDraft();
        voting.setStatus(SubjectStatus.VOTING);
        assertThrows(VotingSubjectActions.IllegalSubjectTransitionException.class,
                () -> VotingSubjectActions.publish(voting));

        // SETTLED 终态不可撤回
        VotingSubject settled = newCommunityDraft();
        settled.setStatus(SubjectStatus.SETTLED);
        assertThrows(VotingSubjectActions.IllegalSubjectTransitionException.class,
                () -> VotingSubjectActions.cancelByGovernment(settled, GOV_USER, "终态强撤", Instant.now()));

        // CANCELLED 终态不可再 publish
        VotingSubject cancelled = newCommunityDraft();
        cancelled.setStatus(SubjectStatus.CANCELLED);
        assertThrows(VotingSubjectActions.IllegalSubjectTransitionException.class,
                () -> VotingSubjectActions.publish(cancelled));
    }

    @Test
    public void openVoting_rejectsFromDraft() {
        // DRAFT 直接开票非法（必须先 PUBLISHED）
        VotingSubject draft = newCommunityDraft();
        assertThrows(VotingSubjectActions.IllegalSubjectTransitionException.class,
                () -> VotingSubjectActions.openVoting(draft, START.plusSeconds(60)));
        assertNotNull(draft.getStatus());
    }
}
