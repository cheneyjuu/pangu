package com.pangu.application.admin;

import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.user.AssignableUser;
import com.pangu.domain.model.user.BuildingAssignment;
import com.pangu.domain.repository.BuildingAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * 楼栋责任田分配读侧服务（M4）。
 *
 * <p>对外提供三条只读 use case：用户列表（按角色筛）、楼栋列表、用户已分配楼栋。
 * 租户隔离由 {@link UserContextHolder#current()} 注入；街道超管 tenantId=null
 * 时跨租户俯瞰，与 {@link BuildingAssignmentRepository} 端口语义一致。
 *
 * <p>读侧不做白名单校验（任何已登录用户都可查阅，由前端按角色门控菜单），
 * 写侧才在 {@link BuildingAssignmentApplicationService} 收口。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BuildingAssignmentQueryService {

    /** 可分配的执行角色白名单——这三类是 OWNER_GROUP 数据范围，靠 sys_user_building 反查。 */
    public static final Set<String> ASSIGNABLE_ROLES =
            Set.of("GRID_OPERATOR", "VOLUNTEER", "OWNER_REPRESENTATIVE");

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
                    "roleKey 必须为 GRID_OPERATOR/VOLUNTEER/OWNER_REPRESENTATIVE 之一，实际：" + roleKey);
        }
        UserContext ctx = userContextHolder.current();
        return repository.listUsersByRole(roleKey, ctx == null ? null : ctx.tenantId());
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
}
