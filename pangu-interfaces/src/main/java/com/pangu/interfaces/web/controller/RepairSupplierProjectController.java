// 关联业务：向施工单位工作台暴露本企业已签约维修项目及工程执行档案，禁止跨供应商读取。
package com.pangu.interfaces.web.controller;

import com.pangu.application.repair.RepairSupplierProjectService;
import com.pangu.application.repair.RepairSupplierProjectService.SupplierProjectDetails;
import com.pangu.application.repair.RepairSupplierProjectService.SupplierProjectSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/supplier/repair-projects")
@RequiredArgsConstructor
public class RepairSupplierProjectController extends BaseController {

    private final RepairSupplierProjectService supplierProjectService;

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
