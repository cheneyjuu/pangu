package com.pangu.infrastructure.persistence.mapper;

import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 管理端导航菜单读侧 mapper。
 *
 * <p>菜单只负责前端导航展示；按钮与接口鉴权仍以 {@code sys_permission}
 * 和 {@code @PreAuthorize} 为准。
 */
@Mapper
public interface SysMenuMapper {

    List<SysMenuRow> selectVisibleMenus();

    List<SysMenuRow> selectGrantedMenusByUserId(@Param("userId") Long userId);

    @Data
    class SysMenuRow {
        private Long menuId;
        private Long parentId;
        private String routeId;
        private String menuName;
        private String icon;
        private Integer orderNum;
        private String requiredPermission;
        private String requiredAnyPermissions;
        private String requiredRoleKeys;
    }
}
