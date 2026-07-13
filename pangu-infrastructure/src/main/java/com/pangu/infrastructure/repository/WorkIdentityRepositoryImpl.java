package com.pangu.infrastructure.repository;

import com.pangu.domain.model.user.WorkIdentityBuildingScope;
import com.pangu.domain.model.user.WorkIdentityDeptOption;
import com.pangu.domain.model.user.WorkIdentityShadow;
import com.pangu.domain.repository.WorkIdentityRepository;
import com.pangu.infrastructure.persistence.mapper.WorkIdentityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@link WorkIdentityRepository} 默认实现。
 */
@Repository
@RequiredArgsConstructor
public class WorkIdentityRepositoryImpl implements WorkIdentityRepository {

    private final WorkIdentityMapper mapper;

    @Override
    public List<AccountCandidate> searchAccountCandidates(String keyword, int limit) {
        return mapper.searchAccountCandidates(keyword, limit).stream()
                .map(this::toAccountCandidate)
                .toList();
    }

    @Override
    public List<AccountCandidate> searchAccountCandidatesByRole(String keyword, String roleKey, int limit) {
        return mapper.searchAccountCandidatesByRole(keyword, roleKey, limit).stream()
                .map(this::toAccountCandidate)
                .toList();
    }

    @Override
    public List<AccountCandidate> listAccountNameSearchPool(int limit) {
        return mapper.listAccountNameSearchPool(limit).stream()
                .map(this::toAccountCandidate)
                .toList();
    }

    @Override
    public List<AccountCandidate> listAccountNameSearchPoolByRole(String roleKey, int limit) {
        return mapper.listAccountNameSearchPoolByRole(roleKey, limit).stream()
                .map(this::toAccountCandidate)
                .toList();
    }

    @Override
    public List<AccountCandidate> searchAccountCandidatesInTenant(String keyword, Long tenantId, int limit) {
        return mapper.searchAccountCandidatesInTenant(keyword, tenantId, limit).stream()
                .map(this::toAccountCandidate)
                .toList();
    }

    @Override
    public List<AccountCandidate> searchAccountCandidatesByRoleInTenant(
            String keyword, String roleKey, Long tenantId, int limit) {
        return mapper.searchAccountCandidatesByRoleInTenant(keyword, roleKey, tenantId, limit).stream()
                .map(this::toAccountCandidate)
                .toList();
    }

    @Override
    public List<AccountCandidate> listAccountNameSearchPoolInTenant(Long tenantId, int limit) {
        return mapper.listAccountNameSearchPoolInTenant(tenantId, limit).stream()
                .map(this::toAccountCandidate)
                .toList();
    }

    @Override
    public List<AccountCandidate> listAccountNameSearchPoolByRoleInTenant(
            String roleKey, Long tenantId, int limit) {
        return mapper.listAccountNameSearchPoolByRoleInTenant(roleKey, tenantId, limit).stream()
                .map(this::toAccountCandidate)
                .toList();
    }

    @Override
    public Optional<AccountCandidate> findAccount(Long accountId) {
        return Optional.ofNullable(mapper.selectAccount(accountId)).map(this::toAccountCandidate);
    }

    @Override
    public Optional<AccountCandidate> findAccountInTenant(Long accountId, Long tenantId) {
        return Optional.ofNullable(mapper.selectAccountInTenant(accountId, tenantId))
                .map(this::toAccountCandidate);
    }

    @Override
    public Optional<AccountCandidate> findAccountByPhone(String phone) {
        return Optional.ofNullable(mapper.selectAccountByPhone(phone)).map(this::toAccountCandidate);
    }

    @Override
    public List<WorkIdentityShadow> listShadows(Long accountId) {
        return mapper.selectShadowsByAccount(accountId).stream()
                .map(this::toShadow)
                .toList();
    }

    @Override
    public List<WorkIdentityShadow> listShadowsInTenant(Long accountId, Long tenantId) {
        return mapper.selectShadowsByAccountInTenant(accountId, tenantId).stream()
                .map(this::toShadow)
                .toList();
    }

    @Override
    public Optional<WorkIdentityShadow> findShadow(Long accountId, Long userId) {
        return Optional.ofNullable(mapper.selectShadow(accountId, userId)).map(this::toShadow);
    }

    @Override
    public Optional<WorkIdentityShadow> findShadowByUserId(Long userId) {
        return Optional.ofNullable(mapper.selectShadowByUserId(userId)).map(this::toShadow);
    }

    @Override
    public List<Long> listActiveBuildingIds(Long userId) {
        return mapper.selectActiveBuildingIds(userId);
    }

    @Override
    public List<WorkIdentityBuildingScope> listDeptBuildingScopes(Long deptId) {
        return mapper.selectDeptBuildingScopes(deptId).stream()
                .map(this::toBuildingScope)
                .toList();
    }

    @Override
    public List<WorkIdentityDeptOption> listDeptOptions(String deptCategory, Long tenantId) {
        return mapper.selectDeptOptions(deptCategory, tenantId).stream()
                .map(this::toDeptOption)
                .toList();
    }

    @Override
    public List<WorkIdentityDeptOption> listGridChildren(Long communityDeptId) {
        return mapper.selectGridChildren(communityDeptId).stream()
                .map(this::toDeptOption)
                .toList();
    }

    @Override
    public List<WorkIdentityDeptOption> listAssignedGridDepts(Long userId) {
        return mapper.selectAssignedGridDepts(userId).stream()
                .map(this::toDeptOption)
                .toList();
    }

    @Override
    public List<WorkIdentityBuildingScope> listBuildingOptions(Long tenantId) {
        return mapper.selectDistinctBuildings(tenantId).stream()
                .map(this::toBuildingScope)
                .toList();
    }

