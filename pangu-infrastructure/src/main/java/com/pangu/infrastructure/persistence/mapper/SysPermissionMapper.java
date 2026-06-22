package com.pangu.infrastructure.persistence.mapper;

import com.pangu.infrastructure.persistence.entity.PermissionCatalogRow;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * {@code sys_permission} 读侧 mapper（M4-1 补）。
 *
 * <p>{@code sys_permission} 此前只在 Flyway 种子里出现，无 Java mapper。本 mapper 仅提供
 * 全量读查询，供管理台授权页勾选可授权限。不暴露写动作——权限项由迁移脚本预置。
 */
@Mapper
public interface SysPermissionMapper {

    /** 全量权限清单，按 permission_group, permission_key 排序。 */
    List<PermissionCatalogRow> selectAll();
}
