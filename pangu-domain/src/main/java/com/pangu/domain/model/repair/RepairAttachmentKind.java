// 关联业务：定义维修工单各阶段可上传并纳入审计链的附件类型。
package com.pangu.domain.model.repair;

public enum RepairAttachmentKind {
    /** 业主首次提交报修时上传的现场照片，创建后立即绑定至工单。 */
    OWNER_REPORT_IMAGE,
    INTAKE_ATTACHMENT,
    LOCATION_IMAGE,
    SURVEY_IMAGE,
    SURVEY_VIDEO,
    QUOTE_DOCUMENT,
    APPROVAL_DOCUMENT,
    SOLITAIRE_SCREENSHOT,
    GOVERNANCE_SEALED_DOCUMENT,
    /** 全小区公共维修竣工验收表的业委会盖章结果。 */
    ACCEPTANCE_SEALED_DOCUMENT
}
