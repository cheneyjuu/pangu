// 关联业务：提供书面委托原件登记、异人核验、撤销、列表和受控预览的管理端接口。
package com.pangu.interfaces.web.controller;

import com.pangu.application.voting.VotingProxyAuthorizationException;
import com.pangu.application.voting.VotingProxyAuthorizationService;
import com.pangu.domain.model.voting.VotingProxyAuthorization;
import com.pangu.interfaces.security.SecurityUtils;
import com.pangu.interfaces.web.controller.dto.voting.ReviewVotingProxyAuthorizationRequest;
import com.pangu.interfaces.web.controller.dto.voting.RevokeVotingProxyAuthorizationRequest;
import com.pangu.interfaces.web.controller.dto.voting.VotingProxyAuthorizationPreviewTicketResponse;
import com.pangu.interfaces.web.controller.dto.voting.VotingProxyAuthorizationResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import static com.pangu.application.voting.VotingProxyAuthorizationException.Reason.FORBIDDEN;
import static com.pangu.application.voting.VotingProxyAuthorizationException.Reason.INVALID_ARGUMENT;

@RestController
@RequestMapping("/api/v1/admin/voting-packages/{packageId}/proxy-authorizations")
@RequiredArgsConstructor
public class VotingProxyAuthorizationController extends BaseController {

    private final VotingProxyAuthorizationService service;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('voting:subject:audit')")
    public ResponseEntity<Result<VotingProxyAuthorizationResponse>> register(
            @PathVariable Long packageId,
            @RequestParam Long principalOpid,
            @RequestParam String agentName,
            @RequestParam VotingProxyAuthorization.IdentityDocumentType agentIdentityDocumentType,
            @RequestParam String agentIdentityNumber,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant validFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant validUntil,
            @RequestPart("file") MultipartFile file) {
        try {
            var authorization = service.register(new VotingProxyAuthorizationService.RegisterCommand(
                    packageId, requireTenantId(), principalOpid, agentName, agentIdentityDocumentType,
                    agentIdentityNumber, validFrom, validUntil, file.getOriginalFilename(), file.getContentType(),
                    file.getBytes(), requireUserId(), Instant.now()));
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(success("书面委托已登记，等待另一名工作人员核对",
                            VotingProxyAuthorizationResponse.from(authorization)));
        } catch (IOException ex) {
            throw new VotingProxyAuthorizationException(INVALID_ARGUMENT, "读取书面委托原件失败", ex);
        }
    }

    @GetMapping
    @PreAuthorize("hasAuthority('voting:subject:audit')")
    public Result<List<VotingProxyAuthorizationResponse>> list(@PathVariable Long packageId) {
        return success(service.list(packageId, requireTenantId()).stream()
                .map(VotingProxyAuthorizationResponse::from).toList());
    }

    @PostMapping("/{authorizationId}/review")
    @PreAuthorize("hasAuthority('voting:subject:audit')")
    public Result<VotingProxyAuthorizationResponse> review(
            @PathVariable Long packageId,
            @PathVariable Long authorizationId,
            @Valid @RequestBody ReviewVotingProxyAuthorizationRequest request) {
        var authorization = service.review(new VotingProxyAuthorizationService.ReviewCommand(
                packageId, authorizationId, requireTenantId(), request.decision(), request.reviewNote(),
                requireUserId(), Instant.now()));
        return success(request.decision() == VotingProxyAuthorizationService.ReviewDecision.CONFIRM
                        ? "书面委托已核对，可以由代理人代办纸质投票" : "书面委托未通过核对",
                VotingProxyAuthorizationResponse.from(authorization));
    }

    @PostMapping("/{authorizationId}/revoke")
    @PreAuthorize("hasAuthority('voting:subject:audit')")
    public Result<VotingProxyAuthorizationResponse> revoke(
            @PathVariable Long packageId,
            @PathVariable Long authorizationId,
            @Valid @RequestBody RevokeVotingProxyAuthorizationRequest request) {
        return success("书面委托已撤销", VotingProxyAuthorizationResponse.from(service.revoke(
                new VotingProxyAuthorizationService.RevokeCommand(
                        packageId, authorizationId, requireTenantId(), request.reason(),
                        requireUserId(), Instant.now()))));
    }

    @GetMapping("/{authorizationId}/preview-ticket")
    @PreAuthorize("hasAuthority('voting:subject:audit')")
    public Result<VotingProxyAuthorizationPreviewTicketResponse> previewTicket(
            @PathVariable Long packageId,
            @PathVariable Long authorizationId) {
        return success(VotingProxyAuthorizationPreviewTicketResponse.from(
                service.createPreviewTicket(packageId, authorizationId, requireTenantId())));
    }

    private Long requireTenantId() {
        Long tenantId = SecurityUtils.getTenantId();
        if (tenantId == null) {
            throw new VotingProxyAuthorizationException(FORBIDDEN, "未识别到小区工作上下文");
        }
        return tenantId;
    }

    private Long requireUserId() {
        Long userId = SecurityUtils.getUserId();
        if (userId == null) {
            throw new VotingProxyAuthorizationException(FORBIDDEN, "未识别到管理端工作身份");
        }
        return userId;
    }
}
