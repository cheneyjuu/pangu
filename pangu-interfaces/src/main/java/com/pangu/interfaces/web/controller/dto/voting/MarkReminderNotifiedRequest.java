package com.pangu.interfaces.web.controller.dto.voting;

import com.pangu.domain.model.voting.ReminderChannel;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MarkReminderNotifiedRequest(
        @NotNull Long uid,
        @NotNull ReminderChannel channel,
        @Size(max = 500) String note
) {
}
