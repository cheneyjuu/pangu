package com.pangu.application.handover;

import com.pangu.domain.repository.VotingSubjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 换届熔断器（HANDOVER_LOCK）。
 *
 * <p>业委会换届选举进行期间（旧业委会即将卸任、新业委会尚未产生）需要冻结敏感治理动作，
 * 避免旧班子在交接空窗内继续推进资金 / 公示等动作。本期仅服务于「财务公示发布」。
 *
 * <p>设计为「查询派生」：不建表、不写锁行、不做 engage/release 记账。「换届进行中」直接由
 * ELECTION 议题状态推导（{@code status ∈ {PUBLISHED, VOTING, CLOSED}}），选举结算 / 撤销后
 * 查询自然返回空，熔断自动解除，无需任何手工解锁。具体状态集合语义见
 * {@link VotingSubjectRepository#findActiveElectionSubjectId(Long)}。
 */
@Service
@RequiredArgsConstructor
public class HandoverCircuitBreaker {

    private final VotingSubjectRepository subjectRepository;

    /**
     * 该租户是否处于换届进行中。
     *
     * @return 任一在途换届选举的 subjectId（供调用方拼装熔断错误信息），无则空
     */
    public Optional<Long> activeElectionSubjectId(Long tenantId) {
        return subjectRepository.findActiveElectionSubjectId(tenantId);
    }
}
