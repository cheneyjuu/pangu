package com.pangu.interfaces.web.controller;

import com.pangu.application.owner.PropertyBindingApplicationService;
import com.pangu.domain.common.Page;
import com.pangu.interfaces.security.SecurityUtils;
import com.pangu.interfaces.web.controller.dto.PageResponse;
import com.pangu.interfaces.web.controller.dto.owner.PropertyBindingClaimRequest;
import com.pangu.interfaces.web.controller.dto.owner.PropertyRosterImportRequest;
import com.pangu.interfaces.web.controller.dto.owner.ReviewPropertyClaimRequest;
import com.pangu.interfaces.web.exception.AppException;
import com.pangu.interfaces.web.exception.CommonErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PropertyBindingController extends BaseController {

    private final PropertyBindingApplicationService service;

    @GetMapping("/me/property-bindings/options")
    @PreAuthorize("isAuthenticated()")
    public Result<PropertyBindingApplicationService.RosterOptionsResponse> options(
            @RequestParam(name = "tenantId", required = false) Long tenantId) {
        requireCUser();
        return success(service.listRosterOptions(tenantId));
    }

    @PostMapping("/me/property-bindings/claims")
    @PreAuthorize("isAuthenticated()")
    public Result<PropertyBindingApplicationService.BindingSubmitResponse> submitClaim(
            @Valid @RequestBody PropertyBindingClaimRequest request) {
        Long uid = requireCUser();
        return success(service.submitClaim(SecurityUtils.getAccountId(), uid, toCommand(request)));
    }

    @GetMapping("/me/property-bindings/claims")
    @PreAuthorize("isAuthenticated()")
    public Result<List<PropertyBindingApplicationService.ClaimResponse>> myClaims() {
        return success(service.listMyClaims(requireCUser()));
    }

    @PostMapping("/admin/property-roster/import")
    @PreAuthorize("hasAuthority('property:roster:import')")
    public Result<PropertyBindingApplicationService.ImportResult> importRoster(
            @Valid @RequestBody PropertyRosterImportRequest request) {
        return success("名册导入完成", service.importRoster(
                SecurityUtils.getTenantId(), SecurityUtils.getUserId(), toCommand(request)));
    }

    @GetMapping("/admin/property-binding-claims")
    @PreAuthorize("hasAuthority('property:binding:review')")
    public Result<PageResponse<PropertyBindingApplicationService.ClaimResponse>> adminClaims(
            @RequestParam(name = "status", defaultValue = "PENDING_VERIFY") String status,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        Page<PropertyBindingApplicationService.ClaimResponse> result =
                service.pageAdminClaims(SecurityUtils.getTenantId(), status, page, size);
        return success(PageResponse.from(result, item -> item));
    }

    @PostMapping("/admin/property-binding-claims/{claimId}/approve")
    @PreAuthorize("hasAuthority('property:binding:review')")
    public Result<PropertyBindingApplicationService.ClaimResponse> approve(@PathVariable("claimId") Long claimId) {
        return success("审核通过，房产权属已更新", service.approve(claimId, SecurityUtils.getUserId()));
    }

    @PostMapping("/admin/property-binding-claims/{claimId}/reject")
    @PreAuthorize("hasAuthority('property:binding:review')")
    public Result<PropertyBindingApplicationService.ClaimResponse> reject(
            @PathVariable("claimId") Long claimId,
            @Valid @RequestBody ReviewPropertyClaimRequest request) {
        return success("已驳回房产绑定申请", service.reject(
                claimId, SecurityUtils.getUserId(), request.reasonCode(), request.reason()));
    }

    private PropertyBindingApplicationService.PropertyRosterImportCommand toCommand(
            PropertyRosterImportRequest request) {
        List<PropertyBindingApplicationService.PropertyRosterImportCommand.Row> rows = request.rows().stream()
                .map(row -> new PropertyBindingApplicationService.PropertyRosterImportCommand.Row(
                        row.tenantId(),
                        row.buildingName(),
                        row.unitName(),
                        row.roomName(),
                        row.buildArea(),
                        row.registeredOwnerName(),
                        row.registeredOwnerPhone()))
                .toList();
        return new PropertyBindingApplicationService.PropertyRosterImportCommand(request.tenantId(), rows);
    }

    private PropertyBindingApplicationService.PropertyBindingClaimCommand toCommand(
            PropertyBindingClaimRequest request) {
        return new PropertyBindingApplicationService.PropertyBindingClaimCommand(
                request.rosterId(),
                request.jointOwnership(),
                request.votingDelegate(),
                request.proofType(),
                request.proofImagesBase64());
    }

    private Long requireCUser() {
        Long uid = SecurityUtils.getUid();
        if (uid == null) {
            throw new AppException(CommonErrorCode.FORBIDDEN, "仅 C 端业主身份可访问房产绑定");
        }
        return uid;
    }
}
