// 关联业务：向当前业主提供已发布业主大会的只读公示和锁定材料下载接口。
package com.pangu.interfaces.web.controller;

import com.pangu.application.assembly.OwnerAssemblyDisclosure;
import com.pangu.application.assembly.OwnerAssemblyDisclosureService;
import com.pangu.interfaces.web.controller.dto.assembly.OwnerAssemblyMaterialDownloadTicketResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * C 端业主大会披露入口。
 *
 * <p>该入口不提供线上投票写操作；服务层会复核当前 C 端业主身份、租户、已发布状态和本人房产资格，
 * 并且不返回任何具体表决选择。
 */
@RestController
@RequestMapping("/api/v1/me/owners-assembly-disclosures")
@RequiredArgsConstructor
public class OwnerAssemblyDisclosureController extends BaseController {

    private final OwnerAssemblyDisclosureService service;

    @GetMapping("/{packageId}")
    @PreAuthorize("isAuthenticated()")
    public Result<OwnerAssemblyDisclosure> disclosure(@PathVariable Long packageId) {
        return success(service.disclosure(packageId));
    }

    @GetMapping("/{packageId}/materials/{materialId}/download-ticket")
    @PreAuthorize("isAuthenticated()")
    public Result<OwnerAssemblyMaterialDownloadTicketResponse> materialDownloadTicket(
            @PathVariable Long packageId,
            @PathVariable Long materialId) {
        return success(OwnerAssemblyMaterialDownloadTicketResponse.from(
                service.createMaterialDownloadTicket(packageId, materialId)));
    }
}
