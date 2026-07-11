package com.pangu.interfaces.web.controller;

import com.pangu.application.assembly.OwnersAssemblyApplicationException;
import com.pangu.application.assembly.OwnersAssemblyApplicationService;
import com.pangu.application.assembly.command.AddAssemblySubjectCommand;
import com.pangu.application.assembly.command.CastAssemblyOnlineVoteCommand;
import com.pangu.application.assembly.command.CastAssemblyPaperVoteCommand;
import com.pangu.application.assembly.command.CreateBallotPackageCommand;
import com.pangu.application.assembly.command.CreateOwnersAssemblySessionCommand;
import com.pangu.application.assembly.command.RecordAssemblyDeliveryCommand;
import com.pangu.domain.model.assembly.OwnersAssemblyDeliveryRecord;
import com.pangu.domain.model.assembly.OwnersAssemblyPackage;
import com.pangu.domain.model.assembly.OwnersAssemblySession;
import com.pangu.domain.model.assembly.OwnersAssemblyVoteRecord;
import com.pangu.domain.model.voting.VotingSubject;
import com.pangu.interfaces.security.SecurityUtils;
import com.pangu.interfaces.web.controller.dto.assembly.AddAssemblySubjectRequest;
import com.pangu.interfaces.web.controller.dto.assembly.CastAssemblyOnlineVoteRequest;
import com.pangu.interfaces.web.controller.dto.assembly.CastAssemblyPaperVoteRequest;
import com.pangu.interfaces.web.controller.dto.assembly.CreateBallotPackageRequest;
import com.pangu.interfaces.web.controller.dto.assembly.CreateOwnersAssemblySessionRequest;
import com.pangu.interfaces.web.controller.dto.assembly.OwnersAssemblyDeliveryResponse;
import com.pangu.interfaces.web.controller.dto.assembly.OwnersAssemblyPackageResponse;
import com.pangu.interfaces.web.controller.dto.assembly.OwnersAssemblySessionResponse;
import com.pangu.interfaces.web.controller.dto.assembly.OwnersAssemblyVoteResponse;
import com.pangu.interfaces.web.controller.dto.assembly.RecordAssemblyDeliveryRequest;
import com.pangu.interfaces.web.controller.dto.voting.AdminSubjectResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.pangu.application.assembly.OwnersAssemblyApplicationException.Reason.FORBIDDEN;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class OwnersAssemblyController extends BaseController {

    private final OwnersAssemblyApplicationService service;

    @PostMapping("/owners-assemblies")
    @PreAuthorize("hasAuthority('voting:subject:create')")
    public ResponseEntity<Result<OwnersAssemblySessionResponse>> createSession(
            @Valid @RequestBody CreateOwnersAssemblySessionRequest request) {
        OwnersAssemblySession session = service.createSession(new CreateOwnersAssemblySessionCommand(
                requireTenantId(), request.title(), request.preparationMode(), requireUserId()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(success("业主大会已创建", OwnersAssemblySessionResponse.from(session)));
    }

    @PostMapping("/owners-assemblies/{sessionId}/packages")
    @PreAuthorize("hasAuthority('voting:subject:create')")
    public ResponseEntity<Result<OwnersAssemblyPackageResponse>> createPackage(
            @PathVariable("sessionId") Long sessionId,
            @Valid @RequestBody CreateBallotPackageRequest request) {
        OwnersAssemblyPackage ballotPackage = service.createPackage(new CreateBallotPackageCommand(
                sessionId,
                requireTenantId(),
                request.votingChannelPolicy(),
                request.publicNoticeDays(),
                request.announcementHash(),
                request.attachmentManifestHash(),
                request.ballotTemplateHash(),
                request.electronicSealHash(),
                request.voteStartAt(),
                request.voteEndAt()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(success("表决包已创建", OwnersAssemblyPackageResponse.from(ballotPackage)));
    }

    @PostMapping("/owners-assembly-packages/{packageId}/subjects")
    @PreAuthorize("hasAuthority('voting:subject:create')")
    public ResponseEntity<Result<AdminSubjectResponse>> addSubject(
            @PathVariable("packageId") Long packageId,
            @Valid @RequestBody AddAssemblySubjectRequest request) {
        VotingSubject subject = service.addSubject(new AddAssemblySubjectCommand(
                packageId,
                requireTenantId(),
                request.subjectType(),
                request.scope(),
                request.scopeReferenceId(),
                request.title(),
                request.content(),
                requireUserId(),
                request.partyRatioFloor()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(success("表决事项已加入表决包", AdminSubjectResponse.from(subject)));
    }

    @PostMapping("/owners-assembly-packages/{packageId}/lock")
    @PreAuthorize("hasAuthority('voting:subject:publish')")
    public Result<OwnersAssemblyPackageResponse> lockPackage(@PathVariable("packageId") Long packageId) {
        return success("表决包已锁定并进入公示",
                OwnersAssemblyPackageResponse.from(service.lockPackage(packageId, requireTenantId())));
    }

    @PostMapping("/owners-assembly-packages/{packageId}/open-voting")
    @PreAuthorize("hasAuthority('voting:subject:publish')")
    public Result<OwnersAssemblyPackageResponse> openVoting(@PathVariable("packageId") Long packageId) {
        return success("表决包已进入投票",
                OwnersAssemblyPackageResponse.from(service.openVoting(packageId, requireTenantId())));
    }

    @PostMapping("/owners-assembly-packages/{packageId}/deliveries")
    @PreAuthorize("hasAuthority('voting:subject:audit')")
    public ResponseEntity<Result<OwnersAssemblyDeliveryResponse>> recordDelivery(
            @PathVariable("packageId") Long packageId,
            @Valid @RequestBody RecordAssemblyDeliveryRequest request) {
        OwnersAssemblyDeliveryRecord delivery = service.recordDelivery(new RecordAssemblyDeliveryCommand(
                packageId,
                requireTenantId(),
                request.opid(),
                request.deliveryChannel(),
                request.deliveryMethod(),
                request.evidenceHash(),
                requireUserId()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(success("送达留痕已记录", OwnersAssemblyDeliveryResponse.from(delivery)));
    }

    @PostMapping("/owners-assembly-packages/{packageId}/paper-votes")
    @PreAuthorize("hasAuthority('voting:subject:audit')")
    public ResponseEntity<Result<OwnersAssemblyVoteResponse>> castPaperVote(
            @PathVariable("packageId") Long packageId,
            @Valid @RequestBody CastAssemblyPaperVoteRequest request) {
        OwnersAssemblyVoteRecord vote = service.castPaperVote(new CastAssemblyPaperVoteCommand(
                packageId,
                request.subjectId(),
                requireTenantId(),
                request.opid(),
                request.choice(),
                request.ballotFileHash(),
                requireUserId()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(success("纸质投票已录入", OwnersAssemblyVoteResponse.from(vote)));
    }

    @PostMapping("/me/owners-assembly-packages/{packageId}/online-votes")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<OwnersAssemblyVoteResponse>> castOnlineVote(
            @PathVariable("packageId") Long packageId,
            @Valid @RequestBody CastAssemblyOnlineVoteRequest request) {
        OwnersAssemblyVoteRecord vote = service.castOnlineVote(new CastAssemblyOnlineVoteCommand(
                packageId,
                request.subjectId(),
                requireTenantId(),
                request.opid(),
                requireUid(),
                request.choice(),
                request.ballotFileHash(),
                request.signatureHash()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(success("线上投票成功", OwnersAssemblyVoteResponse.from(vote)));
    }

    @PostMapping("/owners-assembly-packages/{packageId}/settle")
    @PreAuthorize("hasAuthority('voting:subject:audit')")
    public Result<OwnersAssemblyPackageResponse> settle(@PathVariable("packageId") Long packageId) {
        return success("表决包已结算",
                OwnersAssemblyPackageResponse.from(service.settlePackage(packageId, requireTenantId())));
    }

    private Long requireTenantId() {
        Long tenantId = SecurityUtils.getTenantId();
        if (tenantId == null) {
            throw new OwnersAssemblyApplicationException(FORBIDDEN, "未识别到租户上下文");
        }
        return tenantId;
    }

    private Long requireUserId() {
        Long userId = SecurityUtils.getUserId();
        if (userId == null) {
            throw new OwnersAssemblyApplicationException(FORBIDDEN, "未识别到管理端工作身份");
        }
        return userId;
    }

    private Long requireUid() {
        Long uid = SecurityUtils.getUid();
        if (uid == null) {
            throw new OwnersAssemblyApplicationException(FORBIDDEN, "未识别到业主身份");
        }
        return uid;
    }
}
