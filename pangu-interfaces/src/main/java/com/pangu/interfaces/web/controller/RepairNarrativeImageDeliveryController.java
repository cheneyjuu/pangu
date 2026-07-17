// 关联业务：通过单次短期凭证向小程序同源输出维修方案正文图片，避免暴露私有 OSS 地址。
package com.pangu.interfaces.web.controller;

import com.pangu.application.repair.RepairNarrativeImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/public/repair-plan-images")
@RequiredArgsConstructor
public class RepairNarrativeImageDeliveryController {

    private final RepairNarrativeImageService imageService;

    @GetMapping("/{imageId}")
    public ResponseEntity<byte[]> image(
            @PathVariable("imageId") Long imageId,
            @RequestParam(value = "ticket", required = false) String ticket) {
        RepairNarrativeImageService.DeliveredImage image = imageService.deliver(imageId, ticket);
        byte[] content = image.content();
        Duration remaining = Duration.between(Instant.now(), image.expiresAt());
        Duration cacheDuration = remaining.isNegative() ? Duration.ZERO : remaining;
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .contentLength(content.length)
                .cacheControl(CacheControl.maxAge(cacheDuration).cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(image.originalFileName(), StandardCharsets.UTF_8)
                        .build().toString())
                .header("Referrer-Policy", "no-referrer")
                .header("X-Content-Type-Options", "nosniff")
                .body(content);
    }
}
