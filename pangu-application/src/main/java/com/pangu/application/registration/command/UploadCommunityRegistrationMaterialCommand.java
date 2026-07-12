// 关联业务：承载小区注册证明材料的受控上传内容。
package com.pangu.application.registration.command;

import com.pangu.domain.model.registration.CommunityRegistrationMaterialType;

/**
 * 上传小区注册审核材料命令。
 */
public record UploadCommunityRegistrationMaterialCommand(
        CommunityRegistrationMaterialType materialType,
        String originalFileName,
        String contentType,
        byte[] content
) {
}
