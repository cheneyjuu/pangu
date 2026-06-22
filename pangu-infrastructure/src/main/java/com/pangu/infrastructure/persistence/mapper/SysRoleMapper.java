package com.pangu.infrastructure.persistence.mapper;

import com.pangu.domain.gateway.dto.RoleQuery;
import com.pangu.infrastructure.persistence.entity.RoleListItemRow;
import com.pangu.infrastructure.persistence.entity.RolePermissionDetailRow;
import com.pangu.infrastructure.persistence.entity.SysRoleRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysRoleMapper {

    SysRoleRow selectById(@Param("roleId") Long roleId);

    SysRoleRow selectByRoleKey(@Param("roleKey") String roleKey);

    int insert(SysRoleRow row);

    int deleteById(@Param("roleId") Long roleId);

    /**
     * 角色分页列表（带 permission_count 子查询）。不挂 @DataScope——sys_role 平台级表。
     */
    List<RoleListItemRow> pageRolesList(@Param("q") RoleQuery q);

    /** 角色分页总数（与 pageRolesList 同过滤条件）。 */
    long countRoles(@Param("q") RoleQuery q);

    /** 某角色已授权限明细（JOIN sys_permission），按 group/key 排序。 */
    List<RolePermissionDetailRow> selectPermissionsByRole(@Param("roleId") Long roleId);
}
