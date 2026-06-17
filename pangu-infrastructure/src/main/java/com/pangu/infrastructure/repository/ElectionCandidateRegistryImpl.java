package com.pangu.infrastructure.repository;

import com.pangu.domain.model.voting.CandidatePoolSnapshot;
import com.pangu.domain.repository.ElectionCandidateRegistry;
import com.pangu.infrastructure.persistence.entity.CandidatePoolCount;
import com.pangu.infrastructure.persistence.mapper.PartyRatioPolicyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * {@link ElectionCandidateRegistry} 默认实现：复用 {@link PartyRatioPolicyMapper#countCurrentCandidatePool}，
 * 仅做 row → 领域 record 翻译。
 *
 * <p>Application 与 Infra-Resolver（{@code DefaultPartyRatioPolicyResolver}）通过本 Mapper 取数：
 * 申请瞬间快照（写入 waiver 行）与断路器对账查询用同一份 SQL，确保口径一致。
 */
@Repository
@RequiredArgsConstructor
public class ElectionCandidateRegistryImpl implements ElectionCandidateRegistry {

    private final PartyRatioPolicyMapper mapper;

    @Override
    public CandidatePoolSnapshot countActivePool(Long subjectId) {
        CandidatePoolCount count = mapper.countCurrentCandidatePool(subjectId);
        if (count == null) {
            return new CandidatePoolSnapshot(0L, 0L);
        }
        return new CandidatePoolSnapshot(count.getPartyCount(), count.getEligibleCount());
    }
}
