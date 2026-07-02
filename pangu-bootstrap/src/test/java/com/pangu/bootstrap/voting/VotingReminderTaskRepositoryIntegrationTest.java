package com.pangu.bootstrap.voting;

import com.pangu.domain.model.voting.ReminderChannel;
import com.pangu.domain.repository.VotingReminderTaskRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
public class VotingReminderTaskRepositoryIntegrationTest {

    @Autowired
    private VotingReminderTaskRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final long TENANT = 10001L;
    private static final long USER = 800004L;
    private static final long SUBJECT = 970101L;
    private static final long BUILDING = 30001L;

    @BeforeEach
    void setup() {
        cleanup();
        jdbcTemplate.update("""
                INSERT INTO t_voting_subject (
                    subject_id, tenant_id, title, subject_type, scope, scope_reference_id,
                    status, vote_start_at, vote_end_at, version
                ) VALUES (?, ?, '催票任务集成测试', 3, 2, ?, 3,
                    CURRENT_TIMESTAMP - INTERVAL '1 hour',
                    CURRENT_TIMESTAMP + INTERVAL '1 day',
                    0)
                """, SUBJECT, TENANT, BUILDING);
        jdbcTemplate.update("""
                INSERT INTO t_voting_mobilization_permission (
                    subject_id, tenant_id, building_id, user_id, role_key,
                    can_remind, can_offline_proxy, activated_at, expires_at, status
                ) VALUES (?, ?, ?, ?, 'GRID_MEMBER', TRUE, TRUE,
                    CURRENT_TIMESTAMP - INTERVAL '1 hour',
                    CURRENT_TIMESTAMP + INTERVAL '1 day',
                    1)
                """, SUBJECT, TENANT, BUILDING, USER);
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM t_voting_mobilization_owner_notice WHERE subject_id = ?", SUBJECT);
        jdbcTemplate.update("DELETE FROM t_voting_mobilization_permission WHERE subject_id = ?", SUBJECT);
        jdbcTemplate.update("DELETE FROM t_vote_item WHERE subject_id = ?", SUBJECT);
        jdbcTemplate.update("DELETE FROM t_voting_subject WHERE subject_id = ?", SUBJECT);
    }

    @Test
    public void listTasksAndPendingOwners_thenMarkNotified() {
        var tasks = repository.listTasks(TENANT, USER);

        var task = tasks.stream()
                .filter(t -> t.subjectId().equals(SUBJECT))
                .findFirst()
                .orElseThrow();
        assertEquals("催票任务集成测试", task.subjectTitle());
        assertFalse(task.totalCount() == 0);
        assertEquals(task.totalCount(), task.pendingCount());

        var pending = repository.listPendingOwners(TENANT, USER, SUBJECT);
        assertFalse(pending.isEmpty());
        var owner = pending.get(0);
        assertEquals(BUILDING, owner.buildingId());

        int updated = repository.markNotified(
                TENANT, USER, SUBJECT, owner.uid(), ReminderChannel.PHONE, "电话已联系");

        assertEquals(1, updated);
        var after = repository.listPendingOwners(TENANT, USER, SUBJECT).stream()
                .filter(o -> o.uid().equals(owner.uid()))
                .findFirst()
                .orElseThrow();
        assertEquals(1, after.notified().size());
        assertEquals(ReminderChannel.PHONE, after.notified().get(0));
        assertEquals("电话已联系", after.note());
    }
}
