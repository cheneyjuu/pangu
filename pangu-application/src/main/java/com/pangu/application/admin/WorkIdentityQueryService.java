package com.pangu.application.admin;

import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.role.SysRole;
import com.pangu.domain.model.user.WorkIdentityBuildingScope;
import com.pangu.domain.model.user.WorkIdentityAccount;
import com.pangu.domain.model.user.WorkIdentityDeptOption;
import com.pangu.domain.model.user.WorkIdentityShadow;
import com.pangu.domain.repository.SysRoleRepository;
import com.pangu.domain.repository.WorkIdentityRepository;
import com.pangu.domain.security.NameDecryptor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作身份授权读侧。
 */
@Service
@RequiredArgsConstructor
public class WorkIdentityQueryService {

    static final int SEARCH_LIMIT = 50;
    private static final int NAME_SEARCH_POOL_LIMIT = 500;
    private static final int MIN_PHONE_FRAGMENT_LENGTH = 4;
    private static final int MIN_NAME_KEYWORD_LENGTH = 2;

    private final WorkIdentityRepository repository;
    private final SysRoleRepository roleRepository;
    private final UserContextHolder userContextHolder;
    private final NameDecryptor nameDecryptor;

    @Transactional(readOnly = true)
    public List<WorkIdentityAccount> searchAccounts(String keyword) {
        return searchAccounts(keyword, null);
    }

