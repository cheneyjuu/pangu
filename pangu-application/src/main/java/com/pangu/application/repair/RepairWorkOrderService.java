package com.pangu.application.repair;

import com.pangu.application.handover.TenantTermLockGuard;
import com.pangu.application.repair.command.AssignRepairCommand;
import com.pangu.application.repair.command.CorrectRepairLocationCommand;
import com.pangu.application.repair.command.CreatePrivateRepairCommand;
import com.pangu.application.repair.command.CreatePublicRepairCommand;
import com.pangu.application.repair.command.EvaluateRepairCommand;
import com.pangu.application.repair.command.RepairActionCommand;
import com.pangu.application.repair.command.SubmitRepairPlanCommand;
import com.pangu.domain.common.Page;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.asset.OwnerPropertyDetail;
import com.pangu.domain.model.repair.RepairSource;
import com.pangu.domain.model.repair.RepairSpaceScope;
import com.pangu.domain.model.repair.RepairWorkOrder;
import com.pangu.domain.model.repair.RepairWorkOrderEvent;
import com.pangu.domain.model.repair.RepairWorkOrderStatus;
import com.pangu.domain.model.user.WorkIdentityBuildingScope;
import com.pangu.domain.repository.RepairWorkOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.BUILDING_NOT_IN_SCOPE;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.FORBIDDEN;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.HANDOVER_LOCKED;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.INVALID_STATUS;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.LOCATION_NOT_VERIFIED;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.NOT_FOUND;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.PARAM_INVALID;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.PROPERTY_NOT_OWNED;

@Service
@RequiredArgsConstructor
public class RepairWorkOrderService {

    private static final int DEDUP_MINUTES = 30;
    private static final int MAX_PAGE_SIZE = 100;
    private static final BigDecimal GOVERNANCE_BUDGET_THRESHOLD = new BigDecimal("50000.00");
    private static final String FUND_PROPERTY_INTERNAL = "PROPERTY_INTERNAL";

    private static final Set<String> INTAKE_ROLES = Set.of("PROPERTY_STAFF", "PROPERTY_MANAGER");
    private static final Set<String> FIELD_ROLES = Set.of(
            "PROPERTY_STAFF", "PROPERTY_MANAGER", "GRID_MEMBER", "VOLUNTEER",
            "OWNER_REPRESENTATIVE", "SERVICE_PROVIDER_STAFF", "SERVICE_PROVIDER_MANAGER");
    private static final Set<String> MANAGER_ROLES = Set.of(
            "PROPERTY_MANAGER", "COMMITTEE_DIRECTOR", "COMMUNITY_ADMIN", "GOV_SUPER_ADMIN");
    private static final Set<String> GOVERNANCE_ROLES = Set.of(
            "COMMITTEE_DIRECTOR", "COMMITTEE_MEMBER", "COMMUNITY_ADMIN", "PARTY_SECRETARY", "GOV_SUPER_ADMIN");

    private final RepairWorkOrderRepository repository;
    private final UserContextHolder userContextHolder;
    private final TenantTermLockGuard tenantTermLockGuard;

