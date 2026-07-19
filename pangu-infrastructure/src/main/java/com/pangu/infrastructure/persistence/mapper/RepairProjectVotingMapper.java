// 关联业务：读写维修授权提案与统一表决包关联及业主任务。
package com.pangu.infrastructure.persistence.mapper;

import com.pangu.infrastructure.persistence.entity.RepairProjectVotingOwnerTaskRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectVotingRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

@Mapper
public interface RepairProjectVotingMapper {

    int insert(RepairProjectVotingRow row);

    RepairProjectVotingRow find(@Param("projectId") Long projectId,
                                @Param("planId") Long planId,
                                @Param("tenantId") Long tenantId);

    RepairProjectVotingRow findForUpdate(@Param("projectId") Long projectId,
                                         @Param("planId") Long planId,
                                         @Param("tenantId") Long tenantId);

    int markVoting(@Param("linkId") Long linkId,
                   @Param("tenantId") Long tenantId,
                   @Param("openedByUserId") Long openedByUserId,
                   @Param("openedAt") Instant openedAt,
                   @Param("expectedVersion") long expectedVersion);

    int settle(@Param("linkId") Long linkId,
               @Param("tenantId") Long tenantId,
               @Param("result") String result,
               @Param("settledByUserId") Long settledByUserId,
               @Param("settledAt") Instant settledAt,
               @Param("expectedVersion") long expectedVersion);

    List<RepairProjectVotingOwnerTaskRow> listOwnerTasks(@Param("tenantId") Long tenantId,
                                                         @Param("ownerUid") Long ownerUid);
}
