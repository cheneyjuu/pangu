package com.pangu.bootstrap.voting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.application.voting.VotingReminderOutboxConsumerService;
import com.pangu.domain.model.notification.VotingReminderDeliveryCommand;
import com.pangu.domain.model.notification.VotingReminderDeliveryGateway;
import com.pangu.domain.repository.VotingReminderOutboxRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class VotingReminderOutboxConsumerServiceTest {

    @Mock
    private VotingReminderOutboxRepository outboxRepository;
    @Mock
    private VotingReminderDeliveryGateway deliveryGateway;

    private VotingReminderOutboxConsumerService service() {
        return new VotingReminderOutboxConsumerService(
                outboxRepository, deliveryGateway, new ObjectMapper());
    }

    @Test
    public void consumePending_success_deliversAndMarksConfirmed() {
        when(outboxRepository.claimPending(10)).thenReturn(List.of(event()));

        int delivered = service().consumePending(10);

        assertEquals(1, delivered);
        ArgumentCaptor<VotingReminderDeliveryCommand> captor =
                ArgumentCaptor.forClass(VotingReminderDeliveryCommand.class);
        verify(deliveryGateway).deliver(captor.capture());
        assertEquals(99001L, captor.getValue().outboxEventId());
        assertEquals(7001L, captor.getValue().subjectId());
        assertEquals(30001L, captor.getValue().buildingId());
        assertEquals(8, captor.getValue().targetCount());
        verify(outboxRepository).markConfirmed(99001L);
        verify(outboxRepository, never()).markFailed(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    public void consumePending_deliveryFails_marksFailedAndContinues() {
        when(outboxRepository.claimPending(10)).thenReturn(List.of(event()));
        doThrow(new IllegalStateException("sms gateway unavailable"))
                .when(deliveryGateway).deliver(org.mockito.ArgumentMatchers.any());

        int delivered = service().consumePending(10);

        assertEquals(0, delivered);
        verify(outboxRepository).markFailed(99001L, "sms gateway unavailable");
        verify(outboxRepository, never()).markConfirmed(org.mockito.ArgumentMatchers.any());
    }

    private VotingReminderOutboxRepository.ReminderOutboxEvent event() {
        return new VotingReminderOutboxRepository.ReminderOutboxEvent(
                99001L,
                7001L,
                10001L,
                """
                        {
                          "eventType": "VOTING_REMINDER_REQUESTED",
                          "subjectId": 7001,
                          "tenantId": 10001,
                          "buildingId": 30001,
                          "sentByUserId": 800102,
                          "permissionId": 91001,
                          "targetScope": "UNVOTED_BUILDING_OWNERS",
                          "targetCount": 8,
                          "messageTemplate": "VOTE_REMINDER",
                          "message": "请尽快投票",
                          "sentAt": "2026-07-01T00:00:00Z"
                        }
                        """);
    }
}
