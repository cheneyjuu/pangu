package com.pangu.infrastructure.context;

import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextLoader;
import com.pangu.domain.model.user.AuthenticationLevel;
import com.pangu.domain.model.user.DataScopeType;
import com.pangu.infrastructure.persistence.mapper.UserContextMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * {@link UserContextLoader} 默认实现：基于 {@link UserContextMapper} 的多表 JOIN 装配。
 *
 * <p>缓存策略：M1 阶段先走直查；M2 引入 Redis 5min TTL（key=
 * {@code rbac:ctx:{accountId}:{identityType}:{activeIdentityId}}）。
 */
@Component
@Slf4j
public class DefaultUserContextLoader implements UserContextLoader {

    private final UserContextMapper userContextMapper;

    public DefaultUserContextLoader(UserContextMapper userContextMapper) {
        this.userContextMapper = userContextMapper;
    }

    @Override
    public UserContext load(Long accountId,
                            UserContext.IdentityType identityType,
                            Long activeIdentityId,
                            Long tenantIdHint) {
        if (accountId == null || identityType == null || activeIdentityId == null) {
            return null;
        }
        return switch (identityType) {
            case SYS_USER -> loadSysUser(accountId, activeIdentityId, tenantIdHint);
            case C_USER -> loadCUser(accountId, activeIdentityId, tenantIdHint);
        };
    }

    private UserContext loadSysUser(Long accountId, Long userId, Long tenantIdHint) {
        UserContextMapper.SysUserContextRow row = userContextMapper.loadSysUserContext(userId);
        if (row == null) {
            log.warn("SYS_USER context load miss: userId={}", userId);
            return null;
        }
        DataScopeType scope = DataScopeType.of(row.getEffectiveDataScope());
        UserContext.DeptCategory deptCategory = parseDeptCategory(row.getDeptCategory());

        Set<String> permissions = Set.of();
        if (row.getRoleId() != null) {
            List<String> perms = userContextMapper.selectPermissionsByRoleId(row.getRoleId());
            if (perms != null && !perms.isEmpty()) {
                permissions = new HashSet<>(perms);
            }
        }

        Set<Long> authorizedBuildingIds = Set.of();
        if (scope == DataScopeType.OWNER_GROUP) {
            List<Long> buildings = userContextMapper.selectAuthorizedBuildingIds(userId);
            if (buildings != null && !buildings.isEmpty()) {
                authorizedBuildingIds = new HashSet<>(buildings);
            }
        }

        // 街道办用户跨租户俯瞰：dept.tenant_id = NULL（V1 设计 §4.2）
        Long effectiveTenantId = row.getDeptTenantId() != null ? row.getDeptTenantId() : tenantIdHint;
        // 管理端 authLevel 默认 L1（实名等级与认证等级语义不同，避免混用）
        AuthenticationLevel authLevel = AuthenticationLevel.L1;

        return new UserContext(
                accountId != null ? accountId : row.getAccountId(),
                UserContext.IdentityType.SYS_USER,
                userId,
                effectiveTenantId,
                row.getDeptId(),
                deptCategory,
                scope,
                authLevel,
                row.getRoleKey(),
                permissions,
                authorizedBuildingIds
        );
    }

    private UserContext loadCUser(Long accountId, Long uid, Long tenantIdHint) {
        UserContextMapper.CUserContextRow row = userContextMapper.loadCUserContext(uid);
        if (row == null) {
            log.warn("C_USER context load miss: uid={}", uid);
            return null;
        }
        AuthenticationLevel authLevel = row.getAuthLevel() != null
                ? AuthenticationLevel.of(row.getAuthLevel())
                : AuthenticationLevel.L1;
        // 业主默认 dataScopeType 为 null —— C 端业务接口走 ABAC + opid 校验，不依赖行级 dataScope
        return new UserContext(
                accountId != null ? accountId : row.getAccountId(),
                UserContext.IdentityType.C_USER,
                uid,
                tenantIdHint,
                null,
                null,
                null,
                authLevel,
                null,
                Set.of(),
                Set.of()
        );
    }

    private UserContext.DeptCategory parseDeptCategory(String s) {
        if (s == null) return null;
        return switch (s) {
            case "G" -> UserContext.DeptCategory.G;
            case "B" -> UserContext.DeptCategory.B;
            case "S" -> UserContext.DeptCategory.S;
            default -> null;
        };
    }
}
