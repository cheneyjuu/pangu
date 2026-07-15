// 关联业务：标识两类维修流程中具有法律或专业意义的验收参与角色。
package com.pangu.domain.model.repair;

public enum RepairAcceptancePartyRole {
    AFFECTED_OWNER,
    BUILDING_LEADER,
    COMMITTEE_EXECUTIVE_APPROVER,
    COMMITTEE_SEAL_OPERATOR,
    PROPERTY_TECHNICAL_COSIGNER,
    THIRD_PARTY_TECHNICAL_COSIGNER
}
