// 关联业务：映射实施方案版本对项目原始附件的用途化引用。
package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

@Data
public class RepairPlanAttachmentRow {
    private Long attachmentId;
    private String purpose;
    private Integer sortOrder;
}
