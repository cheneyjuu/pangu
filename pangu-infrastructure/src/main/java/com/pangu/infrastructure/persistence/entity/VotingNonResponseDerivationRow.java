// 关联业务：映射逐事项未反馈票认定的不可变审计记录。
package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class VotingNonResponseDerivationRow {
    private Long derivationId;
    private Long packageId;
    private Long subjectId;
    private Long electorateItemId;
    private Long tenantId;
    private Long representativeOpid;
    private Long representativeUid;
    private BigDecimal certifiedArea;
    private String nonResponsePolicy;
    private Integer derivedChoice;
    private String deliveryEvidenceHash;
    private String ruleSnapshotHash;
    private String reasonCode;
    private String rowHash;
    private Instant derivedAt;
}
