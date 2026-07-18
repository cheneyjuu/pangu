// 关联业务：查询业委会成员在当前小区届期内的有效职务，供主任/副主任专属动作校验。
package com.pangu.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CommitteePositionMapper {

    String findActivePosition(@Param("tenantId") Long tenantId, @Param("userId") Long userId);
}
