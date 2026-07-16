// 关联业务：由物业经理为已登记维修供应商创建独立的后台账号激活邀请。
package com.pangu.interfaces.web.controller;

import com.pangu.application.repair.SupplierActivationResult;
import com.pangu.application.repair.SupplierActivationService;
import com.pangu.application.repair.command.ActivateSupplierAccountCommand;
import com.pangu.application.repair.command.CreateSupplierActivationInvitationCommand;
import com.pangu.domain.model.repair.SupplierActivationInvitation;
import com.pangu.interfaces.web.controller.dto.repair.ActivateSupplierAccountRequest;
import com.pangu.interfaces.web.controller.dto.repair.CreateSupplierActivationInvitationRequest;
import com.pangu.interfaces.web.controller.dto.repair.SupplierActivationInvitationResponse;
import com.pangu.interfaces.web.controller.dto.repair.SupplierActivationResponse;
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

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SupplierActivationController extends BaseController {

    private final SupplierActivationService service;

    @PostMapping("/admin/supplier-organizations/{supplierDeptId}/activation-invitations")
    @PreAuthorize("hasAuthority('repair:supplier:manage')")
    public ResponseEntity<Result<SupplierActivationInvitationResponse>> createInvitation(
            @PathVariable("supplierDeptId") Long supplierDeptId,
            @Valid @RequestBody(required = false) CreateSupplierActivationInvitationRequest request) {
        CreateSupplierActivationInvitationRequest safe = request == null
                ? new CreateSupplierActivationInvitationRequest(null, null, null)
                : request;
        SupplierActivationInvitation invitation = service.createInvitation(supplierDeptId,
                new CreateSupplierActivationInvitationCommand(
                        safe.contactName(), safe.contactPhone(), safe.validHours()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(success(SupplierActivationInvitationResponse.from(invitation)));
    }

    @PostMapping("/supplier-activation/activate")
    @PreAuthorize("permitAll()")
    public Result<SupplierActivationResponse> activate(
            @Valid @RequestBody ActivateSupplierAccountRequest request) {
        SupplierActivationResult result = service.activate(new ActivateSupplierAccountCommand(
                request.invitationId(), request.phone(), request.smsCode(), request.operatorName()));
        return success(SupplierActivationResponse.from(result));
    }
}
