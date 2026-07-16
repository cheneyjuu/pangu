// 关联业务：向施工单位工作台暴露本企业已签约维修项目及工程执行档案，禁止跨供应商读取。
package com.pangu.interfaces.web.controller;

import com.pangu.application.repair.RepairProjectSourcingService;
import com.pangu.application.repair.RepairSupplierProjectService;
import com.pangu.application.repair.RepairSupplierProjectService.SupplierProjectDetails;
import com.pangu.application.repair.RepairSupplierProjectService.SupplierProjectSummary;
import com.pangu.domain.model.repair.RepairProjectSourcing.Quote;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/supplier/repair-projects")
@RequiredArgsConstructor
public class RepairSupplierProjectController extends BaseController {

    private final RepairSupplierProjectService supplierProjectService;
    private final RepairProjectSourcingService sourcingService;

    @GetMapping("/quote-opportunities")
    @PreAuthorize("hasAuthority('repair:workorder:supplier')")
    public Result<List<RepairProjectSourcingService.SupplierOpportunity>> quoteOpportunities() {
        return success(sourcingService.listSupplierOpportunities());
    }

    @PostMapping("/{projectId}/quotes")
    @PreAuthorize("hasAuthority('repair:workorder:supplier')")
    public Result<Quote> submitQuote(
            @PathVariable("projectId") Long projectId,
            @Valid @RequestBody SubmitQuoteRequest request) {
        return success("维修工程报价已提交", sourcingService.submitQuote(projectId, request.toCommand()));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('repair:workorder:supplier')")
    public Result<List<SupplierProjectSummary>> list() {
        return success(supplierProjectService.listAssignedProjects());
    }

    @GetMapping("/{projectId}")
    @PreAuthorize("hasAuthority('repair:workorder:supplier')")
    public Result<SupplierProjectDetails> details(@PathVariable("projectId") Long projectId) {
        return success(supplierProjectService.details(projectId));
    }
}
