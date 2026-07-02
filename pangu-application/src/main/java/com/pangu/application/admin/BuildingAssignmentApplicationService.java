package com.pangu.application.admin;

import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.user.BuildingOccupant;
import com.pangu.domain.repository.BuildingAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 楼栋责任田分配写侧服务（M4）。
 *
 * <p>编排两条 use case：{@link #assign} 分配楼栋 / {@link #revoke} 撤销分配。
 *
 * <p><b>访问模型——数据范围收口</b>：不引入平台权限 key（{@code admin:user:building:assign}
 * 是 G 端红线，trigger 6 拦 B 端，无法覆盖业委会主任给志愿者分配楼栋这条业务诉求）。
 * 改为 service 层校验：
 * <ol>
 *   <li>{@link #requireAssigner()} 校验调用者 roleKey ∈ {@link #ASSIGNER_ROLES}
 *       （超管/居委会管理员/党组书记/业委会主任）；网格员范围额外收紧为居委会管理员；</li>
 *   <li>{@code assign} 进一步校验目标用户角色 ∈
 *       {@link BuildingAssignmentQueryService#ASSIGNABLE_ROLES} 且同租户；</li>
 *   <li>合规检查：账号状态（SQL 已过滤 u.status='0'）/ 实名 / 楼栋上限；</li>
 *   <li>同角色互斥：楼栋已被同 targetRoleKey 其他用户占用时拒绝（{@code force=true}
 *       走「转移」流程：先 revoke 占用者再 assign）；不同角色可共享。</li>
 * </ol>
 *
 * <p>幂等：{@link BuildingAssignmentRepository#assign} 在 repository 层实现
 * （已生效 noop / 已撤销复活 / 否则插入），本服务在前置校验通过后调用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BuildingAssignmentApplicationService {

    /** 分配者角色白名单——四个管理者角色都是 ALL_COMMUNITY 视野。 */
    public static final Set<String> ASSIGNER_ROLES = Set.of(
            "GOV_SUPER_ADMIN",
            "COMMUNITY_ADMIN",
            "PARTY_SECRETARY",
            "COMMITTEE_DIRECTOR");

    /** 单用户楼栋上限（合规检查硬阈值）。 */
    public static final int MAX_BUILDINGS_PER_USER = 5;

    private final BuildingAssignmentRepository repository;
    private final UserContextHolder userContextHolder;

    @Transactional
    public void assign(Long userId, Long buildingId, String targetRoleKey, boolean force) {
        assign(userId, buildingId, targetRoleKey, null, force);
    }

    /**
     * 在指定租户内分配楼栋。
     *
     * <p>普通小区管理员沿用当前登录租户；街道超管 {@code tenantId=null} 时，
     * 工作身份创建流程可传入目标部门租户，避免跨租户视图无法写入
     * {@code sys_user_building.tenant_id}。
     */
    @Transactional
    public void assign(Long userId, Long buildingId, String targetRoleKey, Long targetTenantId, boolean force) {
        if (userId == null || buildingId == null) {
            throw new BuildingAssignmentApplicationException(
                    BuildingAssignmentApplicationException.Reason.PARAM_INVALID,
                    "userId 与 buildingId 必填");
        }
        if (targetRoleKey == null || targetRoleKey.isBlank()) {
            throw new BuildingAssignmentApplicationException(
                    BuildingAssignmentApplicationException.Reason.PARAM_INVALID,
                    "targetRoleKey 必填");
        }
        if (!BuildingAssignmentQueryService.ASSIGNABLE_ROLES.contains(targetRoleKey)) {
            throw new BuildingAssignmentApplicationException(
                    BuildingAssignmentApplicationException.Reason.PARAM_INVALID,
                    "targetRoleKey 必须为 GRID_MEMBER/VOLUNTEER/OWNER_REPRESENTATIVE 之一，实际："
                            + targetRoleKey);
        }
        UserContext ctx = requireAssigner();
        if (WorkIdentityRoleRules.isGridMember(targetRoleKey) && !isCommunityAdmin(ctx)) {
            throw new BuildingAssignmentApplicationException(
                    BuildingAssignmentApplicationException.Reason.FORBIDDEN,
                    "网格员楼栋范围只能由居委会管理身份分配，当前角色=" + ctx.roleKey());
        }
        Long tenantId = resolveTenant(ctx, targetTenantId);

        // 目标用户须持指定可分配角色（同租户；街道超管 tenantId=null 跨租户）
        if (!repository.userHasAssignableRole(userId, targetRoleKey, tenantId)) {
            throw new BuildingAssignmentApplicationException(
                    BuildingAssignmentApplicationException.Reason.USER_NOT_FOUND,
                    "目标用户不存在或不持 " + targetRoleKey + " 角色：userId=" + userId);
        }
        // 楼栋须在分配者租户范围内（街道超管 tenantId=null → repository 返回 true 跳过）
        if (!repository.buildingExistsInTenant(buildingId, tenantId)) {
            throw new BuildingAssignmentApplicationException(
                    BuildingAssignmentApplicationException.Reason.BUILDING_NOT_IN_SCOPE,
                    "楼栋不在当前数据范围内：buildingId=" + buildingId);
        }

        // 写入实际归属租户：街道超管 tenantId=null 时禁止跨租户分配
        // （sys_user_building.tenant_id NOT NULL）；前端在街道超管登录时不显示该入口。
        Long writeTenantId = tenantId;
        if (writeTenantId == null) {
            throw new BuildingAssignmentApplicationException(
                    BuildingAssignmentApplicationException.Reason.BUILDING_NOT_IN_SCOPE,
                    "跨租户视图下不允许分配楼栋；请切换到具体小区身份后再操作");
        }

        // 合规检查：账号状态 SQL 已过滤；这里检实名 + 楼栋上限。
        // 从搜索/列表端点已生成的 AssignableUser 拿到合规标签是另一条路径，
        // 但 service 必须独立校验（不能信前端拼参）—— 从 repository 重新拉。
        // 用 searchAssignableUsers 复用按 userId 过滤会过度，简洁起见直接调
        // listUsersByRole 取该角色全量后过滤——但 OWNER_REPRESENTATIVE 等可能上百。
        // 更直接：用 userHasAssignableRole 之外的轻量校验。这里偷懒，沿用
        // listAssignmentsByUser 取楼栋数，再单查 t_account real_name_verified。
        // 但仓储未暴露后者——增个 listUsersByRole 单点查询 helper 太重。
        // 务实方案：复用 search，按 userId 串联（最多 50 行，可接受）。
        boolean compliant = false;
        boolean buildingLimitOk = repository.listAssignmentsByUser(userId).size()
                < MAX_BUILDINGS_PER_USER;
        // 实名校验：从 listUsersByRole 按 targetRoleKey 拉一遍（同租户内该角色用户量级 OK）
        var sameRoleUsers = repository.listUsersByRole(targetRoleKey, tenantId);
        var target = sameRoleUsers.stream()
                .filter(u -> u.userId().equals(userId))
                .findFirst();
        if (target.isEmpty()) {
            // 极小概率竞态：userHasAssignableRole 通过后 sameRoleUsers 又没有
            throw new BuildingAssignmentApplicationException(
                    BuildingAssignmentApplicationException.Reason.USER_NOT_FOUND,
                    "目标用户上下文消失：userId=" + userId);
        }
        boolean verified = target.get().realNameVerified() == 1;
        compliant = verified && buildingLimitOk;
        if (!compliant) {
            StringBuilder sb = new StringBuilder("目标用户不满足合规要求：");
            if (!verified) sb.append("NOT_VERIFIED ");
            if (!buildingLimitOk) sb.append("BUILDING_LIMIT_REACHED");
            throw new BuildingAssignmentApplicationException(
                    BuildingAssignmentApplicationException.Reason.USER_NOT_COMPLIANT,
                    sb.toString().trim());
        }

        // 同角色互斥：检查该楼栋当前 status=1 占用者
        List<BuildingOccupant> occupants = repository.listOccupants(buildingId, tenantId);
        Optional<BuildingOccupant> conflict = occupants.stream()
                .filter(o -> targetRoleKey.equals(o.roleKey()) && !userId.equals(o.userId()))
                .findFirst();
        if (conflict.isPresent()) {
            if (!force) {
                throw new BuildingAssignmentApplicationException(
                        BuildingAssignmentApplicationException.Reason.BUILDING_OCCUPIED_BY_SAME_ROLE,
                        "该楼栋已被同角色用户「" + conflict.get().nickName()
                                + "」占用：占用者 userId=" + conflict.get().userId()
                                + "；如需转移请传 force=true");
            }
            // force=true：先撤销原占用者，再分配给当前 userId
            BuildingOccupant prev = conflict.get();
            int revoked = repository.revoke(prev.userId(), buildingId);
            log.info("BuildingAssignment force-transfer revoke prev user={} building={} affected={}",
                    prev.userId(), buildingId, revoked);
        }

        repository.assign(userId, buildingId, writeTenantId, ctx.userId());
        log.info("BuildingAssignment assign user={} building={} by={} tenant={} force={}",
                userId, buildingId, ctx.userId(), writeTenantId, force);
    }

    private Long resolveTenant(UserContext ctx, Long targetTenantId) {
        if (ctx.tenantId() != null) {
            if (targetTenantId != null && !ctx.tenantId().equals(targetTenantId)) {
                throw new BuildingAssignmentApplicationException(
                        BuildingAssignmentApplicationException.Reason.BUILDING_NOT_IN_SCOPE,
                        "目标租户不在当前数据范围内：tenantId=" + targetTenantId);
            }
            return ctx.tenantId();
        }
        return targetTenantId;
    }

    @Transactional
    public void revoke(Long userId, Long buildingId) {
        if (userId == null || buildingId == null) {
            throw new BuildingAssignmentApplicationException(
                    BuildingAssignmentApplicationException.Reason.PARAM_INVALID,
                    "userId 与 buildingId 必填");
        }
        UserContext ctx = requireAssigner();
        int affected = repository.revoke(userId, buildingId);
        if (affected == 0) {
            throw new BuildingAssignmentApplicationException(
                    BuildingAssignmentApplicationException.Reason.ASSIGNMENT_NOT_FOUND,
                    "无生效分配记录，无需撤销：userId=" + userId + ", buildingId=" + buildingId);
        }
        log.info("BuildingAssignment revoke user={} building={} by={}",
                userId, buildingId, ctx.userId());
    }

    /**
     * 校验调用者属分配者白名单，返回其 UserContext 供下游取 tenantId/userId 用。
     */
    private UserContext requireAssigner() {
        UserContext ctx = userContextHolder.current();
        if (ctx == null || ctx.roleKey() == null
                || !ASSIGNER_ROLES.contains(ctx.roleKey())) {
            throw new BuildingAssignmentApplicationException(
                    BuildingAssignmentApplicationException.Reason.FORBIDDEN,
                    "当前角色无权分配楼栋责任田：roleKey="
                            + (ctx == null ? "null" : ctx.roleKey()));
        }
        return ctx;
    }

    private boolean isCommunityAdmin(UserContext ctx) {
        return WorkIdentityRoleRules.COMMUNITY_ADMIN.equals(ctx.roleKey())
                && ctx.deptCategory() == UserContext.DeptCategory.G
                && ctx.deptType() != null
                && ctx.deptType() == 2;
    }
}
