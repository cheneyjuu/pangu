// 关联业务：提供业主大会议事规则的原件归档、结构化草稿、主任/副主任确认和审计查询接口。
package com.pangu.interfaces.web.controller;

import com.pangu.application.assembly.OwnersAssemblyApplicationException;
import com.pangu.application.assembly.OwnersAssemblyRuleService;
import com.pangu.application.assembly.command.CreateOwnersAssemblyRuleDraftCommand;
import com.pangu.domain.model.assembly.OwnersAssemblyRuleConfiguration;
import com.pangu.interfaces.web.controller.dto.assembly.OwnersAssemblyRuleAuditResponse;
import com.pangu.interfaces.web.controller.dto.assembly.OwnersAssemblyRuleConfigurationRequest;
import com.pangu.interfaces.web.controller.dto.assembly.OwnersAssemblyRuleDraftUpdateRequest;
import com.pangu.interfaces.web.controller.dto.assembly.OwnersAssemblyRuleFieldConfirmationResponse;
import com.pangu.interfaces.web.controller.dto.assembly.OwnersAssemblyRulePreviewTicketResponse;
import com.pangu.interfaces.web.controller.dto.assembly.OwnersAssemblyRuleResponse;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/** 管理端业主大会议事规则版本管理接口。 */
@RestController
@RequestMapping("/api/v1/admin/owners-assembly-rules")
@RequiredArgsConstructor
public class OwnersAssemblyRuleController extends BaseController {

    private final OwnersAssemblyRuleService service;

    @GetMapping
    @PreAuthorize("hasAuthority('owners-assembly:rule:read')")
    public Result<List<OwnersAssemblyRuleResponse>> list(
            @RequestParam(name = "tenantId", required = false) Long tenantId) {
        return success(service.list(tenantId).stream().map(OwnersAssemblyRuleResponse::from).toList());
    }

    @GetMapping("/active")
    @PreAuthorize("hasAuthority('owners-assembly:rule:read')")
    public Result<OwnersAssemblyRuleResponse> active(
            @RequestParam(name = "tenantId", required = false) Long tenantId) {
        return success(OwnersAssemblyRuleResponse.from(service.active(tenantId)));
    }

    @PostMapping(path = "/drafts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('owners-assembly:rule:draft')")
    public ResponseEntity<Result<OwnersAssemblyRuleResponse>> createDraft(
            @RequestParam(name = "tenantId", required = false) Long tenantId,
            @RequestParam("ruleName") String ruleName,
            @RequestParam("ruleVersion") String ruleVersion,
            @RequestParam("effectiveDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate effectiveDate,
            @RequestParam("changeReason") String changeReason,
            @Valid @RequestPart("configuration") OwnersAssemblyRuleConfigurationRequest configuration,
            @RequestPart("file") MultipartFile file) {
        try {
            var created = service.createDraft(tenantId, new CreateOwnersAssemblyRuleDraftCommand(
                    ruleName,
                    ruleVersion,
                    effectiveDate,
                    changeReason,
                    configuration.toDomain(),
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getBytes()));
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(success("业主大会议事规则草稿已创建", OwnersAssemblyRuleResponse.from(created)));
        } catch (IOException ex) {
            throw new OwnersAssemblyApplicationException(
                    OwnersAssemblyApplicationException.Reason.PARAM_INVALID, "读取议事规则原件失败", ex);
        }
    }

    @PutMapping("/{ruleId}/draft")
    @PreAuthorize("hasAuthority('owners-assembly:rule:draft')")
    public Result<OwnersAssemblyRuleResponse> updateDraft(
            @PathVariable Long ruleId,
            @RequestParam(name = "tenantId", required = false) Long tenantId,
            @Valid @RequestBody OwnersAssemblyRuleDraftUpdateRequest request) {
        return success("业主大会议事规则草稿已更新", OwnersAssemblyRuleResponse.from(
                service.updateDraft(ruleId, tenantId, request.toCommand())));
    }

    @PostMapping("/{ruleId}/submit")
    @PreAuthorize("hasAuthority('owners-assembly:rule:draft')")
    public Result<OwnersAssemblyRuleResponse> submitForConfirmation(
            @PathVariable Long ruleId,
            @RequestParam(name = "tenantId", required = false) Long tenantId) {
        return success("议事规则已提交主任或副主任逐项确认", OwnersAssemblyRuleResponse.from(
                service.submitForConfirmation(ruleId, tenantId)));
    }

    @PostMapping("/{ruleId}/activate")
    @PreAuthorize("hasAuthority('owners-assembly:rule:activate')")
    public Result<OwnersAssemblyRuleResponse> activate(
            @PathVariable Long ruleId,
            @RequestParam(name = "tenantId", required = false) Long tenantId) {
        return success("业主大会议事规则已确认启用", OwnersAssemblyRuleResponse.from(
                service.activate(ruleId, tenantId)));
    }

    @GetMapping("/{ruleId}/audits")
    @PreAuthorize("hasAuthority('owners-assembly:rule:read')")
    public Result<List<OwnersAssemblyRuleAuditResponse>> audits(
            @PathVariable Long ruleId,
            @RequestParam(name = "tenantId", required = false) Long tenantId) {
        return success(service.audits(ruleId, tenantId).stream().map(OwnersAssemblyRuleAuditResponse::from).toList());
    }

    @GetMapping("/{ruleId}/field-confirmations")
    @PreAuthorize("hasAuthority('owners-assembly:rule:read')")
    public Result<List<OwnersAssemblyRuleFieldConfirmationResponse>> fieldConfirmations(
            @PathVariable Long ruleId,
            @RequestParam(name = "tenantId", required = false) Long tenantId) {
        return success(service.fieldConfirmations(ruleId, tenantId).stream()
                .map(OwnersAssemblyRuleFieldConfirmationResponse::from)
                .toList());
    }

    @PostMapping("/{ruleId}/field-confirmations/{field}/confirm")
    @PreAuthorize("hasAuthority('owners-assembly:rule:activate')")
    public Result<OwnersAssemblyRuleFieldConfirmationResponse> confirmField(
            @PathVariable Long ruleId,
            @PathVariable OwnersAssemblyRuleConfiguration.RuleConfigurationField field,
            @RequestParam(name = "tenantId", required = false) Long tenantId) {
        return success("议事规则字段已核对", OwnersAssemblyRuleFieldConfirmationResponse.from(
                service.confirmField(ruleId, tenantId, field)));
    }

    @GetMapping("/{ruleId}/preview-ticket")
    @PreAuthorize("hasAuthority('owners-assembly:rule:read')")
    public Result<OwnersAssemblyRulePreviewTicketResponse> previewTicket(
            @PathVariable Long ruleId,
            @RequestParam(name = "tenantId", required = false) Long tenantId) {
        return success(OwnersAssemblyRulePreviewTicketResponse.from(
                service.createPreviewTicket(ruleId, tenantId)));
    }
}
