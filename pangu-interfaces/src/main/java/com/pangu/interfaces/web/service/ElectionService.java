package com.pangu.interfaces.web.service;

import com.pangu.domain.gateway.PropertyGateway;
import com.pangu.domain.policy.AbacPolicyEngine;
import com.pangu.domain.policy.EvaluationResult;
import com.pangu.infrastructure.persistence.handler.DataScopeInterceptor.UserSecurityContext;
import com.pangu.interfaces.web.controller.AppException;
import com.pangu.interfaces.web.controller.CandidacyRestrictedException;
import com.pangu.interfaces.web.controller.CommonErrorCode;
import com.pangu.interfaces.web.controller.dto.CandidateQualificationResult;
import com.pangu.interfaces.security.SecurityUtils;
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
     * @return 资格通过后的描述数据
     */
    public CandidateQualificationResult checkCandidateQualification() {
        UserSecurityContext userCtx = SecurityUtils.getUserContext();
        if (userCtx == null) {
            throw new AppException(CommonErrorCode.UNAUTHORIZED, "无访问权限：认证失效，请重新登录");
        }

        Long uid = userCtx.getUid();
        Long tenantId = userCtx.getTenantId();

        if (uid == null || tenantId == null) {
            throw new AppException(CommonErrorCode.PARAM_ERROR, "参数错误：无法确定您的用户身份或当前所属小区");
        }

        // 1. 动态数据查询：从数据库查询该业主名下绑定的任意房产是否处于非正常/欠费状态
        boolean hasUnpaidFees = propertyGateway.hasUnpaidFees(uid, tenantId);

        // 2. 调用领域层 ABAC 策略评估引擎进行判定 (默认采用 SCHEME_C 限制被选举权)
        EvaluationResult result = abacPolicyEngine.evaluateCandidacy(uid, tenantId, hasUnpaidFees, "SCHEME_C");

        if (!result.isAllowed()) {
            // 被拦截，抛出带详细限制元数据的 403 业务异常，由全局异常处理器捕获返回给客户端
            throw new CandidacyRestrictedException(CommonErrorCode.FORBIDDEN, Map.of(
                    "policy_type", result.getPolicyType(),
                    "restriction_target", result.getRestrictionTarget(),
                    "is_voting_rights_retained", result.isVotingRightsRetained()
            ), result.getMessage());
        }

        // 3. 校验通过，返回强类型资格结果
        return CandidateQualificationResult.builder()
                .uid(uid)
                .tenantId(tenantId)
                .eligible(true)
                .build();
    }
}
