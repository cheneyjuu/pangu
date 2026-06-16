package com.pangu.domain.model.user;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * 业主社区身份领域模型 (OPID 实体)
 * 处理同一自然人账号在不同小区的身份映射
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerIdentity {

    /** 社区业主业务实体ID (OPID) */
    private Long opid;

    /** 关联的全局自然人ID (UID) */
    private Long uid;

    /** 租户ID（关联特定小区SaaS实例） */
    private Long tenantId;
}
