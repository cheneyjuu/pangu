package com.pangu.interfaces.web.controller;

import com.pangu.application.admin.BuildingAssignmentApplicationService;
import com.pangu.application.admin.BuildingAssignmentQueryService;
import com.pangu.domain.model.user.AssignableUser;
import com.pangu.domain.model.user.BuildingAssignment;
import com.pangu.domain.model.user.BuildingOccupancy;
import com.pangu.interfaces.web.controller.dto.admin.AssignBuildingRequest;
import com.pangu.interfaces.web.controller.dto.admin.AssignableUserResponse;
import com.pangu.interfaces.web.controller.dto.admin.BuildingAssignmentResponse;
import com.pangu.interfaces.web.controller.dto.admin.BuildingOccupancyResponse;
import com.pangu.interfaces.web.controller.dto.admin.BuildingResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 楼栋责任田分配 RESTful 入口（M4）。
 *
 * <p>API 路径：
 * <ul>
 *   <li>{@code GET    /api/v1/admin/building-assignments/users?roleKey=...}        —— 可分配用户列表（按角色）；</li>
 *   <li>{@code GET    /api/v1/admin/building-assignments/search?keyword=...}      —— 模糊搜索（姓名/手机号/手机尾号 OR）；</li>
 *   <li>{@code GET    /api/v1/admin/building-assignments/buildings}               —— 可分配楼栋列表；</li>
 *   <li>{@code GET    /api/v1/admin/building-assignments/buildings/{id}/occupants}—— 楼栋当前占用快照；</li>
 *   <li>{@code GET    /api/v1/admin/building-assignments/users/{userId}/buildings}—— 某用户已分配楼栋；</li>
 *   <li>{@code POST   /api/v1/admin/building-assignments/users/{userId}/buildings}—— 分配楼栋（body 含 force=true 时转移）；</li>
 *   <li>{@code DELETE /api/v1/admin/building-assignments/users/{userId}/buildings/{buildingId}} —— 撤销分配。</li>
 * </ul>
 *
 * <p>访问模型——不引入平台权限 key。{@code @PreAuthorize("isAuthenticated()")} 仅
 * 阻挡未登录访问；分配者白名单（GOV_SUPER_ADMIN / COMMUNITY_ADMIN / PARTY_SECRETARY
 * / COMMITTEE_DIRECTOR）由 {@link BuildingAssignmentApplicationService#requireAssigner()}
 * 在 service 层兜底。读侧任何已登录用户可查；前端按 roleKey 决定是否显示菜单。
 */
@RestController
@RequestMapping("/api/v1/admin/building-assignments")
@RequiredArgsConstructor
public class BuildingAssignmentController extends BaseController {

    private final BuildingAssignmentQueryService queryService;
    private final BuildingAssignmentApplicationService applicationService;

    @GetMapping("/users")
    @PreAuthorize("isAuthenticated()")
    public Result<List<AssignableUserResponse>> listUsers(@RequestParam("roleKey") String roleKey) {
        List<AssignableUser> users = queryService.listAssignableUsers(roleKey);
        return success(users.stream().map(AssignableUserResponse::from).toList());
    }

    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    public Result<List<AssignableUserResponse>> search(@RequestParam("keyword") String keyword) {
        List<AssignableUser> users = queryService.searchAssignableUsers(keyword);
        return success(users.stream().map(AssignableUserResponse::from).toList());
    }

    @GetMapping("/buildings")
    @PreAuthorize("isAuthenticated()")
    public Result<List<BuildingResponse>> listBuildings() {
        List<Long> ids = queryService.listBuildings();
        return success(ids.stream().map(BuildingResponse::of).toList());
    }

    @GetMapping("/buildings/{buildingId}/occupants")
    @PreAuthorize("isAuthenticated()")
    public Result<BuildingOccupancyResponse> listBuildingOccupants(@PathVariable("buildingId") Long buildingId) {
        BuildingOccupancy occupancy = queryService.listBuildingOccupants(buildingId);
        return success(BuildingOccupancyResponse.from(occupancy));
    }

    @GetMapping("/users/{userId}/buildings")
    @PreAuthorize("isAuthenticated()")
    public Result<List<BuildingAssignmentResponse>> listUserBuildings(@PathVariable("userId") Long userId) {
        List<BuildingAssignment> assignments = queryService.listUserBuildings(userId);
        return success(assignments.stream().map(BuildingAssignmentResponse::from).toList());
    }

    @PostMapping("/users/{userId}/buildings")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> assign(@PathVariable("userId") Long userId,
                                @Valid @RequestBody AssignBuildingRequest request) {
        applicationService.assign(userId, request.buildingId(), request.targetRoleKey(), request.isForce());
        return success(request.isForce() ? "楼栋已转移分配" : "楼栋已分配", null);
    }

    @DeleteMapping("/users/{userId}/buildings/{buildingId}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> revoke(@PathVariable("userId") Long userId,
                                @PathVariable("buildingId") Long buildingId) {
        applicationService.revoke(userId, buildingId);
        return success("楼栋分配已撤销", null);
    }
}
