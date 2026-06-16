package com.pangu.interfaces.web.controller;

import com.pangu.domain.gateway.PropertyGateway;
import com.pangu.domain.policy.AbacPolicyEngine;
import com.pangu.domain.policy.EvaluationResult;
import com.pangu.infrastructure.persistence.handler.DataScopeInterceptor.UserSecurityContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 业委会选举与表决业务控制器
 */
@RestController
@RequestMapping("/api/v1/election")
public class ElectionController extends BaseController {

    @Autowired
    private AbacPolicyEngine abacPolicyEngine;

    @Autowired
    private PropertyGateway propertyGateway;

    /**
     * 业委会选举参选资格前置拦截校验 (方案C执行接口)
     */
    @GetMapping("/candidate/check-qualification")
    public Result<Map<String, Object>> checkQualification(@AuthenticationPrincipal UserSecurityContext userCtx) {
        if (userCtx == null) {
            throw new AppException(401, "无访问权限：认证失效，请重新登录");
        }

        Long uid = userCtx.getUid();
        Long tenantId = userCtx.getTenantId();

        if (uid == null || tenantId == null) {
            throw new AppException(400, "参数错误：无法确定您的用户身份或当前所属小区");
        }

        // 动态数据查询：从数据库查询该业主名下绑定的任意房产是否处于非正常/欠费状态
        boolean hasUnpaidFees = propertyGateway.hasUnpaidFees(uid, tenantId);

        // 调用领域层 ABAC 策略评估引擎进行判定
        EvaluationResult result = abacPolicyEngine.evaluateCandidacy(uid, tenantId, hasUnpaidFees, "SCHEME_C");

        if (!result.isAllowed()) {
            // 被拦截，抛出带详细数据的 403 AppException，由全局异常处理器捕获
            throw new AppException(403, result.getMessage(), Map.of(
                    "policy_type", result.getPolicyType(),
                    "restriction_target", result.getRestrictionTarget(),
                    "is_voting_rights_retained", result.isVotingRightsRetained()
            ));
        }

        // 校验放行
        return success("资格校验通过，允许申报参选业委会委员", Map.of(
                "uid", uid,
                "tenant_id", tenantId,
                "is_eligible", true
        ));
    }
}
