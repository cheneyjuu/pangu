package com.pangu.infrastructure.persistence.mapper;

import com.pangu.infrastructure.persistence.entity.TenantTermStateRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TenantTermStateMapper {

    TenantTermStateRow selectByTenantId(@Param("tenantId") Long tenantId);

    int upsertHandoverLock(@Param("tenantId") Long tenantId,
                           @Param("subjectId") Long subjectId);

    int releaseHandoverLock(@Param("tenantId") Long tenantId,
                            @Param("confirmedByUserId") Long confirmedByUserId);
}
