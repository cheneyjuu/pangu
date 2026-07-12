// 关联业务：映射业主自治组织电子印章台账的持久化字段。
package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CommitteeElectronicSealRow {
    private Long electronicSealId;
    private Long tenantId;
    private String sealName;
    private String sealType;
    private String providerCode;
    private String providerSealId;
    private String certificateSerial;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
    private String status;
    private Long custodianUserId;
    private String custodianName;
    private String committeeTermName;
    private Integer simulated;
    private Long createdByUserId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
