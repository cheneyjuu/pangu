package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OwnersAssemblySessionRow {
    private Long sessionId;
    private Long tenantId;
    private String title;
    private String preparationMode;
    private String status;
    private Long createdByUserId;
    private LocalDateTime createTime;
}
