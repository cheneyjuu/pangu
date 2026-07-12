// 关联业务：提供业主自治组织电子印章台账、模拟制发、停用和用印审计接口。
package com.pangu.interfaces.web.controller;

import com.pangu.application.committee.CommitteeSealService;
import com.pangu.interfaces.web.controller.dto.committee.CommitteeElectronicSealResponse;
import com.pangu.interfaces.web.controller.dto.committee.CommitteeSealUsageResponse;
import com.pangu.interfaces.web.controller.dto.committee.CreateMockCommitteeSealRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/v1/admin/committee-seals")
@RequiredArgsConstructor
public class CommitteeSealController extends BaseController {

    private final CommitteeSealService service;

    @GetMapping
    @PreAuthorize("hasAuthority('committee:seal:read')")
    public Result<List<CommitteeElectronicSealResponse>> list() {
        return success(service.list().stream().map(CommitteeElectronicSealResponse::from).toList());
    }

    @GetMapping("/usage-records")
    @PreAuthorize("hasAuthority('committee:seal:read')")
    public Result<List<CommitteeSealUsageResponse>> listUsage(
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        return success(service.listUsage(limit).stream().map(CommitteeSealUsageResponse::from).toList());
    }

    @PostMapping("/mock")
    @PreAuthorize("hasAuthority('committee:seal:manage')")
    public Result<CommitteeElectronicSealResponse> createMock(
            @Valid @RequestBody CreateMockCommitteeSealRequest request) {
        return success("模拟电子印章已创建，仅限开发测试环境且无法律效力",
                CommitteeElectronicSealResponse.from(service.createMock(request.toCommand())));
    }

    @PostMapping("/{sealId}/deactivate")
    @PreAuthorize("hasAuthority('committee:seal:manage')")
    public Result<CommitteeElectronicSealResponse> deactivate(@PathVariable("sealId") Long sealId) {
        return success("电子印章已停用", CommitteeElectronicSealResponse.from(service.deactivate(sealId)));
    }
}