    @Override
    public List<WorkIdentityBuildingScope> listBuildingOptionsByTenants(List<Long> tenantIds) {
        return mapper.selectDistinctBuildingsByTenants(tenantIds).stream()
                .map(this::toBuildingScope)
                .toList();
    }

    @Override
    public List<Long> listCommunityTenantScope(Long communityDeptId) {
        return mapper.selectCommunityTenantScope(communityDeptId);
    }

    @Override
    public Optional<WorkIdentityDeptOption> findDept(Long deptId) {
        return Optional.ofNullable(mapper.selectDept(deptId)).map(this::toDeptOption);
    }

    @Override
    public boolean accountHasDept(Long accountId, Long deptId) {
        return mapper.existsAccountDept(accountId, deptId);
    }

    @Override
    public long countActiveUsersByDept(Long deptId) {
        return mapper.countActiveUsersByDept(deptId);
    }

    @Override
    public void replaceDeptBuildingScope(Long deptId, List<WorkIdentityBuildingScope> scopes, Long assignedBy) {
        mapper.deactivateDeptBuildingScope(deptId);
        for (WorkIdentityBuildingScope scope : scopes) {
            int affected = mapper.upsertDeptBuildingScope(
                    deptId,
                    scope.tenantId(),
                    scope.buildingId(),
                    assignedBy);
            if (affected == 0) {
                throw new IllegalArgumentException("楼栋不存在，无法写入网格范围：tenantId="
                        + scope.tenantId() + ", buildingId=" + scope.buildingId());
            }
        }
    }

    @Override
    public void replaceUserGridDeptAssignments(Long userId, List<Long> gridDeptIds, Long assignedBy) {
        mapper.deactivateUserGridDeptAssignments(userId);
        for (Long gridDeptId : gridDeptIds) {
            mapper.upsertUserGridDeptAssignment(userId, gridDeptId, assignedBy);
        }
    }

    @Override
    public Long insertAccount(String phone, String realName, int realNameVerified) {
        WorkIdentityMapper.AccountInsertRow row = new WorkIdentityMapper.AccountInsertRow();
        row.setPhone(phone);
        row.setRealName(realName);
        row.setRealNameVerified(realNameVerified);
        try {
            mapper.insertAccount(row);
        } catch (DuplicateKeyException e) {
            throw new DuplicateWorkIdentityException("t_account(phone) UNIQUE violated", e);
        }
        return row.getAccountId();
    }

    @Override
    public void updateLastActiveIdentity(Long accountId, Long identityId, String identityType) {
        mapper.updateAccountLastActiveIdentity(accountId, identityId, identityType);
    }

    @Override
    public Long insertSysUser(Long accountId, Long deptId, String userName, String nickName) {
        WorkIdentityMapper.SysUserInsertRow row = new WorkIdentityMapper.SysUserInsertRow();
        row.setAccountId(accountId);
        row.setDeptId(deptId);
        row.setUserName(userName);
        row.setNickName(nickName);
        try {
            mapper.insertSysUser(row);
        } catch (DuplicateKeyException e) {
            throw new DuplicateWorkIdentityException(
                    "sys_user(account_id, dept_id) UNIQUE violated", e);
        }
        return row.getUserId();
    }

    @Override
    public Long insertGridDept(Long parentId, String ancestors, String deptName, Long tenantId, int orderNum) {
        WorkIdentityMapper.GridDeptInsertRow row = new WorkIdentityMapper.GridDeptInsertRow();
        row.setParentId(parentId);
        row.setAncestors(ancestors);
        row.setDeptName(deptName);
        row.setTenantId(tenantId);
        row.setOrderNum(orderNum);
        mapper.insertGridDept(row);
        return row.getDeptId();
    }

    @Override
    public int updateGridDeptName(Long deptId, String deptName) {
        return mapper.updateGridDeptName(deptId, deptName);
    }

    @Override
    public int deactivateGridDept(Long deptId) {
        return mapper.deactivateGridDept(deptId);
    }

    @Override
    public void insertSysUserRole(Long userId, Long roleId, String effectiveDataScope, Long grantedBy) {
        try {
            mapper.insertSysUserRole(userId, roleId, effectiveDataScope, grantedBy);
        } catch (DataAccessException e) {
            throw new RoleBindingConsistencyException(e.getMessage(), e);
        }
    }

    private AccountCandidate toAccountCandidate(WorkIdentityMapper.AccountCandidateRow row) {
        return new AccountCandidate(
                row.getAccountId(),
                row.getPhone(),
                row.getRealNameCipher(),
                row.getRealNameVerified() == null ? 0 : row.getRealNameVerified(),
                row.getStatus() == null ? 0 : row.getStatus());
    }

    private WorkIdentityShadow toShadow(WorkIdentityMapper.ShadowRow row) {
        return new WorkIdentityShadow(
                row.getUserId(),
                row.getAccountId(),
                row.getDeptId(),
                row.getTenantId(),
                row.getUserName(),
                row.getNickName(),
                row.getDeptType(),
                row.getDeptCategory(),
                row.getDeptName(),
                row.getRoleId(),
                row.getRoleKey(),
                row.getRoleName(),
                row.getEffectiveDataScope(),
                List.of(),
                List.of());
    }

    private WorkIdentityDeptOption toDeptOption(WorkIdentityMapper.DeptOptionRow row) {
        return new WorkIdentityDeptOption(
                row.getDeptId(),
                row.getParentId(),
                row.getAncestors(),
                row.getDeptName(),
                row.getDeptType(),
                row.getDeptCategory(),
                row.getTenantId());
    }

    private WorkIdentityBuildingScope toBuildingScope(WorkIdentityMapper.BuildingScopeRow row) {
        return new WorkIdentityBuildingScope(row.getTenantId(), row.getBuildingId());
    }
}
