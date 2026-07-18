// 关联业务：为需由主任或副主任作出的治理确认提供当前届期职务事实。
package com.pangu.domain.repository;

import java.util.Optional;

public interface CommitteePositionRepository {

    Optional<String> findActivePosition(Long tenantId, Long userId);
}
