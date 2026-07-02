package com.pangu.interfaces.web.controller;

import com.pangu.application.repair.RepairWorkOrderApplicationException;
import com.pangu.application.repair.RepairWorkOrderService;
import com.pangu.application.repair.command.AssignRepairCommand;
import com.pangu.application.repair.command.CorrectRepairLocationCommand;
import com.pangu.application.repair.command.CreatePrivateRepairCommand;
import com.pangu.application.repair.command.CreatePublicRepairCommand;
import com.pangu.application.repair.command.EvaluateRepairCommand;
import com.pangu.application.repair.command.RepairActionCommand;
import com.pangu.application.repair.command.SubmitRepairPlanCommand;
import com.pangu.domain.common.Page;
import com.pangu.domain.model.repair.RepairSpaceScope;
import com.pangu.domain.model.repair.RepairWorkOrder;
import com.pangu.domain.model.repair.RepairWorkOrderStatus;
import com.pangu.interfaces.web.controller.dto.PageResponse;
import com.pangu.interfaces.web.controller.dto.repair.AssignRepairRequest;
import com.pangu.interfaces.web.controller.dto.repair.CorrectRepairLocationRequest;
import com.pangu.interfaces.web.controller.dto.repair.CreatePrivateRepairRequest;
import com.pangu.interfaces.web.controller.dto.repair.CreatePublicRepairRequest;
import com.pangu.interfaces.web.controller.dto.repair.EvaluateRepairRequest;
import com.pangu.interfaces.web.controller.dto.repair.RepairActionRequest;
import com.pangu.interfaces.web.controller.dto.repair.RepairWorkOrderEventResponse;
import com.pangu.interfaces.web.controller.dto.repair.RepairWorkOrderResponse;
import com.pangu.interfaces.web.controller.dto.repair.SubmitRepairPlanRequest;
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

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RepairWorkOrderController extends BaseController {

    private final RepairWorkOrderService service;

    @GetMapping("/me/repairs")
    @PreAuthorize("isAuthenticated()")
    public Result<List<RepairWorkOrderResponse>> listMine() {
        return success(service.listMine().stream().map(RepairWorkOrderResponse::from).toList());
    }

    @GetMapping("/me/repairs/{workOrderId}")
    @PreAuthorize("isAuthenticated()")
    public Result<RepairWorkOrderResponse> findMine(@PathVariable("workOrderId") Long workOrderId) {
        return success(RepairWorkOrderResponse.from(service.findMine(workOrderId)));
    }

    @PostMapping("/me/repairs/private")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<RepairWorkOrderResponse>> createPrivate(
            @Valid @RequestBody CreatePrivateRepairRequest request) {
        RepairWorkOrder order = service.createPrivate(new CreatePrivateRepairCommand(
                request.opid(), request.title(), request.description(), request.category(), request.evidenceText()));
        return ResponseEntity.status(HttpStatus.CREATED).body(success(RepairWorkOrderResponse.from(order)));
    }

    @PostMapping("/me/repairs/public")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<RepairWorkOrderResponse>> createPublic(
            @Valid @RequestBody CreatePublicRepairRequest request) {
        RepairWorkOrder order = service.createPublic(toCommand(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(success(RepairWorkOrderResponse.from(order)));
    }

    @PostMapping("/me/repairs/{workOrderId}/evaluation")
    @PreAuthorize("isAuthenticated()")
    public Result<RepairWorkOrderResponse> evaluate(@PathVariable("workOrderId") Long workOrderId,
                                                    @Valid @RequestBody EvaluateRepairRequest request) {
        RepairWorkOrder order = service.evaluate(workOrderId,
                new EvaluateRepairCommand(request.satisfactionScore(), request.comment()));
        return success(RepairWorkOrderResponse.from(order));
    }

    @GetMapping("/admin/repair-work-orders")
    @PreAuthorize("hasAuthority('repair:workorder:read')")
    public Result<PageResponse<RepairWorkOrderResponse>> pageAdmin(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "scope", required = false) String scope,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        Page<RepairWorkOrder> result = service.pageAdmin(parseStatus(status), parseScope(scope), keyword, page, size);
        return success(PageResponse.from(result, RepairWorkOrderResponse::from));
    }

    @PostMapping("/admin/repair-work-orders")
    @PreAuthorize("hasAnyAuthority('repair:workorder:manage','repair:workorder:field')")
    public ResponseEntity<Result<RepairWorkOrderResponse>> createAdminPublic(
            @Valid @RequestBody CreatePublicRepairRequest request) {
        RepairWorkOrder order = service.createAdminPublic(toCommand(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(success(RepairWorkOrderResponse.from(order)));
    }

    @GetMapping("/admin/repair-work-orders/{workOrderId}")
    @PreAuthorize("hasAuthority('repair:workorder:read')")
    public Result<RepairWorkOrderResponse> findAdmin(@PathVariable("workOrderId") Long workOrderId) {
        return success(RepairWorkOrderResponse.from(service.findAdmin(workOrderId)));
    }

    @GetMapping("/admin/repair-work-orders/{workOrderId}/events")
    @PreAuthorize("hasAuthority('repair:workorder:read')")
    public Result<List<RepairWorkOrderEventResponse>> events(@PathVariable("workOrderId") Long workOrderId) {
        return success(service.listEvents(workOrderId).stream().map(RepairWorkOrderEventResponse::from).toList());
    }

    @PostMapping("/admin/repair-work-orders/{workOrderId}/accept")
    @PreAuthorize("hasAnyAuthority('repair:workorder:manage','repair:workorder:field')")
    public Result<RepairWorkOrderResponse> accept(@PathVariable("workOrderId") Long workOrderId,
                                                  @Valid @RequestBody(required = false) RepairActionRequest request) {
        return success(RepairWorkOrderResponse.from(service.accept(workOrderId, action(request))));
    }

    @PostMapping("/admin/repair-work-orders/{workOrderId}/correct-location")
    @PreAuthorize("hasAuthority('repair:workorder:field')")
    public Result<RepairWorkOrderResponse> correctLocation(@PathVariable("workOrderId") Long workOrderId,
                                                           @Valid @RequestBody CorrectRepairLocationRequest request) {
        RepairWorkOrder order = service.correctLocation(workOrderId, new CorrectRepairLocationCommand(
                request.buildingId(), request.roomId(), request.locationText(), request.reason()));
        return success(RepairWorkOrderResponse.from(order));
    }

    @PostMapping("/admin/repair-work-orders/{workOrderId}/verify-location")
    @PreAuthorize("hasAuthority('repair:workorder:field')")
    public Result<RepairWorkOrderResponse> verifyLocation(@PathVariable("workOrderId") Long workOrderId,
                                                          @Valid @RequestBody(required = false) RepairActionRequest request) {
        return success(RepairWorkOrderResponse.from(service.verifyLocation(workOrderId, action(request))));
    }

    @PostMapping("/admin/repair-work-orders/{workOrderId}/assign")
    @PreAuthorize("hasAuthority('repair:workorder:manage')")
    public Result<RepairWorkOrderResponse> assign(@PathVariable("workOrderId") Long workOrderId,
                                                  @Valid @RequestBody AssignRepairRequest request) {
        RepairWorkOrder order = service.assign(workOrderId, new AssignRepairCommand(
                request.assignedUserId(), request.assigneeRoleKey(), request.remark()));
        return success(RepairWorkOrderResponse.from(order));
    }

    @PostMapping("/admin/repair-work-orders/{workOrderId}/start-survey")
    @PreAuthorize("hasAuthority('repair:workorder:field')")
    public Result<RepairWorkOrderResponse> startSurvey(@PathVariable("workOrderId") Long workOrderId,
                                                       @Valid @RequestBody(required = false) RepairActionRequest request) {
        return success(RepairWorkOrderResponse.from(service.startSurvey(workOrderId, action(request))));
    }

    @PostMapping("/admin/repair-work-orders/{workOrderId}/submit-plan")
    @PreAuthorize("hasAuthority('repair:workorder:field')")
    public Result<RepairWorkOrderResponse> submitPlan(@PathVariable("workOrderId") Long workOrderId,
                                                      @Valid @RequestBody SubmitRepairPlanRequest request) {
        RepairWorkOrder order = service.submitPlan(workOrderId, new SubmitRepairPlanCommand(
                request.surveySummary(), request.riskLevel(), request.planBudget(), request.fundSource(), request.remark()));
        return success(RepairWorkOrderResponse.from(order));
    }

    @PostMapping("/admin/repair-work-orders/{workOrderId}/route-plan")
    @PreAuthorize("hasAuthority('repair:workorder:manage')")
    public Result<RepairWorkOrderResponse> routePlan(@PathVariable("workOrderId") Long workOrderId,
                                                     @Valid @RequestBody(required = false) RepairActionRequest request) {
        return success(RepairWorkOrderResponse.from(service.routePlan(workOrderId, action(request))));
    }

    @PostMapping("/admin/repair-work-orders/{workOrderId}/governance-approve")
    @PreAuthorize("hasAuthority('repair:workorder:governance')")
    public Result<RepairWorkOrderResponse> governanceApprove(@PathVariable("workOrderId") Long workOrderId,
                                                             @Valid @RequestBody(required = false) RepairActionRequest request) {
        return success(RepairWorkOrderResponse.from(service.governanceApprove(workOrderId, action(request))));
    }

    @PostMapping("/admin/repair-work-orders/{workOrderId}/start-work")
    @PreAuthorize("hasAuthority('repair:workorder:field')")
    public Result<RepairWorkOrderResponse> startWork(@PathVariable("workOrderId") Long workOrderId,
                                                     @Valid @RequestBody(required = false) RepairActionRequest request) {
        return success(RepairWorkOrderResponse.from(service.startWork(workOrderId, action(request))));
    }

    @PostMapping("/admin/repair-work-orders/{workOrderId}/submit-acceptance")
    @PreAuthorize("hasAuthority('repair:workorder:field')")
    public Result<RepairWorkOrderResponse> submitAcceptance(@PathVariable("workOrderId") Long workOrderId,
                                                            @Valid @RequestBody(required = false) RepairActionRequest request) {
        return success(RepairWorkOrderResponse.from(service.submitAcceptance(workOrderId, action(request))));
    }

    @PostMapping("/admin/repair-work-orders/{workOrderId}/accept-completed")
    @PreAuthorize("hasAuthority('repair:workorder:governance')")
    public Result<RepairWorkOrderResponse> acceptCompleted(@PathVariable("workOrderId") Long workOrderId,
                                                           @Valid @RequestBody(required = false) RepairActionRequest request) {
        return success(RepairWorkOrderResponse.from(service.acceptCompleted(workOrderId, action(request))));
    }

    @PostMapping("/admin/repair-work-orders/{workOrderId}/request-rectification")
    @PreAuthorize("hasAuthority('repair:workorder:governance')")
    public Result<RepairWorkOrderResponse> requestRectification(@PathVariable("workOrderId") Long workOrderId,
                                                                @Valid @RequestBody(required = false) RepairActionRequest request) {
        return success(RepairWorkOrderResponse.from(service.requestRectification(workOrderId, action(request))));
    }

    @PostMapping("/admin/repair-work-orders/{workOrderId}/archive")
    @PreAuthorize("hasAuthority('repair:workorder:governance')")
    public Result<RepairWorkOrderResponse> archive(@PathVariable("workOrderId") Long workOrderId,
                                                   @Valid @RequestBody(required = false) RepairActionRequest request) {
        return success(RepairWorkOrderResponse.from(service.archive(workOrderId, action(request))));
    }

    private CreatePublicRepairCommand toCommand(CreatePublicRepairRequest request) {
        return new CreatePublicRepairCommand(
                request.buildingId(), request.locationText(), request.title(),
                request.description(), request.category(), request.evidenceText());
    }

    private RepairActionCommand action(RepairActionRequest request) {
        return new RepairActionCommand(request == null ? null : request.remark());
    }

    private RepairWorkOrderStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return RepairWorkOrderStatus.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new RepairWorkOrderApplicationException(
                    RepairWorkOrderApplicationException.Reason.PARAM_INVALID,
                    "未知报修状态 status=" + value);
        }
    }

    private RepairSpaceScope parseScope(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return RepairSpaceScope.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new RepairWorkOrderApplicationException(
                    RepairWorkOrderApplicationException.Reason.PARAM_INVALID,
                    "未知报修范围 scope=" + value);
        }
    }
}
