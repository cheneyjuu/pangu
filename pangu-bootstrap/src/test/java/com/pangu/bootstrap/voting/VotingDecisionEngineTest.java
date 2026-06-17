package com.pangu.bootstrap.voting;

import com.pangu.domain.model.voting.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class VotingDecisionEngineTest {

    @Autowired
    private GeneralDecisionEngine generalDecisionEngine;

    @Autowired
    private MajorDecisionEngine majorDecisionEngine;

    /** 测试用 Denominator：占位 snapshot 哈希 + ID，不参与引擎逻辑断言。 */
    private static Denominator denom(BigDecimal totalArea, long totalOwnerCount) {
        return new Denominator(totalArea, totalOwnerCount, "0".repeat(64), 1L);
    }

    /**
     * 测试普通决议引擎的计票判定逻辑 (双参与 && 参会双过半数通过)
     */
    @Test
    public void testGeneralDecisionEngine() {
        VotingSubject subject = VotingSubject.builder()
                .subjectId(3001L)
                .tenantId(9001L)
                .title("普通小区决议表决")
                .build();

        // 模拟小区总业主数 10，总面积 1000 ㎡
        BigDecimal totalArea = new BigDecimal("1000.00");
        long totalOwnerCount = 10L;

        // 1. 模拟 8 户参与投票 (80% 参与率，满足双 2/3 开会门槛)
        // 赞成 5 户 (5 * 100 = 500 ㎡，正好是 500/800 = 62.5% > 50%，通过双过半)
        List<VoteItem> votes = new ArrayList<>();
        for (int opid = 5001; opid <= 5008; opid++) {
            BigDecimal area = new BigDecimal("100.00");
            Long uid = Long.valueOf(opid - 4900);
            VoteChoice choice = (opid <= 5005) ? VoteChoice.SUPPORT : VoteChoice.AGAINST;
            votes.add(new VoteItem(Long.valueOf(opid), uid, 3001L, area, choice));
        }

        VotingResult<VotingSubject> result = generalDecisionEngine.settle(subject, votes, denom(totalArea, totalOwnerCount));
        assertTrue(result.isQuorumSatisfied(), "双参与开会率应该满足");
        assertTrue(result.isPassed(), "5票赞成/8票参与，应该判定表决通过");

        // 2. 模拟 4 户赞成 (4 * 100 = 400 ㎡，正好是 400/800 = 50%，未过半数，应当不通过)
        List<VoteItem> votesHalf = new ArrayList<>();
        for (int opid = 5001; opid <= 5008; opid++) {
            BigDecimal area = new BigDecimal("100.00");
            Long uid = Long.valueOf(opid - 4900);
            VoteChoice choice = (opid <= 5004) ? VoteChoice.SUPPORT : VoteChoice.AGAINST;
            votesHalf.add(new VoteItem(Long.valueOf(opid), uid, 3001L, area, choice));
        }

        VotingResult<VotingSubject> resultHalf = generalDecisionEngine.settle(subject, votesHalf, denom(totalArea, totalOwnerCount));
        assertTrue(resultHalf.isQuorumSatisfied());
        assertFalse(resultHalf.isPassed(), "4票赞成/8票参与，赞成刚好等于50%未过半数，应当判定通过失败");
    }

    /**
     * 测试重大决议引擎的计票判定逻辑 (双参与 && 参会双 3/4 极其以上通过)
     */
    @Test
    public void testMajorDecisionEngine() {
        VotingSubject subject = VotingSubject.builder()
                .subjectId(4001L)
                .tenantId(9001L)
                .title("重大决议表决（筹集维修资金）")
                .build();

        BigDecimal totalArea = new BigDecimal("1000.00");
        long totalOwnerCount = 10L;

        // 1. 模拟 8 户参与投票 (满足双 2/3 开会门槛)
        // 赞成 6 户 (6 * 100 = 600 ㎡，占比 600/800 = 75% = 3/4，刚好压线通过双 3/4)
        List<VoteItem> votesExact = new ArrayList<>();
        for (int opid = 5001; opid <= 5008; opid++) {
            BigDecimal area = new BigDecimal("100.00");
            Long uid = Long.valueOf(opid - 4900);
            VoteChoice choice = (opid <= 5006) ? VoteChoice.SUPPORT : VoteChoice.AGAINST;
            votesExact.add(new VoteItem(Long.valueOf(opid), uid, 4001L, area, choice));
        }

        VotingResult<VotingSubject> resultExact = majorDecisionEngine.settle(subject, votesExact, denom(totalArea, totalOwnerCount));
        assertTrue(resultExact.isQuorumSatisfied());
        assertTrue(resultExact.isPassed(), "6票赞成/8票参与，刚好是75%比例，满足双3/4压线通过门槛");

        // 2. 赞成 5 户 (5 * 100 = 500 ㎡，占比 500/800 = 62.5% < 75%，应当判定未通过)
        List<VoteItem> votesFail = new ArrayList<>();
        for (int opid = 5001; opid <= 5008; opid++) {
            BigDecimal area = new BigDecimal("100.00");
            Long uid = Long.valueOf(opid - 4900);
            VoteChoice choice = (opid <= 5005) ? VoteChoice.SUPPORT : VoteChoice.AGAINST;
            votesFail.add(new VoteItem(Long.valueOf(opid), uid, 4001L, area, choice));
        }

        VotingResult<VotingSubject> resultFail = majorDecisionEngine.settle(subject, votesFail, denom(totalArea, totalOwnerCount));
        assertTrue(resultFail.isQuorumSatisfied());
        assertFalse(resultFail.isPassed(), "5票赞成/8票参与，未达到3/4比例，应当判定通过失败");
    }
}
