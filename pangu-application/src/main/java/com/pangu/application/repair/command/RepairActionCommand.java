package com.pangu.application.repair.command;

import java.util.List;

public record RepairActionCommand(
        String remark,
        String fieldSupplement,
        List<String> evidenceImagesBase64
) {
}
