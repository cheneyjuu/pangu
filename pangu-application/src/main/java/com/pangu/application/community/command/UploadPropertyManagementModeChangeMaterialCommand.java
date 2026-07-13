// 关联业务：承载物业管理模式变更申请私有材料上传的输入。
package com.pangu.application.community.command;

import com.pangu.domain.model.community.PropertyManagementModeChangeMaterialType;

/**
 * 上传物业管理模式变更材料命令。
 */
public record UploadPropertyManagementModeChangeMaterialCommand(
        PropertyManagementModeChangeMaterialType materialType,
        String originalFileName,
        String contentType,
        byte[] content
) {
}
