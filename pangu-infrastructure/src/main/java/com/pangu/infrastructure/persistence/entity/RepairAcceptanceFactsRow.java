// 关联业务：承载维修验收定案所需的各角色最新有效事实。
package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

@Data
public class RepairAcceptanceFactsRow {
    private Integer affectedOwnerCount;
    private Integer participatingAffectedOwnerCount;
    private Integer passedAffectedOwnerCount;
    private Integer rectificationCount;
    private Boolean buildingLeaderPassed;
    private Boolean committeeExecutivePassed;
    private Boolean committeeSealApplied;
    private Boolean propertyTechnicalCosigned;
    private Boolean thirdPartyTechnicalCosigned;
}
