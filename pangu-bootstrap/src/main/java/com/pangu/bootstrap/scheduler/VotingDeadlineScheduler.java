package com.pangu.bootstrap.scheduler;

import com.pangu.application.voting.VotingApplicationException;
import com.pangu.application.voting.VotingApplicationService;
import com.pangu.application.voting.command.SettleSubjectCommand;
import com.pangu.domain.model.voting.VotingSubject;
import com.pangu.domain.repository.VotingSubjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * 议题截止扫描器：每分钟扫一次 {@code status = VOTING && vote_end_at < now()} 的议题，
 * 逐个调用 {@link VotingApplicationService#settle} 触发结算。
 *
 * <p>事务边界：每个议题独立事务（由 {@code VotingApplicationService.settle} 上的
 * {@code @Transactional} 提供），单议题失败不影响其他议题。
 *
 * <p>限流：每轮最多扫 {@code platform.voting.deadline-batch-size}（默认 100）条，
 * 防止单 tick 内大量议题同时到期把 DB 打满；未处理完的议题在下一轮继续被扫到。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VotingDeadlineScheduler {

    private final VotingSubjectRepository subjectRepository;
    private final VotingApplicationService votingApplicationService;

    @Value("${platform.voting.deadline-batch-size:100}")
    private int batchSize;

    /**
     * 每 60 秒扫一次（cron 格式：秒 分 时 日 月 周）。生产环境可通过 application.yml 覆盖
     * {@code platform.voting.deadline-cron}。
     */
    @Scheduled(cron = "${platform.voting.deadline-cron:0 * * * * *}")
    public void tick() {
        Instant now = Instant.now();
        List<VotingSubject> expired = subjectRepository.findExpiredVoting(now, batchSize);
        if (expired.isEmpty()) {
            return;
        }
        log.info("VotingDeadlineScheduler tick: found {} expired subjects (batchSize={})",
                expired.size(), batchSize);
        for (VotingSubject subject : expired) {
            try {
                votingApplicationService.settle(
                        new SettleSubjectCommand(subject.getSubjectId(), "SCHEDULER"));
            } catch (VotingApplicationException e) {
                log.warn("Settle skipped subjectId={} reason={} msg={}",
                        subject.getSubjectId(), e.getReason(), e.getMessage());
            } catch (RuntimeException e) {
                // 不让单议题失败影响整轮，记录后继续
                log.error("Settle failed unexpectedly subjectId={}", subject.getSubjectId(), e);
            }
        }
    }
}
