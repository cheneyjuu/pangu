package com.pangu.interfaces.web.controller;

import com.pangu.application.handover.TenantTermLockService;
import com.pangu.application.voting.ProposalLifecycleService;
import com.pangu.application.voting.ProposalReviewService;
import com.pangu.application.voting.VoteMonitorQueryService;
import com.pangu.application.voting.VotingApplicationException;
import com.pangu.application.voting.VotingMobilizationService;
import com.pangu.application.voting.VotingReminderDeliveryQueryService;
import com.pangu.application.voting.VotingProgressQueryService;
import com.pangu.application.voting.command.CancelSubjectCommand;
import com.pangu.application.voting.command.OfflineProxyVoteCommand;
import com.pangu.application.voting.command.ProposeSubjectCommand;
import com.pangu.application.voting.command.PublishSubjectCommand;
import com.pangu.application.voting.command.SendMobilizationReminderCommand;
import com.pangu.domain.common.Page;
import com.pangu.domain.model.voting.SubjectStatus;
import com.pangu.domain.model.voting.SubjectType;
import com.pangu.domain.model.voting.VotingMobilizationPermission;
import com.pangu.domain.model.voting.VotingMobilizationReminder;
import com.pangu.domain.model.voting.VotingProgress;
import com.pangu.domain.model.voting.VotingSubject;
import com.pangu.domain.model.notification.VotingReminderDeliveryStatus;
import com.pangu.domain.repository.VoteDetailQueryRepository;
import com.pangu.domain.repository.VotingSubjectRepository;
import com.pangu.interfaces.security.SecurityUtils;
import com.pangu.interfaces.web.controller.dto.PageResponse;
import com.pangu.interfaces.web.controller.dto.voting.AdminSubjectResponse;
import com.pangu.interfaces.web.controller.dto.voting.CancelRequest;
import com.pangu.interfaces.web.controller.dto.voting.OfflineProxyVoteRequest;
import com.pangu.interfaces.web.controller.dto.voting.ProposeRequest;
import com.pangu.interfaces.web.controller.dto.voting.ReviewSubjectRequest;
import com.pangu.interfaces.web.controller.dto.voting.SendMobilizationReminderRequest;
import com.pangu.interfaces.web.controller.dto.voting.SubjectProgressResponse;
import com.pangu.interfaces.web.controller.dto.voting.VoteAcknowledgement;
import com.pangu.interfaces.web.controller.dto.voting.VoteDetailResponse;
import com.pangu.interfaces.web.controller.dto.voting.VoteMonitorResponse;
import com.pangu.interfaces.web.controller.dto.voting.VotingMobilizationPermissionResponse;
import com.pangu.interfaces.web.controller.dto.voting.VotingMobilizationReminderResponse;
import com.pangu.interfaces.web.controller.dto.voting.VotingReminderDeliveryStatusResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * B/G 端议题生命周期管理 endpoint（M3-2 引入）。
 *
 * <p>权限路由：
 * <ul>
 *   <li>{@code POST /api/v1/voting-subjects}        —— GENERAL/MAJOR 用 {@code voting:subject:create}；
 *                                                         ELECTION 用 {@code voting:subject:create:election}</li>
 *   <li>{@code POST /api/v1/voting-subjects/{id}/publish} —— {@code voting:subject:publish}（非 ELECTION 直接公示）</li>
 *   <li>{@code POST /api/v1/voting-subjects/{id}/cancel}  —— DRAFT 阶段：发起者本人即可（{@code voting:subject:create}）；
 *                                                         PUBLISHED 阶段：必须 {@code voting:subject:cancel}（街道办独占）</li>
 *   <li>{@code GET  /api/v1/voting-subjects/{id}}    —— {@code voting:subject:audit}（管理端工作台）</li>
 * </ul>
 *
 * <p>M3-3 起 SubjectType=ELECTION 放开（须携带 maxWinners）；endpoint 上的 @PreAuthorize
 * 先按议题类型粗筛权限，类型与角色/dept_type 的最终匹配由 service 层判定。
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SubjectAdminController extends BaseController {

    private final ProposalLifecycleService proposalLifecycleService;
    private final ProposalReviewService proposalReviewService;
    private final TenantTermLockService tenantTermLockService;
    private final VotingSubjectRepository votingSubjectRepository;
    private final VotingProgressQueryService votingProgressQueryService;
    private final VoteMonitorQueryService voteMonitorQueryService;
    private final VotingMobilizationService votingMobilizationService;
    private final VotingReminderDeliveryQueryService reminderDeliveryQueryService;

    /** 立项：DRAFT 写入。 */
    @PostMapping("/voting-subjects")
    @PreAuthorize("(#request.subjectType == T(com.pangu.domain.model.voting.SubjectType).ELECTION"
            + " and hasAuthority('voting:subject:create:election'))"
            + " or (#request.subjectType != T(com.pangu.domain.model.voting.SubjectType).ELECTION"
            + " and hasAuthority('voting:subject:create'))")
    public ResponseEntity<Result<AdminSubjectResponse>> propose(
            @Valid @RequestBody ProposeRequest request) {
        ProposeSubjectCommand cmd = new ProposeSubjectCommand(
                requireTenantId(),
                request.subjectType(),
                request.scope(),
                request.scopeReferenceId(),
                request.title(),
                request.voteStartAt(),
                request.voteEndAt(),
                requireUserId(),
                request.partyRatioFloor(),
                request.maxWinners());
        VotingSubject draft = proposalLifecycleService.propose(cmd);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(success("议题已草稿落库，待公示", AdminSubjectResponse.from(draft)));
    }

    /** 非 ELECTION 直接公示：DRAFT → PUBLISHED；ELECTION 必须走 street-review。 */
    @PostMapping("/voting-subjects/{subjectId}/publish")
    @PreAuthorize("hasAuthority('voting:subject:publish')")
    public Result<AdminSubjectResponse> publish(@PathVariable("subjectId") Long subjectId) {
        VotingSubject subject = proposalLifecycleService.publish(
                new PublishSubjectCommand(subjectId, requireUserId()));
        return success("议题已公示", AdminSubjectResponse.from(subject));
    }

    /** ELECTION：DRAFT → PENDING_COMMITTEE。 */
    @PostMapping("/voting-subjects/{subjectId}/submit-for-review")
    @PreAuthorize("hasAuthority('voting:subject:create:election')")
    public Result<AdminSubjectResponse> submitForReview(@PathVariable("subjectId") Long subjectId) {
        VotingSubject subject = proposalReviewService.submitForCommitteeReview(subjectId, requireUserId());
        return success("议题已提交居委会初审", AdminSubjectResponse.from(subject));
    }

    /** ELECTION：居委会初审，通过进入街道办终审，驳回回到 DRAFT。 */
    @PostMapping("/voting-subjects/{subjectId}/committee-review")
    @PreAuthorize("hasAuthority('voting:subject:review:committee')")
    public Result<AdminSubjectResponse> committeeReview(
            @PathVariable("subjectId") Long subjectId,
            @RequestBody ReviewSubjectRequest request) {
        VotingSubject subject = switch (requireDecision(request)) {
            case APPROVE -> proposalReviewService.committeeApprove(subjectId, requireUserId());
            case REJECT -> proposalReviewService.committeeReject(subjectId, requireUserId(), request.reason());
        };
        return success("居委会初审已处理", AdminSubjectResponse.from(subject));
    }

    /** ELECTION：街道办终审，通过即 PUBLISHED，驳回回到 DRAFT。 */
    @PostMapping("/voting-subjects/{subjectId}/street-review")
    @PreAuthorize("hasAuthority('voting:subject:review:street')")
    public Result<AdminSubjectResponse> streetReview(
            @PathVariable("subjectId") Long subjectId,
            @RequestBody ReviewSubjectRequest request) {
        VotingSubject subject = switch (requireDecision(request)) {
            case APPROVE -> proposalReviewService.streetApprove(subjectId, requireUserId());
            case REJECT -> proposalReviewService.streetReject(subjectId, requireUserId(), request.reason());
        };
        return success("街道办终审已处理", AdminSubjectResponse.from(subject));
    }

    /** 街道办换届备案通过：租户任期状态 HANDOVER_LOCK → NORMAL。 */
    @PostMapping("/handover/confirm")
    @PreAuthorize("hasAuthority('voting:subject:review:street')")
    public Result<String> confirmHandover() {
        tenantTermLockService.confirmHandover(requireTenantId(), requireUserId());
        return success("换届备案已通过", "NORMAL");
    }

    /**
     * 撤回。
     *
     * <p>授权策略：DRAFT 阶段允许 {@code voting:subject:create} 持有者撤回，service 层会
     * 校验 currentUserId == proposedByUserId；PUBLISHED 阶段只允许 {@code voting:subject:cancel}
     * （街道办强撤）。endpoint 用 {@code isAuthenticated()} 兜底，由 service 层根据 byGovernment
     * 标志 + 角色判定准入。
     */
    @PostMapping("/voting-subjects/{subjectId}/cancel")
    @PreAuthorize("isAuthenticated()")
    public Result<AdminSubjectResponse> cancel(
            @PathVariable("subjectId") Long subjectId,
            @Valid @RequestBody CancelRequest request) {
        boolean byGovernment = hasAuthority("voting:subject:cancel");
        boolean canCreate = hasAuthority("voting:subject:create");
        if (!byGovernment && !canCreate) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.CANCEL_FORBIDDEN,
                    "缺少撤回权限：需 voting:subject:cancel 或 voting:subject:create");
        }
        CancelSubjectCommand cmd = new CancelSubjectCommand(
                subjectId, requireUserId(), request.reason(), byGovernment);
        VotingSubject subject = proposalLifecycleService.cancel(cmd);
        return success("议题已撤回", AdminSubjectResponse.from(subject));
    }

    /**
     * 管理端议题分页列表（M4-1）。
     *
     * <p>租户内分页查询，可选 {@code status}/{@code type} 筛选（按枚举 name 传参，如 {@code ?status=VOTING}）。
     * 返回统一分页契约 {@code { items, total, page, size }}。
     */
    @GetMapping("/voting-subjects")
    @PreAuthorize("hasAuthority('voting:subject:audit')")
    public Result<PageResponse<AdminSubjectResponse>> page(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "status", required = false) SubjectStatus status,
            @RequestParam(name = "type", required = false) SubjectType type) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<VotingSubject> result = votingSubjectRepository.pageForAdmin(
                requireTenantId(), status, type, safePage, safeSize);
        return success(PageResponse.from(result, AdminSubjectResponse::from));
    }

    /** 管理端议题详情（B/G 端）。 */
    @GetMapping("/voting-subjects/{subjectId}")
    @PreAuthorize("hasAuthority('voting:subject:audit')")
    public Result<AdminSubjectResponse> findAdminDetail(
            @PathVariable("subjectId") Long subjectId) {
        VotingSubject subject = votingSubjectRepository.findById(subjectId)
                .orElseThrow(() -> new VotingApplicationException(
                        VotingApplicationException.Reason.SUBJECT_NOT_FOUND,
                        "议题不存在 subjectId=" + subjectId));
        return success(AdminSubjectResponse.from(subject));
    }

    /**
     * 议题双过半进度（M4-2）。
     *
     * <p>VOTING/CLOSED 等未结算态返回实时进度（与结算口径一致但非法定值）；
     * SETTLED 态返回法定结算快照。{@code support*} 在 SETTLED 态为 null。
     */
    @GetMapping("/voting-subjects/{subjectId}/progress")
    @PreAuthorize("hasAuthority('voting:subject:audit')")
    public Result<SubjectProgressResponse> progress(@PathVariable("subjectId") Long subjectId) {
        VotingProgress progress = votingProgressQueryService.queryProgress(subjectId, requireTenantId());
        return success(SubjectProgressResponse.from(progress));
    }

    /** 投票监控基线：Bloom/计数派生的无签名票占比与快速连续投票告警。 */
    @GetMapping("/voting-subjects/{subjectId}/monitor")
    @PreAuthorize("hasAuthority('voting:subject:audit')")
    public Result<VoteMonitorResponse> monitor(@PathVariable("subjectId") Long subjectId) {
        return success(VoteMonitorResponse.from(
                voteMonitorQueryService.query(subjectId, requireTenantId())));
    }

    /** 当前管理端用户在该议题下的投票期催票 / 线下代录动态授权。 */
    @GetMapping("/voting-subjects/{subjectId}/mobilization-permissions/me")
    @PreAuthorize("hasAuthority('voting:subject:audit')")
    public Result<List<VotingMobilizationPermissionResponse>> myMobilizationPermissions(
            @PathVariable("subjectId") Long subjectId) {
        List<VotingMobilizationPermission> permissions = votingMobilizationService.listMine(subjectId);
        return success(permissions.stream().map(VotingMobilizationPermissionResponse::from).toList());
    }

    /** 当前管理端用户对授权楼栋发起一次催票，落催票记录并写入通知 outbox。 */
    @PostMapping("/voting-subjects/{subjectId}/mobilization-reminders")
    @PreAuthorize("hasAuthority('voting:subject:audit')")
    public Result<VotingMobilizationReminderResponse> sendMobilizationReminder(
            @PathVariable("subjectId") Long subjectId,
            @Valid @RequestBody SendMobilizationReminderRequest request) {
        VotingMobilizationReminder reminder = votingMobilizationService.sendReminder(
                new SendMobilizationReminderCommand(subjectId, request.buildingId(), request.message()));
        return success("催票已提交", VotingMobilizationReminderResponse.from(reminder));
    }

    /** 管理端查询催票逐户投递明细，可按楼栋与投递状态筛选。 */
    @GetMapping("/voting-subjects/{subjectId}/reminder-deliveries")
    @PreAuthorize("hasAuthority('voting:subject:audit')")
    public Result<List<VotingReminderDeliveryStatusResponse>> reminderDeliveries(
            @PathVariable("subjectId") Long subjectId,
            @RequestParam(name = "buildingId", required = false) Long buildingId,
            @RequestParam(name = "status", required = false) Integer status,
            @RequestParam(name = "limit", defaultValue = "100") int limit) {
        List<VotingReminderDeliveryStatus> deliveries = reminderDeliveryQueryService.listBySubject(
                subjectId, buildingId, status, limit);
        return success(deliveries.stream().map(VotingReminderDeliveryStatusResponse::from).toList());
    }

    /** 当前管理端用户对授权楼栋进行线下代录，写票通道固定为 OFFLINE_PROXY。 */
    @PostMapping("/voting-subjects/{subjectId}/offline-proxy-votes")
    @PreAuthorize("hasAuthority('voting:subject:audit')")
    public Result<VoteAcknowledgement> castOfflineProxyVote(
            @PathVariable("subjectId") Long subjectId,
            @Valid @RequestBody OfflineProxyVoteRequest request) {
        long voteId = votingMobilizationService.castOfflineProxyVote(
                new OfflineProxyVoteCommand(
                        subjectId,
                        request.opid(),
                        request.targetId(),
                        request.choice(),
                        request.offlineEvidenceHash()));
        return success("线下代录成功", VoteAcknowledgement.of(voteId));
    }

    /**
     * 逐户投票明细分页（M4-2）。
     *
     * <p>以分母范围内应投房产全量铺开，未投房产以 {@code voted=false} 出现，
     * 返回统一分页契约 {@code { items, total, page, size }}。
     */
    @GetMapping("/voting-subjects/{subjectId}/vote-details")
    @PreAuthorize("hasAuthority('voting:subject:audit')")
    public Result<PageResponse<VoteDetailResponse>> voteDetails(
            @PathVariable("subjectId") Long subjectId,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        Page<VoteDetailQueryRepository.VoteDetailRow> result =
                votingProgressQueryService.pageVoteDetails(subjectId, requireTenantId(), page, size);
        return success(PageResponse.from(result, VoteDetailResponse::from));
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

    private boolean hasAuthority(String key) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            return false;
        }
        return auth.getAuthorities().stream().anyMatch(a -> key.equals(a.getAuthority()));
    }

    private ReviewSubjectRequest.Decision requireDecision(ReviewSubjectRequest request) {
        if (request == null || request.decision() == null) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.REVIEW_REJECT_REASON_REQUIRED,
                    "审批决定不能为空");
        }
        return request.decision();
    }
}
