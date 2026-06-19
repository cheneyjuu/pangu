package com.pangu.application.waiver.command;

import java.math.BigDecimal;

/**
 * 居委会发起 waiver 草稿并直接进入初审的命令对象（M1 RBAC 重构后版本）。
 *
 * <p>调用方（{@code WaiverController}）负责：
 * <ul>
 *   <li>从 {@code SecurityUtils} 解析 initiatorUserId / tenantId；</li>
 *   <li>{@code @PreAuthorize("hasAuthority('waiver:submit')")} 完成权限校验；</li>
 *   <li>将 OSS 凭证 keys 拼成逗号分隔的字符串。</li>
 * </ul>
 *
 * <p>**已剥离 dept_type 字段** —— 部门类型校验已经由 RBAC permission_key 取代，
 * 在 controller 层 @PreAuthorize 通过 hasAuthority 完成。
 *
 * @param subjectId           申请放宽的议题 ID
 * @param tenantId            租户 ID（与 subjectId 一致校验由 application 完成）
 * @param initiatorUserId     发起人 sys_user.user_id
 * @param requestedRatio      申请放宽至的党员比例 [0.00, 0.50)
 * @param reasonText          申请理由（实质字符 ≥ 50；水文检测必过）
 * @param reasonEvidenceKeys  OSS 凭证 keys 列表（逗号分隔；可为 null）
 */
public record SubmitDraftCommand(
        Long subjectId,
        Long tenantId,
        Long initiatorUserId,
        BigDecimal requestedRatio,
        String reasonText,
        String reasonEvidenceKeys
) {
}
