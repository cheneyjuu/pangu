// 关联业务：上传维修工程项目级报价、现场照片和正式文件原件。
package com.pangu.application.repair.command;

public record UploadRepairProjectAttachmentCommand(
        String originalFileName,
        String contentType,
        byte[] content
) {
}
