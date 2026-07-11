package com.pangu.interfaces.web.controller.dto.assembly;

import com.pangu.domain.model.voting.SubjectType;
import com.pangu.domain.model.voting.VotingScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AddAssemblySubjectRequest(
        @NotNull SubjectType subjectType,
        @NotNull VotingScope scope,
        Long scopeReferenceId,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 20000) String content,
        BigDecimal partyRatioFloor
) {
}
