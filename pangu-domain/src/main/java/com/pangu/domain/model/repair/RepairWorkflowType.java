// 关联业务：区分楼栋/单元共有维修与全小区公共维修的技术处理路径；资金、治理和验收依据必须另有可信快照。
package com.pangu.domain.model.repair;

public enum RepairWorkflowType {
    BUILDING_REPAIR,
    COMMUNITY_PUBLIC_REPAIR
}
