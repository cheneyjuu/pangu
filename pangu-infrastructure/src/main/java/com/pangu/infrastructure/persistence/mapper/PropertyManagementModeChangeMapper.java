// 关联业务：映射物业管理模式变更申请、决议材料、审核审计和租户模式原子执行操作。
package com.pangu.infrastructure.persistence.mapper;

import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

/**
 * 物业管理模式变更 MyBatis Mapper。
 */
@Mapper
public interface PropertyManagementModeChangeMapper {

    List<RequestRow> selectRequests(@Param("tenantId") Long tenantId);

    RequestRow selectRequest(@Param("tenantId") Long tenantId, @Param("requestId") Long requestId);

    RequestRow selectRequestForUpdate(@Param("tenantId") Long tenantId, @Param("requestId") Long requestId);

    int insertRequest(RequestRow row);

    int updateRequest(@Param("row") RequestRow row, @Param("expectedVersion") int expectedVersion);

    int insertMaterial(MaterialRow row);

    MaterialRow selectMaterial(@Param("requestId") Long requestId, @Param("materialId") Long materialId);

    List<MaterialRow> selectMaterials(@Param("requestId") Long requestId);

    int deactivateMaterial(@Param("requestId") Long requestId, @Param("materialId") Long materialId);

    long countActiveMaterialsByType(@Param("requestId") Long requestId,
                                    @Param("materialType") String materialType);

    List<AuditRow> selectAudits(@Param("requestId") Long requestId);

    int insertAudit(@Param("requestId") Long requestId,
                    @Param("actorAccountId") Long actorAccountId,
                    @Param("actorUserId") Long actorUserId,
                    @Param("actorDeptId") Long actorDeptId,
                    @Param("eventType") String eventType,
                    @Param("fromStatus") String fromStatus,
                    @Param("toStatus") String toStatus,
                    @Param("payloadJson") String payloadJson);

    int applyMode(@Param("tenantId") Long tenantId,
                  @Param("expectedCurrentMode") String expectedCurrentMode,
                  @Param("requestedMode") String requestedMode,
                  @Param("historyJson") String historyJson);

    @Data
    class RequestRow {
        private Long requestId;
        private Long tenantId;
        private String currentPropertyMode;
        private String requestedPropertyMode;
        private String ownersAssemblyResolutionReference;
        private String changeReason;
        private String status;
        private Long applicantAccountId;
        private Long applicantUserId;
        private Long applicantDeptId;
        private Instant submittedAt;
        private Long reviewerAccountId;
        private Long reviewerUserId;
        private Long reviewerDeptId;
        private String reviewComment;
        private Instant reviewedAt;
        private Instant executedAt;
        private Integer version;
        private Instant createTime;
        private Instant updateTime;
    }

    @Data
    class MaterialRow {
        private Long materialId;
        private Long requestId;
        private String materialType;
        private String objectKey;
        private String originalFileName;
        private String contentType;
        private Long fileSize;
        private String etag;
        private String sha256;
        private Long uploadedByAccountId;
        private String status;
        private Instant createTime;
    }

    @Data
    class AuditRow {
        private Long auditId;
        private Long requestId;
        private Long actorAccountId;
        private Long actorUserId;
        private Long actorDeptId;
        private String eventType;
        private String fromStatus;
        private String toStatus;
        private String payloadJson;
        private Instant createTime;
    }
}
