package com.pangu.interfaces.web.controller;

import com.pangu.application.admin.WorkIdentityApplicationService;
import com.pangu.application.admin.WorkIdentityQueryService;
import com.pangu.application.admin.command.CreateWorkIdentityCommand;
import com.pangu.domain.model.user.WorkIdentityAccount;
import com.pangu.domain.model.user.WorkIdentityDeptOption;
import com.pangu.domain.model.user.WorkIdentityShadow;
import com.pangu.interfaces.web.controller.dto.admin.CreateWorkIdentityRequest;
import com.pangu.interfaces.web.controller.dto.admin.BuildingResponse;
import com.pangu.interfaces.web.controller.dto.admin.UpdateGridBuildingScopeRequest;
import com.pangu.interfaces.web.controller.dto.admin.WorkIdentityAccountResponse;
import com.pangu.interfaces.web.controller.dto.admin.WorkIdentityDeptOptionResponse;
import com.pangu.interfaces.web.controller.dto.admin.WorkIdentityShadowResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
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

    @GetMapping("/accounts/search")
    @PreAuthorize("hasAuthority('admin:user:assign-role')")
    public Result<List<WorkIdentityAccountResponse>> searchAccounts(@RequestParam("keyword") String keyword) {
        List<WorkIdentityAccount> accounts = queryService.searchAccounts(keyword);
        return success(accounts.stream().map(WorkIdentityAccountResponse::from).toList());
    }

    @GetMapping("/accounts/{accountId}")
    @PreAuthorize("hasAuthority('admin:user:assign-role')")
    public Result<WorkIdentityAccountResponse> getAccount(@PathVariable("accountId") Long accountId) {
        return success(WorkIdentityAccountResponse.from(queryService.getAccount(accountId)));
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
        List<Long> ids = queryService.listBuildingOptions(deptId);
        return success(ids.stream().map(BuildingResponse::of).toList());
    }

    @PostMapping("/depts/{communityDeptId}/grid-nodes")
    @PreAuthorize("hasAuthority('admin:user:assign-role')")
    public Result<List<WorkIdentityDeptOptionResponse>> ensureGridNodes(
            @PathVariable("communityDeptId") Long communityDeptId) {
        List<WorkIdentityDeptOption> options = applicationService.ensureGridNodes(communityDeptId);
        return success("网格节点已生成", options.stream().map(WorkIdentityDeptOptionResponse::from).toList());
    }

    @GetMapping("/depts/{deptId}/building-scope")
    @PreAuthorize("hasAuthority('admin:user:assign-role')")
    public Result<List<BuildingResponse>> listGridBuildingScope(@PathVariable("deptId") Long deptId) {
        List<Long> ids = queryService.listGridDeptBuildingScope(deptId);
        return success(ids.stream().map(BuildingResponse::of).toList());
    }

    @PutMapping("/depts/{deptId}/building-scope")
    @PreAuthorize("hasAuthority('admin:user:assign-role')")
    public Result<List<BuildingResponse>> updateGridBuildingScope(
            @PathVariable("deptId") Long deptId,
            @Valid @RequestBody UpdateGridBuildingScopeRequest request) {
        List<Long> ids = applicationService.replaceGridDeptBuildingScope(deptId, request.buildingIds());
        return success("网格楼栋范围已更新", ids.stream().map(BuildingResponse::of).toList());
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
