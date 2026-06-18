package com.pangu.bootstrap.voting;

import com.pangu.domain.model.voting.Denominator;
import com.pangu.domain.model.voting.GeneralDecisionEngine;
import com.pangu.domain.model.voting.VoteChoice;
import com.pangu.domain.model.voting.VoteItem;
import com.pangu.domain.model.voting.VotingResult;
import com.pangu.domain.model.voting.VotingSubject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link com.pangu.domain.model.voting.AbstractVotingEngine#settle} 模板方法的纯域行为测试。
 *
 * <p>覆盖三类核心契约：
 * <ol>
 *   <li>{@link Denominator} record 的强校验（防止「应过未过」群体诉讼级事故）；</li>
 *   <li>settle null 参数防御；</li>
 *   <li>双 2/3 法定门槛（专有面积 + 人数）—— 任一不达标即视为未召开会议；</li>
 *   <li>同一 uid+opid 重复投票仅计 1 次面积、同一 uid 多 opid 仅计 1 次人头（一户多房合并）。</li>
 * </ol>
 *
 * <p>选用 {@link GeneralDecisionEngine}（最简策略子类）作为引擎载体，避免引入候选人/党员维度噪声。
 */
public class AbstractVotingEngineSettleTest {

    private final GeneralDecisionEngine engine = new GeneralDecisionEngine();

    private static Denominator denom(BigDecimal totalArea, long totalOwnerCount) {
        return new Denominator(totalArea, totalOwnerCount, "0".repeat(64), 1L);
    }

    private static VotingSubject subject() {
        return VotingSubject.builder().subjectId(1L).tenantId(1L).title("t").build();
    }

    // ===== Denominator 强校验 =====

    @Test
    public void denominator_rejectsNonPositiveArea() {
        assertThrows(IllegalArgumentException.class,
                () -> new Denominator(BigDecimal.ZERO, 10L, "0".repeat(64), 1L),
                "totalArea=0 必须被拒");
        assertThrows(IllegalArgumentException.class,
                () -> new Denominator(new BigDecimal("-1.00"), 10L, "0".repeat(64), 1L),
                "totalArea<0 必须被拒");
        assertThrows(IllegalArgumentException.class,
                () -> new Denominator(null, 10L, "0".repeat(64), 1L),
                "totalArea=null 必须被拒");
    }

    @Test
    public void denominator_rejectsNonPositiveOwnerCount() {
        assertThrows(IllegalArgumentException.class,
                () -> new Denominator(new BigDecimal("100"), 0L, "0".repeat(64), 1L));
        assertThrows(IllegalArgumentException.class,
                () -> new Denominator(new BigDecimal("100"), -1L, "0".repeat(64), 1L));
    }

    @Test
    public void denominator_rejectsInvalidSnapshotHash() {
        assertThrows(IllegalArgumentException.class,
                () -> new Denominator(new BigDecimal("100"), 1L, null, 1L),
                "snapshotHash=null 必须被拒");
        assertThrows(IllegalArgumentException.class,
                () -> new Denominator(new BigDecimal("100"), 1L, "abc", 1L),
                "非 64-hex 长度必须被拒");
    }

    @Test
    public void denominator_rejectsNullSnapshotId() {
        assertThrows(IllegalArgumentException.class,
                () -> new Denominator(new BigDecimal("100"), 1L, "0".repeat(64), null));
    }

    // ===== settle null 参数防御 =====

