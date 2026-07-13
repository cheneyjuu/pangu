// 关联业务：创建和维护管理端工作身份，并把角色绑定到当前小区的真实组织节点。
package com.pangu.application.admin;

import com.pangu.application.admin.command.CreateWorkIdentityCommand;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.role.SysRole;
import com.pangu.domain.model.user.WorkIdentityAccount;
import com.pangu.domain.model.user.WorkIdentityBuildingScope;
import com.pangu.domain.model.user.WorkIdentityDeptOption;
import com.pangu.domain.model.user.WorkIdentityShadow;
import com.pangu.domain.repository.WorkIdentityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

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
    private static final Pattern MAINLAND_MOBILE = Pattern.compile("^1[3-9]\\d{9}$");

    private final WorkIdentityRepository repository;
    private final WorkIdentityQueryService queryService;
    private final BuildingAssignmentApplicationService buildingAssignmentApplicationService;
    private final UserContextHolder userContextHolder;

    @Transactional
    public WorkIdentityShadow create(CreateWorkIdentityCommand cmd) {
        validateCommand(cmd);
        WorkIdentityAccount account = queryService.getAccount(cmd.accountId());
        return createForAccount(cmd, account);
    }

    /**
     * 在已确认属于当前小区的自然人账号上创建管理端工作身份。
     *
     * <p>新建自然人账号在首个身份落库前尚未有小区归属，因此由
     * {@link #createAccountWithIdentity(String, String, CreateWorkIdentityCommand)} 直接传入刚创建的账号；
     * 其他入口必须先经 {@link WorkIdentityQueryService#getAccount(Long)} 做小区范围校验。
     */
    private WorkIdentityShadow createForAccount(CreateWorkIdentityCommand cmd, WorkIdentityAccount account) {
        UserContext ctx = requireOperator();
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
        validateRoleDept(ctx, role, dept);
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
    public WorkIdentityAccount createAccountWithIdentity(String phone,
                                                         String realName,
                                                         CreateWorkIdentityCommand identityCommand) {
        String normalizedPhone = normalizePhone(phone);
        String normalizedRealName = normalizeRealName(realName);
        if (identityCommand == null) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.PARAM_INVALID,
                    "工作身份参数不能为空");
        }
        if (repository.findAccountByPhone(normalizedPhone).isPresent()) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.DUPLICATE_IDENTITY,
                    "手机号已存在，请搜索该自然人后编辑工作身份：" + normalizedPhone);
        }
        Long accountId;
        try {
            accountId = repository.insertAccount(normalizedPhone, normalizedRealName, 0);
        } catch (WorkIdentityRepository.DuplicateWorkIdentityException e) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.DUPLICATE_IDENTITY,
                    "手机号已存在，请搜索该自然人后编辑工作身份：" + normalizedPhone, e);
        }
        WorkIdentityAccount account = new WorkIdentityAccount(
                accountId,
                normalizedPhone,
                normalizedRealName,
                0,
                1,
                List.of());
        CreateWorkIdentityCommand createCommand = new CreateWorkIdentityCommand(
                accountId,
                identityCommand.deptId(),
                identityCommand.roleKey(),
                identityCommand.nickName(),
                identityCommand.buildingIds(),
                identityCommand.forceBuildingTransfer());
        // 新建账号的请求体不携带 accountId，生成账号后再复用统一的身份参数校验。
        validateCommand(createCommand);
        WorkIdentityShadow shadow = createForAccount(createCommand, account);
        repository.updateLastActiveIdentity(accountId, shadow.userId(), UserContext.IdentityType.SYS_USER.name());
        return queryService.getAccount(accountId);
    }

    @Transactional
    public List<WorkIdentityBuildingScope> replaceGridDeptBuildingScopeLegacy(Long deptId, List<Long> buildingIds) {
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
        replaceDeptBuildingScopeWithLegacyIds(dept.deptId(), normalized, ctx.userId());
        return repository.listDeptBuildingScopes(dept.deptId());
    }

    @Transactional
    public List<WorkIdentityBuildingScope> replaceGridDeptBuildingScope(
            Long deptId,
            List<WorkIdentityBuildingScope> requestedScopes) {
        if (deptId == null) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.PARAM_INVALID,
                    "deptId 必填");
        }
        List<WorkIdentityBuildingScope> normalized = normalizeBuildingScopes(requestedScopes);
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
        validateGridBuildingScopesAllowed(dept, normalized);
        replaceDeptBuildingScope(dept.deptId(), normalized, ctx.userId());
        return repository.listDeptBuildingScopes(dept.deptId());
    }

    @Transactional
    public WorkIdentityDeptOption createGridNode(Long communityDeptId, String deptName) {
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
        return createGridNodeUnderCommunity(ctx, communityDept, deptName);
    }

    @Transactional
    public WorkIdentityDeptOption createGridNode(String deptName) {
        UserContext ctx = requireOperator();
        WorkIdentityDeptOption communityDept = currentCommunityDept(ctx);
        return createGridNodeUnderCommunity(ctx, communityDept, deptName);
    }

    @Transactional
    public WorkIdentityDeptOption updateGridNode(Long deptId, String deptName) {
        if (deptId == null) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.PARAM_INVALID,
                    "deptId 必填");
        }
        String normalizedName = normalizeGridName(deptName);
        UserContext ctx = requireOperator();
        WorkIdentityDeptOption gridDept = repository.findDept(deptId)
                .orElseThrow(() -> new WorkIdentityApplicationException(
                        WorkIdentityApplicationException.Reason.DEPT_NOT_FOUND,
                        "网格节点不存在或已停用：deptId=" + deptId));
        requireCommunityGridScopeOperator(ctx, gridDept);
        boolean duplicateName = repository.listGridChildren(gridDept.parentId()).stream()
                .anyMatch(dept -> !dept.deptId().equals(deptId)
                        && normalizedName.equals(dept.deptName()));
        if (duplicateName) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.DUPLICATE_IDENTITY,
                    "同一居委会下已存在同名网格节点：" + normalizedName);
        }
        int affected = repository.updateGridDeptName(deptId, normalizedName);
        if (affected == 0) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.DEPT_NOT_FOUND,
                    "网格节点不存在或已停用：deptId=" + deptId);
        }
        return repository.findDept(deptId)
                .orElseThrow(() -> new WorkIdentityApplicationException(
                        WorkIdentityApplicationException.Reason.DEPT_NOT_FOUND,
                        "网格节点更新后未能回读：deptId=" + deptId));
    }

    @Transactional
    public void deleteGridNode(Long deptId) {
        if (deptId == null) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.PARAM_INVALID,
                    "deptId 必填");
        }
        UserContext ctx = requireOperator();
        WorkIdentityDeptOption gridDept = repository.findDept(deptId)
                .orElseThrow(() -> new WorkIdentityApplicationException(
                        WorkIdentityApplicationException.Reason.DEPT_NOT_FOUND,
                        "网格节点不存在或已停用：deptId=" + deptId));
        requireCommunityGridScopeOperator(ctx, gridDept);
        long activeUsers = repository.countActiveUsersByDept(deptId);
        if (activeUsers > 0) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.ROLE_BINDING_INCONSISTENT,
                    "网格节点下仍有有效工作身份，需先迁移或停用网格员：deptId=" + deptId);
        }
        repository.replaceDeptBuildingScope(deptId, List.of(), ctx.userId());
        int affected = repository.deactivateGridDept(deptId);
        if (affected == 0) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.DEPT_NOT_FOUND,
                    "网格节点不存在或已停用：deptId=" + deptId);
        }
    }

    @Transactional
    public List<WorkIdentityDeptOption> replaceGridMemberGridNodes(Long userId, List<Long> gridDeptIds) {
        if (userId == null) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.PARAM_INVALID,
                    "userId 必填");
        }
        List<Long> normalized = normalizeGridDeptIds(gridDeptIds);
        if (normalized.isEmpty()) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.BUILDING_REQUIRED,
                    "网格员必须至少分配一个网格");
        }
        UserContext ctx = requireOperator();
        WorkIdentityShadow shadow = repository.findShadowByUserId(userId)
                .orElseThrow(() -> new WorkIdentityApplicationException(
                        WorkIdentityApplicationException.Reason.ACCOUNT_NOT_FOUND,
                        "工作身份不存在或已停用：userId=" + userId));
        requireCurrentTenantScope(ctx, shadow);
        if (!WorkIdentityRoleRules.isGridMember(shadow.roleKey())) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.ROLE_DEPT_MISMATCH,
                    "只有 GRID_MEMBER 工作身份可分配网格：userId=" + userId);
        }
        for (Long gridDeptId : normalized) {
            WorkIdentityDeptOption gridDept = repository.findDept(gridDeptId)
                    .orElseThrow(() -> new WorkIdentityApplicationException(
                            WorkIdentityApplicationException.Reason.DEPT_NOT_FOUND,
                            "网格节点不存在或已停用：deptId=" + gridDeptId));
            requireCommunityGridScopeOperator(ctx, gridDept);
            if (repository.listDeptBuildingScopes(gridDept.deptId()).isEmpty()) {
                throw new WorkIdentityApplicationException(
                        WorkIdentityApplicationException.Reason.BUILDING_REQUIRED,
                        "网格节点必须先配置至少一个楼栋范围：deptId=" + gridDept.deptId());
            }
        }
        repository.replaceUserGridDeptAssignments(userId, normalized, ctx.userId());
        return repository.listAssignedGridDepts(userId);
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

    private String normalizeGridName(String deptName) {
        if (deptName == null || deptName.isBlank()) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.PARAM_INVALID,
                    "机构名称不能为空");
        }
        String normalized = deptName.trim();
        if (normalized.length() > 100) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.PARAM_INVALID,
                    "机构名称不能超过100个字符");
        }
        return normalized;
    }

    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.PARAM_INVALID,
                    "手机号不能为空");
        }
        String normalized = phone.trim();
        if (!MAINLAND_MOBILE.matcher(normalized).matches()) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.PARAM_INVALID,
                    "手机号格式不正确");
        }
        return normalized;
    }

    private String normalizeRealName(String realName) {
        if (realName == null || realName.isBlank()) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.PARAM_INVALID,
                    "姓名不能为空");
        }
        String normalized = realName.trim();
        if (normalized.length() > 50) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.PARAM_INVALID,
                    "姓名不能超过50个字符");
        }
        return normalized;
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
            replaceDeptBuildingScopeWithLegacyIds(dept.deptId(), requestedBuildingIds, ctx.userId());
            return;
        }
        if (repository.listDeptBuildingScopes(dept.deptId()).isEmpty()) {
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

    /**
     * 网格分配是小区内权限操作，不能通过直接提交其他小区的 userId 跨越当前工作身份范围。
     */
    private void requireCurrentTenantScope(UserContext ctx, WorkIdentityShadow shadow) {
        if (ctx.tenantId() == null || shadow.tenantId() == null
                || !ctx.tenantId().equals(shadow.tenantId())) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.FORBIDDEN,
                    "工作身份不在当前小区数据范围内：userId=" + shadow.userId());
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

    private WorkIdentityDeptOption currentCommunityDept(UserContext ctx) {
        if (ctx.deptId() == null) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.FORBIDDEN,
                    "当前身份未绑定居委会组织节点，不能创建网格");
        }
        WorkIdentityDeptOption communityDept = repository.findDept(ctx.deptId())
                .orElseThrow(() -> new WorkIdentityApplicationException(
                        WorkIdentityApplicationException.Reason.DEPT_NOT_FOUND,
                        "当前居委会组织节点不存在或已停用：deptId=" + ctx.deptId()));
        requireCommunityNodeOperator(ctx, communityDept);
        return communityDept;
    }

    private WorkIdentityDeptOption createGridNodeUnderCommunity(
            UserContext ctx,
            WorkIdentityDeptOption communityDept,
            String deptName) {
        String normalizedName = normalizeGridName(deptName);
        requireCommunityNodeOperator(ctx, communityDept);

        List<WorkIdentityDeptOption> existing = repository.listGridChildren(communityDept.deptId());
        boolean duplicateName = existing.stream()
                .anyMatch(dept -> normalizedName.equals(dept.deptName()));
        if (duplicateName) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.DUPLICATE_IDENTITY,
                    "同一居委会下已存在同名网格节点：" + normalizedName);
        }

        String ancestors = communityDept.ancestors() == null || communityDept.ancestors().isBlank()
                ? String.valueOf(communityDept.deptId())
                : communityDept.ancestors() + "," + communityDept.deptId();
        Long deptId = repository.insertGridDept(
                communityDept.deptId(),
                ancestors,
                normalizedName,
                communityDept.tenantId(),
                existing.size() + 1);
        return repository.findDept(deptId)
                .orElseThrow(() -> new WorkIdentityApplicationException(
                        WorkIdentityApplicationException.Reason.DEPT_NOT_FOUND,
                        "网格节点创建后未能回读：deptId=" + deptId));
    }

    private void requireCommunityOperator(UserContext ctx) {
        if (!WorkIdentityRoleRules.COMMUNITY_ADMIN.equals(ctx.roleKey()) || ctx.deptType() == null
                || ctx.deptType() != 2 || ctx.deptCategory() != UserContext.DeptCategory.G) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.FORBIDDEN,
                    "网格组织与楼栋范围只能由居委会管理身份配置，当前角色=" + ctx.roleKey());
        }
    }

    private void replaceDeptBuildingScope(Long deptId, List<WorkIdentityBuildingScope> scopes, Long assignedBy) {
        try {
            repository.replaceDeptBuildingScope(deptId, scopes, assignedBy);
        } catch (IllegalArgumentException e) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.PARAM_INVALID,
                    e.getMessage(), e);
        }
    }

    private void replaceDeptBuildingScopeWithLegacyIds(Long deptId, List<Long> buildingIds, Long assignedBy) {
        try {
            WorkIdentityDeptOption dept = repository.findDept(deptId)
                    .orElseThrow(() -> new WorkIdentityApplicationException(
                            WorkIdentityApplicationException.Reason.DEPT_NOT_FOUND,
                            "部门不存在或已停用：deptId=" + deptId));
            List<WorkIdentityBuildingScope> scopes = normalizeLegacyBuildingScopes(buildingIds, dept.tenantId());
            validateGridBuildingScopesAllowed(dept, scopes);
            repository.replaceDeptBuildingScope(deptId, scopes, assignedBy);
        } catch (IllegalArgumentException e) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.PARAM_INVALID,
                    e.getMessage(), e);
        }
    }

    private void validateGridBuildingScopesAllowed(
            WorkIdentityDeptOption gridDept,
            List<WorkIdentityBuildingScope> requestedScopes) {
        Set<Long> allowedTenantIds = effectiveCommunityTenantScope(gridDept);
        for (WorkIdentityBuildingScope scope : requestedScopes) {
            if (!allowedTenantIds.contains(scope.tenantId())) {
                throw new WorkIdentityApplicationException(
                        WorkIdentityApplicationException.Reason.FORBIDDEN,
                        "楼栋不在该居委会管辖的小区范围内：tenantId=" + scope.tenantId()
                                + ", buildingId=" + scope.buildingId());
            }
        }
    }

    private Set<Long> effectiveCommunityTenantScope(WorkIdentityDeptOption gridDept) {
        if (gridDept.parentId() == null) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.ROLE_DEPT_MISMATCH,
                    "网格节点缺少上级居委会，不能配置楼栋范围：deptId=" + gridDept.deptId());
        }
        List<Long> tenantIds = repository.listCommunityTenantScope(gridDept.parentId());
        if (tenantIds.isEmpty() && gridDept.tenantId() != null) {
            return Set.of(gridDept.tenantId());
        }
        return Set.copyOf(tenantIds);
    }

    private void validateRoleDept(UserContext context, SysRole role, WorkIdentityDeptOption dept) {
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
        if (WorkIdentityRoleRules.isPropertyServiceRole(role.roleKey())
                && (context.tenantId() == null || dept.tenantId() == null
                || !context.tenantId().equals(dept.tenantId()))) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.ROLE_DEPT_MISMATCH,
                    "物业角色必须绑定当前小区已核验启用的物业服务项目部");
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

    private List<Long> normalizeGridDeptIds(List<Long> gridDeptIds) {
        if (gridDeptIds == null || gridDeptIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> normalized = new LinkedHashSet<>();
        for (Long id : gridDeptIds) {
            if (id == null) {
                throw new WorkIdentityApplicationException(
                        WorkIdentityApplicationException.Reason.PARAM_INVALID,
                        "gridDeptIds 不允许包含 null");
            }
            normalized.add(id);
        }
        return List.copyOf(normalized);
    }

    private List<WorkIdentityBuildingScope> normalizeLegacyBuildingScopes(List<Long> buildingIds, Long fallbackTenantId) {
        if (buildingIds == null || buildingIds.isEmpty()) {
            return List.of();
        }
        if (fallbackTenantId == null) {
            throw new WorkIdentityApplicationException(
                    WorkIdentityApplicationException.Reason.PARAM_INVALID,
                    "旧 buildingIds 请求缺少 tenantId，不能用于跨小区网格");
        }
        return normalizeBuildingIds(buildingIds).stream()
                .map(buildingId -> new WorkIdentityBuildingScope(fallbackTenantId, buildingId))
                .toList();
    }

    private List<WorkIdentityBuildingScope> normalizeBuildingScopes(List<WorkIdentityBuildingScope> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<WorkIdentityBuildingScope> normalized = new LinkedHashSet<>();
        for (WorkIdentityBuildingScope scope : scopes) {
            if (scope == null || scope.tenantId() == null || scope.buildingId() == null) {
                throw new WorkIdentityApplicationException(
                        WorkIdentityApplicationException.Reason.PARAM_INVALID,
                        "网格楼栋范围必须同时包含 tenantId 与 buildingId");
            }
            normalized.add(scope);
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
