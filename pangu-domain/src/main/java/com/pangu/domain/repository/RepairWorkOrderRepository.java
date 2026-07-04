package com.pangu.domain.repository;

import com.pangu.domain.model.asset.OwnerPropertyDetail;
import com.pangu.domain.model.repair.RepairSpaceScope;
import com.pangu.domain.model.repair.RepairWorkOrder;
import com.pangu.domain.model.repair.RepairWorkOrderEvent;
import com.pangu.domain.model.repair.RepairWorkOrderStatus;
import com.pangu.domain.model.user.WorkIdentityBuildingScope;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RepairWorkOrderRepository {

    Optional<OwnerPropertyDetail> findOwnerProperty(Long uid, Long tenantId, Long opid);

    boolean buildingExists(Long tenantId, Long buildingId);

    Optional<RepairWorkOrder> findDuplicate(Long tenantId,
                                            Long reporterAccountId,
                                            RepairSpaceScope spaceScope,
                                            Long roomId,
                                            Long buildingId,
                                            String title,
                                            LocalDateTime since);

    RepairWorkOrder insert(RepairWorkOrder workOrder);

    Optional<RepairWorkOrder> findById(Long workOrderId);

    Optional<RepairWorkOrder> findByIdForOwner(Long workOrderId, Long accountId, Long uid, Long tenantId);

    List<RepairWorkOrder> listForOwner(Long accountId, Long uid, Long tenantId);

    List<RepairWorkOrder> listForAdmin(Long tenantId,
                                       String roleKey,
                                       Long userId,
                                       List<WorkIdentityBuildingScope> authorizedBuildingScopes,
                                       RepairWorkOrderStatus status,
                                       RepairSpaceScope scope,
                                       String keyword,
                                       int page,
                                       int size);

    long countForAdmin(Long tenantId,
                       String roleKey,
                       Long userId,
                       List<WorkIdentityBuildingScope> authorizedBuildingScopes,
                       RepairWorkOrderStatus status,
                       RepairSpaceScope scope,
                       String keyword);

    int update(RepairWorkOrder workOrder);

    void insertEvent(RepairWorkOrderEvent event);

    List<RepairWorkOrderEvent> listEvents(Long workOrderId, Long tenantId);
}
