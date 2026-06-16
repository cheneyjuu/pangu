package com.pangu.interfaces.web.service;

import com.pangu.domain.gateway.PropertyGateway;
import com.pangu.domain.policy.AbacPolicyEngine;
import com.pangu.domain.policy.EvaluationResult;
import com.pangu.infrastructure.persistence.handler.DataScopeInterceptor.UserSecurityContext;
import com.pangu.interfaces.web.controller.AppException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 业主自治与选举资格服务 (控制层与领域层的业务编排载体)
 */
@Service
public class ElectionService {

    @Autowired
    private PropertyGateway propertyGateway;

    @Autowired
    private AbacPolicyEngine abacPolicyEngine;

    /**
     * 校验业委会委员候选人参选资格
     * @param userCtx 当前已认证用户的安全上下文
     * @return 资格通过后的描述数据
     */
    public Map<String, Object> checkCandidateQualification(UserSecurityContext userCtx) {
        if (userCtx == null) {
            throw new AppException(401, "无访问权限：认证失效，请重新登录");
        }

        Long uid = userCtx.getUid();
        Long tenantId = userCtx.getTenantId();

        if (uid == null || tenantId == null) {
            throw new AppException(400, "参数错误：无法确定您的用户身份或当前所属小区");
        }

        // 1. 动态数据查询：从数据库查询该业主名下绑定的任意房产是否处于非正常/欠费状态
        boolean hasUnpaidFees = propertyGateway.hasUnpaidFees(uid, tenantId);

        // 2. 调用领域层 ABAC 策略评估引擎进行判定 (默认采用 SCHEME_C 限制被选举权)
        EvaluationResult result = abacPolicyEngine.evaluateCandidacy(uid, tenantId, hasUnpaidFees, "SCHEME_C");

        if (!result.isAllowed()) {
            // 被拦截，抛出带详细限制元数据的 403 AppException，由全局异常处理器捕获返回给客户端
            throw new AppException(403, result.getMessage(), Map.of(
                    "policy_type", result.getPolicyType(),
                    "restriction_target", result.getRestrictionTarget(),
                    "is_voting_rights_retained", result.isVotingRightsRetained()
            ));
        }

        // 3. 校验通过，返回资格合格的成功声明
        return Map.of(
                "uid", uid,
                "tenant_id", tenantId,
                "is_eligible", true
        );
    }
}