    @Transactional
    public RepairWorkOrder createPrivate(CreatePrivateRepairCommand command) {
        UserContext ctx = requireOwnerContext();
        String title = requireText(command.title(), "title");
        OwnerPropertyDetail property = repository.findOwnerProperty(ctx.uid(), ctx.tenantId(), command.opid())
                .orElseThrow(() -> new RepairWorkOrderApplicationException(PROPERTY_NOT_OWNED,
                        "房产不属于当前业主或不在当前小区 opid=" + command.opid()));
        if (property.accountStatus() == null || property.accountStatus() != 1) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN,
                    "该房产尚未完成线下核销或账户状态异常，禁止报修 opid=" + command.opid());
        }
        RepairWorkOrder duplicate = duplicate(ctx, RepairSpaceScope.PRIVATE, property.roomId(), property.buildingId(), title);
        if (duplicate != null) {
            return duplicate;
        }
        RepairWorkOrder inserted = repository.insert(new RepairWorkOrder(
                null, null, ctx.tenantId(), title, trim(command.description()), RepairSource.C_OWNER_APP,
                RepairSpaceScope.PRIVATE, RepairWorkOrderStatus.SUBMITTED, ctx.accountId(),
                ctx.uid(), null, property.roomId(), property.buildingId(), null,
                false, false, null, null, null, trim(command.category()), null,
                trim(command.evidenceText()), null, null, true, null, null, 0L, null, null));
        event(inserted, "OWNER_SUBMIT_PRIVATE", null, inserted.status(), "业主提交私有空间报修");
        return inserted;
    }

    @Transactional
    public RepairWorkOrder createPublic(CreatePublicRepairCommand command) {
        UserContext ctx = requireOwnerContext();
        String title = requireText(command.title(), "title");
        Long buildingId = command.buildingId();
        if (buildingId != null && !repository.buildingExists(ctx.tenantId(), buildingId)) {
            throw new RepairWorkOrderApplicationException(BUILDING_NOT_IN_SCOPE,
                    "楼栋不在当前小区范围内 buildingId=" + buildingId);
        }
        boolean needManualLocation = buildingId == null;
        RepairWorkOrder duplicate = duplicate(ctx, RepairSpaceScope.PUBLIC, null, buildingId, title);
        if (duplicate != null) {
            return duplicate;
        }
        RepairWorkOrderStatus status = needManualLocation
                ? RepairWorkOrderStatus.NEED_MANUAL_LOCATION
                : RepairWorkOrderStatus.SUBMITTED;
        RepairWorkOrder inserted = repository.insert(new RepairWorkOrder(
                null, null, ctx.tenantId(), title, trim(command.description()), RepairSource.C_OWNER_APP,
                RepairSpaceScope.PUBLIC, status, ctx.accountId(), ctx.uid(), null,
                null, buildingId, trim(command.locationText()), needManualLocation, false,
                null, null, null, trim(command.category()), null, trim(command.evidenceText()),
                null, null, true, null, null, 0L, null, null));
        event(inserted, "OWNER_SUBMIT_PUBLIC", null, inserted.status(),
                needManualLocation ? "公共报修信息不足，进入现场补充" : "业主提交公共区域报修");
        return inserted;
    }

    @Transactional
    public RepairWorkOrder createAdminPublic(CreatePublicRepairCommand command) {
        UserContext ctx = requireRole(INTAKE_ROLES, "当前角色无权登记报修");
        String title = requireText(command.title(), "title");
        Long tenantId = requireTenant(ctx);
        Long buildingId = command.buildingId();
        if (buildingId != null && !repository.buildingExists(tenantId, buildingId)) {
            throw new RepairWorkOrderApplicationException(BUILDING_NOT_IN_SCOPE,
                    "楼栋不在当前小区范围内 buildingId=" + buildingId);
        }
        boolean needManualLocation = buildingId == null;
        RepairWorkOrderStatus status = needManualLocation
                ? RepairWorkOrderStatus.NEED_MANUAL_LOCATION
                : RepairWorkOrderStatus.SUBMITTED;
        RepairWorkOrder inserted = repository.insert(new RepairWorkOrder(
                null, null, tenantId, title, trim(command.description()), RepairSource.ADMIN_PC,
                RepairSpaceScope.PUBLIC, status, ctx.accountId(), null, ctx.userId(),
                null, buildingId, trim(command.locationText()), needManualLocation, false,
                null, null, null, trim(command.category()), null, trim(command.evidenceText()),
                null, null, true, null, null, 0L, null, null));
        event(inserted, "ADMIN_REGISTER_PUBLIC", null, inserted.status(),
                needManualLocation ? "物业登记公共报修，待现场定位" : "物业登记公共报修");
        return inserted;
    }

    @Transactional(readOnly = true)
    public List<RepairWorkOrder> listMine() {
        UserContext ctx = requireOwnerContext();
        return repository.listForOwner(ctx.accountId(), ctx.uid(), ctx.tenantId());
    }

    @Transactional(readOnly = true)
    public RepairWorkOrder findMine(Long workOrderId) {
        UserContext ctx = requireOwnerContext();
        return repository.findByIdForOwner(workOrderId, ctx.accountId(), ctx.uid(), ctx.tenantId())
                .orElseThrow(() -> new RepairWorkOrderApplicationException(NOT_FOUND,
                        "工单不存在或不在当前业主可见范围内 workOrderId=" + workOrderId));
    }

    @Transactional(readOnly = true)
    public Page<RepairWorkOrder> pageAdmin(RepairWorkOrderStatus status,
                                           RepairSpaceScope scope,
                                           String keyword,
                                           int page,
                                           int size) {
        UserContext ctx = requireSysContext();
        Long tenantId = ctx.tenantId();
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        List<WorkIdentityBuildingScope> buildingScopes = ctx.authorizedBuildingScopes().stream().toList();
        long total = repository.countForAdmin(
                tenantId, ctx.roleKey(), ctx.userId(), buildingScopes, status, scope, trim(keyword));
        List<RepairWorkOrder> items = repository.listForAdmin(
                tenantId, ctx.roleKey(), ctx.userId(), buildingScopes, status, scope, trim(keyword), safePage, safeSize);
        return new Page<>(items, total, safePage, safeSize);
    }

    @Transactional(readOnly = true)
    public RepairWorkOrder findAdmin(Long workOrderId) {
        UserContext ctx = requireSysContext();
        RepairWorkOrder order = repository.findById(workOrderId)
                .orElseThrow(() -> new RepairWorkOrderApplicationException(NOT_FOUND,
                        "工单不存在 workOrderId=" + workOrderId));
        assertVisible(ctx, order);
        return order;
    }

    @Transactional(readOnly = true)
    public List<RepairWorkOrderEvent> listEvents(Long workOrderId) {
        RepairWorkOrder order = findAdmin(workOrderId);
        return repository.listEvents(order.workOrderId(), order.tenantId());
    }

    @Transactional
    public RepairWorkOrder accept(Long workOrderId, RepairActionCommand command) {
        UserContext ctx = requireRole(INTAKE_ROLES, "当前角色无权受理报修");
        RepairWorkOrder order = loadVisible(ctx, workOrderId);
        requireStatus(order, RepairWorkOrderStatus.SUBMITTED, RepairWorkOrderStatus.NEED_MANUAL_LOCATION);
        RepairWorkOrderStatus next = order.needManualLocation()
                ? RepairWorkOrderStatus.NEED_MANUAL_LOCATION
                : RepairWorkOrderStatus.PENDING_VERIFY;
        return transition(order, order.withStatus(next, order.needManualLocation(), false, true),
                "ACCEPT", command.remark());
    }

    @Transactional
    public RepairWorkOrder correctLocation(Long workOrderId, CorrectRepairLocationCommand command) {
        UserContext ctx = requireRole(FIELD_ROLES, "当前角色无权纠偏报修位置");
        RepairWorkOrder order = loadVisible(ctx, workOrderId);
        if (command.buildingId() == null && command.roomId() == null) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "buildingId 或 roomId 至少填写一个");
        }
        if (command.buildingId() != null && !repository.buildingExists(order.tenantId(), command.buildingId())) {
            throw new RepairWorkOrderApplicationException(BUILDING_NOT_IN_SCOPE,
                    "楼栋不在当前小区范围内 buildingId=" + command.buildingId());
        }
        RepairWorkOrder next = order.withLocation(command.buildingId(), command.roomId(),
                trim(command.locationText()), RepairWorkOrderStatus.PENDING_VERIFY, false, false, true);
        return transition(order, next, "CORRECT_LOCATION", command.reason());
    }

    @Transactional
    public RepairWorkOrder verifyLocation(Long workOrderId, RepairActionCommand command) {
        UserContext ctx = requireRole(FIELD_ROLES, "当前角色无权核验现场");
        RepairWorkOrder order = loadVisible(ctx, workOrderId);
        requireStatus(order, RepairWorkOrderStatus.SUBMITTED, RepairWorkOrderStatus.PENDING_VERIFY,
                RepairWorkOrderStatus.NEED_MANUAL_LOCATION);
        if (order.needManualLocation()) {
            throw new RepairWorkOrderApplicationException(LOCATION_NOT_VERIFIED,
                    "工单位置仍需现场补充，禁止核验通过");
        }
        if (order.buildingId() == null && order.roomId() == null) {
            throw new RepairWorkOrderApplicationException(LOCATION_NOT_VERIFIED,
                    "工单未绑定楼栋或房屋，禁止核验通过");
        }
        return transition(order, order.withStatus(RepairWorkOrderStatus.VERIFIED, false, true, false),
                "VERIFY_LOCATION", command.remark());
    }

    @Transactional
    public RepairWorkOrder assign(Long workOrderId, AssignRepairCommand command) {
        UserContext ctx = requireRole(MANAGER_ROLES, "当前角色无权派单");
        RepairWorkOrder order = loadVisible(ctx, workOrderId);
        requireStatus(order, RepairWorkOrderStatus.VERIFIED, RepairWorkOrderStatus.ASSIGNED);
        Long assignee = command.assignedUserId() == null ? ctx.userId() : command.assignedUserId();
        RepairWorkOrder next = order.withAssignment(assignee, trim(command.assigneeRoleKey()), ctx.deptId(),
                RepairWorkOrderStatus.ASSIGNED);
        return transition(order, next, "ASSIGN", command.remark());
    }

    @Transactional
    public RepairWorkOrder startSurvey(Long workOrderId, RepairActionCommand command) {
        UserContext ctx = requireRole(FIELD_ROLES, "当前角色无权开始初勘");
        RepairWorkOrder order = loadVisible(ctx, workOrderId);
        requireStatus(order, RepairWorkOrderStatus.ASSIGNED);
        return transition(order, order.withStatus(RepairWorkOrderStatus.SURVEYING, false, true, false),
                "START_SURVEY", command.remark());
    }

    @Transactional
    public RepairWorkOrder submitPlan(Long workOrderId, SubmitRepairPlanCommand command) {
        UserContext ctx = requireRole(FIELD_ROLES, "当前角色无权提交方案预算");
        RepairWorkOrder order = loadVisible(ctx, workOrderId);
        if (!order.locationLocked()) {
            throw new RepairWorkOrderApplicationException(LOCATION_NOT_VERIFIED,
                    "未完成现场核验，禁止提交方案预算");
        }
        requireStatus(order, RepairWorkOrderStatus.VERIFIED, RepairWorkOrderStatus.ASSIGNED,
                RepairWorkOrderStatus.SURVEYING, RepairWorkOrderStatus.PLAN_SUBMITTED);
        if (command.planBudget() == null || command.planBudget().compareTo(BigDecimal.ZERO) < 0) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "planBudget 必须为非负数");
        }
        RepairWorkOrder next = order.withPlan(trim(command.surveySummary()), trim(command.riskLevel()),
                command.planBudget(), trim(command.fundSource()), RepairWorkOrderStatus.PLAN_SUBMITTED);
        return transition(order, next, "SUBMIT_PLAN", command.remark());
    }

    @Transactional
    public RepairWorkOrder routePlan(Long workOrderId, RepairActionCommand command) {
        UserContext ctx = requireRole(MANAGER_ROLES, "当前角色无权进行方案路径判定");
        RepairWorkOrder order = loadVisible(ctx, workOrderId);
        requireStatus(order, RepairWorkOrderStatus.PLAN_SUBMITTED, RepairWorkOrderStatus.GOVERNANCE_PENDING);
        assertHandoverUnlocked(order);
        RepairWorkOrderStatus nextStatus = requiresGovernance(order)
                ? RepairWorkOrderStatus.GOVERNANCE_PENDING
                : RepairWorkOrderStatus.APPROVED;
        return transition(order, order.withStatus(nextStatus, false, true, false),
                "ROUTE_PLAN", command.remark());
    }

    @Transactional
    public RepairWorkOrder governanceApprove(Long workOrderId, RepairActionCommand command) {
        UserContext ctx = requireRole(GOVERNANCE_ROLES, "当前角色无权审批治理路径");
        RepairWorkOrder order = loadVisible(ctx, workOrderId);
        requireStatus(order, RepairWorkOrderStatus.GOVERNANCE_PENDING);
        assertHandoverUnlocked(order);
        return transition(order, order.withStatus(RepairWorkOrderStatus.APPROVED, false, true, false),
                "GOVERNANCE_APPROVE", command.remark());
    }

    @Transactional
    public RepairWorkOrder startWork(Long workOrderId, RepairActionCommand command) {
        UserContext ctx = requireRole(FIELD_ROLES, "当前角色无权开工");
        RepairWorkOrder order = loadVisible(ctx, workOrderId);
        requireStatus(order, RepairWorkOrderStatus.APPROVED);
        return transition(order, order.withStatus(RepairWorkOrderStatus.IN_PROGRESS, false, true, false),
                "START_WORK", command.remark());
    }

    @Transactional
    public RepairWorkOrder submitAcceptance(Long workOrderId, RepairActionCommand command) {
        UserContext ctx = requireRole(FIELD_ROLES, "当前角色无权提交验收");
        RepairWorkOrder order = loadVisible(ctx, workOrderId);
        requireStatus(order, RepairWorkOrderStatus.IN_PROGRESS, RepairWorkOrderStatus.RECTIFICATION_REQUIRED);
        return transition(order, order.withStatus(RepairWorkOrderStatus.PENDING_ACCEPTANCE, false, true, false),
                "SUBMIT_ACCEPTANCE", command.remark());
    }

    @Transactional
    public RepairWorkOrder acceptCompleted(Long workOrderId, RepairActionCommand command) {
        UserContext ctx = requireRole(GOVERNANCE_ROLES, "当前角色无权验收工单");
        RepairWorkOrder order = loadVisible(ctx, workOrderId);
        requireStatus(order, RepairWorkOrderStatus.PENDING_ACCEPTANCE);
        return transition(order, order.withStatus(RepairWorkOrderStatus.COMPLETED, false, true, false),
                "ACCEPT_COMPLETED", command.remark());
    }

    @Transactional
    public RepairWorkOrder requestRectification(Long workOrderId, RepairActionCommand command) {
        UserContext ctx = requireRole(GOVERNANCE_ROLES, "当前角色无权要求整改");
        RepairWorkOrder order = loadVisible(ctx, workOrderId);
        requireStatus(order, RepairWorkOrderStatus.PENDING_ACCEPTANCE);
        return transition(order, order.withStatus(RepairWorkOrderStatus.RECTIFICATION_REQUIRED, false, true, false),
                "REQUEST_RECTIFICATION", command.remark());
    }

    @Transactional
    public RepairWorkOrder evaluate(Long workOrderId, EvaluateRepairCommand command) {
        UserContext ctx = requireOwnerContext();
        RepairWorkOrder order = findMine(workOrderId);
        requireStatus(order, RepairWorkOrderStatus.COMPLETED);
        if (!ctx.accountId().equals(order.reporterAccountId())) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, "只有报修人可评价该工单");
        }
        if (command.satisfactionScore() == null || command.satisfactionScore() < 1 || command.satisfactionScore() > 5) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "satisfactionScore 必须为 1-5");
        }
        return transition(order, order.withEvaluation(command.satisfactionScore(), trim(command.comment()),
                RepairWorkOrderStatus.EVALUATED), "OWNER_EVALUATE", "业主评价");
    }

    @Transactional
    public RepairWorkOrder archive(Long workOrderId, RepairActionCommand command) {
        UserContext ctx = requireRole(GOVERNANCE_ROLES, "当前角色无权归档工单");
        RepairWorkOrder order = loadVisible(ctx, workOrderId);
        requireStatus(order, RepairWorkOrderStatus.COMPLETED, RepairWorkOrderStatus.EVALUATED);
        return transition(order, order.withStatus(RepairWorkOrderStatus.ARCHIVED, false, true, false),
                "ARCHIVE", command.remark());
    }

    private RepairWorkOrder duplicate(UserContext ctx, RepairSpaceScope scope, Long roomId, Long buildingId, String title) {
        return repository.findDuplicate(ctx.tenantId(), ctx.accountId(), scope, roomId, buildingId, title,
                LocalDateTime.now().minusMinutes(DEDUP_MINUTES)).orElse(null);
    }

    private RepairWorkOrder transition(RepairWorkOrder before, RepairWorkOrder after, String action, String remark) {
        int updated = repository.update(after);
        if (updated != 1) {
            throw new RepairWorkOrderApplicationException(INVALID_STATUS, "工单已被并发修改，请刷新后重试");
        }
        RepairWorkOrder persisted = repository.findById(before.workOrderId())
                .orElseThrow(() -> new RepairWorkOrderApplicationException(NOT_FOUND, "工单不存在"));
        event(persisted, action, before.status(), persisted.status(), trim(remark));
        return persisted;
    }

    private void event(RepairWorkOrder order,
                       String action,
                       RepairWorkOrderStatus fromStatus,
                       RepairWorkOrderStatus toStatus,
                       String remark) {
        UserContext ctx = userContextHolder.current();
        repository.insertEvent(new RepairWorkOrderEvent(
                null,
                order.workOrderId(),
                order.tenantId(),
                action,
                fromStatus,
                toStatus,
                ctx == null ? null : ctx.accountId(),
                ctx == null || ctx.identityType() == null ? null : ctx.identityType().name(),
                ctx == null ? null : ctx.activeIdentityId(),
                remark,
                "{}",
                null));
    }

    private void assertHandoverUnlocked(RepairWorkOrder order) {
        tenantTermLockGuard.lockedElectionForLargeAmount(order.tenantId(), order.planBudget())
                .ifPresent(electionId -> {
                    throw new RepairWorkOrderApplicationException(HANDOVER_LOCKED,
                            "换届锁定中，大额维修方案已熔断 electionSubjectId=" + electionId
                                    + " amount=" + order.planBudget());
                });
    }

    private boolean requiresGovernance(RepairWorkOrder order) {
        BigDecimal budget = order.planBudget() == null ? BigDecimal.ZERO : order.planBudget();
        String fundSource = order.fundSource() == null ? "" : order.fundSource();
        return budget.compareTo(GOVERNANCE_BUDGET_THRESHOLD) >= 0
                || !FUND_PROPERTY_INTERNAL.equals(fundSource);
    }

    private RepairWorkOrder loadVisible(UserContext ctx, Long workOrderId) {
        RepairWorkOrder order = repository.findById(workOrderId)
                .orElseThrow(() -> new RepairWorkOrderApplicationException(NOT_FOUND,
                        "工单不存在 workOrderId=" + workOrderId));
        assertVisible(ctx, order);
        return order;
    }

    private void assertVisible(UserContext ctx, RepairWorkOrder order) {
        boolean ownerGroup = "OWNER_GROUP".equals(ctx.dataScopeType() == null ? null : ctx.dataScopeType().getValue());
        if (!ownerGroup && ctx.tenantId() != null && !ctx.tenantId().equals(order.tenantId())) {
            throw new RepairWorkOrderApplicationException(NOT_FOUND, "工单不在当前租户范围内");
        }
        if (ownerGroup) {
            boolean assigned = ctx.userId() != null && ctx.userId().equals(order.assignedUserId());
            boolean buildingAllowed = order.buildingId() != null
                    && ctx.authorizedBuildingScopes().contains(
                            new WorkIdentityBuildingScope(order.tenantId(), order.buildingId()));
            if (!assigned && !buildingAllowed) {
                throw new RepairWorkOrderApplicationException(NOT_FOUND, "工单不在当前责任田范围内");
            }
        }
    }

    private UserContext requireOwnerContext() {
        UserContext ctx = userContextHolder.current();
        if (ctx == null || !ctx.isCUser() || ctx.uid() == null || ctx.tenantId() == null) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, "未识别到业主身份，禁止访问报修");
        }
        if (ctx.authLevel() == null || ctx.authLevel().getValue() < 2) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, "业主实名等级不足，禁止提交报修");
        }
        return ctx;
    }

    private UserContext requireSysContext() {
        UserContext ctx = userContextHolder.current();
        if (ctx == null || !ctx.isSysUser() || ctx.roleKey() == null) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, "未识别到管理端工作身份");
        }
        return ctx;
    }

    private UserContext requireRole(Set<String> roles, String message) {
        UserContext ctx = requireSysContext();
        if (!roles.contains(ctx.roleKey())) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, message + " roleKey=" + ctx.roleKey());
        }
        return ctx;
    }

    private Long requireTenant(UserContext ctx) {
        if (ctx.tenantId() == null) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, "未识别到租户上下文");
        }
        return ctx.tenantId();
    }

    private void requireStatus(RepairWorkOrder order, RepairWorkOrderStatus... allowed) {
        for (RepairWorkOrderStatus status : allowed) {
            if (order.status() == status) {
                return;
            }
        }
        throw new RepairWorkOrderApplicationException(INVALID_STATUS,
                "当前状态不允许该动作 status=" + order.status());
    }

    private String requireText(String value, String field) {
        String trimmed = trim(value);
        if (trimmed == null) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, field + " 必填");
        }
        return trimmed;
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
