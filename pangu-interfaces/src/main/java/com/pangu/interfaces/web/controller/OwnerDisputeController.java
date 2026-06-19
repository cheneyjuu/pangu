package com.pangu.interfaces.web.controller;

import com.pangu.application.dispute.DisputeApplicationException;
import com.pangu.application.dispute.DisputeApplicationService;
import com.pangu.application.dispute.command.AddEvidenceCommand;
import com.pangu.application.dispute.command.ConcludeCommand;
import com.pangu.application.dispute.command.EscalateCommand;
import com.pangu.application.dispute.command.GotoLitigationCommand;
import com.pangu.application.dispute.command.OpenCommand;
import com.pangu.application.dispute.command.WithdrawCommand;
import com.pangu.domain.model.dispute.Dispute;
import com.pangu.domain.model.dispute.DisputeEvidence;
import com.pangu.interfaces.security.SecurityUtils;
import com.pangu.interfaces.web.controller.dto.dispute.AddEvidenceRequest;
import com.pangu.interfaces.web.controller.dto.dispute.DisputeResponse;
import com.pangu.interfaces.web.controller.dto.dispute.OpenDisputeRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 业主异议 C 端入口（M3-1）。
 *
 * <p>API 路径：
 * <ul>
 *   <li>{@code POST /api/v1/disputes}                        —— 提起异议；</li>
 *   <li>{@code GET  /api/v1/disputes/{id}}                   —— 查单条（service 校验 ownerId 一致，越权返 NOT_FOUND）；</li>
 *   <li>{@code GET  /api/v1/disputes}                        —— "我的异议"列表；</li>
 *   <li>{@code POST /api/v1/disputes/{id}/escalate}          —— 升级到下一级；</li>
 *   <li>{@code POST /api/v1/disputes/{id}/litigation}        —— 走 Level 5 行政诉讼；</li>
 *   <li>{@code POST /api/v1/disputes/{id}/withdraw}          —— 撤回；</li>
 *   <li>{@code POST /api/v1/disputes/{id}/conclude}          —— 接受最终决议；</li>
 *   <li>{@code POST /api/v1/disputes/{id}/evidence}          —— 补充证据。</li>
 * </ul>
 *
 * <p>鉴权策略：C_USER 不进 sys_permission 链路（与 V2.7 disclosure:view:owner 同模式），
 * 全部使用 {@code @PreAuthorize("isAuthenticated()")} + service 层 {@link SecurityUtils#getUid()}
 * + tenant 一致性校验。
 */
@RestController
@RequestMapping("/api/v1/disputes")
@RequiredArgsConstructor
public class OwnerDisputeController extends BaseController {

    private final DisputeApplicationService disputeApplicationService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<DisputeResponse>> open(@Valid @RequestBody OpenDisputeRequest request) {
        OpenCommand cmd = new OpenCommand(
                requireTenantId(), requireUid(), request.disputeKind(),
                request.relatedEntityType(), request.relatedEntityId(),
                request.businessPayloadJson());
        Dispute d = disputeApplicationService.open(cmd);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(success("异议已提起", DisputeResponse.from(d)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<DisputeResponse> getOne(@PathVariable("id") Long disputeId) {
        Dispute d = disputeApplicationService.getDispute(disputeId, requireUid());
        return success(DisputeResponse.from(d));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Result<List<DisputeResponse>> listMine(
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            @RequestParam(value = "offset", defaultValue = "0") int offset) {
        List<Dispute> list = disputeApplicationService.listOwnerDisputes(
                requireTenantId(), requireUid(), limit, offset);
        return success(list.stream().map(DisputeResponse::from).toList());
    }

    @PostMapping("/{id}/escalate")
    @PreAuthorize("isAuthenticated()")
    public Result<DisputeResponse> escalate(@PathVariable("id") Long disputeId) {
        Dispute d = disputeApplicationService.escalate(new EscalateCommand(disputeId, requireUid()));
        return success("已升级到下一级", DisputeResponse.from(d));
    }

    @PostMapping("/{id}/litigation")
    @PreAuthorize("isAuthenticated()")
    public Result<DisputeResponse> gotoLitigation(@PathVariable("id") Long disputeId) {
        Dispute d = disputeApplicationService.gotoLitigation(
                new GotoLitigationCommand(disputeId, requireUid()));
        return success("已转入行政诉讼", DisputeResponse.from(d));
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize("isAuthenticated()")
    public Result<DisputeResponse> withdraw(@PathVariable("id") Long disputeId) {
        Dispute d = disputeApplicationService.withdraw(new WithdrawCommand(disputeId, requireUid()));
        return success("异议已撤回", DisputeResponse.from(d));
    }

    @PostMapping("/{id}/conclude")
    @PreAuthorize("isAuthenticated()")
    public Result<DisputeResponse> concludeFinal(@PathVariable("id") Long disputeId) {
        Dispute d = disputeApplicationService.concludeFinal(
                new ConcludeCommand(disputeId, requireUid()));
        return success("已接受最终决议", DisputeResponse.from(d));
    }

    @PostMapping("/{id}/evidence")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<Long>> addEvidence(@PathVariable("id") Long disputeId,
                                                     @Valid @RequestBody AddEvidenceRequest request) {
        DisputeEvidence ev = disputeApplicationService.addEvidence(
                new AddEvidenceCommand(disputeId, requireUid(),
                        request.evidenceKind(), request.contentUrl(), request.description()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(success("证据已上传", ev.evidenceId()));
    }

    private Long requireUid() {
        Long uid = SecurityUtils.getUid();
        if (uid == null) {
            throw new DisputeApplicationException(
                    DisputeApplicationException.Reason.DISPUTE_NOT_OWNER,
                    "未识别到业主身份（c_user.uid），禁止访问该操作");
        }
        return uid;
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
