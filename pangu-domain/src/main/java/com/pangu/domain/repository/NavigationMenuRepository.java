package com.pangu.domain.repository;

import java.util.List;

public interface NavigationMenuRepository {

    List<MenuItem> findGrantedMenusByUserId(Long userId);

    record MenuItem(
            Long menuId,
            Long parentId,
            String routeId,
            String menuName,
            String icon,
            Integer orderNum) {
    }
}
