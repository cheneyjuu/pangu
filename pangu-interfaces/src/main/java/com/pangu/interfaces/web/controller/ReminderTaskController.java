package com.pangu.interfaces.web.controller;

import com.pangu.application.voting.VotingReminderTaskService;
import com.pangu.domain.model.voting.ReminderChannel;
import com.pangu.domain.model.voting.ReminderPendingOwner;
import com.pangu.domain.model.voting.ReminderTask;
import com.pangu.interfaces.web.controller.dto.voting.MarkReminderNotifiedRequest;
import com.pangu.interfaces.web.controller.dto.voting.ReminderPendingOwnerResponse;
import com.pangu.interfaces.web.controller.dto.voting.ReminderTaskResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reminder/tasks")
@RequiredArgsConstructor
public class ReminderTaskController extends BaseController {

    private final VotingReminderTaskService reminderTaskService;

    @GetMapping
    @PreAuthorize("hasAuthority('voting:subject:audit')")
    public Result<List<ReminderTaskResponse>> listTasks() {
        List<ReminderTask> tasks = reminderTaskService.listTasks();
        return success(tasks.stream().map(ReminderTaskResponse::from).toList());
    }

    @GetMapping("/{subjectId}/pending")
    @PreAuthorize("hasAuthority('voting:subject:audit')")
    public Result<List<ReminderPendingOwnerResponse>> listPendingOwners(
            @PathVariable("subjectId") Long subjectId) {
        List<ReminderPendingOwner> owners = reminderTaskService.listPendingOwners(subjectId);
        return success(owners.stream().map(ReminderPendingOwnerResponse::from).toList());
    }

    @PostMapping("/{subjectId}/notify")
    @PreAuthorize("hasAuthority('voting:subject:audit')")
    public Result<Void> markNotified(
            @PathVariable("subjectId") Long subjectId,
            @Valid @RequestBody MarkReminderNotifiedRequest request) {
        reminderTaskService.markNotified(
                subjectId,
                request.uid(),
                request.channel() == null ? ReminderChannel.PHONE : request.channel(),
                request.note());
        return success("已标记通知", null);
    }
}
