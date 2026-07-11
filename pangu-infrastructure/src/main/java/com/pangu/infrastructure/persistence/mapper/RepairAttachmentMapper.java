package com.pangu.infrastructure.persistence.mapper;

import com.pangu.infrastructure.persistence.entity.RepairAttachmentRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface RepairAttachmentMapper {

    int insert(RepairAttachmentRow row);

    RepairAttachmentRow findById(@Param("attachmentId") Long attachmentId,
                                 @Param("workOrderId") Long workOrderId,
                                 @Param("tenantId") Long tenantId);

    List<RepairAttachmentRow> findByIds(@Param("attachmentIds") Collection<Long> attachmentIds,
                                        @Param("workOrderId") Long workOrderId,
                                        @Param("tenantId") Long tenantId);

    int countActive(@Param("workOrderId") Long workOrderId,
                    @Param("tenantId") Long tenantId,
                    @Param("attachmentKind") String attachmentKind);

    int markBound(@Param("attachmentIds") Collection<Long> attachmentIds,
                  @Param("workOrderId") Long workOrderId,
                  @Param("tenantId") Long tenantId,
                  @Param("boundAction") String boundAction);

    int deleteUnbound(@Param("attachmentId") Long attachmentId,
                      @Param("workOrderId") Long workOrderId,
                      @Param("tenantId") Long tenantId);
}
