package com.pangu.application.assembly.command;

import com.pangu.domain.model.voting.SubjectType;
import com.pangu.domain.model.voting.VotingScope;

import java.math.BigDecimal;

public record AddAssemblySubjectCommand(
        Long packageId,
        Long tenantId,
        SubjectType subjectType,
        VotingScope scope,
        Long scopeReferenceId,
        String title,
        String content,
        Long proposedByUserId,
        BigDecimal partyRatioFloor
) {
}
