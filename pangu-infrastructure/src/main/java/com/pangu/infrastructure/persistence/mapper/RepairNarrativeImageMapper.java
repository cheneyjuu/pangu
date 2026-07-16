// 关联业务：读写维修实施方案正文图片，执行单次绑定和过期草稿清理。
package com.pangu.infrastructure.persistence.mapper;

import com.pangu.infrastructure.persistence.entity.RepairNarrativeImageRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Mapper
public interface RepairNarrativeImageMapper {

    int insert(RepairNarrativeImageRow row);

    RepairNarrativeImageRow findById(@Param("imageId") Long imageId,
                                     @Param("tenantId") Long tenantId);

    List<RepairNarrativeImageRow> findByIds(@Param("imageIds") Collection<Long> imageIds,
                                            @Param("tenantId") Long tenantId);

    int bindDraftImages(@Param("imageIds") Collection<Long> imageIds,
                        @Param("tenantId") Long tenantId,
                        @Param("uploadedByAccountId") Long uploadedByAccountId,
                        @Param("projectId") Long projectId,
                        @Param("planId") Long planId);

    int deleteDraft(@Param("imageId") Long imageId,
                    @Param("tenantId") Long tenantId,
                    @Param("uploadedByAccountId") Long uploadedByAccountId);

    List<RepairNarrativeImageRow> listExpiredDrafts(@Param("createdBefore") LocalDateTime createdBefore,
                                                     @Param("limit") int limit);

    int deleteExpiredDraft(@Param("imageId") Long imageId);
}
