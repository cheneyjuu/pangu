// 关联业务：承载物业服务组织登记材料的私有上传内容。
package com.pangu.application.propertyservice.command;

import com.pangu.domain.model.propertyservice.PropertyServiceOrganizationMaterialType;

/**
 * 上传物业服务组织登记材料命令。
 */
public record UploadPropertyServiceOrganizationMaterialCommand(
        PropertyServiceOrganizationMaterialType materialType,
        String originalFileName,
        String contentType,
        byte[] content
) {
}
