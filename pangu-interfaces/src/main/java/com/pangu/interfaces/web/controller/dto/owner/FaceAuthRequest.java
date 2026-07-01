package com.pangu.interfaces.web.controller.dto.owner;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 业主端刷脸核身结果提交。
 *
 * <p>人脸采集和活体检测在小程序宿主能力或云厂商 SDK 中完成；后端只接收核身成功后的
 * 凭证摘要并升级当前 C_USER 的认证等级，不接收或存储人脸图片 / 视频。
 */
public record FaceAuthRequest(
        @NotBlank @Size(max = 32) String provider,
        @NotBlank @Size(max = 128) String providerRequestId,
        @Size(max = 512) String providerResult
) {
}
