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
public class ElectionVotingEngineTest {

    @Autowired
    private ElectionVotingEngine electionVotingEngine;

    @Test
    public void testElectionVotingWithPartyRatio() {
        // 1. 初始化 5 名候选人 (C1, C3为党员; C2, C4, C5为非党员)
        Candidate c1 = Candidate.builder().candidateId(1L).name("党员候选人C1").partyMember(true).build();
        Candidate c2 = Candidate.builder().candidateId(2L).name("非党员候选人C2").partyMember(false).build();
        Candidate c3 = Candidate.builder().candidateId(3L).name("党员候选人C3").partyMember(true).build();
        Candidate c4 = Candidate.builder().candidateId(4L).name("非党员候选人C4").partyMember(false).build();
        Candidate c5 = Candidate.builder().candidateId(5L).name("非党员候选人C5").partyMember(false).build();

        // 业委会席位 maxWinners = 3。党员目标最低席位应为 ceil(3/2.0) = 2 名
        ElectionSubject subject = ElectionSubject.builder()
                .subjectId(2001L)
                .tenantId(9001L)
                .title("求是小区第一届业主委员会选举")
                .candidates(List.of(c1, c2, c3, c4, c5))
                .maxWinners(3)
                .build();

        // 2. 模拟业主投票。共 10 户业主，每户 100 ㎡。总面积 1000 ㎡，总业主数 10。
        // 参会投票数：8 户 (80% 参与率，双参与门槛满足)
        BigDecimal totalArea = new BigDecimal("1000.00");
        long totalOwnerCount = 10L;

        List<VoteItem> votes = new ArrayList<>();
        // 模拟 8 位业主 (OPID: 5001-5008，每人面积 100) 投票数据
        // 参会过半数 (50%) 的基准为：> 4 票 (即至少 5 票赞成才通过双过半)
        for (int opid = 5001; opid <= 5008; opid++) {
            BigDecimal area = new BigDecimal("100.00");
            Long uid = Long.valueOf(opid - 4900); // 映射自然人 UID 为 101 至 108
            
            // C1 (党员): 6 票赞成 (5001-5006), 2 票反对/弃权 -> 超过过半数
            if (opid <= 5006) {
                votes.add(new VoteItem(Long.valueOf(opid), uid, 1L, area, VoteChoice.SUPPORT));
            }
            
            // C2 (非党员): 7 票赞成 (5001-5007), 1 票反对 -> 超过过半数，高分
            if (opid <= 5007) {
                votes.add(new VoteItem(Long.valueOf(opid), uid, 2L, area, VoteChoice.SUPPORT));
            }
            
            // C3 (党员): 5 票赞成 (5001-5005) -> 刚经过半数 (5/8 = 62.5%)
            if (opid <= 5005) {
                votes.add(new VoteItem(Long.valueOf(opid), uid, 3L, area, VoteChoice.SUPPORT));
            }
            
            // C4 (非党员): 6 票赞成 (5001-5006) -> 超过过半数
            if (opid <= 5006) {
                votes.add(new VoteItem(Long.valueOf(opid), uid, 4L, area, VoteChoice.SUPPORT));
            }
            
            // C5 (非党员): 3 票赞成 (5001-5003) -> 3/8 = 37.5%，低于过半数，应该被直接过滤
            if (opid <= 5003) {
                votes.add(new VoteItem(Long.valueOf(opid), uid, 5L, area, VoteChoice.SUPPORT));
            }
        }

        // 3. 执行计票引擎结算（使用 Phase 3 引入的 Denominator record；测试场景下用零哈希占位）
        Denominator denominator = new Denominator(
                totalArea,
                totalOwnerCount,
                "0".repeat(64),
                1L);
        ElectionVotingResult result = electionVotingEngine.settle(subject, votes, denominator);

        // 4. 验证结算结果
        assertTrue(result.isQuorumSatisfied(), "双参与比例应该满足限制");
        assertTrue(result.isPassed(), "应当选出最终委员");
        assertEquals(5, result.getCandidateResults().size(), "应有5名候选人的计票统计");

        // 验证候选人 C5 的双过半过滤状态
        CandidateElectionResult c5Result = result.getCandidateResults().stream()
                .filter(r -> r.getCandidate().getCandidateId() == 5L)
                .findFirst().orElseThrow();
        assertFalse(c5Result.isPassedHalf(), "C5 只得3票，未过半，应该判定未过半");

        // 验证当选名单 Winners 筛选 (计划选出3人，要求党员 >= 2人)
        // 合格的候选人为：C2(非, 7票), C1(党, 6票), C4(非, 6票), C3(党, 5票)
        // 按分数高低排序为：1. C2(非)  2. C1(党) & C4(非)[并列]  4. C3(党)
        // 选出3名，由于党员比例原则：
        // 必须优先保 2 名党员：C1(党) 与 C3(党) 必定当选！
        // 剩余的 1 个席位，由剩余最高分的非党员/党员中选出：最高分是非党员 C2(非)！
        // 最终当选名单应为：C1(党), C3(党), C2(非)。C4(非) 虽分数高于 C3(党)，但受限于党员比例控制被过滤掉！
        
        List<Candidate> winners = result.getWinners();
        assertEquals(3, winners.size(), "最终当选人数应为3人");

        long partyWinnerCount = winners.stream().filter(Candidate::isPartyMember).count();
        assertEquals(2, partyWinnerCount, "当选的党员人数应为2人（不低于50%）");

        // 验证具体的名单
        List<Long> winnerIds = winners.stream().map(Candidate::getCandidateId).toList();
        assertTrue(winnerIds.contains(1L), "C1 应该当选");
        assertTrue(winnerIds.contains(3L), "C3 应该当选");
        assertTrue(winnerIds.contains(2L), "C2 应该当选");
        assertFalse(winnerIds.contains(4L), "C4 应该在多阶党员控制中被挤掉");
        assertFalse(winnerIds.contains(5L), "C5 因未双过半不应该当选");

        System.out.println("====== 差额选举计票与多阶党员比例调配引擎测试通过！ ======");
        for (int i = 0; i < winners.size(); i++) {
            System.out.printf("当选委员第 [%d] 席: %s (党员: %b)%n", 
                    i + 1, winners.get(i).getName(), winners.get(i).isPartyMember());
        }
    }
}
