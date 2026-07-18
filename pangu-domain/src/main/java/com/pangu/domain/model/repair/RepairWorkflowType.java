// 关联业务：区分楼栋/单元共有维修与全小区公共维修，防止两个治理和验收流程互相替代。
package com.pangu.domain.model.repair;

public enum RepairWorkflowType {
    BUILDING_REPAIR,
    COMMUNITY_PUBLIC_REPAIR
}
