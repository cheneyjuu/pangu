// 关联业务：接收业主大会会前新增表决事项的业务字段，不暴露表决包标识。
package com.pangu.interfaces.web.controller.dto.assembly;

import com.pangu.domain.model.voting.SubjectType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAssemblySubjectDraftRequest(
        @NotNull SubjectType subjectType,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 20_000) String content
) {
}
