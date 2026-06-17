package com.pangu.application.waiver.command;

import java.math.BigDecimal;

/**
 * 居委会发起 waiver 草稿并直接进入初审的命令对象。
 *
 * <p>调用方（{@code WaiverController}）负责：
 * <ul>
 *   <li>从 {@code SecurityUtils} 解析 initiatorUserId / tenantId / initiatorDeptType；</li>
 *   <li>校验 deptType=2（居委会），用户必须是议题所属租户的成员；</li>
 *   <li>将 OSS 凭证 keys 拼成逗号分隔的字符串。</li>
 * </ul>
 *
 * @param subjectId           申请放宽的议题 ID
 * @param tenantId            租户 ID（与 subjectId 一致校验由 application 完成）
 * @param initiatorUserId     发起人 sys_user.user_id
 * @param initiatorDeptType   发起人部门类型（必须为 2 居委会）
 * @param requestedRatio      申请放宽至的党员比例 [0.00, 0.50)
 * @param reasonText          申请理由（实质字符 ≥ 50；水文检测必过）
 * @param reasonEvidenceKeys  OSS 凭证 keys 列表（逗号分隔；可为 null）
 */
public record SubmitDraftCommand(
        Long subjectId,
        Long tenantId,
        Long initiatorUserId,
        Integer initiatorDeptType,
        BigDecimal requestedRatio,
        String reasonText,
        String reasonEvidenceKeys
) {
}
