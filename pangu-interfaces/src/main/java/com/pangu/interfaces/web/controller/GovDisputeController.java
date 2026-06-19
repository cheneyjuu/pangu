package com.pangu.interfaces.web.controller;

import com.pangu.application.dispute.DisputeApplicationException;
import com.pangu.application.dispute.DisputeApplicationService;
import com.pangu.application.dispute.command.DecideCommand;
import com.pangu.application.dispute.command.StartReviewCommand;
import com.pangu.domain.model.dispute.Decision;
import com.pangu.domain.model.dispute.Dispute;
import com.pangu.interfaces.security.SecurityUtils;
import com.pangu.interfaces.web.controller.dto.dispute.DecideRequest;
import com.pangu.interfaces.web.controller.dto.dispute.DisputeResponse;
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

/**
 * 业主异议 G 端仲裁工作台（M3-1）。
 *
 * <ul>
 *   <li>{@code GET  /api/v1/gov/disputes}                    —— 辖区 dashboard（按 level/status 过滤）；</li>
 *   <li>{@code POST /api/v1/gov/disputes/{id}/review/start}  —— 启动审查 RAISED → UNDER_REVIEW；</li>
 *   <li>{@code POST /api/v1/gov/disputes/{id}/review/decide} —— 出具决议。</li>
 * </ul>
 *
 * <p>鉴权：
 * <ul>
 *   <li>{@code list} —— {@code dispute:audit}（G / redline=0）；</li>
 *   <li>{@code startReview} / {@code decide} —— {@code dispute:decide}（G / redline=1，
 *       trigger 6 要求挂载角色 fixed_data_scope NOT NULL）。</li>
 * </ul>
 *
 * <p>M3-1 不做按 level 分配辖区（街道办只看 L2 / 区政府只看 L3 等）；
 * 当前 audit 持有者按 fixed_data_scope 看辖区所有 level，
 * M3-2 / M3-3 引入 JurisdictionalScope 区分。
 */
@RestController
@RequestMapping("/api/v1/gov/disputes")
@RequiredArgsConstructor
public class GovDisputeController extends BaseController {

    private final DisputeApplicationService disputeApplicationService;

    @GetMapping
    @PreAuthorize("hasAuthority('dispute:audit')")
    public Result<List<DisputeResponse>> list(
            @RequestParam(value = "level", required = false) Integer level,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @RequestParam(value = "offset", defaultValue = "0") int offset) {
        List<Dispute> list = disputeApplicationService.listJurisdiction(
                requireTenantId(), level, status, limit, offset);
        return success(list.stream().map(DisputeResponse::from).toList());
    }

    @PostMapping("/{id}/review/start")
    @PreAuthorize("hasAuthority('dispute:decide')")
    public Result<DisputeResponse> startReview(@PathVariable("id") Long disputeId) {
        Dispute d = disputeApplicationService.startReview(new StartReviewCommand(disputeId));
        return success("审查已启动", DisputeResponse.from(d));
    }

    @PostMapping("/{id}/review/decide")
    @PreAuthorize("hasAuthority('dispute:decide')")
    public Result<Long> decide(@PathVariable("id") Long disputeId,
                                @Valid @RequestBody DecideRequest request) {
        Decision decision = disputeApplicationService.decide(new DecideCommand(
                disputeId, request.decisionKind(), requireUserId(),
                request.content(), request.docUrl()));
        return success("决议已出具", decision.decisionId());
    }

    private Long requireUserId() {
        Long userId = SecurityUtils.getUserId();
        if (userId == null) {
            throw new DisputeApplicationException(
                    DisputeApplicationException.Reason.DISPUTE_NOT_OWNER,
                    "未识别到行政机关用户（sys_user.user_id），禁止访问该操作");
        }
        return userId;
    }

    private Long requireTenantId() {
        Long tenantId = SecurityUtils.getTenantId();
        if (tenantId == null) {
            throw new DisputeApplicationException(
                    DisputeApplicationException.Reason.DISPUTE_NOT_FOUND,
                    "未识别到租户上下文，禁止访问该操作");
        }
        return tenantId;
    }
}
