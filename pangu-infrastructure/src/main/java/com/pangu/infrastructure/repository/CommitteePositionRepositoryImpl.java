// 关联业务：将业委会成员届期职务查询提供给需要主任或副主任确认的治理服务。
package com.pangu.infrastructure.repository;

import com.pangu.domain.repository.CommitteePositionRepository;
import com.pangu.infrastructure.persistence.mapper.CommitteePositionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CommitteePositionRepositoryImpl implements CommitteePositionRepository {

    private final CommitteePositionMapper mapper;

    @Override
    public Optional<String> findActivePosition(Long tenantId, Long userId) {
        return Optional.ofNullable(mapper.findActivePosition(tenantId, userId));
    }
}
