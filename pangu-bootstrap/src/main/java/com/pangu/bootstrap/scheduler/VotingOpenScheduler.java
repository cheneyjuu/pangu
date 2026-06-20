package com.pangu.bootstrap.scheduler;

import com.pangu.application.voting.ProposalLifecycleService;
import com.pangu.application.voting.VotingApplicationException;
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
 * 议题开票扫描器（M3-2 引入）：每分钟扫一次 {@code status = PUBLISHED && vote_start_at <= now()}
 * 的议题，逐个调用 {@link ProposalLifecycleService#openVoting} 翻状态到 VOTING。
 *
 * <p>设计风格完全镜像 {@link VotingDeadlineScheduler}：
 * <ul>
 *   <li>每议题独立事务（由 {@code ProposalLifecycleService.openVoting} 上的 {@code @Transactional} 提供）；</li>
 *   <li>失败不阻塞下一议题；乐观锁失败 → 下一轮继续扫；</li>
 *   <li>限流：每轮最多 {@code platform.voting.open-batch-size}（默认 100）条。</li>
 * </ul>
 *
 * <p>分布式部署风险（已知）：本期假设单实例运行；多实例并发会有重复触发，靠
 * {@code subject_repository.updateStatus(... WHERE version=?)} 乐观锁兜底（重复方失败自然丢弃），
 * 不会出现状态错乱。M4 引入 Redisson scheduler 锁后再彻底消除。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VotingOpenScheduler {

    private final VotingSubjectRepository subjectRepository;
    private final ProposalLifecycleService proposalLifecycleService;

    @Value("${platform.voting.open-batch-size:100}")
    private int batchSize;

    @Scheduled(cron = "${platform.voting.open-cron:0 * * * * *}")
    public void tick() {
        Instant now = Instant.now();
        List<VotingSubject> ready = subjectRepository.findPublishedReadyForOpen(now, batchSize);
        if (ready.isEmpty()) {
            return;
        }
        log.info("VotingOpenScheduler tick: found {} subjects ready to open (batchSize={})",
                ready.size(), batchSize);
        for (VotingSubject subject : ready) {
            try {
                proposalLifecycleService.openVoting(subject.getSubjectId(), now);
            } catch (VotingApplicationException e) {
                log.warn("Open voting skipped subjectId={} reason={} msg={}",
                        subject.getSubjectId(), e.getReason(), e.getMessage());
            } catch (RuntimeException e) {
                log.error("Open voting failed unexpectedly subjectId={}", subject.getSubjectId(), e);
            }
        }
    }
}
