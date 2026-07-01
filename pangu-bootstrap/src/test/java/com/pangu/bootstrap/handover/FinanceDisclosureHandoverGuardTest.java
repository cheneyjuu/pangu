package com.pangu.bootstrap.handover;

import com.pangu.application.disclosure.FinanceDisclosureApplicationException;
import com.pangu.application.disclosure.FinanceDisclosureApplicationService;
import com.pangu.application.disclosure.DisclosureDiffCalculator;
import com.pangu.application.disclosure.command.LockAndPublishCommand;
import com.pangu.application.handover.HandoverCircuitBreaker;
import com.pangu.application.lock.GovernanceLockApplicationService;
import com.pangu.application.lock.command.LockCommand;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.lock.DistributedLockTemplate;
import com.pangu.domain.model.disclosure.DisclosureStatus;
import com.pangu.domain.model.disclosure.DisclosureType;
import com.pangu.domain.model.disclosure.FinanceDisclosureSnapshot;
import com.pangu.domain.model.user.AuthenticationLevel;
import com.pangu.domain.model.user.DataScopeType;
import com.pangu.domain.model.lock.GovernanceLock;
import com.pangu.domain.repository.DisclosureCompareRepository;
import com.pangu.domain.repository.FinanceDisclosureRepository;
import com.pangu.domain.repository.FundLedgerQueryGateway;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FinanceDisclosureApplicationService#lockAndPublish} 的换届熔断闸门单元测试（Mockito）。
 *
 * <p>聚焦熔断分支，不启 Spring 容器：
 * <ul>
 *   <li>breaker 命中 → 抛 {@code HANDOVER_IN_PROGRESS}，且**绝不**进入治理锁
 *       （{@code lockApplicationService.lock} 一次都不能被调用）；</li>
 *   <li>breaker 空 → 正常推进 DRAFT → LOCKED → PUBLISHED（治理锁被调用，终态 PUBLISHED）。</li>
 * </ul>
 */
public class FinanceDisclosureHandoverGuardTest {

    private static final long TENANT = 10001L;
    private static final long SNAPSHOT_ID = 5001L;
    private static final long PUBLISH_USER = 800101L;

    private final FinanceDisclosureRepository disclosureRepository = mock(FinanceDisclosureRepository.class);
    private final DisclosureCompareRepository compareRepository = mock(DisclosureCompareRepository.class);
    private final FundLedgerQueryGateway ledgerQueryGateway = mock(FundLedgerQueryGateway.class);
    private final GovernanceLockApplicationService lockApplicationService = mock(GovernanceLockApplicationService.class);
    private final DistributedLockTemplate distributedLockTemplate = mock(DistributedLockTemplate.class);
    private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
    private final DisclosureDiffCalculator diffCalculator = mock(DisclosureDiffCalculator.class);
    private final HandoverCircuitBreaker handoverCircuitBreaker = mock(HandoverCircuitBreaker.class);
    private final UserContextHolder userContextHolder = mock(UserContextHolder.class);

    private final FinanceDisclosureApplicationService service = new FinanceDisclosureApplicationService(
            disclosureRepository, compareRepository, ledgerQueryGateway, lockApplicationService,
            distributedLockTemplate, transactionTemplate, diffCalculator, handoverCircuitBreaker,
            userContextHolder);

    /** 构造一条 DRAFT 态、归属 TENANT 的快照（compose 工厂产物即 DRAFT）。 */
    private FinanceDisclosureSnapshot draftSnapshot() {
        return FinanceDisclosureSnapshot.compose(
                TENANT, "2099-12", DisclosureType.MAINTENANCE_FUND,
                "{}", "a".repeat(64), 7001L, 1);
    }

    private UserContext committeeDirector() {
        return new UserContext(
                999811L,
                UserContext.IdentityType.SYS_USER,
                PUBLISH_USER,
                TENANT,
                9001L,
                UserContext.DeptCategory.B,
                10,
                DataScopeType.ALL_COMMUNITY,
                AuthenticationLevel.L1,
                "COMMITTEE_DIRECTOR",
                Set.of(),
                Set.of());
    }

    @Test
    public void handoverInProgress_blocksPublishAndSkipsGovernanceLock() {
        when(userContextHolder.current()).thenReturn(committeeDirector());
        when(disclosureRepository.findByIdForUpdate(SNAPSHOT_ID)).thenReturn(Optional.of(draftSnapshot()));
        when(handoverCircuitBreaker.activeElectionSubjectId(TENANT)).thenReturn(Optional.of(888L));

        FinanceDisclosureApplicationException ex = assertThrows(
                FinanceDisclosureApplicationException.class,
                () -> service.lockAndPublish(new LockAndPublishCommand(TENANT, SNAPSHOT_ID, PUBLISH_USER)));

        assertEquals(FinanceDisclosureApplicationException.Reason.HANDOVER_IN_PROGRESS, ex.getReason());
        verify(lockApplicationService, never()).lock(any(LockCommand.class));
    }

    @Test
    public void noHandover_proceedsToLockAndPublish() {
        when(userContextHolder.current()).thenReturn(committeeDirector());
        FinanceDisclosureSnapshot snapshot = draftSnapshot();
        when(disclosureRepository.findByIdForUpdate(SNAPSHOT_ID)).thenReturn(Optional.of(snapshot));
        when(handoverCircuitBreaker.activeElectionSubjectId(TENANT)).thenReturn(Optional.empty());

        GovernanceLock lock = mock(GovernanceLock.class);
        when(lock.getLockId()).thenReturn(9001L);
        when(lockApplicationService.lock(any(LockCommand.class))).thenReturn(lock);

        FinanceDisclosureSnapshot result = service.lockAndPublish(
                new LockAndPublishCommand(TENANT, SNAPSHOT_ID, PUBLISH_USER));

        assertEquals(DisclosureStatus.PUBLISHED, result.getStatus(), "无换届时应正常推进到 PUBLISHED");
        verify(lockApplicationService).lock(any(LockCommand.class));
    }
}
