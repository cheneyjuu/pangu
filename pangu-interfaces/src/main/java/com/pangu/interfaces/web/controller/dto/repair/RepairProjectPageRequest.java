// 关联业务：校验维修工程项目台账分页和筛选参数。
package com.pangu.interfaces.web.controller.dto.repair;

import com.pangu.domain.model.repair.RepairProject.Status;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RepairProjectPageRequest {
    private Status status;

    @Size(max = 100)
    private String keyword;

    @Min(1)
    private int page = 1;

    @Min(1)
    @Max(100)
    private int size = 20;
}
