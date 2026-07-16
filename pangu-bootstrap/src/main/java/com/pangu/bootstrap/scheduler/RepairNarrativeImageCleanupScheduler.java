// 关联业务：定期清理未绑定到维修实施方案的过期正文图片。
package com.pangu.bootstrap.scheduler;

import com.pangu.application.repair.RepairNarrativeImageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RepairNarrativeImageCleanupScheduler {

    private final RepairNarrativeImageService imageService;

    @Scheduled(cron = "${platform.repair.narrative-image-cleanup-cron:0 20 3 * * *}")
    public void cleanup() {
        int removed = imageService.cleanupExpiredDrafts();
        if (removed > 0) {
            log.info("已清理过期维修方案正文图片 count={}", removed);
        }
    }
}
