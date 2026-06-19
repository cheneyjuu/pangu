package com.pangu.interfaces.web.service;

import com.pangu.domain.context.UserContext;
import com.pangu.domain.gateway.PropertyGateway;
import com.pangu.domain.policy.AbacPolicyEngine;
import com.pangu.domain.policy.EvaluationResult;
import com.pangu.interfaces.security.SecurityUtils;
import com.pangu.interfaces.web.controller.dto.CandidateQualificationResult;
import com.pangu.interfaces.web.exception.AppException;
import com.pangu.interfaces.web.exception.CandidacyRestrictedException;
import com.pangu.interfaces.web.exception.CommonErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 业主自治与选举资格服务（M1 RBAC 重构后版本）。
 *
 * <p>仅消费 {@link UserContext} 业主端身份（{@code IdentityType.C_USER}）；
 * 管理端发起的资格审核走 {@code WaiverController / VotingController}。
 */
@Service
public class ElectionService {

    @Autowired
    private PropertyGateway propertyGateway;

    @Autowired
    private AbacPolicyEngine abacPolicyEngine;

    /**
     * 校验业委会委员候选人参选资格。
     */
    public CandidateQualificationResult checkCandidateQualification() {
        UserContext ctx = SecurityUtils.getUserContext();
        if (ctx == null) {
            throw new AppException(CommonErrorCode.UNAUTHORIZED, "无访问权限：认证失效，请重新登录");
        }
        if (!ctx.isCUser()) {
            throw new AppException(CommonErrorCode.FORBIDDEN, "仅业主身份可发起候选人资格自检");
        }

        Long uid = ctx.uid();
        Long tenantId = ctx.tenantId();
        if (uid == null || tenantId == null) {
            throw new AppException(CommonErrorCode.PARAM_ERROR, "参数错误：无法确定您的用户身份或当前所属小区");
        }

        boolean hasUnpaidFees = propertyGateway.hasUnpaidFees(uid, tenantId);

        EvaluationResult result = abacPolicyEngine.evaluateCandidacy(uid, tenantId, hasUnpaidFees, "SCHEME_C");
        if (!result.isAllowed()) {
            throw new CandidacyRestrictedException(CommonErrorCode.FORBIDDEN, Map.of(
                    "policy_type", result.getPolicyType(),
                    "restriction_target", result.getRestrictionTarget(),
                    "is_voting_rights_retained", result.isVotingRightsRetained()
            ), result.getMessage());
        }

        return CandidateQualificationResult.builder()
                .uid(uid)
                .tenantId(tenantId)
                .eligible(true)
                .build();
    }
}
