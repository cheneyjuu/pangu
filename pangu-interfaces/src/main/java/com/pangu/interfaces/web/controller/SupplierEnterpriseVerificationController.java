// 关联业务：提供物业手工核验、可替换平台核验及供应商核验审计历史接口。
package com.pangu.interfaces.web.controller;

import com.pangu.application.repair.SupplierEnterpriseVerificationService;
import com.pangu.application.repair.command.ManualSupplierEnterpriseVerificationCommand;
import com.pangu.application.repair.command.PlatformSupplierEnterpriseVerificationCommand;
import com.pangu.domain.model.repair.SupplierEnterpriseVerificationRecord;
import com.pangu.interfaces.web.controller.dto.repair.EnterpriseVerificationProviderResponse;
import com.pangu.interfaces.web.controller.dto.repair.ManualSupplierEnterpriseVerificationRequest;
import com.pangu.interfaces.web.controller.dto.repair.PlatformSupplierEnterpriseVerificationRequest;
import com.pangu.interfaces.web.controller.dto.repair.SupplierEnterpriseVerificationResponse;
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

@RestController
@RequestMapping("/api/v1/admin/supplier-organizations")
@RequiredArgsConstructor
public class SupplierEnterpriseVerificationController extends BaseController {

    private final SupplierEnterpriseVerificationService service;

    @GetMapping("/verification-provider")
    @PreAuthorize("hasAuthority('repair:supplier:verify')")
    public Result<EnterpriseVerificationProviderResponse> providerDescriptor() {
        return success(EnterpriseVerificationProviderResponse.from(service.providerDescriptor()));
    }

    @PostMapping("/{supplierDeptId}/manual-verifications")
    @PreAuthorize("hasAuthority('repair:supplier:verify')")
    public ResponseEntity<Result<SupplierEnterpriseVerificationResponse>> verifyManually(
            @PathVariable("supplierDeptId") Long supplierDeptId,
            @Valid @RequestBody ManualSupplierEnterpriseVerificationRequest request) {
        SupplierEnterpriseVerificationRecord record = service.verifyManually(
                supplierDeptId,
                new ManualSupplierEnterpriseVerificationCommand(
                        request.unifiedSocialCreditCode(), request.sourceCode(), request.verificationResult(),
                        request.evidenceReference(), request.remark()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(success(SupplierEnterpriseVerificationResponse.from(record)));
    }

    @PostMapping("/{supplierDeptId}/platform-verifications")
    @PreAuthorize("hasAuthority('repair:supplier:verify')")
    public ResponseEntity<Result<SupplierEnterpriseVerificationResponse>> verifyWithPlatform(
            @PathVariable("supplierDeptId") Long supplierDeptId,
            @Valid @RequestBody PlatformSupplierEnterpriseVerificationRequest request) {
        SupplierEnterpriseVerificationRecord record = service.verifyWithPlatform(
                supplierDeptId,
                new PlatformSupplierEnterpriseVerificationCommand(
                        request.unifiedSocialCreditCode(), request.supplierAuthorizationConfirmed()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(success(SupplierEnterpriseVerificationResponse.from(record)));
    }

    @GetMapping("/{supplierDeptId}/verifications")
    @PreAuthorize("hasAuthority('repair:supplier:verify')")
    public Result<List<SupplierEnterpriseVerificationResponse>> listHistory(
            @PathVariable("supplierDeptId") Long supplierDeptId) {
        return success(service.listHistory(supplierDeptId).stream()
                .map(SupplierEnterpriseVerificationResponse::from)
                .toList());
    }
}
