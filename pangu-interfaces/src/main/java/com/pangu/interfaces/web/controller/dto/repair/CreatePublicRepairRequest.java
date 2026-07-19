// 关联业务：接收业主端和物业端公共区域报修登记请求，并兼容已发布客户端的附件提示字段。
package com.pangu.interfaces.web.controller.dto.repair;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePublicRepairRequest(
        @Size(max = 16) String publicAreaScope,
        Long buildingId,
        @Size(max = 200) String locationText,
        @NotBlank @Size(max = 120) String title,
        @Size(max = 2000) String description,
        @Size(max = 64) String category,
        /**
         * 兼容旧版业主端请求；现场照片应通过附件接口保存，不能作为物业勘验结论。
         */
        @Size(max = 2000) String evidenceText
) {
}
