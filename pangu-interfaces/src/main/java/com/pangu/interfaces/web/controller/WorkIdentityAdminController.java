package com.pangu.interfaces.web.controller;

import com.pangu.application.admin.WorkIdentityApplicationService;
import com.pangu.application.admin.WorkIdentityQueryService;
import com.pangu.application.admin.command.CreateWorkIdentityCommand;
import com.pangu.domain.model.user.WorkIdentityAccount;
import com.pangu.domain.model.user.WorkIdentityBuildingScope;
import com.pangu.domain.model.user.WorkIdentityDeptOption;
import com.pangu.domain.model.user.WorkIdentityShadow;
import com.pangu.interfaces.web.controller.dto.admin.AssignGridNodesRequest;
import com.pangu.interfaces.web.controller.dto.admin.CreateWorkIdentityRequest;
import com.pangu.interfaces.web.controller.dto.admin.CreateGridNodeRequest;
import com.pangu.interfaces.web.controller.dto.admin.CreateWorkIdentityAccountRequest;
import com.pangu.interfaces.web.controller.dto.admin.BuildingResponse;
import com.pangu.interfaces.web.controller.dto.admin.UpdateGridNodeRequest;
import com.pangu.interfaces.web.controller.dto.admin.UpdateGridBuildingScopeRequest;
import com.pangu.interfaces.web.controller.dto.admin.WorkIdentityAccountResponse;
import com.pangu.interfaces.web.controller.dto.admin.WorkIdentityDeptOptionResponse;
import com.pangu.interfaces.web.controller.dto.admin.WorkIdentityShadowResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端工作身份与授权入口。
 */
@RestController
@RequestMapping("/api/v1/admin/work-identities")
@RequiredArgsConstructor
public class WorkIdentityAdminController extends BaseController {

    private final WorkIdentityQueryService queryService;
    private final WorkIdentityApplicationService applicationService;

    @GetMapping("/accounts")
    @PreAuthorize("hasAuthority('admin:user:assign-role')")
    public Result<List<WorkIdentityAccountResponse>> listAccounts(
            @RequestParam(name = "roleKey", required = false) String roleKey) {
        List<WorkIdentityAccount> accounts = queryService.listAccounts(roleKey);
        return success(accounts.stream().map(WorkIdentityAccountResponse::from).toList());
    }

    @GetMapping("/accounts/search")
    @PreAuthorize("hasAuthority('admin:user:assign-role')")
    public Result<List<WorkIdentityAccountResponse>> searchAccounts(
            @RequestParam("keyword") String keyword,
            @RequestParam(name = "roleKey", required = false) String roleKey) {
        List<WorkIdentityAccount> accounts = queryService.searchAccounts(keyword, roleKey);
        return success(accounts.stream().map(WorkIdentityAccountResponse::from).toList());
    }

    @GetMapping("/accounts/{accountId}")
    @PreAuthorize("hasAuthority('admin:user:assign-role')")
    public Result<WorkIdentityAccountResponse> getAccount(@PathVariable("accountId") Long accountId) {
        return success(WorkIdentityAccountResponse.from(queryService.getAccount(accountId)));
    }

