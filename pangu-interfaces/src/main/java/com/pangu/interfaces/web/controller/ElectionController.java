package com.pangu.interfaces.web.controller;

import com.pangu.domain.policy.AbacPolicyEngine;
import com.pangu.domain.policy.EvaluationResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 业委会选举与表决业务控制器
 */
@RestController
@RequestMapping("/api/v1/election")
public class ElectionController {

    @Autowired
    private AbacPolicyEngine abacPolicyEngine;

    /**
     * 业委会选举参选资格前置拦截校验 (方案C执行接口)
     */
    @GetMapping("/candidate/check-qualification")
    public ResponseEntity<Map<String, Object>> checkQualification(
            @RequestParam("tenant_id") Long tenantId,
            @RequestParam("uid") Long uid) {

        // 模拟从数据库中查询欠费状态：
        // 依照我们 V1.1__seed_mock_data.sql 播种数据：
        // 王五 (uid = 103) 名下的 opid 5003 房产状态 account_status = 2 (欠费挂起)
        // 张三 (101) 和 李四 (102) 状态均为正常 (1)
        boolean hasUnpaidFees = (uid == 103L);

        // 调用领域层 ABAC 策略评估引擎判定
        EvaluationResult result = abacPolicyEngine.evaluateCandidacy(uid, tenantId, hasUnpaidFees, "SCHEME_C");

        if (!result.isAllowed()) {
            // 被拦截，返回符合 PRD 要求的 403 详细报文
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "code", 403,
                    "msg", result.getMessage(),
                    "data", Map.of(
                            "policy_type", result.getPolicyType(),
                            "restriction_target", result.getRestrictionTarget(),
                            "is_voting_rights_retained", result.isVotingRightsRetained()
                    )
            ));
        }

        // 校验放行
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "msg", "资格校验通过，允许申报参选业委会委员",
                "data", Map.of(
                        "uid", uid,
                        "tenant_id", tenantId,
                        "is_eligible", true
                )
        ));
    }
}