    @Transactional(readOnly = true)
    public List<WorkIdentityAccount> listAccounts(String roleKey) {
        Long tenantId = requireCurrentTenantId();
        String normalizedRoleKey = normalizeOptionalRoleKey(roleKey);
        return listAccountNameSearchPool(normalizedRoleKey, tenantId, SEARCH_LIMIT).stream()
                .limit(SEARCH_LIMIT)
                .map(row -> toAccount(row, tenantId))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WorkIdentityAccount> searchAccounts(String keyword, String roleKey) {
        Long tenantId = requireCurrentTenantId();
        if (keyword == null) {
            return List.of();
        }
        String k = keyword.trim();
        if (k.isEmpty()) {
            return List.of();
        }
        String normalizedRoleKey = normalizeOptionalRoleKey(roleKey);
        Map<Long, WorkIdentityRepository.AccountCandidate> candidates = new LinkedHashMap<>();
        if (isAllDigits(k)) {
            if (k.length() < MIN_PHONE_FRAGMENT_LENGTH) {
                return List.of();
            }
            searchCandidates(k, normalizedRoleKey, tenantId, SEARCH_LIMIT)
                    .forEach(row -> candidates.putIfAbsent(row.accountId(), row));
        } else {
            if (k.length() < MIN_NAME_KEYWORD_LENGTH) {
                return List.of();
            }
            searchCandidates(k, normalizedRoleKey, tenantId, SEARCH_LIMIT)
                    .forEach(row -> candidates.putIfAbsent(row.accountId(), row));
            listAccountNameSearchPool(normalizedRoleKey, tenantId, NAME_SEARCH_POOL_LIMIT).stream()
                    .filter(row -> decrypt(row.realNameCipher()).contains(k))
                    .limit(SEARCH_LIMIT)
                    .forEach(row -> candidates.putIfAbsent(row.accountId(), row));
        }
        return candidates.values().stream()
                .limit(SEARCH_LIMIT)
                .map(row -> toAccount(row, tenantId))
                .toList();
    }

    private String normalizeOptionalRoleKey(String roleKey) {
        if (roleKey == null || roleKey.isBlank()) {
            return null;
        }
        String normalized = roleKey.trim();
        roleByKey(normalized);
        return normalized;
    }

    private List<WorkIdentityRepository.AccountCandidate> searchCandidates(
            String keyword,
            String roleKey,
            Long tenantId,
            int limit) {
        if (roleKey == null) {
            return repository.searchAccountCandidatesInTenant(keyword, tenantId, limit);
        }
        return repository.searchAccountCandidatesByRoleInTenant(keyword, roleKey, tenantId, limit);
    }

    private List<WorkIdentityRepository.AccountCandidate> listAccountNameSearchPool(
            String roleKey, Long tenantId, int limit) {
        if (roleKey == null) {
            return repository.listAccountNameSearchPoolInTenant(tenantId, limit);
        }
        return repository.listAccountNameSearchPoolByRoleInTenant(roleKey, tenantId, limit);
    }

    @Transactional(readOnly = true)
    public WorkIdentityAccount getAccount(Long accountId) {
        if (accountId == null) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.PARAM_INVALID,
                    "accountId 必填");
        }
        Long tenantId = requireCurrentTenantId();
        return repository.findAccountInTenant(accountId, tenantId)
                .map(row -> toAccount(row, tenantId))
                .orElseThrow(() -> new WorkIdentityApplicationException(
                        WorkIdentityApplicationException.Reason.ACCOUNT_NOT_FOUND,
                        "自然人账号不存在：accountId=" + accountId));
    }

    @Transactional(readOnly = true)
    public List<WorkIdentityDeptOption> listDeptOptions(String roleKey) {
        SysRole role = roleByKey(roleKey);
        UserContext ctx = userContextHolder.current();
        Long tenantId = ctx == null ? null : ctx.tenantId();
        return repository.listDeptOptions(role.allowedDeptCategory(), tenantId).stream()
                .filter(dept -> WorkIdentityRoleRules.matchesDeptType(role.roleKey(), dept.deptType()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WorkIdentityDeptOption> listGridNodes(Long communityDeptId) {
        if (communityDeptId == null) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.PARAM_INVALID,
                    "communityDeptId 必填");
        }
        WorkIdentityDeptOption communityDept = repository.findDept(communityDeptId)
                .orElseThrow(() -> new WorkIdentityApplicationException(
                        WorkIdentityApplicationException.Reason.DEPT_NOT_FOUND,
                        "居委会部门不存在或已停用：deptId=" + communityDeptId));
        if (communityDept.deptType() == null || communityDept.deptType() != 2
                || !"G".equals(communityDept.deptCategory())) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.ROLE_DEPT_MISMATCH,
                    "只能读取 G 端 dept_type=2 居委会节点下的网格：deptId=" + communityDeptId);
        }
        UserContext ctx = userContextHolder.current();
        if (ctx != null && ctx.tenantId() != null && communityDept.tenantId() != null
                && !ctx.tenantId().equals(communityDept.tenantId())) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.FORBIDDEN,
                    "居委会节点不在当前数据范围内：deptId=" + communityDeptId);
        }
        return repository.listGridChildren(communityDeptId);
    }

    @Transactional(readOnly = true)
    public List<WorkIdentityDeptOption> listAssignedGridNodes(Long userId) {
        if (userId == null) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.PARAM_INVALID,
                    "userId 必填");
        }
        Long tenantId = requireCurrentTenantId();
        WorkIdentityShadow shadow = repository.findShadowByUserId(userId)
                .orElseThrow(() -> new WorkIdentityApplicationException(
                        WorkIdentityApplicationException.Reason.ACCOUNT_NOT_FOUND,
                        "工作身份不存在或已停用：userId=" + userId));
        if (!tenantId.equals(shadow.tenantId())) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.FORBIDDEN,
                    "工作身份不在当前小区数据范围内：userId=" + userId);
        }
        if (!WorkIdentityRoleRules.isGridMember(shadow.roleKey())) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.ROLE_DEPT_MISMATCH,
                    "只有 GRID_MEMBER 工作身份可读取网格分配：userId=" + userId);
        }
        return repository.listAssignedGridDepts(userId);
    }

    @Transactional(readOnly = true)
    public List<WorkIdentityBuildingScope> listBuildingOptions(Long deptId) {
        if (deptId == null) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.PARAM_INVALID,
                    "deptId 必填");
        }
        WorkIdentityDeptOption dept = repository.findDept(deptId)
                .orElseThrow(() -> new WorkIdentityApplicationException(
                        WorkIdentityApplicationException.Reason.DEPT_NOT_FOUND,
                        "部门不存在或已停用：deptId=" + deptId));
        boolean gridNode = dept.deptType() != null && dept.deptType() == 5 && "G".equals(dept.deptCategory());
        if (dept.tenantId() == null && !gridNode) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.PARAM_INVALID,
                    "该部门未归属具体小区，不能选择楼栋：deptId=" + deptId);
        }
        UserContext ctx = userContextHolder.current();
        if (ctx != null && ctx.tenantId() != null && !ctx.tenantId().equals(dept.tenantId())) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.FORBIDDEN,
                    "目标部门不在当前数据范围内：deptId=" + deptId);
        }
        if (gridNode) {
            return repository.listBuildingOptionsByTenants(effectiveCommunityTenantScope(dept));
        }
        return repository.listBuildingOptions(dept.tenantId());
    }

    @Transactional(readOnly = true)
    public List<WorkIdentityBuildingScope> listGridDeptBuildingScope(Long deptId) {
        if (deptId == null) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.PARAM_INVALID,
                    "deptId 必填");
        }
        WorkIdentityDeptOption dept = repository.findDept(deptId)
                .orElseThrow(() -> new WorkIdentityApplicationException(
                        WorkIdentityApplicationException.Reason.DEPT_NOT_FOUND,
                        "部门不存在或已停用：deptId=" + deptId));
        if (dept.deptType() == null || dept.deptType() != 5 || !"G".equals(dept.deptCategory())) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.ROLE_DEPT_MISMATCH,
                    "仅 dept_type=5 的 G 端网格节点支持楼栋范围配置：deptId=" + deptId);
        }
        UserContext ctx = userContextHolder.current();
        if (ctx != null && ctx.tenantId() != null && dept.tenantId() != null
                && !ctx.tenantId().equals(dept.tenantId())) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.FORBIDDEN,
                    "目标部门不在当前数据范围内：deptId=" + deptId);
        }
        return repository.listDeptBuildingScopes(deptId);
    }

    SysRole roleByKey(String roleKey) {
        if (roleKey == null || roleKey.isBlank()) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.PARAM_INVALID,
                    "roleKey 必填");
        }
        return roleRepository.findByRoleKey(roleKey.trim())
                .orElseThrow(() -> new WorkIdentityApplicationException(
                        WorkIdentityApplicationException.Reason.ROLE_NOT_FOUND,
                        "角色不存在：roleKey=" + roleKey));
    }

    private Long requireCurrentTenantId() {
        UserContext ctx = userContextHolder.current();
        if (ctx == null || ctx.tenantId() == null) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.FORBIDDEN,
                    "当前工作身份未绑定小区，无法读取小区用户");
        }
        return ctx.tenantId();
    }

    private WorkIdentityAccount toAccount(WorkIdentityRepository.AccountCandidate row, Long tenantId) {
        return new WorkIdentityAccount(
                row.accountId(),
                row.phone(),
                decrypt(row.realNameCipher()),
                row.realNameVerified(),
                row.status(),
                repository.listShadowsInTenant(row.accountId(), tenantId).stream()
                        .map(this::withBuildings)
                        .toList());
    }

    String decrypt(String cipher) {
        String value = nameDecryptor.safeDecrypt(cipher);
        return value == null ? "" : value;
    }

    WorkIdentityShadow withBuildings(WorkIdentityShadow shadow) {
        return new WorkIdentityShadow(
                shadow.userId(),
                shadow.accountId(),
                shadow.deptId(),
                shadow.tenantId(),
                shadow.userName(),
                shadow.nickName(),
                shadow.deptType(),
                shadow.deptCategory(),
                shadow.deptName(),
                shadow.roleId(),
                shadow.roleKey(),
                shadow.roleName(),
                shadow.effectiveDataScope(),
                repository.listActiveBuildingIds(shadow.userId()),
                WorkIdentityRoleRules.isGridMember(shadow.roleKey())
                        ? repository.listAssignedGridDepts(shadow.userId())
                        : List.of());
    }

    private static boolean isAllDigits(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private List<Long> effectiveCommunityTenantScope(WorkIdentityDeptOption gridDept) {
        if (gridDept.parentId() == null) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.ROLE_DEPT_MISMATCH,
                    "网格节点缺少上级居委会，不能选择楼栋：deptId=" + gridDept.deptId());
        }
        List<Long> tenantIds = repository.listCommunityTenantScope(gridDept.parentId());
        if (tenantIds.isEmpty() && gridDept.tenantId() != null) {
            return List.of(gridDept.tenantId());
        }
        return tenantIds;
    }
}
