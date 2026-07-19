// 关联业务：接收一张纸质表决票的逐事项录入，区分有效选择与人工判定的无效原因。
package com.pangu.interfaces.web.controller.dto.assembly;

import com.pangu.domain.model.voting.PaperBallotEntry;
import com.pangu.domain.model.voting.VoteChoice;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SubmitAssemblyPaperBallotEntryRequest(
        @NotEmpty List<@Valid Item> items
) {

    public List<PaperBallotEntry.Item> toDomainItems() {
        return items.stream().map(Item::toDomain).toList();
    }

    public record Item(
            @NotNull Long subjectId,
            @NotNull PaperBallotEntry.Determination determination,
            VoteChoice choice,
            PaperBallotEntry.InvalidReasonCode invalidReasonCode,
            String invalidReasonDescription
    ) {
        private PaperBallotEntry.Item toDomain() {
            return new PaperBallotEntry.Item(
                    null,
                    null,
                    subjectId,
                    determination,
                    choice,
                    invalidReasonCode,
                    invalidReasonDescription);
        }
    }
}
