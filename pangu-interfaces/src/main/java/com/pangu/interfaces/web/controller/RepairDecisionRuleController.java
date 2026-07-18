// 关联业务：提供小区维修征询规则备案、历史查询和原件受控预览接口。
package com.pangu.interfaces.web.controller;

import com.pangu.application.repair.RepairDecisionRuleService;
import com.pangu.application.repair.RepairWorkOrderApplicationException;
import com.pangu.application.repair.command.RegisterRepairDecisionRuleCommand;
import com.pangu.domain.model.repair.RepairProjectGovernance.NonResponseRule;
import com.pangu.interfaces.web.controller.dto.repair.RepairDecisionRulePreviewTicketResponse;
import com.pangu.interfaces.web.controller.dto.repair.RepairDecisionRuleResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/repair-decision-rules")
@RequiredArgsConstructor
public class RepairDecisionRuleController extends BaseController {

    private final RepairDecisionRuleService service;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('community:settings:read','repair:workorder:read')")
    public Result<List<RepairDecisionRuleResponse>> list(
            @RequestParam(name = "tenantId", required = false) Long tenantId) {
        return success(service.list(tenantId).stream().map(RepairDecisionRuleResponse::from).toList());
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyAuthority('community:settings:read','repair:workorder:read')")
    public Result<RepairDecisionRuleResponse> active(
            @RequestParam(name = "tenantId", required = false) Long tenantId) {
        return success(RepairDecisionRuleResponse.from(service.active(tenantId)));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('community:settings:policy:write')")
    public ResponseEntity<Result<RepairDecisionRuleResponse>> register(
            @RequestParam(name = "tenantId", required = false) Long tenantId,
            @RequestParam("ruleName") String ruleName,
            @RequestParam("ruleVersion") String ruleVersion,
            @RequestParam("effectiveDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate effectiveDate,
            @RequestParam("deliveryRule") String deliveryRule,
            @RequestParam("nonResponseRule") String nonResponseRule,
            @RequestPart("file") MultipartFile file) {
        try {
            var rule = service.register(tenantId, new RegisterRepairDecisionRuleCommand(
                    ruleName, ruleVersion, effectiveDate, deliveryRule,
                    parseNonResponseRule(nonResponseRule), file.getOriginalFilename(),
                    file.getContentType(), file.getBytes()));
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(success("维修征询规则已备案", RepairDecisionRuleResponse.from(rule)));
        } catch (IOException ex) {
            throw new RepairWorkOrderApplicationException(
                    RepairWorkOrderApplicationException.Reason.PARAM_INVALID, "读取规则原件失败", ex);
        }
    }

    @GetMapping("/{ruleId}/preview-ticket")
    @PreAuthorize("hasAnyAuthority('community:settings:read','repair:workorder:read')")
    public Result<RepairDecisionRulePreviewTicketResponse> preview(
            @PathVariable("ruleId") Long ruleId,
            @RequestParam(name = "tenantId", required = false) Long tenantId) {
        return success(RepairDecisionRulePreviewTicketResponse.from(
                service.createPreviewTicket(ruleId, tenantId)));
    }

    private NonResponseRule parseNonResponseRule(String value) {
        try {
            return NonResponseRule.valueOf(value == null ? "" : value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new RepairWorkOrderApplicationException(
                    RepairWorkOrderApplicationException.Reason.PARAM_INVALID,
                    "不支持的未表态处理规则：" + value, ex);
        }
    }
}
