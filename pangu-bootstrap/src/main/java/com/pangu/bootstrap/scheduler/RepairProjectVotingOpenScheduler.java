// 关联业务：按已确认的表决时间自动开启维修工程相关业主表决，并同步统一表决包与维修办理状态。
package com.pangu.bootstrap.scheduler;

import com.pangu.application.repair.RepairProjectVotingService;
import com.pangu.domain.model.repair.RepairProjectVoting;
import com.pangu.domain.repository.RepairProjectVotingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * 维修项目表决自动开启任务。
 *
 * <p>普通议题扫描器只负责未接入统一表决包的历史议题；维修项目必须通过本任务在同一事务中
 * 同步开启维修关联、统一表决包和议题，确保管理端与 C 端看到同一办理状态。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RepairProjectVotingOpenScheduler {

    private final RepairProjectVotingRepository votingRepository;
    private final RepairProjectVotingService votingService;
    private final Clock clock;

    @Value("${platform.repair.voting.open-batch-size:100}")
    private int batchSize;

    @Scheduled(cron = "${platform.repair.voting.open-cron:15 * * * * *}")
    public void tick() {
        Instant now = clock.instant();
        List<RepairProjectVoting> ready = votingRepository.listReadyForOpen(now, batchSize);
        for (RepairProjectVoting candidate : ready) {
            try {
                if (votingService.openDue(candidate, now)) {
                    log.info("Repair voting opened automatically projectId={} linkId={}",
                            candidate.projectId(), candidate.linkId());
                }
            } catch (RuntimeException failure) {
                log.error("Repair voting automatic open failed projectId={} linkId={}",
                        candidate.projectId(), candidate.linkId(), failure);
            }
        }
    }
}
