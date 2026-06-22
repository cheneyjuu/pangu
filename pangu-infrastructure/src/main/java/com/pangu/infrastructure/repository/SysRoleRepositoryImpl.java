package com.pangu.infrastructure.repository;

import com.pangu.domain.common.Page;
import com.pangu.domain.gateway.dto.RoleQuery;
import com.pangu.domain.model.role.RoleListItem;
import com.pangu.domain.model.role.RolePermissionDetail;
import com.pangu.domain.model.role.SysRole;
import com.pangu.domain.repository.SysRoleRepository;
import com.pangu.infrastructure.persistence.entity.RoleListItemRow;
import com.pangu.infrastructure.persistence.entity.RolePermissionDetailRow;
import com.pangu.infrastructure.persistence.entity.SysRoleRow;
import com.pangu.infrastructure.persistence.mapper.SysRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@link SysRoleRepository} 默认实现。
 *
 * <p>{@link DuplicateKeyException}（{@code sys_role.role_key UNIQUE} 触发）
 * 转译为 {@link SysRoleRepository.DuplicateRoleKeyException}；
 * 删除时遭遇 trigger 7（is_system=1 的预置角色）抛出的
 * PostgreSQL {@code RAISE EXCEPTION}（SQLSTATE=P0001）经 Spring 翻译为
 * {@link DataAccessException} 子类（多数为 {@code UncategorizedSQLException}），
 * 这里统一以"任何 DataAccessException + cause msg 含 [trigger 7]"识别并转译为
 * {@link SysRoleRepository.SystemRoleProtectedException}。
 */
@Repository
@RequiredArgsConstructor
public class SysRoleRepositoryImpl implements SysRoleRepository {

    private final SysRoleMapper mapper;

    @Override
    public Optional<SysRole> findById(Long roleId) {
        return Optional.ofNullable(mapper.selectById(roleId)).map(this::toAggregate);
    }

    @Override
    public Optional<SysRole> findByRoleKey(String roleKey) {
        return Optional.ofNullable(mapper.selectByRoleKey(roleKey)).map(this::toAggregate);
    }

    @Override
    public SysRole insert(SysRole role) {
        SysRoleRow row = toRow(role);
        try {
            mapper.insert(row);
        } catch (DuplicateKeyException e) {
            throw new DuplicateRoleKeyException(
                    "sys_role.role_key UNIQUE violated: " + role.roleKey(), e);
        }
        return role.withId(row.getRoleId());
    }

    @Override
    public int delete(Long roleId) {
        try {
            return mapper.deleteById(roleId);
        } catch (DataAccessException e) {
            // trigger 7 走 RAISE EXCEPTION（SQLSTATE=P0001）→ UncategorizedSQLException；
            // 同族的 DataIntegrityViolationException 仅在 FK / CHECK 等级别命中。
            // 在此父类层兜底，再用 cause msg 精准识别 trigger 7 业务语义。
            if (causeMessageContains(e, "[trigger 7]")) {
                throw new SystemRoleProtectedException(
                        "is_system=1 的预置角色禁止删除：role_id=" + roleId, e);
            }
            throw e;
        }
    }

    @Override
    public Page<RoleListItem> pageRoles(RoleQuery query) {
        long total = mapper.countRoles(query);
        List<RoleListItem> items = total == 0
                ? List.of()
                : mapper.pageRolesList(query).stream().map(this::toListItem).toList();
        return new Page<>(items, total, query.page(), query.size());
    }

    @Override
    public List<RolePermissionDetail> listPermissionsByRole(Long roleId) {
        return mapper.selectPermissionsByRole(roleId).stream()
                .map(this::toPermissionDetail).toList();
    }

    private RoleListItem toListItem(RoleListItemRow row) {
        return new RoleListItem(
                row.getRoleId(),
                row.getRoleKey(),
                row.getRoleName(),
                row.getAllowedDeptCategory(),
                row.getFixedDataScope(),
                row.getDefaultDataScope(),
                row.getIsSystem() == null ? 0 : row.getIsSystem(),
                row.getStatus(),
                row.getPermissionCount() == null ? 0L : row.getPermissionCount(),
                row.getCreateTime());
    }

    private RolePermissionDetail toPermissionDetail(RolePermissionDetailRow row) {
        return new RolePermissionDetail(
                row.getPermissionKey(),
                row.getDescription(),
                row.getPermissionGroup(),
                row.getAllowedDeptCategories(),
                row.getIsLegalRedline(),
                row.getGrantedBy(),
                row.getGrantedAt());
    }

    private static boolean causeMessageContains(Throwable t, String token) {
        Throwable cur = t;
        while (cur != null) {
            String msg = cur.getMessage();
            if (msg != null && msg.contains(token)) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    private SysRoleRow toRow(SysRole r) {
        SysRoleRow row = new SysRoleRow();
        row.setRoleId(r.roleId());
        row.setRoleName(r.roleName());
        row.setRoleKey(r.roleKey());
        row.setAllowedDeptCategory(r.allowedDeptCategory());
        row.setFixedDataScope(r.fixedDataScope());
        row.setDefaultDataScope(r.defaultDataScope());
        row.setIsSystem(r.isSystem());
        return row;
    }

    private SysRole toAggregate(SysRoleRow row) {
        return new SysRole(
                row.getRoleId(),
                row.getRoleKey(),
                row.getRoleName(),
                row.getAllowedDeptCategory(),
                row.getFixedDataScope(),
                row.getDefaultDataScope(),
                row.getIsSystem() == null ? 0 : row.getIsSystem());
    }
}
