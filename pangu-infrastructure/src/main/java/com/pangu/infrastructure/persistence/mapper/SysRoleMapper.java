package com.pangu.infrastructure.persistence.mapper;

import com.pangu.infrastructure.persistence.entity.SysRoleRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysRoleMapper {

    SysRoleRow selectById(@Param("roleId") Long roleId);

    SysRoleRow selectByRoleKey(@Param("roleKey") String roleKey);

    int insert(SysRoleRow row);

    int deleteById(@Param("roleId") Long roleId);
}
