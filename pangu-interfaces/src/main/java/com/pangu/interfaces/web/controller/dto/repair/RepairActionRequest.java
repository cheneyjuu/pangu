package com.pangu.interfaces.web.controller.dto.repair;

import jakarta.validation.constraints.Size;

import java.util.List;

public record RepairActionRequest(
        @Size(max = 500) String remark,
        @Size(max = 1000) String fieldSupplement,
        @Size(max = 3) List<@Size(max = 2800000) String> evidenceImagesBase64
) {
}
