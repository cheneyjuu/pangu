// 关联业务：定义维修工单各阶段可上传并纳入审计链的附件类型。
package com.pangu.domain.model.repair;

public enum RepairAttachmentKind {
    INTAKE_ATTACHMENT,
    LOCATION_IMAGE,
    SURVEY_IMAGE,
    SURVEY_VIDEO,
    QUOTE_DOCUMENT,
    APPROVAL_DOCUMENT,
    SOLITAIRE_SCREENSHOT,
    GOVERNANCE_SEALED_DOCUMENT
}
