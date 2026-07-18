// 关联业务：暴露维修工程项目邀价、报价，以及业委会依据有效授权确认中选供应商的接口。
package com.pangu.interfaces.web.controller;

import com.pangu.application.repair.RepairProjectSourcingService;
import com.pangu.domain.model.repair.RepairProjectSourcing.Details;
import com.pangu.domain.model.repair.RepairProjectSourcing.Quote;
import com.pangu.interfaces.web.controller.dto.repair.RepairProjectSourcingRequests.InviteRequest;
import com.pangu.interfaces.web.controller.dto.repair.RepairProjectSourcingRequests.RevisionRequest;
import com.pangu.interfaces.web.controller.dto.repair.RepairProjectSourcingRequests.SelectQuoteRequest;
import com.pangu.interfaces.web.controller.dto.repair.RepairProjectSourcingRequests.SubmitQuoteRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/repair-projects/{projectId}/sourcing")
@RequiredArgsConstructor
public class RepairProjectSourcingController extends BaseController {

    private final RepairProjectSourcingService sourcingService;

    @GetMapping
    @PreAuthorize("hasAuthority('repair:workorder:read')")
    public Result<Details> details(@PathVariable("projectId") Long projectId) {
        return success(sourcingService.details(projectId));
    }

    @PostMapping("/invitations")
    @PreAuthorize("hasAuthority('repair:workorder:manage')")
    public Result<Details> invite(
            @PathVariable("projectId") Long projectId,
            @Valid @RequestBody InviteRequest request) {
        return success("维修工程邀价已发出", sourcingService.invite(projectId, request.toCommand()));
    }

    @PostMapping("/quote-revisions")
    @PreAuthorize("hasAuthority('repair:workorder:manage')")
    public Result<Details> requestRevisions(
            @PathVariable("projectId") Long projectId,
            @Valid @RequestBody RevisionRequest request) {
        return success("供应商报价修订要求已发出",
                sourcingService.requestRevisions(projectId, request.toCommand()));
    }

    @PostMapping("/quotes")
    @PreAuthorize("hasAuthority('repair:workorder:manage')")
    public Result<Quote> submitPropertyQuote(
            @PathVariable("projectId") Long projectId,
            @Valid @RequestBody SubmitQuoteRequest request) {
        return success("供应商原始报价已登记",
                sourcingService.submitQuote(projectId, request.toCommand()));
    }

    @PostMapping("/selection")
    @PreAuthorize("hasAuthority('repair:workorder:governance')")
    public Result<Details> confirmSelection(
            @PathVariable("projectId") Long projectId,
            @Valid @RequestBody SelectQuoteRequest request) {
        return success("业委会已确认维修工程中选供应商",
                sourcingService.selectQuote(projectId, request.toCommand()));
    }
}
