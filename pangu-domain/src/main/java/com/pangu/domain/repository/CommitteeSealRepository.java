// 关联业务：持久化业主自治组织电子印章台账和不可篡改的用印记录。
package com.pangu.domain.repository;

import com.pangu.domain.model.committee.CommitteeElectronicSeal;
import com.pangu.domain.model.committee.CommitteeSealUsageRecord;

import java.util.List;
import java.util.Optional;

public interface CommitteeSealRepository {

    List<CommitteeElectronicSeal> listByTenant(Long tenantId);

    Optional<CommitteeElectronicSeal> findById(Long sealId, Long tenantId);

    Long insert(CommitteeElectronicSeal seal);

    int deactivate(Long sealId, Long tenantId, Long operatorUserId);

    Long insertUsage(CommitteeSealUsageRecord usage);

    List<CommitteeSealUsageRecord> listUsageByTenant(Long tenantId, int limit);
}
