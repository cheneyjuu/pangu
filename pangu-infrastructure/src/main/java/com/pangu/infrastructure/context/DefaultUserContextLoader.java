package com.pangu.infrastructure.context;

import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextLoader;
import com.pangu.domain.model.user.AuthenticationLevel;
import com.pangu.domain.model.user.DataScopeType;
import com.pangu.domain.model.user.WorkIdentityBuildingScope;
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
        UserContextMapper.SysUserContextRow row = userContextMapper.loadSysUserContext(accountId, userId);
        if (row == null) {
            log.warn("SYS_USER context load miss: userId={}", userId);
            return null;
        }
        // G 端根组织的 tenantId 来自可切换会话令牌。每次装配都回查当前辖区授权，
        // 防止授权撤销后旧令牌在有效期内仍能穿透到已移出范围的小区。
        if (requiresGovernmentScopeValidation(row, tenantIdHint)
                && !userContextMapper.existsManagedCommunityByGovernmentDept(row.getDeptId(), tenantIdHint)) {
            log.warn("SYS_USER tenant context out of government scope: userId={}, deptId={}, tenantId={}",
                    userId, row.getDeptId(), tenantIdHint);
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
        Set<WorkIdentityBuildingScope> authorizedBuildingScopes = Set.of();
        if (scope == DataScopeType.OWNER_GROUP) {
            List<UserContextMapper.AuthorizedBuildingScopeRow> scopes =
                    userContextMapper.selectAuthorizedBuildingScopes(userId);
            if (scopes != null && !scopes.isEmpty()) {
                Set<WorkIdentityBuildingScope> scopeSet = new HashSet<>();
                Set<Long> buildingSet = new HashSet<>();
                for (UserContextMapper.AuthorizedBuildingScopeRow item : scopes) {
                    if (item.getTenantId() != null && item.getBuildingId() != null) {
                        scopeSet.add(new WorkIdentityBuildingScope(item.getTenantId(), item.getBuildingId()));
                        buildingSet.add(item.getBuildingId());
                    }
                }
                authorizedBuildingScopes = scopeSet;
                authorizedBuildingIds = buildingSet;
            }
        }

        Long effectiveTenantId = resolveSysUserTenant(row, tenantIdHint);
        // 管理端 authLevel 默认 L1（实名等级与认证等级语义不同，避免混用）
        AuthenticationLevel authLevel = AuthenticationLevel.L1;

        return new UserContext(
                accountId != null ? accountId : row.getAccountId(),
                UserContext.IdentityType.SYS_USER,
                userId,
                effectiveTenantId,
                row.getDeptId(),
                deptCategory,
                row.getDeptType(),
                scope,
                authLevel,
                row.getRoleKey(),
                permissions,
                authorizedBuildingIds,
                authorizedBuildingScopes
        );
    }

    private Long resolveSysUserTenant(UserContextMapper.SysUserContextRow row, Long tenantIdHint) {
        if (row.getDeptTenantId() != null) {
            return row.getDeptTenantId();
        }
        if (tenantIdHint != null) {
            return tenantIdHint;
        }
        if (row.getDeptCategory() == null || !"G".equals(row.getDeptCategory()) || row.getDeptId() == null) {
            return null;
        }
        return userContextMapper.selectDefaultTenantByGovernmentDept(row.getDeptId());
    }

    private boolean requiresGovernmentScopeValidation(UserContextMapper.SysUserContextRow row, Long tenantIdHint) {
        return tenantIdHint != null
                && row.getDeptTenantId() == null
                && row.getDeptId() != null
                && "G".equals(row.getDeptCategory())
                && Integer.valueOf(1).equals(row.getDeptType());
    }

    private UserContext loadCUser(Long accountId, Long uid, Long tenantIdHint) {
        UserContextMapper.CUserContextRow row = userContextMapper.loadCUserContext(accountId, uid);
        if (row == null) {
            log.warn("C_USER context load miss: uid={}", uid);
            return null;
        }
        AuthenticationLevel authLevel = row.getAuthLevel() != null
                ? AuthenticationLevel.of(row.getAuthLevel())
                : AuthenticationLevel.L1;
        // 业主默认 dataScopeType 为 null —— C 端业务接口走 ABAC + opid 校验，不依赖行级 dataScope
        // 修复：JWT 未带 tenantIdHint 时（如首次登录），从业主名下任一房产反查作为默认值。
        // 否则后端 OwnerVotingController 等会因 tenantId=null 拒绝服务（40332）。
        Long effectiveTenantId = tenantIdHint != null
                ? tenantIdHint
                : userContextMapper.selectDefaultTenantByUid(uid);
        return new UserContext(
                accountId != null ? accountId : row.getAccountId(),
                UserContext.IdentityType.C_USER,
                uid,
                effectiveTenantId,
                null,
                null,
                null,
                null,
                authLevel,
                null,
                Set.of(),
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
