package com.pangu.bootstrap.voting;

import com.pangu.domain.model.notification.VotingReminderDeliveryCommand;
import com.pangu.domain.model.notification.VotingReminderDeliveryGateway;
import com.pangu.domain.repository.VotingReminderDeliveryQueryRepository;
import com.pangu.domain.repository.VotingReminderOutboxRepository;
import com.pangu.application.voting.VotingReminderDeliveryDispatchService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
public class VotingReminderOutboxRepositoryIntegrationTest {

    @Autowired
    private VotingReminderOutboxRepository repository;

    @Autowired
    private VotingReminderDeliveryGateway deliveryGateway;

    @Autowired
    private VotingReminderDeliveryQueryRepository deliveryQueryRepository;

    @Autowired
    private VotingReminderDeliveryDispatchService dispatchService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final long TENANT = 190001L;
    private static final long SUBJECT = 970001L;
    private static final long DELIVERY_TENANT = 10001L;
    private static final long DELIVERY_SUBJECT = 970002L;
    private static final long DELIVERY_BUILDING = 30001L;

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM t_voting_reminder_delivery WHERE subject_id IN (?, ?)",
                SUBJECT, DELIVERY_SUBJECT);
        jdbcTemplate.update("DELETE FROM t_outbox_event WHERE tenant_id = ? AND business_ref_id = ?",
                TENANT, SUBJECT);
        jdbcTemplate.update("DELETE FROM t_outbox_event WHERE tenant_id = ? AND business_ref_id = ?",
                DELIVERY_TENANT, DELIVERY_SUBJECT);
        jdbcTemplate.update("DELETE FROM t_vote_item WHERE subject_id = ?", DELIVERY_SUBJECT);
        jdbcTemplate.update("DELETE FROM t_voting_subject WHERE subject_id = ?", DELIVERY_SUBJECT);
    }

    @Test
    public void claimPending_marksSubmittedAndConfirmed() {
        long eventId = insertPendingEvent();

        List<VotingReminderOutboxRepository.ReminderOutboxEvent> claimed = repository.claimPending(10);

        assertEquals(1, claimed.stream().filter(e -> eventId == e.eventId()).count());
        assertEquals(2, statusOf(eventId));
        assertEquals(1, attemptsOf(eventId));

        repository.markConfirmed(eventId);

        assertEquals(3, statusOf(eventId));
        assertEquals(1, confirmedCount(eventId));
    }

    @Test
    public void markFailed_keepsEventRetryable() {
        long eventId = insertPendingEvent();
        repository.claimPending(10);

        repository.markFailed(eventId, "mock failure");

        assertEquals(4, statusOf(eventId));
        assertEquals("mock failure", lastErrorOf(eventId));

        List<VotingReminderOutboxRepository.ReminderOutboxEvent> retried = repository.claimPending(10);

        assertEquals(1, retried.stream().filter(e -> eventId == e.eventId()).count());
        assertEquals(2, statusOf(eventId));
        assertEquals(2, attemptsOf(eventId));
    }

    @Test
    public void databaseDeliveryGateway_expandsUnvotedOwnersIdempotently() {
        insertVotingSubject(DELIVERY_SUBJECT, DELIVERY_TENANT, DELIVERY_BUILDING);
        long eventId = insertPendingEvent(DELIVERY_SUBJECT, DELIVERY_TENANT, DELIVERY_BUILDING, 1);

        var command = new VotingReminderDeliveryCommand(
                eventId,
                DELIVERY_SUBJECT,
                DELIVERY_TENANT,
                DELIVERY_BUILDING,
                800102L,
                91001L,
                "UNVOTED_BUILDING_OWNERS",
                1,
                "VOTE_REMINDER",
                "请尽快投票",
                java.time.Instant.parse("2026-07-01T00:00:00Z"));

        deliveryGateway.deliver(command);
        int firstCount = deliveryCount(eventId);
        assertTrue(firstCount > 0);

        deliveryGateway.deliver(command);
        assertEquals(firstCount, deliveryCount(eventId));
    }

    @Test
    public void dispatchService_claimsReadyDeliveryAndMarksConfirmed() {
        insertVotingSubject(DELIVERY_SUBJECT, DELIVERY_TENANT, DELIVERY_BUILDING);
        long eventId = insertPendingEvent(DELIVERY_SUBJECT, DELIVERY_TENANT, DELIVERY_BUILDING, 1);
        insertReadyDelivery(eventId);

        int confirmed = dispatchService.dispatchPending(10);

        assertEquals(1, confirmed);
        assertEquals(3, deliveryStatus(eventId));
        assertEquals(1, deliveryAttempts(eventId));
        assertEquals(1, confirmedDeliveryCount(eventId));
        assertTrue(providerMessageId(eventId).startsWith("mock-sms-"));

        var statuses = deliveryQueryRepository.listBySubject(
                DELIVERY_TENANT, DELIVERY_SUBJECT, DELIVERY_BUILDING, 3, 10);
        assertEquals(1, statuses.size());
        assertEquals(DELIVERY_SUBJECT, statuses.get(0).subjectId());
        assertEquals(DELIVERY_BUILDING, statuses.get(0).buildingId());
        assertEquals(3, statuses.get(0).deliveryStatus());
        assertTrue(statuses.get(0).phoneMasked().contains("****"));
        assertTrue(statuses.get(0).providerMessageId().startsWith("mock-sms-"));
    }

    private long insertPendingEvent() {
        return insertPendingEvent(SUBJECT, TENANT, 30001L, 8);
    }

    private long insertPendingEvent(long subjectId, long tenantId, long buildingId, int targetCount) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO t_outbox_event (
                    event_type, business_ref_id, tenant_id, payload_json, status
                ) VALUES (
                    4, ?, ?, CAST(? AS JSONB), 1
                )
                RETURNING event_id
                """, Long.class, subjectId, tenantId, payload(subjectId, tenantId, buildingId, targetCount));
    }

    private void insertVotingSubject(long subjectId, long tenantId, long buildingId) {
        jdbcTemplate.update("""
                INSERT INTO t_voting_subject (
                    subject_id, tenant_id, title, subject_type, scope, scope_reference_id,
                    status, vote_start_at, vote_end_at, version
                ) VALUES (?, ?, '催票投递集成测试', 3, 2, ?, 3,
                    CURRENT_TIMESTAMP - INTERVAL '1 hour',
                    CURRENT_TIMESTAMP + INTERVAL '1 day',
                    0)
                """, subjectId, tenantId, buildingId);
    }

    private int statusOf(long eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM t_outbox_event WHERE event_id = ?", Integer.class, eventId);
    }

    private int attemptsOf(long eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT attempts FROM t_outbox_event WHERE event_id = ?", Integer.class, eventId);
    }

    private int confirmedCount(long eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_outbox_event WHERE event_id = ? AND confirmed_at IS NOT NULL",
                Integer.class, eventId);
    }

    private String lastErrorOf(long eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT last_error FROM t_outbox_event WHERE event_id = ?", String.class, eventId);
    }

    private int deliveryCount(long eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_voting_reminder_delivery WHERE outbox_event_id = ?",
                Integer.class, eventId);
    }

    private void insertReadyDelivery(long eventId) {
        Long opid = jdbcTemplate.queryForObject("""
                SELECT opid
                FROM c_owner_property
                WHERE tenant_id = ? AND building_id = ? AND account_status = 1
                ORDER BY opid
                LIMIT 1
                """, Long.class, DELIVERY_TENANT, DELIVERY_BUILDING);
        Long uid = jdbcTemplate.queryForObject(
                "SELECT uid FROM c_owner_property WHERE opid = ?", Long.class, opid);
        String phone = jdbcTemplate.queryForObject("""
                SELECT a.phone
                FROM c_owner_property op
                JOIN c_user cu ON cu.uid = op.uid
                JOIN t_account a ON a.account_id = cu.account_id
                WHERE op.opid = ?
                """, String.class, opid);
        jdbcTemplate.update("""
                INSERT INTO t_voting_reminder_delivery (
                    outbox_event_id, subject_id, tenant_id, building_id,
                    opid, uid, phone, channel, message_template, message, delivery_status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 'SMS', 'VOTE_REMINDER', '请尽快投票', 1)
                """, eventId, DELIVERY_SUBJECT, DELIVERY_TENANT, DELIVERY_BUILDING, opid, uid, phone);
    }

    private int deliveryStatus(long eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT delivery_status FROM t_voting_reminder_delivery WHERE outbox_event_id = ?",
                Integer.class, eventId);
    }

    private int deliveryAttempts(long eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT attempts FROM t_voting_reminder_delivery WHERE outbox_event_id = ?",
                Integer.class, eventId);
    }

    private int confirmedDeliveryCount(long eventId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM t_voting_reminder_delivery
                WHERE outbox_event_id = ? AND confirmed_at IS NOT NULL
                """, Integer.class, eventId);
    }

    private String providerMessageId(long eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT provider_message_id FROM t_voting_reminder_delivery WHERE outbox_event_id = ?",
                String.class, eventId);
    }

    private String payload(long subjectId, long tenantId, long buildingId, int targetCount) {
        return """
                {
                  "eventType": "VOTING_REMINDER_REQUESTED",
                  "subjectId": %d,
                  "tenantId": %d,
                  "buildingId": %d,
                  "sentByUserId": 800102,
                  "permissionId": 91001,
                  "targetScope": "UNVOTED_BUILDING_OWNERS",
                  "targetCount": %d,
                  "messageTemplate": "VOTE_REMINDER",
                  "message": "请尽快投票",
                  "sentAt": "2026-07-01T00:00:00Z"
                }
                """.formatted(subjectId, tenantId, buildingId, targetCount);
    }
}
