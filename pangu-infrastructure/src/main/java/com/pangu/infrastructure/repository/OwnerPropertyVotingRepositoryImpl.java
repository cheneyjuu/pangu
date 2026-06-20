package com.pangu.infrastructure.repository;

import com.pangu.domain.model.asset.OwnerPropertyVotingView;
import com.pangu.domain.repository.OwnerPropertyVotingRepository;
import com.pangu.infrastructure.persistence.entity.OwnerPropertyVotingViewRow;
import com.pangu.infrastructure.persistence.mapper.OwnerPropertyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@link OwnerPropertyVotingRepository} 默认实现：c_owner_property 直读。
 */
@Repository
@RequiredArgsConstructor
public class OwnerPropertyVotingRepositoryImpl implements OwnerPropertyVotingRepository {

    private final OwnerPropertyMapper mapper;

    @Override
    public Optional<OwnerPropertyVotingView> findByOpid(Long opid) {
        if (opid == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectVotingViewByOpid(opid)).map(this::toView);
    }

    @Override
    public List<Long> findBuildingIdsByUid(Long uid, Long tenantId) {
        if (uid == null || tenantId == null) {
            return List.of();
        }
        return mapper.selectBuildingIdsByUid(uid, tenantId);
    }

    private OwnerPropertyVotingView toView(OwnerPropertyVotingViewRow r) {
        return new OwnerPropertyVotingView(
                r.getOpid(),
                r.getUid(),
                r.getTenantId(),
                r.getBuildingId(),
                r.getBuildArea(),
                r.getVotingDelegate() == 1,
                r.getAccountStatus());
    }
}
