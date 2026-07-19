// 关联业务：提供业主本人维修表决任务、材料确认、实名投票、纸质协助和回执查询入口。
package com.pangu.interfaces.web.controller;

import com.pangu.application.repair.OwnerRepairProjectVotingService;
import com.pangu.domain.model.repair.RepairProjectVoting;
import com.pangu.interfaces.web.controller.dto.assembly.OnlineBallotReceiptResponse;
import com.pangu.interfaces.web.controller.dto.assembly.OnlineBallotSubmissionRequest;
import com.pangu.interfaces.web.controller.dto.assembly.OnlineVotingAcknowledgementRequest;
import com.pangu.interfaces.web.controller.dto.assembly.OnlineVotingAcknowledgementResponse;
import com.pangu.interfaces.web.controller.dto.assembly.PaperAssistanceRequest;
import com.pangu.interfaces.web.controller.dto.assembly.PaperAssistanceResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** C 端只处理当前实名业主在冻结名册中的维修表决权，不接收客户端提交的身份或计票结果。 */
@RestController
@RequestMapping("/api/v1/me/repair-projects")
@RequiredArgsConstructor
public class OwnerRepairProjectVotingController extends BaseController {

    private final OwnerRepairProjectVotingService service;

    @GetMapping("/voting-tasks")
    @PreAuthorize("isAuthenticated()")
    public Result<List<RepairProjectVoting.OwnerTask>> tasks() {
        return success(service.listTasks());
    }

    @GetMapping("/{projectId}/voting")
    @PreAuthorize("isAuthenticated()")
    public Result<OwnerRepairProjectVotingService.Disclosure> disclosure(@PathVariable Long projectId) {
        return success(service.disclosure(projectId));
    }

    @PostMapping("/{projectId}/voting/acknowledgements")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<OnlineVotingAcknowledgementResponse>> acknowledge(
            @PathVariable Long projectId,
            @Valid @RequestBody OnlineVotingAcknowledgementRequest request) {
        var acknowledgement = service.acknowledge(
                projectId, request.opid(), request.packageHash(), request.confirmed());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(success("已确认阅读本次维修方案和表决材料",
                        OnlineVotingAcknowledgementResponse.from(acknowledgement)));
    }

    @PostMapping("/{projectId}/voting/ballots")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<OnlineBallotReceiptResponse>> submit(
            @PathVariable Long projectId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody OnlineBallotSubmissionRequest request) {
        var submission = service.submit(
                projectId, request.opid(), request.packageHash(), request.confirmed(),
                idempotencyKey, request.toCommands());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(success("本专有部分已完成表决", OnlineBallotReceiptResponse.from(submission)));
    }

    @PostMapping("/{projectId}/voting/paper-assistance")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<PaperAssistanceResponse>> requestPaperAssistance(
            @PathVariable Long projectId,
            @Valid @RequestBody PaperAssistanceRequest request) {
        var assistance = service.requestPaperAssistance(projectId, request.opid(), request.packageHash());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(success("已申请纸质表决票", PaperAssistanceResponse.from(assistance)));
    }

    @PostMapping("/{projectId}/voting/paper-assistance/{requestId}/withdraw")
    @PreAuthorize("isAuthenticated()")
    public Result<PaperAssistanceResponse> withdrawPaperAssistance(
            @PathVariable Long projectId,
            @PathVariable Long requestId,
            @Valid @RequestBody PaperAssistanceRequest request) {
        return success("纸质办理申请已撤回", PaperAssistanceResponse.from(
                service.withdrawPaperAssistance(
                        projectId, requestId, request.opid(), request.packageHash())));
    }
}
