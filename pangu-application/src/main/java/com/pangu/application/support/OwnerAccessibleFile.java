// 关联业务：在业主身份和材料可见范围核验通过后，向 C 端交付原始文件内容。
package com.pangu.application.support;

import java.util.Arrays;

/**
 * 已通过业务权限与完整性校验的业主可访问文件。
 *
 * <p>文件内容使用防御性复制，避免控制器或后续处理意外改写已核验的字节。
 */
public record OwnerAccessibleFile(
        String originalFileName,
        String contentType,
        byte[] content
) {

    public OwnerAccessibleFile {
        content = content == null ? new byte[0] : Arrays.copyOf(content, content.length);
    }

    @Override
    public byte[] content() {
        return Arrays.copyOf(content, content.length);
    }
}
