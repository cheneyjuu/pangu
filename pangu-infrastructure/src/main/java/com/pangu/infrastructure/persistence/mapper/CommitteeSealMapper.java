// 关联业务：提供电子印章台账、停用操作和用印记录的 MyBatis 数据访问。
package com.pangu.infrastructure.persistence.mapper;

import com.pangu.infrastructure.persistence.entity.CommitteeElectronicSealRow;
import com.pangu.infrastructure.persistence.entity.CommitteeSealUsageRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CommitteeSealMapper {

    List<CommitteeElectronicSealRow> selectByTenant(@Param("tenantId") Long tenantId);

    CommitteeElectronicSealRow selectById(@Param("sealId") Long sealId, @Param("tenantId") Long tenantId);

    int insertSeal(CommitteeElectronicSealRow row);

    int deactivate(@Param("sealId") Long sealId,
                   @Param("tenantId") Long tenantId,
                   @Param("operatorUserId") Long operatorUserId);

    int insertUsage(CommitteeSealUsageRow row);

    List<CommitteeSealUsageRow> selectUsageByTenant(@Param("tenantId") Long tenantId,
                                                     @Param("limit") int limit);
}
