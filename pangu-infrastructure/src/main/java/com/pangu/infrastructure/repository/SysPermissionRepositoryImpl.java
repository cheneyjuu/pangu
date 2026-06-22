package com.pangu.infrastructure.repository;

import com.pangu.domain.model.role.PermissionCatalog;
import com.pangu.domain.repository.SysPermissionRepository;
import com.pangu.infrastructure.persistence.entity.PermissionCatalogRow;
import com.pangu.infrastructure.persistence.mapper.SysPermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * {@link SysPermissionRepository} 默认实现。
 *
 * <p>{@code sys_permission} 平台级表，全量读查询不挂 {@code @DataScope}——收口由 endpoint
 * {@code @PreAuthorize(admin:role:read)} 保证。
 */
@Repository
@RequiredArgsConstructor
public class SysPermissionRepositoryImpl implements SysPermissionRepository {

    private final SysPermissionMapper mapper;

    @Override
    public List<PermissionCatalog> listAll() {
        return mapper.selectAll().stream().map(this::toCatalog).toList();
    }

    private PermissionCatalog toCatalog(PermissionCatalogRow row) {
        return new PermissionCatalog(
                row.getPermissionKey(),
                row.getDescription(),
                row.getPermissionGroup(),
                row.getAllowedDeptCategories(),
                row.getIsLegalRedline());
    }
}
