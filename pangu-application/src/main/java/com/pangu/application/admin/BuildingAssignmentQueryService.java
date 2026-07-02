package com.pangu.application.admin;

import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.user.AssignableUser;
import com.pangu.domain.model.user.BuildingAssignment;
import com.pangu.domain.model.user.BuildingOccupancy;
import com.pangu.domain.repository.BuildingAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 楼栋责任田分配读侧服务（M4）。
 *
 * <p>对外提供：用户列表（按角色筛） / 模糊搜索（含合规快照） / 楼栋列表 /
 * 用户已分配楼栋 / 楼栋占用快照。租户隔离由 {@link UserContextHolder#current()}
 * 注入；街道超管 tenantId=null 时跨租户俯瞰。
 *
 * <p>读侧不做白名单校验（任何已登录用户都可查阅，由前端按角色门控菜单），
 * 写侧才在 {@link BuildingAssignmentApplicationService} 收口。
 *
 * <p>合规规则（与 {@link BuildingAssignmentApplicationService} 的 assign 写侧
 * 校验对齐）：
 * <ul>
 *   <li>{@code NOT_VERIFIED}：t_account.real_name_verified != 1（未实名）；</li>
 *   <li>{@code BUILDING_LIMIT_REACHED}：当前已生效楼栋数 ≥
 *       {@link BuildingAssignmentApplicationService#MAX_BUILDINGS_PER_USER}。</li>
 * </ul>
 * 账号状态正常与同租户已在搜索 SQL 过滤（{@code u.status='0'} + tenantId），
 * 因此 issues 只暴露 NOT_VERIFIED / BUILDING_LIMIT_REACHED 两类。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BuildingAssignmentQueryService {

    /** 可分配的执行角色白名单。GRID_MEMBER 走网格节点范围，其余 OWNER_GROUP 角色走用户责任田。 */
    public static final Set<String> ASSIGNABLE_ROLES =
            Set.of("GRID_MEMBER", "VOLUNTEER", "OWNER_REPRESENTATIVE");

    /** 搜索返回上限——防 N+1 把整张表拉回。 */
    public static final int SEARCH_LIMIT = 50;

    /** 合规原因常量——与前端 i18n 对齐。 */
    public static final String ISSUE_NOT_VERIFIED = "NOT_VERIFIED";
    public static final String ISSUE_BUILDING_LIMIT_REACHED = "BUILDING_LIMIT_REACHED";

    private final BuildingAssignmentRepository repository;
    private final UserContextHolder userContextHolder;

    @Transactional(readOnly = true)
    public List<AssignableUser> listAssignableUsers(String roleKey) {
        if (roleKey == null || roleKey.isBlank()) {
            throw new BuildingAssignmentApplicationException(
                    BuildingAssignmentApplicationException.Reason.PARAM_INVALID,
                    "roleKey 必填");
        }
        if (!ASSIGNABLE_ROLES.contains(roleKey)) {
            throw new BuildingAssignmentApplicationException(
                    BuildingAssignmentApplicationException.Reason.PARAM_INVALID,
                    "roleKey 必须为 GRID_MEMBER/VOLUNTEER/OWNER_REPRESENTATIVE 之一，实际：" + roleKey);
        }
        UserContext ctx = userContextHolder.current();
        return repository.listUsersByRole(roleKey, ctx == null ? null : ctx.tenantId()).stream()
                .map(this::withCompliance)
                .toList();
    }

    /**
     * 模糊搜索可分配用户。三字段 OR（姓名 / 手机号 / 手机尾号），结果含合规快照。
     */
    @Transactional(readOnly = true)
    public List<AssignableUser> searchAssignableUsers(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new BuildingAssignmentApplicationException(
                    BuildingAssignmentApplicationException.Reason.PARAM_INVALID,
                    "keyword 必填");
        }
        UserContext ctx = userContextHolder.current();
        return repository.searchAssignableUsers(
                keyword.trim(),
                ctx == null ? null : ctx.tenantId(),
                SEARCH_LIMIT).stream()
                .map(this::withCompliance)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Long> listBuildings() {
        UserContext ctx = userContextHolder.current();
        return repository.listBuildings(ctx == null ? null : ctx.tenantId());
    }

    @Transactional(readOnly = true)
    public List<BuildingAssignment> listUserBuildings(Long userId) {
        if (userId == null) {
            throw new BuildingAssignmentApplicationException(
                    BuildingAssignmentApplicationException.Reason.PARAM_INVALID,
                    "userId 必填");
        }
        return repository.listAssignmentsByUser(userId);
    }

    /**
     * 楼栋占用快照（status=1 的所有占用者，含不同角色）。
     */
    @Transactional(readOnly = true)
    public BuildingOccupancy listBuildingOccupants(Long buildingId) {
        if (buildingId == null) {
            throw new BuildingAssignmentApplicationException(
                    BuildingAssignmentApplicationException.Reason.PARAM_INVALID,
                    "buildingId 必填");
        }
        UserContext ctx = userContextHolder.current();
        return new BuildingOccupancy(
                buildingId,
                repository.listOccupants(buildingId, ctx == null ? null : ctx.tenantId()));
    }

    /** 计算并附 complianceIssues。SQL 已过滤 status='0' 与同租户，因此只检 NOT_VERIFIED + 楼栋上限。 */
    private AssignableUser withCompliance(AssignableUser u) {
        List<String> issues = new ArrayList<>();
        if (u.realNameVerified() != 1) {
            issues.add(ISSUE_NOT_VERIFIED);
        }
        if (u.buildingCount() >= BuildingAssignmentApplicationService.MAX_BUILDINGS_PER_USER) {
            issues.add(ISSUE_BUILDING_LIMIT_REACHED);
        }
        if (issues.isEmpty()) {
            return u;
        }
        return new AssignableUser(
                u.userId(), u.nickName(), u.roleKey(), u.phone(),
                u.realNameVerified(), u.buildingCount(), issues);
    }
}
