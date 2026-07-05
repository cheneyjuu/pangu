package com.pangu.interfaces.web.controller;

import com.pangu.application.community.CommunitySettingsService;
import com.pangu.interfaces.web.controller.dto.community.CommunitySettingsResponse;
import com.pangu.interfaces.web.controller.dto.community.ReviewDenominatorRequest;
import com.pangu.interfaces.web.controller.dto.community.SubmitDenominatorReviewRequest;
import com.pangu.interfaces.web.controller.dto.community.UpdateCommunityAssetLedgerRequest;
import com.pangu.interfaces.web.controller.dto.community.UpdateCommunityOrganizationRequest;
import com.pangu.interfaces.web.controller.dto.community.UpdateCommunityRulesRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/community-settings")
@RequiredArgsConstructor
public class CommunitySettingsController extends BaseController {

    private final CommunitySettingsService service;

    @GetMapping
    @PreAuthorize("hasAuthority('community:settings:read')")
    public Result<CommunitySettingsResponse> getSettings(
            @RequestParam(name = "tenantId", required = false) Long tenantId) {
        return success(CommunitySettingsResponse.from(service.getSettings(tenantId)));
    }

    @PatchMapping("/organization")
    @PreAuthorize("hasAuthority('community:settings:official:write')")
    public Result<CommunitySettingsResponse> updateOrganization(
            @RequestParam(name = "tenantId", required = false) Long tenantId,
            @Valid @RequestBody UpdateCommunityOrganizationRequest request) {
        return success("组织备案已更新",
                CommunitySettingsResponse.from(service.updateOrganization(tenantId, request.toCommand())));
    }

    @PatchMapping("/asset-ledger")
    @PreAuthorize("hasAuthority('community:settings:asset:write')")
    public Result<CommunitySettingsResponse> updateAssetLedger(
            @RequestParam(name = "tenantId", required = false) Long tenantId,
            @Valid @RequestBody UpdateCommunityAssetLedgerRequest request) {
        return success("建筑名册已更新",
                CommunitySettingsResponse.from(service.updateAssetLedger(tenantId, request.toCommand())));
    }

    @PatchMapping("/rules")
    @PreAuthorize("hasAuthority('community:settings:policy:write')")
    public Result<CommunitySettingsResponse> updateRules(
            @RequestParam(name = "tenantId", required = false) Long tenantId,
            @Valid @RequestBody UpdateCommunityRulesRequest request) {
        return success("自治规则已更新",
                CommunitySettingsResponse.from(service.updateRules(tenantId, request.toCommand())));
    }

    @PostMapping("/denominator/recalculate")
    @PreAuthorize("hasAuthority('community:settings:denominator:reconcile')")
    public Result<CommunitySettingsResponse> recalculateDenominator(
            @RequestParam(name = "tenantId", required = false) Long tenantId) {
        return success("计票基数已重新校对",
                CommunitySettingsResponse.from(service.reconcileDenominator(tenantId)));
    }

    @PostMapping("/denominator/review-requests")
    @PreAuthorize("hasAuthority('community:settings:read')")
    public Result<CommunitySettingsResponse> submitDenominatorReview(
            @RequestParam(name = "tenantId", required = false) Long tenantId,
            @Valid @RequestBody SubmitDenominatorReviewRequest request) {
        return success("计票基数复核申请已提交",
                CommunitySettingsResponse.from(service.submitDenominatorReview(tenantId, request.toCommand())));
    }

    @PostMapping("/denominator/review-requests/{requestId}/review")
    @PreAuthorize("hasAuthority('community:settings:denominator:reconcile')")
    public Result<CommunitySettingsResponse> reviewDenominatorRequest(
            @PathVariable("requestId") Long requestId,
            @Valid @RequestBody ReviewDenominatorRequest request) {
        return success("计票基数复核已处理",
                CommunitySettingsResponse.from(service.reviewDenominatorRequest(requestId, request.toCommand())));
    }
}
