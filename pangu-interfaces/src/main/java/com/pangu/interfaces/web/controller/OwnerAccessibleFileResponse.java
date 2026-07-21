// 关联业务：以小程序已备案的同源 API 向当前业主返回经授权核验的原始材料。
package com.pangu.interfaces.web.controller;

import com.pangu.application.support.OwnerAccessibleFile;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

/** 统一生成禁止共享缓存、允许微信文档浏览器内联打开的文件响应。 */
final class OwnerAccessibleFileResponse {

    private OwnerAccessibleFileResponse() {
    }

    static ResponseEntity<byte[]> inline(OwnerAccessibleFile file) {
        byte[] content = file.content();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(content.length)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(file.originalFileName(), StandardCharsets.UTF_8)
                        .build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(content);
    }
}
