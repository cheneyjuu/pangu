package com.pangu.application.admin;

import com.pangu.application.admin.command.CreateWorkIdentityCommand;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.role.SysRole;
import com.pangu.domain.model.user.WorkIdentityAccount;
import com.pangu.domain.model.user.WorkIdentityDeptOption;
import com.pangu.domain.model.user.WorkIdentityShadow;
import com.pangu.domain.repository.WorkIdentityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * 工作身份授权写侧编排。
 *
 * <p>一个 {@code sys_user} 仍只绑定一个 RBAC 角色；同一自然人需要承担多个职责时，
 * 创建多个工作身份并通过登录分身切换使用。OWNER_GROUP 类职责在同一事务内补足
 * 楼栋 ABAC 范围，满足数据库 deferred trigger。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkIdentityApplicationService {

    private static final int DEFAULT_GRID_NODE_COUNT = 5;

    private final WorkIdentityRepository repository;
    private final WorkIdentityQueryService queryService;
    private final BuildingAssignmentApplicationService buildingAssignmentApplicationService;
    private final UserContextHolder userContextHolder;

    @Transactional
    public WorkIdentityShadow create(CreateWorkIdentityCommand cmd) {
        validateCommand(cmd);
        UserContext ctx = requireOperator();
        WorkIdentityAccount account = queryService.getAccount(cmd.accountId());
        if (account.status() != 1) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.ACCOUNT_NOT_FOUND,
                    "自然人账号不可用：accountId=" + cmd.accountId());
        }

        SysRole role = queryService.roleByKey(cmd.roleKey());
        WorkIdentityDeptOption dept = repository.findDept(cmd.deptId())
                .orElseThrow(() -> new WorkIdentityApplicationException(
                        WorkIdentityApplicationException.Reason.DEPT_NOT_FOUND,
                        "部门不存在或已停用：deptId=" + cmd.deptId()));
        validateRoleDept(role, dept);
        if (repository.accountHasDept(cmd.accountId(), cmd.deptId())) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.DUPLICATE_IDENTITY,
                    "该自然人在该部门下已有工作身份：accountId=" + cmd.accountId()
                            + ", deptId=" + cmd.deptId());
        }

        List<Long> buildingIds = normalizeBuildingIds(cmd.buildingIds());
        boolean needsBuildings = WorkIdentityRoleRules.needsBuildings(role.roleKey());
        boolean gridMember = WorkIdentityRoleRules.isGridMember(role.roleKey());
        if (!needsBuildings && !buildingIds.isEmpty()) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.BUILDING_NOT_ALLOWED,
                    role.roleKey() + " 不是 OWNER_GROUP 楼栋责任田角色，不允许随创建绑定楼栋");
        }
        if (buildingIds.size() > BuildingAssignmentApplicationService.MAX_BUILDINGS_PER_USER) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.PARAM_INVALID,
                    "单个工作身份最多绑定 "
                            + BuildingAssignmentApplicationService.MAX_BUILDINGS_PER_USER + " 个楼栋");
        }
        if (gridMember) {
            ensureGridDeptScopePrepared(ctx, dept, buildingIds);
        } else if (needsBuildings && buildingIds.isEmpty()) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.BUILDING_REQUIRED,
                    role.roleKey() + " 必须至少绑定一个楼栋");
        }

        String nickName = chooseNickName(cmd.nickName(), account);
        String userName = "acct_" + cmd.accountId() + "_dept_" + cmd.deptId();
        Long userId;
        try {
            userId = repository.insertSysUser(cmd.accountId(), cmd.deptId(), userName, nickName);
            repository.insertSysUserRole(userId, role.roleId(), effectiveScope(role), ctx.userId());
        } catch (WorkIdentityRepository.DuplicateWorkIdentityException e) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.DUPLICATE_IDENTITY,
                    "该自然人在该部门下已有工作身份", e);
        } catch (WorkIdentityRepository.RoleBindingConsistencyException e) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.ROLE_BINDING_INCONSISTENT,
                    "角色绑定被数据库一致性规则拒绝：" + e.getMessage(), e);
        }

        if (!gridMember) {
            for (Long buildingId : buildingIds) {
                buildingAssignmentApplicationService.assign(
                        userId,
                        buildingId,
                        role.roleKey(),
                        dept.tenantId(),
                        cmd.forceBuildingTransfer());
            }
        }

        WorkIdentityShadow created = repository.findShadow(cmd.accountId(), userId)
                .map(queryService::withBuildings)
                .orElseThrow(() -> new WorkIdentityApplicationException(
                        WorkIdentityApplicationException.Reason.ACCOUNT_NOT_FOUND,
                        "工作身份创建后未能回读：userId=" + userId));
        log.info("WorkIdentity created account={} user={} role={} dept={} by={}",
                cmd.accountId(), userId, role.roleKey(), cmd.deptId(), ctx.userId());
        return created;
    }

    @Transactional
    public List<Long> replaceGridDeptBuildingScope(Long deptId, List<Long> buildingIds) {
        if (deptId == null) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.PARAM_INVALID,
                    "deptId 必填");
        }
        List<Long> normalized = normalizeBuildingIds(buildingIds);
        if (normalized.isEmpty()) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.BUILDING_REQUIRED,
                    "网格节点必须至少绑定一个楼栋");
        }
        if (normalized.size() > BuildingAssignmentApplicationService.MAX_BUILDINGS_PER_USER) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.PARAM_INVALID,
                    "单个网格节点最多绑定 "
                            + BuildingAssignmentApplicationService.MAX_BUILDINGS_PER_USER + " 个楼栋");
        }
        UserContext ctx = requireOperator();
        WorkIdentityDeptOption dept = repository.findDept(deptId)
                .orElseThrow(() -> new WorkIdentityApplicationException(
                        WorkIdentityApplicationException.Reason.DEPT_NOT_FOUND,
                        "部门不存在或已停用：deptId=" + deptId));
        requireCommunityGridScopeOperator(ctx, dept);
        replaceDeptBuildingScope(dept.deptId(), normalized, ctx.userId());
        return repository.listDeptBuildingScopeIds(dept.deptId());
    }

    @Transactional
    public List<WorkIdentityDeptOption> ensureGridNodes(Long communityDeptId) {
        if (communityDeptId == null) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.PARAM_INVALID,
                    "communityDeptId 必填");
        }
        UserContext ctx = requireOperator();
        WorkIdentityDeptOption communityDept = repository.findDept(communityDeptId)
                .orElseThrow(() -> new WorkIdentityApplicationException(
                        WorkIdentityApplicationException.Reason.DEPT_NOT_FOUND,
                        "居委会部门不存在或已停用：deptId=" + communityDeptId));
        requireCommunityNodeOperator(ctx, communityDept);

        List<WorkIdentityDeptOption> existing = repository.listGridChildren(communityDeptId);
        String ancestors = communityDept.ancestors() == null || communityDept.ancestors().isBlank()
                ? String.valueOf(communityDept.deptId())
                : communityDept.ancestors() + "," + communityDept.deptId();
        for (int i = 1; i <= DEFAULT_GRID_NODE_COUNT; i++) {
            String gridName = i + "号网格";
            boolean exists = existing.stream().anyMatch(dept -> gridName.equals(dept.deptName()));
            if (!exists) {
                repository.insertGridDept(
                        communityDept.deptId(),
                        ancestors,
                        gridName,
                        communityDept.tenantId(),
                        i);
            }
        }
        return repository.listGridChildren(communityDeptId);
    }

    private void validateCommand(CreateWorkIdentityCommand cmd) {
        if (cmd == null || cmd.accountId() == null || cmd.deptId() == null
                || cmd.roleKey() == null || cmd.roleKey().isBlank()) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.PARAM_INVALID,
                    "accountId / deptId / roleKey 必填");
        }
    }

    private UserContext requireOperator() {
        UserContext ctx = userContextHolder.current();
        if (ctx == null || !ctx.isSysUser() || ctx.userId() == null) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.FORBIDDEN,
                    "未识别到管理端登录身份，禁止分配工作身份");
        }
        return ctx;
    }

    private void ensureGridDeptScopePrepared(UserContext ctx,
                                             WorkIdentityDeptOption dept,
                                             List<Long> requestedBuildingIds) {
        if (!requestedBuildingIds.isEmpty()) {
            requireCommunityGridScopeOperator(ctx, dept);
            replaceDeptBuildingScope(dept.deptId(), requestedBuildingIds, ctx.userId());
            return;
        }
        if (repository.listDeptBuildingScopeIds(dept.deptId()).isEmpty()) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.BUILDING_REQUIRED,
                    "GRID_MEMBER 网格节点必须先配置至少一个楼栋范围");
        }
    }

    private void requireCommunityGridScopeOperator(UserContext ctx, WorkIdentityDeptOption gridDept) {
        requireCommunityOperator(ctx);
        if (gridDept.deptType() == null || gridDept.deptType() != 5 || !"G".equals(gridDept.deptCategory())) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.ROLE_DEPT_MISMATCH,
                    "GRID_MEMBER 必须绑定 dept_type=5 的 G 端网格节点：deptId=" + gridDept.deptId());
        }
        if (ctx.tenantId() != null && gridDept.tenantId() != null && !ctx.tenantId().equals(gridDept.tenantId())) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.FORBIDDEN,
                    "网格节点不在当前居委会数据范围内：deptId=" + gridDept.deptId());
        }
    }

    private void requireCommunityNodeOperator(UserContext ctx, WorkIdentityDeptOption communityDept) {
        requireCommunityOperator(ctx);
        if (communityDept.deptType() == null || communityDept.deptType() != 2
                || !"G".equals(communityDept.deptCategory())) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.ROLE_DEPT_MISMATCH,
                    "只能在 G 端 dept_type=2 居委会节点下生成网格：deptId=" + communityDept.deptId());
        }
        if (ctx.tenantId() != null && communityDept.tenantId() != null
                && !ctx.tenantId().equals(communityDept.tenantId())) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.FORBIDDEN,
                    "居委会节点不在当前数据范围内：deptId=" + communityDept.deptId());
        }
    }

    private void requireCommunityOperator(UserContext ctx) {
        if (!WorkIdentityRoleRules.COMMUNITY_ADMIN.equals(ctx.roleKey()) || ctx.deptType() == null
                || ctx.deptType() != 2 || ctx.deptCategory() != UserContext.DeptCategory.G) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.FORBIDDEN,
                    "网格组织与楼栋范围只能由居委会管理身份配置，当前角色=" + ctx.roleKey());
        }
    }

    private void replaceDeptBuildingScope(Long deptId, List<Long> buildingIds, Long assignedBy) {
        try {
            repository.replaceDeptBuildingScope(deptId, buildingIds, assignedBy);
        } catch (IllegalArgumentException e) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.PARAM_INVALID,
                    e.getMessage(), e);
        }
    }

    private void validateRoleDept(SysRole role, WorkIdentityDeptOption dept) {
        if (!role.allowedDeptCategory().equals(dept.deptCategory())) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.ROLE_DEPT_MISMATCH,
                    "角色 " + role.roleKey() + " 限定 " + role.allowedDeptCategory()
                            + " 端，不能挂在 " + dept.deptCategory() + " 端部门");
        }
        if (!WorkIdentityRoleRules.matchesDeptType(role.roleKey(), dept.deptType())) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.ROLE_DEPT_MISMATCH,
                    "角色 " + role.roleKey() + " 与部门类型 deptType=" + dept.deptType()
                            + " 不匹配");
        }
    }

    private List<Long> normalizeBuildingIds(List<Long> buildingIds) {
        if (buildingIds == null || buildingIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> normalized = new LinkedHashSet<>();
        for (Long id : buildingIds) {
            if (id == null) {
                throw new WorkIdentityApplicationException(
                        WorkIdentityApplicationException.Reason.PARAM_INVALID,
                        "buildingIds 不允许包含 null");
            }
            normalized.add(id);
        }
        return List.copyOf(normalized);
    }

    private static String effectiveScope(SysRole role) {
        return role.fixedDataScope() != null ? role.fixedDataScope() : role.defaultDataScope();
    }

    private String chooseNickName(String requested, WorkIdentityAccount account) {
        if (requested != null && !requested.isBlank()) {
            return requested.trim();
        }
        if (account.realName() != null && !account.realName().isBlank()) {
            return account.realName();
        }
        String phone = account.phone();
        if (phone != null && phone.length() >= 4) {
            return "账号" + phone.substring(phone.length() - 4);
        }
        return "账号" + account.accountId();
    }
}
