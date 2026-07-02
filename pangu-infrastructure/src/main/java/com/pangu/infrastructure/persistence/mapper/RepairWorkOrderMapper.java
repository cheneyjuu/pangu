package com.pangu.infrastructure.persistence.mapper;

import com.pangu.infrastructure.persistence.entity.OwnerPropertyDetailRow;
import com.pangu.infrastructure.persistence.entity.RepairWorkOrderEventRow;
import com.pangu.infrastructure.persistence.entity.RepairWorkOrderRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface RepairWorkOrderMapper {

    OwnerPropertyDetailRow selectOwnerProperty(@Param("uid") Long uid,
                                               @Param("tenantId") Long tenantId,
                                               @Param("opid") Long opid);

    boolean existsBuilding(@Param("tenantId") Long tenantId,
                           @Param("buildingId") Long buildingId);

    RepairWorkOrderRow findDuplicate(@Param("tenantId") Long tenantId,
                                     @Param("reporterAccountId") Long reporterAccountId,
                                     @Param("spaceScope") String spaceScope,
                                     @Param("roomId") Long roomId,
                                     @Param("buildingId") Long buildingId,
                                     @Param("title") String title,
                                     @Param("since") LocalDateTime since);

    int insert(RepairWorkOrderRow row);

    RepairWorkOrderRow findById(@Param("workOrderId") Long workOrderId);

    RepairWorkOrderRow findByIdForOwner(@Param("workOrderId") Long workOrderId,
                                        @Param("accountId") Long accountId,
                                        @Param("uid") Long uid,
                                        @Param("tenantId") Long tenantId);

    List<RepairWorkOrderRow> listForOwner(@Param("accountId") Long accountId,
                                          @Param("uid") Long uid,
                                          @Param("tenantId") Long tenantId);

    List<RepairWorkOrderRow> listForAdmin(@Param("tenantId") Long tenantId,
                                          @Param("roleKey") String roleKey,
                                          @Param("userId") Long userId,
                                          @Param("buildingIds") List<Long> buildingIds,
                                          @Param("status") String status,
                                          @Param("scope") String scope,
                                          @Param("keyword") String keyword,
                                          @Param("limit") int limit,
                                          @Param("offset") int offset);

    long countForAdmin(@Param("tenantId") Long tenantId,
                       @Param("roleKey") String roleKey,
                       @Param("userId") Long userId,
                       @Param("buildingIds") List<Long> buildingIds,
                       @Param("status") String status,
                       @Param("scope") String scope,
                       @Param("keyword") String keyword);

    int update(RepairWorkOrderRow row);

    int insertEvent(RepairWorkOrderEventRow row);

    List<RepairWorkOrderEventRow> listEvents(@Param("workOrderId") Long workOrderId,
                                             @Param("tenantId") Long tenantId);
}
