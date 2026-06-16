package com.pangu.interfaces.web.controller;

import com.pangu.infrastructure.persistence.handler.DataScopeInterceptor.UserSecurityContext;
import com.pangu.interfaces.web.controller.dto.CandidateQualificationResult;
import com.pangu.interfaces.web.service.ElectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 业委会选举与表决业务控制器
 */
@RestController
@RequestMapping("/api/v1/election")
public class ElectionController extends BaseController {

    @Autowired
    private ElectionService electionService;

    /**
     * 业委会选举参选资格前置拦截校验 (RESTful 强类型接口)
     */
    @GetMapping("/candidates/me/eligibility")
    public Result<CandidateQualificationResult> checkQualification(@AuthenticationPrincipal UserSecurityContext userCtx) {
        CandidateQualificationResult result = electionService.checkCandidateQualification(userCtx);
        return success("资格校验完成", result);
    }
}
