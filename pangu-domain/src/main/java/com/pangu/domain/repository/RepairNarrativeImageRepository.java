// 关联业务：持久化维修实施方案正文图片，并保证草稿图片只能绑定一次。
package com.pangu.domain.repository;

import com.pangu.domain.model.repair.RepairNarrativeImage;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RepairNarrativeImageRepository {

    RepairNarrativeImage insert(RepairNarrativeImage image);

    Optional<RepairNarrativeImage> findById(Long imageId, Long tenantId);

    List<RepairNarrativeImage> findByIds(Collection<Long> imageIds, Long tenantId);

    int bindDraftImages(Collection<Long> imageIds, Long tenantId, Long uploadedByAccountId,
                        Long projectId, Long planId);

    int deleteDraft(Long imageId, Long tenantId, Long uploadedByAccountId);

    List<RepairNarrativeImage> listExpiredDrafts(LocalDateTime createdBefore, int limit);

    int deleteExpiredDraft(Long imageId);
}
