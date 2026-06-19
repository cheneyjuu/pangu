package com.pangu.interfaces.web.controller;

import com.pangu.application.disclosure.FinanceDisclosureApplicationException;
import com.pangu.application.disclosure.FinanceDisclosureApplicationService;
import com.pangu.application.disclosure.command.ComposeCommand;
import com.pangu.application.disclosure.command.CompareCommand;
import com.pangu.application.disclosure.command.LockAndPublishCommand;
import com.pangu.domain.model.disclosure.DisclosureDiff;
import com.pangu.domain.model.disclosure.FinanceDisclosureSnapshot;
import com.pangu.interfaces.security.SecurityUtils;
import com.pangu.interfaces.web.controller.dto.disclosure.CompareResponse;
import com.pangu.interfaces.web.controller.dto.disclosure.ComposeRequest;
import com.pangu.interfaces.web.controller.dto.disclosure.SnapshotResponse;
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
 * 财务公示 RESTful 入口（M2-3）。
 *
 * <p>API 路径：
 * <ul>
 *   <li>{@code POST /api/v1/disclosures/compose}                          —— 物业 / 业委会聚合 DRAFT；</li>
 *   <li>{@code POST /api/v1/disclosures/{snapshotId}/publish}             —— 业委会主任锁 + 发布；</li>
 *   <li>{@code GET  /api/v1/disclosures/{snapshotId}}                     —— 业主侧拉单期，service 校验 tenant + status=PUBLISHED；</li>
 *   <li>{@code POST /api/v1/disclosures/{currId}/compare/{prevId}}        —— G 端审计差分。</li>
 * </ul>
 *
 * <p>鉴权策略：
 * <ul>
 *   <li>{@code compose} —— {@code disclosure:compose}（GB / redline=0）；</li>
 *   <li>{@code publish} —— {@code disclosure:publish}（B / redline=1，仅 COMMITTEE_DIRECTOR）；</li>
 *   <li>{@code GET 单期} —— {@code @PreAuthorize("isAuthenticated()")}，service 层做 tenant + 状态校验；</li>
 *   <li>{@code compare} —— {@code disclosure:audit}（G / redline=0，GOV_SUPER_ADMIN / COMMUNITY_ADMIN）。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/disclosures")
@RequiredArgsConstructor
public class FinanceDisclosureController extends BaseController {

    private final FinanceDisclosureApplicationService disclosureApplicationService;

    /** compose：聚合 → 落 DRAFT 快照。 */
    @PostMapping("/compose")
    @PreAuthorize("hasAuthority('disclosure:compose')")
    public ResponseEntity<Result<SnapshotResponse>> compose(@Valid @RequestBody ComposeRequest request) {
        ComposeCommand cmd = new ComposeCommand(
                requireTenantId(),
                request.period(),
                request.disclosureType(),
                requireUserId());
        FinanceDisclosureSnapshot snapshot = disclosureApplicationService.compose(cmd);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(success("财务公示快照已聚合", SnapshotResponse.from(snapshot)));
    }

    /** lockAndPublish：DRAFT → LOCKED → PUBLISHED 单事务推进。 */
    @PostMapping("/{snapshotId}/publish")
    @PreAuthorize("hasAuthority('disclosure:publish')")
    public Result<SnapshotResponse> publish(@PathVariable("snapshotId") Long snapshotId) {
        LockAndPublishCommand cmd = new LockAndPublishCommand(
                requireTenantId(),
                snapshotId,
                requireUserId());
        FinanceDisclosureSnapshot snapshot = disclosureApplicationService.lockAndPublish(cmd);
        return success("财务公示快照已锁定并发布", SnapshotResponse.from(snapshot));
    }

    /** 业主侧拉单期：service 层校验 tenant + status=PUBLISHED。 */
    @GetMapping("/{snapshotId}")
    @PreAuthorize("isAuthenticated()")
    public Result<SnapshotResponse> getOne(@PathVariable("snapshotId") Long snapshotId) {
        FinanceDisclosureSnapshot snapshot = disclosureApplicationService
                .getReadablePublishedSnapshot(snapshotId, requireTenantId());
        return success(SnapshotResponse.from(snapshot));
    }

    /** compare：W/R/N 差分审计 + 落 audit 表。 */
    @PostMapping("/{currId}/compare/{prevId}")
    @PreAuthorize("hasAuthority('disclosure:audit')")
    public Result<CompareResponse> compare(@PathVariable("currId") Long currId,
                                            @PathVariable("prevId") Long prevId) {
        CompareCommand cmd = new CompareCommand(
                requireTenantId(),
                prevId,
                currId,
                requireUserId());
        DisclosureDiff diff = disclosureApplicationService.compare(cmd);
        return success("差分审计完成", CompareResponse.from(diff));
    }

    private Long requireUserId() {
        Long userId = SecurityUtils.getUserId();
        if (userId == null) {
            throw new FinanceDisclosureApplicationException(
                    FinanceDisclosureApplicationException.Reason.SNAPSHOT_NOT_FOUND,
                    "未识别到登录用户，禁止访问该操作");
        }
        return userId;
    }

    private Long requireTenantId() {
        Long tenantId = SecurityUtils.getTenantId();
        if (tenantId == null) {
            throw new FinanceDisclosureApplicationException(
                    FinanceDisclosureApplicationException.Reason.SNAPSHOT_NOT_FOUND,
                    "未识别到租户上下文，禁止访问该操作");
        }
        return tenantId;
    }
}
