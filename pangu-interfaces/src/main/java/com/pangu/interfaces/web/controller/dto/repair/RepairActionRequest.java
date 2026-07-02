package com.pangu.interfaces.web.controller.dto.repair;

import jakarta.validation.constraints.Size;

public record RepairActionRequest(@Size(max = 500) String remark) {
}
