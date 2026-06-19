package com.pangu.interfaces.web.controller;

import com.pangu.application.waiver.WaiverApplicationException;
import com.pangu.application.waiver.WaiverApplicationService;
import com.pangu.application.waiver.command.CommitteeReviewCommand;
import com.pangu.application.waiver.command.RevokeWaiverCommand;
import com.pangu.application.waiver.command.StreetReviewCommand;
import com.pangu.application.waiver.command.SubmitDraftCommand;
import com.pangu.domain.model.waiver.PartyRatioWaiver;
import com.pangu.interfaces.security.SecurityUtils;
import com.pangu.interfaces.web.controller.dto.waiver.ReviewWaiverRequest;
import com.pangu.interfaces.web.controller.dto.waiver.SubmitWaiverRequest;
import com.pangu.interfaces.web.controller.dto.waiver.WaiverResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 党员比例放宽 (Waiver) 申请的 RESTful 入口（M1 RBAC 重构后版本）。
 *
 * <p>API 路径：
 * <ul>
 *   <li>{@code POST /api/v1/elections/{subjectId}/waivers} —— 居委会发起；</li>
 *   <li>{@code POST /api/v1/waivers/{waiverId}/committee-review} —— 居委会初审；</li>
 *   <li>{@code POST /api/v1/waivers/{waiverId}/street-review} —— 街道办终审；</li>
 *   <li>{@code POST /api/v1/waivers/{waiverId}/revoke} —— 人工撤销；</li>
 *   <li>{@code GET  /api/v1/waivers/{waiverId}} —— 获取详情。</li>
 * </ul>
 *
 * <p>鉴权策略（M1 重构后）：
 * 全部走 Spring Security {@code @PreAuthorize("hasAuthority('<permission_key>')")}：
 * <ul>
 *   <li>{@code waiver:submit}            —— 居委会发起；</li>
 *   <li>{@code waiver:approve:committee} —— 居委会初审；</li>
 *   <li>{@code waiver:approve:street}    —— 街道办终审；</li>
 *   <li>{@code waiver:revoke}            —— 人工撤销；</li>
 *   <li>{@code waiver:read}              —— GET 详情。</li>
 * </ul>
 * 权限来自 {@code sys_role_permission}；JWT 不嵌权限，由 {@code UserContextLoader} 实时反查。
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class WaiverController extends BaseController {

    private final WaiverApplicationService waiverApplicationService;

    /** 居委会发起 waiver 草稿并直接进入初审待审。 */
    @PostMapping("/elections/{subjectId}/waivers")
    @PreAuthorize("hasAuthority('waiver:submit')")
    public ResponseEntity<Result<WaiverResponse>> submitWaiver(
            @PathVariable("subjectId") Long subjectId,
            @Valid @RequestBody SubmitWaiverRequest request) {
        SubmitDraftCommand cmd = new SubmitDraftCommand(
                subjectId,
                requireTenantId(),
                requireUserId(),
                request.requestedRatio(),
                request.reasonText(),
                request.reasonEvidenceKeys());
        PartyRatioWaiver waiver = waiverApplicationService.submitDraft(cmd);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(success("申请已提交，等待居委会初审", WaiverResponse.from(waiver)));
    }

    /** 居委会初审。 */
    @PostMapping("/waivers/{waiverId}/committee-review")
    @PreAuthorize("hasAuthority('waiver:approve:committee')")
    public Result<WaiverResponse> reviewByCommittee(
            @PathVariable("waiverId") Long waiverId,
            @Valid @RequestBody ReviewWaiverRequest request) {
        CommitteeReviewCommand cmd = new CommitteeReviewCommand(
                waiverId,
                requireUserId(),
                Boolean.TRUE.equals(request.approve()),
                request.opinion());
        PartyRatioWaiver waiver = waiverApplicationService.reviewByCommittee(cmd);
        return success(Boolean.TRUE.equals(request.approve()) ? "初审通过，已转街道办终审" : "初审已驳回",
                WaiverResponse.from(waiver));
    }

    /** 街道办终审。通过 → APPROVED + 锁 payloadHash + outbox 上链 stub；驳回 → REJECTED。 */
    @PostMapping("/waivers/{waiverId}/street-review")
    @PreAuthorize("hasAuthority('waiver:approve:street')")
    public Result<WaiverResponse> reviewByStreet(
            @PathVariable("waiverId") Long waiverId,
            @Valid @RequestBody ReviewWaiverRequest request) {
        StreetReviewCommand cmd = new StreetReviewCommand(
                waiverId,
                requireUserId(),
                Boolean.TRUE.equals(request.approve()),
                request.opinion());
        PartyRatioWaiver waiver = waiverApplicationService.reviewByStreet(cmd);
        return success(Boolean.TRUE.equals(request.approve()) ? "终审通过，已锁定证据并提交存证" : "终审已驳回",
                WaiverResponse.from(waiver));
    }

    /** 人工撤销。允许 DRAFT / PENDING_* / APPROVED 阶段调用。 */
    @PostMapping("/waivers/{waiverId}/revoke")
    @PreAuthorize("hasAuthority('waiver:revoke')")
    public Result<WaiverResponse> revoke(@PathVariable("waiverId") Long waiverId) {
        RevokeWaiverCommand cmd = new RevokeWaiverCommand(waiverId, requireUserId());
        PartyRatioWaiver waiver = waiverApplicationService.revoke(cmd);
        return success("Waiver 已撤销", WaiverResponse.from(waiver));
    }

    /** GET 详情。 */
    @GetMapping("/waivers/{waiverId}")
    @PreAuthorize("hasAuthority('waiver:read')")
    public Result<WaiverResponse> findById(@PathVariable("waiverId") Long waiverId) {
        return waiverApplicationService.findById(waiverId)
                .map(w -> success(WaiverResponse.from(w)))
                .orElseThrow(() -> new WaiverApplicationException(
                        WaiverApplicationException.Reason.WAIVER_NOT_FOUND,
                        "Waiver 不存在 waiverId=" + waiverId));
    }

    private Long requireUserId() {
        Long userId = SecurityUtils.getUserId();
        if (userId == null) {
            throw new WaiverApplicationException(
                    WaiverApplicationException.Reason.INITIATOR_NOT_COMMITTEE,
                    "未识别到登录用户，禁止访问该操作");
        }
        return userId;
    }

    private Long requireTenantId() {
        Long tenantId = SecurityUtils.getTenantId();
        if (tenantId == null) {
            throw new WaiverApplicationException(
                    WaiverApplicationException.Reason.TENANT_MISMATCH,
                    "未识别到租户上下文，禁止访问该操作");
        }
        return tenantId;
    }
}
