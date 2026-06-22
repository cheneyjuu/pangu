package com.pangu.application.admin;

import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.repository.BuildingAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 *       （超管/居委会管理员/党组书记/业委会主任）；</li>
 *   <li>{@code assign} 进一步校验目标用户角色 ∈ {@link BuildingAssignmentQueryService#ASSIGNABLE_ROLES}
 *       且同租户，楼栋归属同租户。街道超管 tenantId=null 跨租户俯瞰，跳过租户校验。</li>
 * </ol>
 *
 * <p>幂等：{@link BuildingAssignmentRepository#assign} 在 repository 层实现
 * （已生效 noop / 已撤销复活 / 否则插入），本服务不需要重复编排。
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

    private final BuildingAssignmentRepository repository;
    private final UserContextHolder userContextHolder;

    @Transactional
    public void assign(Long userId, Long buildingId, String targetRoleKey) {
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
                    "targetRoleKey 必须为 GRID_OPERATOR/VOLUNTEER/OWNER_REPRESENTATIVE 之一，实际："
                            + targetRoleKey);
        }
        UserContext ctx = requireAssigner();
        Long tenantId = ctx.tenantId();

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
        // 写入实际归属租户：街道超管 tenantId=null 时，按目标用户的租户落库——
        // 这里取 ctx.tenantId() 即可（街道超管的 buildingExistsInTenant 已放行），
        // 但 sys_user_building.tenant_id 不可为 null（NOT NULL）。
        // 故 tenantId=null 时退化为「禁止跨租户分配」——超管也须切到具体租户操作。
        // 为简单起见：tenantId=null 时不允许分配，前端在街道超管登录时不显示该入口。
        Long writeTenantId = tenantId;
        if (writeTenantId == null) {
            throw new BuildingAssignmentApplicationException(
                    BuildingAssignmentApplicationException.Reason.BUILDING_NOT_IN_SCOPE,
                    "跨租户视图下不允许分配楼栋；请切换到具体小区身份后再操作");
        }
        repository.assign(userId, buildingId, writeTenantId, ctx.userId());
        log.info("BuildingAssignment assign user={} building={} by={} tenant={}",
                userId, buildingId, ctx.userId(), writeTenantId);
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
}