    @Test
    public void settle_rejectsNullArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> engine.settle(null, List.of(), denom(new BigDecimal("100"), 1L)));
        assertThrows(IllegalArgumentException.class,
                () -> engine.settle(subject(), null, denom(new BigDecimal("100"), 1L)));
        assertThrows(IllegalArgumentException.class,
                () -> engine.settle(subject(), List.of(), null));
    }

    // ===== 双 2/3 法定门槛 =====

    @Test
    public void quorum_areaShortByOneVote_quorumNotSatisfied() {
        // 总面积 1000 ㎡, 总人 10；面积门槛 2/3 ≈ 666.67
        // 6 户参与（每户 100 ㎡）= 600 ㎡，面积参与率 60% < 2/3
        BigDecimal totalArea = new BigDecimal("1000.00");
        long totalOwnerCount = 10L;

        List<VoteItem> votes = new ArrayList<>();
        for (int opid = 5001; opid <= 5006; opid++) {
            Long uid = (long) (opid - 4900);
            votes.add(new VoteItem((long) opid, uid, 1L, new BigDecimal("100.00"), VoteChoice.SUPPORT));
        }

        VotingResult<VotingSubject> result = engine.settle(subject(), votes, denom(totalArea, totalOwnerCount));
        assertFalse(result.isQuorumSatisfied(), "面积参与率 60% 未达 2/3，会议未成立");
        assertFalse(result.isPassed(), "未成立会议不可能 passed");
    }

    @Test
    public void quorum_ownerShortButAreaPasses_quorumNotSatisfied() {
        // 构造一个面积达标但人头不达标的极端场景：
        // 总人 12，参会 7 人（仅 7/12 ≈ 58% < 2/3） —— 但每户 200 ㎡，面积 1400/2000 = 70% > 2/3
        BigDecimal totalArea = new BigDecimal("2000.00");
        long totalOwnerCount = 12L;

        List<VoteItem> votes = new ArrayList<>();
        for (int opid = 5001; opid <= 5007; opid++) {
            Long uid = (long) (opid - 4900);
            votes.add(new VoteItem((long) opid, uid, 1L, new BigDecimal("200.00"), VoteChoice.SUPPORT));
        }

        VotingResult<VotingSubject> result = engine.settle(subject(), votes, denom(totalArea, totalOwnerCount));
        assertFalse(result.isQuorumSatisfied(), "人数参与率 7/12<2/3，即使面积过线，会议仍未成立");
        assertFalse(result.isPassed());
    }

    @Test
    public void quorum_bothExactlyTwoThirds_quorumSatisfied() {
        // 面积 2/3 等号成立 + 人数 2/3 等号成立 → 满足（>= 而非 >）
        BigDecimal totalArea = new BigDecimal("900.00");
        long totalOwnerCount = 9L;

        List<VoteItem> votes = new ArrayList<>();
        for (int opid = 5001; opid <= 5006; opid++) {
            Long uid = (long) (opid - 4900);
            votes.add(new VoteItem((long) opid, uid, 1L, new BigDecimal("100.00"), VoteChoice.SUPPORT));
        }

        VotingResult<VotingSubject> result = engine.settle(subject(), votes, denom(totalArea, totalOwnerCount));
        assertTrue(result.isQuorumSatisfied(), "面积 600/900=2/3 等号成立 + 人数 6/9=2/3 等号成立，会议成立");
    }

    // ===== 双重去重（一户多房合并） =====

    @Test
    public void dedup_oneOwnerVotesMultipleProperties_areaSummedOwnerCountedOnce() {
        // 同一 uid=101 用三个不同 opid（一户多房）投票，面积 80+90+70=240 ㎡。
        // 期望参会面积=240, 参会人头=1。
        BigDecimal totalArea = new BigDecimal("300.00");
        long totalOwnerCount = 1L;

        List<VoteItem> votes = List.of(
                new VoteItem(7001L, 101L, 1L, new BigDecimal("80.00"), VoteChoice.SUPPORT),
                new VoteItem(7002L, 101L, 1L, new BigDecimal("90.00"), VoteChoice.SUPPORT),
                new VoteItem(7003L, 101L, 1L, new BigDecimal("70.00"), VoteChoice.SUPPORT)
        );

        VotingResult<VotingSubject> result = engine.settle(subject(), votes, denom(totalArea, totalOwnerCount));
        assertEquals(0, new BigDecimal("240.00").compareTo(result.getParticipatingArea()),
                "三套房产总面积应累加 = 240");
        assertEquals(1L, result.getParticipatingOwnerCount(),
                "同一自然人 uid 仅计 1 次人头");
    }

    @Test
    public void dedup_sameUidAndOpidVotedTwice_areaCountedOnce() {
        // 同一 uid+opid 重复出现两条记录（重复投票被持久化两遍的攻击/异常场景）
        // → uniqueUidAndOpid Set 去重，只计入一次面积
        BigDecimal totalArea = new BigDecimal("1000.00");
        long totalOwnerCount = 10L;

        List<VoteItem> votes = new ArrayList<>();
        // 7 户正常投票
        for (int opid = 5001; opid <= 5007; opid++) {
            Long uid = (long) (opid - 4900);
            votes.add(new VoteItem((long) opid, uid, 1L, new BigDecimal("100.00"), VoteChoice.SUPPORT));
        }
        // 第 5001 票被重复 push 进列表（同 uid+opid 应被去重）
        votes.add(new VoteItem(5001L, 101L, 1L, new BigDecimal("100.00"), VoteChoice.SUPPORT));

        VotingResult<VotingSubject> result = engine.settle(subject(), votes, denom(totalArea, totalOwnerCount));
        assertEquals(0, new BigDecimal("700.00").compareTo(result.getParticipatingArea()),
                "重复 uid+opid 不应被双计面积");
        assertEquals(7L, result.getParticipatingOwnerCount(),
                "重复 uid 不应被双计人头");
    }
}
