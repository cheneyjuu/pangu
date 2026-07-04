package com.pangu.application.dispute.command;

import com.pangu.domain.model.dispute.DisputeKind;

/**
 * 业主提起异议 use case 输入。
 *
 * @param tenantId        租户（小区）
 * @param raisedByOwnerId 发起人 c_user.uid（必填，由 controller 从 SecurityUtils.getUid() 透传）
 * @param relatedPropertyOpid 异议关联房产 opid（可选；网格调解按该房产楼栋做数据范围）
 * @param disputeKind     异议类型 4 选 1
 * @param relatedEntityType 业务关联类型字符串（可选，e.g. "EXPENSE_VOUCHER" / "PROPOSAL"）
 * @param relatedEntityId 业务关联主键（可选）
 * @param businessPayloadJson 业务字段 JSON（可选，软关联）
 */
public record OpenCommand(
        Long tenantId,
        Long raisedByOwnerId,
        Long relatedPropertyOpid,
        DisputeKind disputeKind,
        String relatedEntityType,
        Long relatedEntityId,
        String businessPayloadJson
) {
    public OpenCommand(Long tenantId,
                       Long raisedByOwnerId,
                       DisputeKind disputeKind,
                       String relatedEntityType,
                       Long relatedEntityId,
                       String businessPayloadJson) {
        this(tenantId, raisedByOwnerId, null, disputeKind, relatedEntityType, relatedEntityId,
                businessPayloadJson);
    }
}
