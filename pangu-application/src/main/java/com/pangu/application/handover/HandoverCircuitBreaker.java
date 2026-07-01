package com.pangu.application.handover;

import com.pangu.domain.model.handover.TenantTermState;
import com.pangu.domain.repository.VotingSubjectRepository;
import com.pangu.domain.repository.TenantTermStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 换届熔断器（HANDOVER_LOCK）。
 *
 * <p>业委会换届选举进行期间（旧业委会即将卸任、新业委会尚未产生）需要冻结敏感治理动作，
 * 避免旧班子在交接空窗内继续推进资金 / 公示等动作。本期仅服务于「财务公示发布」。
 *
 * <p>梯度 C 起增加持久任期锁：ELECTION 正式结算后进入 {@code HANDOVER_LOCK}，街道办换届备案通过后
 * 手工恢复 {@code NORMAL}。为兼容既有在途选举判断，仍保留 {@link VotingSubjectRepository} 查询派生兜底。
 */
@Service
@RequiredArgsConstructor
public class HandoverCircuitBreaker {

    private final TenantTermStateRepository termStateRepository;
    private final VotingSubjectRepository subjectRepository;

    /**
     * 该租户是否处于换届进行中。
     *
     * @return 任一在途换届选举的 subjectId（供调用方拼装熔断错误信息），无则空
     */
    public Optional<Long> activeElectionSubjectId(Long tenantId) {
        Optional<Long> lockedSubjectId = termStateRepository.findByTenantId(tenantId)
                .filter(TenantTermState::locked)
                .map(TenantTermState::lockedBySubjectId);
        if (lockedSubjectId.isPresent()) {
            return lockedSubjectId;
        }
        return subjectRepository.findActiveElectionSubjectId(tenantId);
    }
}
