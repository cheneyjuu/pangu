package com.pangu.interfaces.web.controller;

import com.pangu.application.voting.ElectionCandidateService;
import com.pangu.application.voting.VotingApplicationException;
import com.pangu.application.voting.command.NominateCandidateCommand;
import com.pangu.application.voting.command.PartyReviewCandidateCommand;
import com.pangu.application.voting.command.ReviewCandidateCommand;
import com.pangu.domain.model.voting.Candidate;
import com.pangu.interfaces.security.SecurityUtils;
import com.pangu.interfaces.web.controller.dto.voting.CandidateResponse;
import com.pangu.interfaces.web.controller.dto.voting.NominateCandidateRequest;
import com.pangu.interfaces.web.controller.dto.voting.ReviewCandidateRequest;
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

import java.util.List;
import java.util.Map;

/**
 * 业委会选举候选人管理端点（M3-3 引入）。
 *
 * <p>权限路由（V1.4 既有 + 本期 V3.2 新增 candidate:review:party）：
 * <ul>
 *   <li>{@code POST /api/v1/voting-subjects/{id}/candidates} —— {@code candidate:nominate}
 *       （GB 端：网格员 / 业委会筹备组提名候选人）</li>
 *   <li>{@code POST /api/v1/candidates/{candidateId}/party-review} —— {@code candidate:review:party}
 *       （党组书记前置审查：政治/资格初筛，PENDING_PARTY_REVIEW → PENDING_COMMITTEE_REVIEW/REJECTED）</li>
 *   <li>{@code POST /api/v1/candidates/{candidateId}/review} —— {@code candidate:approve}
 *       （居委会资格审查：PENDING_COMMITTEE_REVIEW → APPROVED/REJECTED）</li>
 *   <li>{@code GET  /api/v1/voting-subjects/{id}/candidates} —— {@code voting:subject:audit}
 *       （管理端候选人列表，含所有状态）</li>
 * </ul>
 *
 * <p>endpoint 上的 @PreAuthorize 只做粗筛，议题状态 / 租户一致性 / 候选人归属由
 * {@link ElectionCandidateService} 在 service 层强校验。
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ElectionCandidateController extends BaseController {

    private final ElectionCandidateService electionCandidateService;

    /** 提名候选人：写入 PENDING_PARTY_REVIEW，仅 ELECTION 且议题处于 DRAFT/PUBLISHED。 */
    @PostMapping("/voting-subjects/{subjectId}/candidates")
    @PreAuthorize("hasAuthority('candidate:nominate')")
    public ResponseEntity<Result<Map<String, Object>>> nominate(
            @PathVariable("subjectId") Long subjectId,
            @Valid @RequestBody NominateCandidateRequest request) {
        NominateCandidateCommand cmd = new NominateCandidateCommand(
                subjectId,
                request.uid(),
                request.name(),
                request.partyMember(),
                requireTenantId(),
                requireUserId());
        Long candidateId = electionCandidateService.nominate(cmd);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(success("候选人已提名，待资格审查", Map.of("candidateId", candidateId)));
    }

    /** 党组书记前置审查：PENDING_PARTY_REVIEW → PENDING_COMMITTEE_REVIEW（通过）/ REJECTED（驳回）。 */
    @PostMapping("/candidates/{candidateId}/party-review")
    @PreAuthorize("hasAuthority('candidate:review:party')")
    public Result<CandidateResponse> partyReview(
            @PathVariable("candidateId") Long candidateId,
            @Valid @RequestBody ReviewCandidateRequest request) {
        PartyReviewCandidateCommand cmd = new PartyReviewCandidateCommand(
                candidateId, request.approve(), requireUserId());
        Candidate candidate = electionCandidateService.partyReview(cmd);
        return success("党组书记前置审查已更新", CandidateResponse.from(candidate));
    }

    /** 居委会资格审查：PENDING_COMMITTEE_REVIEW → APPROVED / REJECTED。 */
    @PostMapping("/candidates/{candidateId}/review")
    @PreAuthorize("hasAuthority('candidate:approve')")
    public Result<CandidateResponse> review(
            @PathVariable("candidateId") Long candidateId,
            @Valid @RequestBody ReviewCandidateRequest request) {
        ReviewCandidateCommand cmd = new ReviewCandidateCommand(
                candidateId, request.approve(), requireUserId());
        Candidate candidate = electionCandidateService.review(cmd);
        return success("候选人资格已更新", CandidateResponse.from(candidate));
    }

    /** 管理端候选人列表（含所有状态）。 */
    @GetMapping("/voting-subjects/{subjectId}/candidates")
    @PreAuthorize("hasAuthority('voting:subject:audit')")
    public Result<List<CandidateResponse>> listForAdmin(
            @PathVariable("subjectId") Long subjectId) {
        List<Candidate> candidates = electionCandidateService.listCandidates(subjectId);
        return success(candidates.stream().map(CandidateResponse::from).toList());
    }

    private Long requireUserId() {
        Long userId = SecurityUtils.getUserId();
        if (userId == null) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.PROPOSE_FORBIDDEN_FOR_TYPE,
                    "未识别到 sys_user 上下文，禁止访问该操作");
        }
        return userId;
    }

    private Long requireTenantId() {
        Long tenantId = SecurityUtils.getTenantId();
        if (tenantId == null) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.PROPOSE_FORBIDDEN_FOR_TYPE,
                    "未识别到租户上下文，禁止访问该操作");
        }
        return tenantId;
    }
}
