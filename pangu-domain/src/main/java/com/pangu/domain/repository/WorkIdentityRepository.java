// 关联业务：提供管理端角色分配时按小区隔离的组织、人员与楼栋范围查询能力。
package com.pangu.domain.repository;

import com.pangu.domain.model.user.WorkIdentityDeptOption;
import com.pangu.domain.model.user.WorkIdentityBuildingScope;
import com.pangu.domain.model.user.WorkIdentityShadow;

import java.util.List;
import java.util.Optional;

/**
 * 管理端工作身份仓储端口。
 *
 * <p>写侧保持“一自然人多工作身份、一个 sys_user 只绑一个角色”的模型：
 * 创建第二个职责时新增 {@code sys_user}，而不是给同一 {@code sys_user} 叠多个角色。
 */
public interface WorkIdentityRepository {

    List<AccountCandidate> searchAccountCandidates(String keyword, int limit);

    List<AccountCandidate> searchAccountCandidatesByRole(String keyword, String roleKey, int limit);

    List<AccountCandidate> listAccountNameSearchPool(int limit);

    List<AccountCandidate> listAccountNameSearchPoolByRole(String roleKey, int limit);

    /**
     * 查询当前小区可分配工作身份的自然人。
     *
     * <p>账号必须在该小区拥有有效管理端工作身份，或已绑定有效业主房产，避免跨小区
     * 自然人进入角色分配范围。
     */
    List<AccountCandidate> searchAccountCandidatesInTenant(String keyword, Long tenantId, int limit);

    List<AccountCandidate> searchAccountCandidatesByRoleInTenant(
            String keyword, String roleKey, Long tenantId, int limit);

    List<AccountCandidate> listAccountNameSearchPoolInTenant(Long tenantId, int limit);

    List<AccountCandidate> listAccountNameSearchPoolByRoleInTenant(String roleKey, Long tenantId, int limit);

    Optional<AccountCandidate> findAccount(Long accountId);

    Optional<AccountCandidate> findAccountInTenant(Long accountId, Long tenantId);

    Optional<AccountCandidate> findAccountByPhone(String phone);

    List<WorkIdentityShadow> listShadows(Long accountId);

    /**
     * 仅回读当前小区的工作身份，防止同一自然人的其他小区身份泄露到管理端。
     */
    List<WorkIdentityShadow> listShadowsInTenant(Long accountId, Long tenantId);

    Optional<WorkIdentityShadow> findShadow(Long accountId, Long userId);

    Optional<WorkIdentityShadow> findShadowByUserId(Long userId);

    List<Long> listActiveBuildingIds(Long userId);

    List<WorkIdentityBuildingScope> listDeptBuildingScopes(Long deptId);

    List<WorkIdentityDeptOption> listDeptOptions(String deptCategory, Long tenantId);

    /**
     * 查询只属于当前小区的组织，避免物业角色落到跨小区企业根组织。
     */
    List<WorkIdentityDeptOption> listTenantDeptOptions(String deptCategory, Long tenantId);

    List<WorkIdentityDeptOption> listGridChildren(Long communityDeptId);

    List<WorkIdentityDeptOption> listAssignedGridDepts(Long userId);

    List<WorkIdentityBuildingScope> listBuildingOptions(Long tenantId);

    List<WorkIdentityBuildingScope> listBuildingOptionsByTenants(List<Long> tenantIds);

    List<Long> listCommunityTenantScope(Long communityDeptId);

    Optional<WorkIdentityDeptOption> findDept(Long deptId);

    boolean accountHasDept(Long accountId, Long deptId);

    long countActiveUsersByDept(Long deptId);

    void replaceDeptBuildingScope(Long deptId, List<WorkIdentityBuildingScope> scopes, Long assignedBy);

    void replaceUserGridDeptAssignments(Long userId, List<Long> gridDeptIds, Long assignedBy);

    Long insertAccount(String phone, String realName, int realNameVerified);

    void updateLastActiveIdentity(Long accountId, Long identityId, String identityType);

    Long insertSysUser(Long accountId, Long deptId, String userName, String nickName);

    Long insertGridDept(Long parentId, String ancestors, String deptName, Long tenantId, int orderNum);

    int updateGridDeptName(Long deptId, String deptName);

    int deactivateGridDept(Long deptId);

    void insertSysUserRole(Long userId, Long roleId, String effectiveDataScope, Long grantedBy);

    record AccountCandidate(
            Long accountId,
            String phone,
            String realNameCipher,
            int realNameVerified,
            int status) {
    }

    class DuplicateWorkIdentityException extends RuntimeException {
        public DuplicateWorkIdentityException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    class RoleBindingConsistencyException extends RuntimeException {
        public RoleBindingConsistencyException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
