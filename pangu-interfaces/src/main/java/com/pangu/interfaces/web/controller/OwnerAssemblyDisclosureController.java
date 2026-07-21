// 关联业务：向当前业主提供业主大会公示、锁定材料和本人线上或纸质办理入口。
package com.pangu.interfaces.web.controller;

import com.pangu.application.assembly.OwnerAssemblyDisclosure;
import com.pangu.application.assembly.OwnerAssemblyDisclosureService;
import com.pangu.application.assembly.OwnerAssemblyOnlineVotingService;
import com.pangu.interfaces.web.controller.dto.assembly.OnlineBallotReceiptResponse;
import com.pangu.interfaces.web.controller.dto.assembly.OnlineBallotSubmissionRequest;
import com.pangu.interfaces.web.controller.dto.assembly.OnlineVotingAcknowledgementRequest;
import com.pangu.interfaces.web.controller.dto.assembly.OnlineVotingAcknowledgementResponse;
import com.pangu.interfaces.web.controller.dto.assembly.OwnerAssemblyMaterialDownloadTicketResponse;
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

/**
 * C 端业主大会披露入口。
 *
 * <p>服务层会复核当前 C 端业主身份、租户、冻结表决代表和表决包版本。线上提交使用整包确认，
 * 返回本人回执但不返回任何具体表决选择。
 */
@RestController
@RequestMapping("/api/v1/me/owners-assembly-disclosures")
@RequiredArgsConstructor
public class OwnerAssemblyDisclosureController extends BaseController {

    private final OwnerAssemblyDisclosureService service;
    private final OwnerAssemblyOnlineVotingService onlineVotingService;

    @GetMapping("/{packageId}")
    @PreAuthorize("isAuthenticated()")
    public Result<OwnerAssemblyDisclosure> disclosure(@PathVariable Long packageId) {
        return success(service.disclosure(packageId));
    }

    @GetMapping("/{packageId}/materials/{materialId}/download-ticket")
    @PreAuthorize("isAuthenticated()")
    public Result<OwnerAssemblyMaterialDownloadTicketResponse> materialDownloadTicket(
            @PathVariable Long packageId,
            @PathVariable Long materialId) {
        return success(OwnerAssemblyMaterialDownloadTicketResponse.from(
                service.createMaterialDownloadTicket(packageId, materialId)));
    }

    @GetMapping("/{packageId}/materials/{materialId}/content")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> materialContent(
            @PathVariable Long packageId,
            @PathVariable Long materialId) {
        return OwnerAccessibleFileResponse.inline(service.readMaterial(packageId, materialId));
    }

    @PostMapping("/{packageId}/online-acknowledgements")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<OnlineVotingAcknowledgementResponse>> acknowledgeMaterials(
            @PathVariable Long packageId,
            @Valid @RequestBody OnlineVotingAcknowledgementRequest request) {
        var acknowledgement = onlineVotingService.acknowledge(
                packageId, request.opid(), request.packageHash(), request.confirmed());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(success("已确认阅读本次表决材料", OnlineVotingAcknowledgementResponse.from(acknowledgement)));
    }

    @PostMapping("/{packageId}/online-ballots")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<OnlineBallotReceiptResponse>> submitOnlineBallot(
            @PathVariable Long packageId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody OnlineBallotSubmissionRequest request) {
        var submission = onlineVotingService.submit(
                packageId, request.opid(), request.packageHash(), request.confirmed(),
                idempotencyKey, request.toCommands());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(success("本专有部分已完成表决", OnlineBallotReceiptResponse.from(submission)));
    }

    @PostMapping("/{packageId}/paper-assistance-requests")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<PaperAssistanceResponse>> requestPaperAssistance(
            @PathVariable Long packageId,
            @Valid @RequestBody PaperAssistanceRequest request) {
        var assistance = onlineVotingService.requestPaperAssistance(
                packageId, request.opid(), request.packageHash());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(success("已申请纸质表决票", PaperAssistanceResponse.from(assistance)));
    }

    @PostMapping("/{packageId}/paper-assistance-requests/{requestId}/withdraw")
    @PreAuthorize("isAuthenticated()")
    public Result<PaperAssistanceResponse> withdrawPaperAssistance(
            @PathVariable Long packageId,
            @PathVariable Long requestId,
            @Valid @RequestBody PaperAssistanceRequest request) {
        return success("纸质办理申请已撤回", PaperAssistanceResponse.from(
                onlineVotingService.withdrawPaperAssistance(
                        packageId, requestId, request.opid(), request.packageHash())));
    }
}
