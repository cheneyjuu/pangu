// 关联业务：映射维修工程项目已办节点的最小化持久化字段，避免把个人审计信息带入管理端流程记录。
package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RepairProjectProcessEventRow {
    private Long eventId;
    private Long projectId;
    private Long tenantId;
    private String action;
    private LocalDateTime occurredAt;
}
