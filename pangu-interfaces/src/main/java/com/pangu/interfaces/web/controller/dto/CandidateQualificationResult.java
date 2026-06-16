package com.pangu.interfaces.web.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 业委会候选人参选资格校验结果 DTO (强类型结构)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CandidateQualificationResult {
    /** 自然人 ID */
    private Long uid;
    
    /** 小区租户 ID */
    private Long tenantId;
    
    /** 是否具备参选资格 */
    private boolean eligible;
}
