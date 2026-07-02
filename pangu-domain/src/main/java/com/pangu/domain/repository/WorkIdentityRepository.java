package com.pangu.domain.repository;

import com.pangu.domain.model.user.WorkIdentityDeptOption;
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

    List<AccountCandidate> listAccountNameSearchPool(int limit);

    Optional<AccountCandidate> findAccount(Long accountId);

    List<WorkIdentityShadow> listShadows(Long accountId);

    Optional<WorkIdentityShadow> findShadow(Long accountId, Long userId);

    List<Long> listActiveBuildingIds(Long userId);

    List<Long> listDeptBuildingScopeIds(Long deptId);

    List<WorkIdentityDeptOption> listDeptOptions(String deptCategory, Long tenantId);

    List<WorkIdentityDeptOption> listGridChildren(Long communityDeptId);

    List<Long> listBuildingOptions(Long tenantId);

    Optional<WorkIdentityDeptOption> findDept(Long deptId);

    boolean accountHasDept(Long accountId, Long deptId);

    void replaceDeptBuildingScope(Long deptId, List<Long> buildingIds, Long assignedBy);

    Long insertSysUser(Long accountId, Long deptId, String userName, String nickName);

    Long insertGridDept(Long parentId, String ancestors, String deptName, Long tenantId, int orderNum);

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
