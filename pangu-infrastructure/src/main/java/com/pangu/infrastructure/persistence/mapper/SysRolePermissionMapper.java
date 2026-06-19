package com.pangu.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysRolePermissionMapper {

    int insert(@Param("roleId") Long roleId,
               @Param("permissionKey") String permissionKey,
               @Param("grantedBy") Long grantedBy);

    int delete(@Param("roleId") Long roleId,
               @Param("permissionKey") String permissionKey);

    long countByRole(@Param("roleId") Long roleId);
}
