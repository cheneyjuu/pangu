// 关联业务：将管理端录入的富文本转换为可安全向业主端披露的规范 HTML。
package com.pangu.domain.gateway;

/**
 * 用户输入富文本的安全边界。
 *
 * <p>实现必须移除脚本、样式、事件属性和非白名单节点，并返回稳定的规范 HTML。
 */
public interface RichTextSanitizer {

    SanitizedRichText sanitize(String source);

    record SanitizedRichText(String html, String plainText) {

        public boolean isBlank() {
            return plainText == null || plainText.isBlank();
        }
    }
}
