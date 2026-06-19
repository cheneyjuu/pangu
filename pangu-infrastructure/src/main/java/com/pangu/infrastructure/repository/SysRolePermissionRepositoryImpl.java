package com.pangu.infrastructure.repository;

import com.pangu.domain.repository.SysRolePermissionRepository;
import com.pangu.infrastructure.persistence.mapper.SysRolePermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/**
 * {@link SysRolePermissionRepository} 默认实现。
 *
 * <p>主键冲突翻译为 {@link DuplicateAssignmentException}；trigger 6 抛错（端归属
 * 不一致 / redline 无 fixed_data_scope）以及外键不存在等一致性故障翻译为
 * {@link AssignmentConsistencyException}，由应用层映射为业务错误码。
 *
 * <p>trigger 6 走 {@code RAISE EXCEPTION}（默认 SQLSTATE=P0001），Spring 多数会
 * 翻译为 {@code UncategorizedSQLException} 而非 DIVE，所以这里 catch 父类
 * {@link DataAccessException} 兜底，再用 cause msg 判定 trigger 来源。
 */
@Repository
@RequiredArgsConstructor
public class SysRolePermissionRepositoryImpl implements SysRolePermissionRepository {

    private final SysRolePermissionMapper mapper;

    @Override
    public void assign(Long roleId, String permissionKey, Long grantedBy) {
        try {
            mapper.insert(roleId, permissionKey, grantedBy);
        } catch (DuplicateKeyException e) {
            throw new DuplicateAssignmentException(
                    "role_id=" + roleId + " 已经持有 permission=" + permissionKey, e);
        } catch (DataAccessException e) {
            // trigger 6 / FK 不存在等
            Throwable root = e.getMostSpecificCause();
            String msg = root == null ? "" : root.getMessage();
            throw new AssignmentConsistencyException(
                    "授予 permission 失败：role_id=" + roleId
                            + " permission=" + permissionKey + " 原因：" + msg, e);
        }
    }

    @Override
    public int revoke(Long roleId, String permissionKey) {
        return mapper.delete(roleId, permissionKey);
    }

    @Override
    public long countByRole(Long roleId) {
        return mapper.countByRole(roleId);
    }
}
