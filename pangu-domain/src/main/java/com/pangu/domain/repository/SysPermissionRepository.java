package com.pangu.domain.repository;

import com.pangu.domain.model.role.PermissionCatalog;

import java.util.List;

/**
 * 平台权限仓储端口（读侧，{@code sys_permission}）。
 *
 * <p>实现位置：{@code pangu-infrastructure/.../repository/SysPermissionRepositoryImpl}。
 *
 * <p>{@code sys_permission} 是平台级配置表（无 tenant 维度），目前只在 Flyway 种子里出现，
 * 本端口为其补一个全量读查询，供管理台授权页勾选可授权限。不暴露写动作——权限项由
 * 迁移脚本预置，不允许在线增删。
 */
public interface SysPermissionRepository {

    /**
     * 全量可授权限清单，按 {@code permission_group, permission_key} 排序。
     *
     * @return 平台全部权限元信息
     */
    List<PermissionCatalog> listAll();
}
