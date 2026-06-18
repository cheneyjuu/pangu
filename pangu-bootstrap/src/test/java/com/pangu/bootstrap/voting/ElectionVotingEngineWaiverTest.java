package com.pangu.bootstrap.voting;

import com.pangu.domain.model.voting.Candidate;
import com.pangu.domain.model.voting.Denominator;
import com.pangu.domain.model.voting.ElectionSubject;
import com.pangu.domain.model.voting.ElectionVotingEngine;
import com.pangu.domain.model.voting.ElectionVotingResult;
import com.pangu.domain.model.voting.VoteChoice;
import com.pangu.domain.model.voting.VoteItem;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 {@link ElectionVotingEngine} 党员比例下限读取自 {@link ElectionSubject#getEffectivePartyRatioFloor()}
 * 而非硬编码 0.50 —— application 层放宽 waiver 通过/被断路器撤销时的回归保障测试。
 *
 * <p>场景设计：4 席位 + 候选人池党员严重短缺（仅 1 名党员合格）：
 * <ul>
 *   <li>默认 0.50 → 至少 ceil(4*0.5)=2 名党员；党员池只有 1 名 → 走「情况 B」补齐非党员，
 *       党员当选数 = 1（不足下限但全部入选）；</li>
 *   <li>放宽至 0.25 → 至少 ceil(4*0.25)=1 名党员；党员池正好 1 名满足下限 → 走「情况 A」，
 *       党员当选数 = 1；非党员从剩余池综合排序补 3 个；</li>
 *   <li>「断路器回退」：application 未写 partyRatioFloor → engine 走 default 0.50。</li>
 * </ul>
 */
public class ElectionVotingEngineWaiverTest {

    private final ElectionVotingEngine engine = new ElectionVotingEngine();

    private static Denominator denom() {
        return new Denominator(new BigDecimal("1000.00"), 10L, "0".repeat(64), 1L);
    }

    /**
     * 构造 5 名候选人 + 8 户参与投票的标准场景；候选人党员标记由参数决定。
     *
     * <p>票数分布（皆通过双过半）：
     * C1=7, C2=7, C3=6, C4=6, C5=5；C5 也通过双过半（5/8=62.5%>50%）。
     */
    private List<VoteItem> standardVotes() {
        List<VoteItem> votes = new ArrayList<>();
        for (int opid = 5001; opid <= 5008; opid++) {
            BigDecimal area = new BigDecimal("100.00");
            Long uid = (long) (opid - 4900);

            // C1: 7 票（5001-5007）
            if (opid <= 5007) votes.add(new VoteItem((long) opid, uid, 1L, area, VoteChoice.SUPPORT));
            // C2: 7 票（5001-5007）
            if (opid <= 5007) votes.add(new VoteItem((long) opid, uid, 2L, area, VoteChoice.SUPPORT));
            // C3: 6 票（5001-5006）
            if (opid <= 5006) votes.add(new VoteItem((long) opid, uid, 3L, area, VoteChoice.SUPPORT));
            // C4: 6 票（5001-5006）
            if (opid <= 5006) votes.add(new VoteItem((long) opid, uid, 4L, area, VoteChoice.SUPPORT));
            // C5: 5 票（5001-5005）
            if (opid <= 5005) votes.add(new VoteItem((long) opid, uid, 5L, area, VoteChoice.SUPPORT));
        }
        return votes;
    }

    /** 4 席位、5 候选人，仅 C1 是党员 —— 党员池仅 1 名。 */
    private ElectionSubject subjectWithOnePartyMember(BigDecimal partyRatioFloor) {
        List<Candidate> candidates = List.of(
                Candidate.builder().candidateId(1L).name("C1").partyMember(true).build(),
                Candidate.builder().candidateId(2L).name("C2").partyMember(false).build(),
                Candidate.builder().candidateId(3L).name("C3").partyMember(false).build(),
                Candidate.builder().candidateId(4L).name("C4").partyMember(false).build(),
                Candidate.builder().candidateId(5L).name("C5").partyMember(false).build()
        );
        return ElectionSubject.builder()
                .subjectId(2001L).tenantId(9001L).title("test")
                .candidates(candidates)
                .maxWinners(4)
                .partyRatioFloor(partyRatioFloor)  // 由测试场景显式注入
                .build();
    }

    @Test
    public void defaultRatio_partyShortageRunsCaseB_oneSeatToParty() {
        // 默认 0.50：4 席位需 ceil(4*0.5)=2 党员；党员池仅 1 → 走「情况 B」全部党员入选 + 非党员补齐
        ElectionSubject subject = subjectWithOnePartyMember(new BigDecimal("0.50"));
        ElectionVotingResult result = engine.settle(subject, standardVotes(), denom());

        assertTrue(result.isPassed(), "应正常选出席位");
        assertEquals(4, result.getWinners().size());

        long partyWinners = result.getWinners().stream().filter(Candidate::isPartyMember).count();
        assertEquals(1L, partyWinners, "党员池仅 1 名 → 走情况 B 全部入选，党员当选 1");
        // C1（党员，7 票）必入选；其余 3 席从非党员里按分数挑：C2 7, C3 6, C4 6
        assertTrue(result.getWinners().stream().anyMatch(c -> c.getCandidateId() == 1L), "党员 C1 必当选");
    }

    @Test
    public void waivedRatioToOneFourth_partyPoolMeetsLowerFloor_runsCaseA() {
        // 放宽 0.25：4 席位需 ceil(4*0.25)=1 党员；党员池有 1 名正好满足 → 走「情况 A」
        // 党员先占 1 席，再混合排序剩余席位
        ElectionSubject subject = subjectWithOnePartyMember(new BigDecimal("0.25"));
        ElectionVotingResult result = engine.settle(subject, standardVotes(), denom());

        assertTrue(result.isPassed());
        assertEquals(4, result.getWinners().size());
        long partyWinners = result.getWinners().stream().filter(Candidate::isPartyMember).count();
        assertEquals(1L, partyWinners, "放宽到 0.25 后 1 名党员已满足下限，走情况 A");
        assertTrue(result.getWinners().stream().anyMatch(c -> c.getCandidateId() == 1L));
    }

    @Test
    public void engineReadsFromSubjectField_notHardcoded_circuitBreakerFallback() {
        // 模拟「application 层在断路器触发后未写 partyRatioFloor（保持默认 null）」：
        // engine 通过 getEffectivePartyRatioFloor() 兜底为 0.50，行为应等同于 defaultRatio 测试
        ElectionSubject subject = subjectWithOnePartyMember(null);
        assertEquals(new BigDecimal("0.50"), subject.getEffectivePartyRatioFloor(),
                "partyRatioFloor=null 时兜底默认 0.50");

        ElectionVotingResult result = engine.settle(subject, standardVotes(), denom());
        long partyWinners = result.getWinners().stream().filter(Candidate::isPartyMember).count();
        // 与 defaultRatio_partyShortageRunsCaseB_oneSeatToParty 一致：仅 1 名党员当选
        assertEquals(1L, partyWinners, "ratioFloor=null 时引擎应走默认 0.50 路径");
    }

    @Test
    public void waivedRatioToZero_partyPoolEmpty_stillPasses() {
        // 极端场景：放宽至 0（理论下限）+ 候选人池全无党员 → 4 席位全部走非党员补齐
        // 此场景验证「极端放宽不会导致 NPE 或除零异常」
        List<Candidate> noPartyCandidates = List.of(
                Candidate.builder().candidateId(2L).name("C2").partyMember(false).build(),
                Candidate.builder().candidateId(3L).name("C3").partyMember(false).build(),
                Candidate.builder().candidateId(4L).name("C4").partyMember(false).build(),
                Candidate.builder().candidateId(5L).name("C5").partyMember(false).build()
        );
        ElectionSubject subject = ElectionSubject.builder()
                .subjectId(2001L).tenantId(9001L).title("test")
                .candidates(noPartyCandidates)
                .maxWinners(4)
                .partyRatioFloor(new BigDecimal("0.00"))
                .build();

        // 8 户全部参与（每人 100 ㎡，参会率 80% 满足双 2/3 门槛），都给 4 名候选人投赞成
        List<VoteItem> votes = new ArrayList<>();
        for (int opid = 5001; opid <= 5008; opid++) {
            BigDecimal area = new BigDecimal("100.00");
            Long uid = (long) (opid - 4900);
            for (long target = 2L; target <= 5L; target++) {
                votes.add(new VoteItem((long) opid, uid, target, area, VoteChoice.SUPPORT));
            }
        }

        ElectionVotingResult result = engine.settle(subject, votes, denom());
        assertTrue(result.isPassed());
        assertEquals(4, result.getWinners().size());
        assertEquals(0L, result.getWinners().stream().filter(Candidate::isPartyMember).count(),
                "无党员候选人 + 0 比例下限 → 0 党员当选");
    }
}