    @PostMapping("/accounts")
    @PreAuthorize("hasAuthority('admin:user:assign-role')")
    public ResponseEntity<Result<WorkIdentityAccountResponse>> createAccount(
            @Valid @RequestBody CreateWorkIdentityAccountRequest request) {
        WorkIdentityAccount created = applicationService.createAccountWithIdentity(
                request.phone(),
                request.realName(),
                new CreateWorkIdentityCommand(
                        null,
                        request.deptId(),
                        request.roleKey(),
                        request.nickName(),
                        request.buildingIds(),
                        request.isForceBuildingTransfer()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(success("用户账号已创建", WorkIdentityAccountResponse.from(created)));
    }

    @GetMapping("/dept-options")
    @PreAuthorize("hasAuthority('admin:user:assign-role')")
    public Result<List<WorkIdentityDeptOptionResponse>> listDeptOptions(
            @RequestParam("roleKey") String roleKey) {
        List<WorkIdentityDeptOption> options = queryService.listDeptOptions(roleKey);
        return success(options.stream().map(WorkIdentityDeptOptionResponse::from).toList());
    }

    @GetMapping("/building-options")
    @PreAuthorize("hasAuthority('admin:user:assign-role')")
    public Result<List<BuildingResponse>> listBuildingOptions(@RequestParam("deptId") Long deptId) {
        List<WorkIdentityBuildingScope> scopes = queryService.listBuildingOptions(deptId);
        return success(scopes.stream().map(BuildingResponse::from).toList());
    }

    @PostMapping("/grid-nodes")
    @PreAuthorize("hasAuthority('admin:user:assign-role')")
    public Result<WorkIdentityDeptOptionResponse> createGridNode(
            @Valid @RequestBody CreateGridNodeRequest request) {
        WorkIdentityDeptOption option = applicationService.createGridNode(request.deptName());
        return success("网格节点已创建", WorkIdentityDeptOptionResponse.from(option));
    }

    @PostMapping("/depts/{communityDeptId}/grid-nodes")
    @PreAuthorize("hasAuthority('admin:user:assign-role')")
    public Result<List<WorkIdentityDeptOptionResponse>> ensureGridNodes(
            @PathVariable("communityDeptId") Long communityDeptId,
            @Valid @RequestBody(required = false) CreateGridNodeRequest request) {
        List<WorkIdentityDeptOption> options = request == null
                ? applicationService.ensureGridNodes(communityDeptId)
                : List.of(applicationService.createGridNode(communityDeptId, request.deptName()));
        return success("网格节点已生成", options.stream().map(WorkIdentityDeptOptionResponse::from).toList());
    }

    @GetMapping("/depts/{communityDeptId}/grid-nodes")
    @PreAuthorize("hasAuthority('admin:user:assign-role')")
    public Result<List<WorkIdentityDeptOptionResponse>> listGridNodes(
            @PathVariable("communityDeptId") Long communityDeptId) {
        List<WorkIdentityDeptOption> options = queryService.listGridNodes(communityDeptId);
        return success(options.stream().map(WorkIdentityDeptOptionResponse::from).toList());
    }

    @PatchMapping("/depts/{deptId}/grid-node")
    @PreAuthorize("hasAuthority('admin:user:assign-role')")
    public Result<WorkIdentityDeptOptionResponse> updateGridNode(
            @PathVariable("deptId") Long deptId,
            @Valid @RequestBody UpdateGridNodeRequest request) {
        WorkIdentityDeptOption updated = applicationService.updateGridNode(deptId, request.deptName());
        return success("网格节点已更新", WorkIdentityDeptOptionResponse.from(updated));
    }

    @DeleteMapping("/depts/{deptId}/grid-node")
    @PreAuthorize("hasAuthority('admin:user:assign-role')")
    public Result<Void> deleteGridNode(@PathVariable("deptId") Long deptId) {
        applicationService.deleteGridNode(deptId);
        return success("网格节点已删除", null);
    }

    @GetMapping("/users/{userId}/grid-nodes")
    @PreAuthorize("hasAuthority('admin:user:assign-role')")
    public Result<List<WorkIdentityDeptOptionResponse>> listAssignedGridNodes(
            @PathVariable("userId") Long userId) {
        List<WorkIdentityDeptOption> options = queryService.listAssignedGridNodes(userId);
        return success(options.stream().map(WorkIdentityDeptOptionResponse::from).toList());
    }

    @PutMapping("/users/{userId}/grid-nodes")
    @PreAuthorize("hasAuthority('admin:user:assign-role')")
    public Result<List<WorkIdentityDeptOptionResponse>> replaceAssignedGridNodes(
            @PathVariable("userId") Long userId,
            @Valid @RequestBody AssignGridNodesRequest request) {
        List<WorkIdentityDeptOption> options = applicationService.replaceGridMemberGridNodes(
                userId, request.gridDeptIds());
        return success("网格员数据范围已更新", options.stream().map(WorkIdentityDeptOptionResponse::from).toList());
    }

    @GetMapping("/depts/{deptId}/building-scope")
    @PreAuthorize("hasAuthority('admin:user:assign-role')")
    public Result<List<BuildingResponse>> listGridBuildingScope(@PathVariable("deptId") Long deptId) {
        List<WorkIdentityBuildingScope> scopes = queryService.listGridDeptBuildingScope(deptId);
        return success(scopes.stream().map(BuildingResponse::from).toList());
    }

    @PutMapping("/depts/{deptId}/building-scope")
    @PreAuthorize("hasAuthority('admin:user:assign-role')")
    public Result<List<BuildingResponse>> updateGridBuildingScope(
            @PathVariable("deptId") Long deptId,
            @Valid @RequestBody UpdateGridBuildingScopeRequest request) {
        List<WorkIdentityBuildingScope> scopes = request.buildingScopes() != null && !request.buildingScopes().isEmpty()
                ? applicationService.replaceGridDeptBuildingScope(deptId, request.toDomainScopes(null))
                : applicationService.replaceGridDeptBuildingScopeLegacy(deptId, request.buildingIds());
        return success("网格楼栋范围已更新", scopes.stream().map(BuildingResponse::from).toList());
    }

    @PostMapping("/accounts/{accountId}/shadows")
    @PreAuthorize("hasAuthority('admin:user:assign-role')")
    public ResponseEntity<Result<WorkIdentityShadowResponse>> createShadow(
            @PathVariable("accountId") Long accountId,
            @Valid @RequestBody CreateWorkIdentityRequest request) {
        WorkIdentityShadow created = applicationService.create(new CreateWorkIdentityCommand(
                accountId,
                request.deptId(),
                request.roleKey(),
                request.nickName(),
                request.buildingIds(),
                request.isForceBuildingTransfer()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(success("工作身份已创建", WorkIdentityShadowResponse.from(created)));
    }
}
