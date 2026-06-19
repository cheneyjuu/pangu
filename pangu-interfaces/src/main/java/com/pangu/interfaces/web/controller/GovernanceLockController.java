package com.pangu.interfaces.web.controller;

import com.pangu.application.lock.GovernanceLockApplicationException;
import com.pangu.application.lock.GovernanceLockApplicationService;
import com.pangu.application.lock.command.CommitteeUnlockCommand;
import com.pangu.application.lock.command.LockCommand;
import com.pangu.application.lock.command.StreetUnlockCommand;
import com.pangu.domain.model.lock.GovernanceLock;
import com.pangu.interfaces.security.SecurityUtils;
import com.pangu.interfaces.web.controller.dto.lock.LockRequest;
import com.pangu.interfaces.web.controller.dto.lock.LockResponse;
import com.pangu.interfaces.web.controller.dto.lock.UnlockRequest;
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

/**
 * 通用治理锁 (governance_lock) 的 RESTful 入口（M2-1）。
 *
 * <p>API 路径：
 * <ul>
 *   <li>{@code POST /api/v1/locks}                          —— 创建锁定记录；</li>
 *   <li>{@code POST /api/v1/locks/{lockId}/committee-sign}  —— 业委会主任解锁初签；</li>
 *   <li>{@code POST /api/v1/locks/{lockId}/street-sign}     —— 街道办解锁终签。</li>
 * </ul>
 *
 * <p>鉴权策略：
 * <ul>
 *   <li>{@code POST /locks}（创建）—— 本期由系统管理员触发回归测试，挂
 *       {@code @PreAuthorize("hasAuthority('admin:role:manage')")}；
 *       M2-3 落地时由 disclosure publish 内部委派，再下沉到具体业务权限。</li>
 *   <li>{@code committee-sign} —— {@code lock:unlock:committee}（V2.5 注册到 COMMITTEE_DIRECTOR）；</li>
 *   <li>{@code street-sign}    —— {@code lock:unlock:street}（V2.5 注册到 GOV_SUPER_ADMIN / COMMUNITY_ADMIN）。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/locks")
@RequiredArgsConstructor
public class GovernanceLockController extends BaseController {

    private final GovernanceLockApplicationService lockApplicationService;

    /** 创建治理锁。 */
    @PostMapping
    @PreAuthorize("hasAuthority('admin:role:manage')")
    public ResponseEntity<Result<LockResponse>> lock(@Valid @RequestBody LockRequest request) {
        LockCommand cmd = new LockCommand(
                requireTenantId(),
                request.entityType(),
                request.entityId(),
                requireUserId(),
                request.lockPayloadHash());
        GovernanceLock lock = lockApplicationService.lock(cmd);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(success("治理锁已创建", LockResponse.from(lock)));
    }

    /** 业委会主任解锁初签。 */
    @PostMapping("/{lockId}/committee-sign")
    @PreAuthorize("hasAuthority('lock:unlock:committee')")
    public Result<LockResponse> committeeSign(@PathVariable("lockId") Long lockId,
                                               @Valid @RequestBody UnlockRequest request) {
        CommitteeUnlockCommand cmd = new CommitteeUnlockCommand(
                lockId, requireUserId(), request.signature());
        GovernanceLock lock = lockApplicationService.committeeSign(cmd);
        return success("初签已记录，等待街道办终签", LockResponse.from(lock));
    }

    /** 街道办解锁终签。 */
    @PostMapping("/{lockId}/street-sign")
    @PreAuthorize("hasAuthority('lock:unlock:street')")
    public Result<LockResponse> streetSign(@PathVariable("lockId") Long lockId,
                                            @Valid @RequestBody UnlockRequest request) {
        StreetUnlockCommand cmd = new StreetUnlockCommand(
                lockId, requireUserId(), request.signature());
        GovernanceLock lock = lockApplicationService.streetSign(cmd);
        return success("终签已记录，治理锁完全解锁", LockResponse.from(lock));
    }

    private Long requireUserId() {
        Long userId = SecurityUtils.getUserId();
        if (userId == null) {
            throw new GovernanceLockApplicationException(
                    GovernanceLockApplicationException.Reason.LOCK_NOT_FOUND,
                    "未识别到登录用户，禁止访问该操作");
        }
        return userId;
    }

    private Long requireTenantId() {
        Long tenantId = SecurityUtils.getTenantId();
        if (tenantId == null) {
            throw new GovernanceLockApplicationException(
                    GovernanceLockApplicationException.Reason.LOCK_NOT_FOUND,
                    "未识别到租户上下文，禁止访问该操作");
        }
        return tenantId;
    }
}
