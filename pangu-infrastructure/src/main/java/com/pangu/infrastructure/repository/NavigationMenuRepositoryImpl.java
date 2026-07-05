package com.pangu.infrastructure.repository;

import com.pangu.domain.repository.NavigationMenuRepository;
import com.pangu.infrastructure.persistence.mapper.SysMenuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class NavigationMenuRepositoryImpl implements NavigationMenuRepository {

    private final SysMenuMapper sysMenuMapper;

    @Override
    public List<MenuItem> findGrantedMenusByUserId(Long userId) {
        return sysMenuMapper.selectGrantedMenusByUserId(userId).stream()
                .map(row -> new MenuItem(
                        row.getMenuId(),
                        row.getParentId(),
                        row.getRouteId(),
                        row.getMenuName(),
                        row.getIcon(),
                        row.getOrderNum()))
                .toList();
    }
}
