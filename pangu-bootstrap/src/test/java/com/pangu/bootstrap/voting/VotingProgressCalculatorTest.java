package com.pangu.bootstrap.voting;

import com.pangu.domain.model.voting.SubjectStatus;
import com.pangu.domain.model.voting.SubjectType;
import com.pangu.domain.model.voting.VoteChoice;
import com.pangu.domain.model.voting.VoteItem;
import com.pangu.domain.model.voting.VotingProgress;
import com.pangu.domain.model.voting.VotingProgressCalculator;
import com.pangu.domain.model.voting.VotingScope;
import com.pangu.domain.model.voting.VotingSubject;
import com.pangu.domain.repository.VotingDenominatorReader.DenominatorTotals;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link VotingProgressCalculator} 纯领域计算测试。
 *
 * <p>验证其去重 / 双 2/3 门槛口径与 {@link com.pangu.domain.model.voting.AbstractVotingEngine#settle}
 * 完全一致（不经 Spring，纯函数断言）：
 * <ul>
 *   <li>参与面积按 (uid, opid) 去重累加，参与人数按 uid 去重；</li>
 *   <li>赞成子集同法计算；</li>
 *   <li>双 2/3 门槛的边界（恰好 2/3 通过，差一点不通过）；</li>
 *   <li>分母为 0 时所有比例返回 0、quorum=false。</li>
 * </ul>
 */
public class VotingProgressCalculatorTest {

    private final VotingProgressCalculator calculator = new VotingProgressCalculator();

    private VotingSubject subject() {
        VotingSubject s = new VotingSubject();
        s.setSubjectId(1L);
        s.setStatus(SubjectStatus.VOTING);
        s.setScope(VotingScope.COMMUNITY);
        s.setSubjectType(SubjectType.GENERAL);
        return s;
    }

    private VoteItem vote(long uid, long opid, BigDecimal area, VoteChoice choice) {
        return VoteItem.builder().uid(uid).opid(opid).propertyArea(area).choice(choice).build();
    }

    @Test
    public void dedupByUidOpidForArea_andByUidForOwnerCount() {
        // uid=1 持两套房(opid 10/11)各 50；uid=1 又对 opid 10 重复投了一票(应被去重)
        // uid=2 持一套房 opid 20 面积 100
        List<VoteItem> votes = List.of(
                vote(1L, 10L, new BigDecimal("50.00"), VoteChoice.SUPPORT),
                vote(1L, 10L, new BigDecimal("50.00"), VoteChoice.SUPPORT),
                vote(1L, 11L, new BigDecimal("50.00"), VoteChoice.AGAINST),
                vote(2L, 20L, new BigDecimal("100.00"), VoteChoice.SUPPORT));

        DenominatorTotals totals = new DenominatorTotals(new BigDecimal("300.00"), 3L);
        VotingProgress p = calculator.compute(subject(), totals, votes);

        // 面积：50(opid10) + 50(opid11) + 100(opid20) = 200，重复的 opid10 不重复计
        assertEquals(0, new BigDecimal("200.00").compareTo(p.participatingArea()));
        // 人数：uid 1、2 去重 = 2
        assertEquals(2, p.participatingOwnerCount());
        // 赞成面积：opid10(50) + opid20(100) = 150；uid1 的 opid11 是反对不计
        assertEquals(0, new BigDecimal("150.00").compareTo(p.supportArea()));
        // 赞成人数：uid1(投了 SUPPORT) + uid2 = 2
        assertEquals(2L, p.supportOwnerCount());
    }

    @Test
    public void quorumBoundary_exactlyTwoThirdsPasses() {
        // 总 300 面积 / 3 人；参与恰好 200 面积 / 2 人 → 双 2/3 恰好达标
        List<VoteItem> votes = List.of(
                vote(1L, 10L, new BigDecimal("100.00"), VoteChoice.SUPPORT),
                vote(2L, 20L, new BigDecimal("100.00"), VoteChoice.AGAINST));
        DenominatorTotals totals = new DenominatorTotals(new BigDecimal("300.00"), 3L);

        VotingProgress p = calculator.compute(subject(), totals, votes);

        assertTrue(p.quorumSatisfied(), "面积 200/300 且人数 2/3 恰好达双 2/3 门槛");
        assertEquals(0, new BigDecimal("0.6667").compareTo(p.participatingAreaRatio()));
    }

    @Test
    public void quorumBoundary_justBelowFails() {
        // 面积达标(200/300)但人数仅 1/3 → 人数维度不足，整体不达标
        List<VoteItem> votes = List.of(
                vote(1L, 10L, new BigDecimal("200.00"), VoteChoice.SUPPORT));
        DenominatorTotals totals = new DenominatorTotals(new BigDecimal("300.00"), 3L);

        VotingProgress p = calculator.compute(subject(), totals, votes);

        assertFalse(p.quorumSatisfied(), "人数 1/3 未达 2/3，整体不达标");
        assertEquals(1, p.participatingOwnerCount());
    }

    @Test
    public void zeroDenominator_ratiosZeroAndQuorumFalse() {
        List<VoteItem> votes = List.of(
                vote(1L, 10L, new BigDecimal("100.00"), VoteChoice.SUPPORT));
        DenominatorTotals totals = new DenominatorTotals(BigDecimal.ZERO, 0L);

        VotingProgress p = calculator.compute(subject(), totals, votes);

        assertEquals(0, BigDecimal.ZERO.compareTo(p.participatingAreaRatio()));
        assertEquals(0, BigDecimal.ZERO.compareTo(p.participatingOwnerRatio()));
        assertFalse(p.quorumSatisfied());
        assertFalse(p.settled());
    }

    @Test
    public void emptyVotes_zeroParticipation() {
        DenominatorTotals totals = new DenominatorTotals(new BigDecimal("300.00"), 3L);
        VotingProgress p = calculator.compute(subject(), totals, List.of());

        assertEquals(0, BigDecimal.ZERO.compareTo(p.participatingArea()));
        assertEquals(0, p.participatingOwnerCount());
        assertEquals(0L, p.supportOwnerCount());
        assertFalse(p.quorumSatisfied());
    }
}
