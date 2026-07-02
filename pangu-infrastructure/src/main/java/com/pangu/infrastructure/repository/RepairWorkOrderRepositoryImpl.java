package com.pangu.infrastructure.repository;

import com.pangu.domain.model.asset.OwnerPropertyDetail;
import com.pangu.domain.model.repair.RepairSource;
import com.pangu.domain.model.repair.RepairSpaceScope;
import com.pangu.domain.model.repair.RepairWorkOrder;
import com.pangu.domain.model.repair.RepairWorkOrderEvent;
import com.pangu.domain.model.repair.RepairWorkOrderStatus;
import com.pangu.domain.repository.RepairWorkOrderRepository;
import com.pangu.infrastructure.persistence.entity.OwnerPropertyDetailRow;
import com.pangu.infrastructure.persistence.entity.RepairWorkOrderEventRow;
import com.pangu.infrastructure.persistence.entity.RepairWorkOrderRow;
import com.pangu.infrastructure.persistence.mapper.RepairWorkOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RepairWorkOrderRepositoryImpl implements RepairWorkOrderRepository {

    private final RepairWorkOrderMapper mapper;

    @Override
    public Optional<OwnerPropertyDetail> findOwnerProperty(Long uid, Long tenantId, Long opid) {
        return Optional.ofNullable(mapper.selectOwnerProperty(uid, tenantId, opid)).map(this::toOwnerProperty);
    }

    @Override
    public boolean buildingExists(Long tenantId, Long buildingId) {
        return mapper.existsBuilding(tenantId, buildingId);
    }

    @Override
    public Optional<RepairWorkOrder> findDuplicate(Long tenantId,
                                                   Long reporterAccountId,
                                                   RepairSpaceScope spaceScope,
                                                   Long roomId,
                                                   Long buildingId,
                                                   String title,
                                                   LocalDateTime since) {
        return Optional.ofNullable(mapper.findDuplicate(
                tenantId, reporterAccountId, spaceScope.name(), roomId, buildingId, title, since)).map(this::toDomain);
    }

    @Override
    public RepairWorkOrder insert(RepairWorkOrder workOrder) {
        RepairWorkOrderRow row = toRow(workOrder);
        mapper.insert(row);
        return findById(row.getWorkOrderId()).orElseThrow();
    }

    @Override
    public Optional<RepairWorkOrder> findById(Long workOrderId) {
        return Optional.ofNullable(mapper.findById(workOrderId)).map(this::toDomain);
    }

    @Override
    public Optional<RepairWorkOrder> findByIdForOwner(Long workOrderId, Long accountId, Long uid, Long tenantId) {
        return Optional.ofNullable(mapper.findByIdForOwner(workOrderId, accountId, uid, tenantId)).map(this::toDomain);
    }

    @Override
    public List<RepairWorkOrder> listForOwner(Long accountId, Long uid, Long tenantId) {
        return mapper.listForOwner(accountId, uid, tenantId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<RepairWorkOrder> listForAdmin(Long tenantId,
                                              String roleKey,
                                              Long userId,
                                              List<Long> authorizedBuildingIds,
                                              RepairWorkOrderStatus status,
                                              RepairSpaceScope scope,
                                              String keyword,
                                              int page,
                                              int size) {
        int offset = (Math.max(page, 1) - 1) * size;
        return mapper.listForAdmin(tenantId, roleKey, userId, authorizedBuildingIds,
                status == null ? null : status.name(),
                scope == null ? null : scope.name(),
                keyword, size, offset).stream().map(this::toDomain).toList();
    }

    @Override
    public long countForAdmin(Long tenantId,
                              String roleKey,
                              Long userId,
                              List<Long> authorizedBuildingIds,
                              RepairWorkOrderStatus status,
                              RepairSpaceScope scope,
                              String keyword) {
        return mapper.countForAdmin(tenantId, roleKey, userId, authorizedBuildingIds,
                status == null ? null : status.name(),
                scope == null ? null : scope.name(),
                keyword);
    }

    @Override
    public int update(RepairWorkOrder workOrder) {
        return mapper.update(toRow(workOrder));
    }

    @Override
    public void insertEvent(RepairWorkOrderEvent event) {
        mapper.insertEvent(toRow(event));
    }

    @Override
    public List<RepairWorkOrderEvent> listEvents(Long workOrderId, Long tenantId) {
        return mapper.listEvents(workOrderId, tenantId).stream().map(this::toDomain).toList();
    }

    private OwnerPropertyDetail toOwnerProperty(OwnerPropertyDetailRow row) {
        return new OwnerPropertyDetail(
                row.getOpid(),
                row.getBuildingId(),
                row.getRoomId(),
                row.getBuildArea(),
                row.getVotingDelegate() != null && row.getVotingDelegate() == 1,
                row.getAccountStatus());
    }

    private RepairWorkOrder toDomain(RepairWorkOrderRow row) {
        return new RepairWorkOrder(
                row.getWorkOrderId(),
                row.getOrderNo(),
                row.getTenantId(),
                row.getTitle(),
                row.getDescription(),
                RepairSource.valueOf(row.getSource()),
                RepairSpaceScope.valueOf(row.getSpaceScope()),
                RepairWorkOrderStatus.valueOf(row.getStatus()),
                row.getReporterAccountId(),
                row.getReporterUid(),
                row.getReporterUserId(),
                row.getRoomId(),
                row.getBuildingId(),
                row.getLocationText(),
                flag(row.getNeedManualLocation()),
                flag(row.getLocationLocked()),
                row.getAssignedUserId(),
                row.getAssigneeRoleKey(),
                row.getAssigneeDeptId(),
                row.getCategory(),
                row.getRiskLevel(),
                row.getSurveySummary(),
                row.getPlanBudget(),
                row.getFundSource(),
                flag(row.getFundGateBlocked()),
                row.getSatisfactionScore(),
                row.getSatisfactionComment(),
                row.getVersion(),
                row.getCreateTime(),
                row.getUpdateTime());
    }

    private RepairWorkOrderRow toRow(RepairWorkOrder domain) {
        RepairWorkOrderRow row = new RepairWorkOrderRow();
        row.setWorkOrderId(domain.workOrderId());
        row.setOrderNo(domain.orderNo());
        row.setTenantId(domain.tenantId());
        row.setTitle(domain.title());
        row.setDescription(domain.description());
        row.setSource(domain.source().name());
        row.setSpaceScope(domain.spaceScope().name());
        row.setStatus(domain.status().name());
        row.setReporterAccountId(domain.reporterAccountId());
        row.setReporterUid(domain.reporterUid());
        row.setReporterUserId(domain.reporterUserId());
        row.setRoomId(domain.roomId());
        row.setBuildingId(domain.buildingId());
        row.setLocationText(domain.locationText());
        row.setNeedManualLocation(flag(domain.needManualLocation()));
        row.setLocationLocked(flag(domain.locationLocked()));
        row.setAssignedUserId(domain.assignedUserId());
        row.setAssigneeRoleKey(domain.assigneeRoleKey());
        row.setAssigneeDeptId(domain.assigneeDeptId());
        row.setCategory(domain.category());
        row.setRiskLevel(domain.riskLevel());
        row.setSurveySummary(domain.surveySummary());
        row.setPlanBudget(domain.planBudget());
        row.setFundSource(domain.fundSource());
        row.setFundGateBlocked(flag(domain.fundGateBlocked()));
        row.setSatisfactionScore(domain.satisfactionScore());
        row.setSatisfactionComment(domain.satisfactionComment());
        row.setVersion(domain.version());
        return row;
    }

    private RepairWorkOrderEvent toDomain(RepairWorkOrderEventRow row) {
        return new RepairWorkOrderEvent(
                row.getEventId(),
                row.getWorkOrderId(),
                row.getTenantId(),
                row.getAction(),
                row.getFromStatus() == null ? null : RepairWorkOrderStatus.valueOf(row.getFromStatus()),
                row.getToStatus() == null ? null : RepairWorkOrderStatus.valueOf(row.getToStatus()),
                row.getActorAccountId(),
                row.getActorIdentityType(),
                row.getActorIdentityId(),
                row.getRemark(),
                row.getPayloadJson(),
                row.getCreateTime());
    }

    private RepairWorkOrderEventRow toRow(RepairWorkOrderEvent event) {
        RepairWorkOrderEventRow row = new RepairWorkOrderEventRow();
        row.setEventId(event.eventId());
        row.setWorkOrderId(event.workOrderId());
        row.setTenantId(event.tenantId());
        row.setAction(event.action());
        row.setFromStatus(event.fromStatus() == null ? null : event.fromStatus().name());
        row.setToStatus(event.toStatus() == null ? null : event.toStatus().name());
        row.setActorAccountId(event.actorAccountId());
        row.setActorIdentityType(event.actorIdentityType());
        row.setActorIdentityId(event.actorIdentityId());
        row.setRemark(event.remark());
        row.setPayloadJson(event.payloadJson());
        return row;
    }

    private boolean flag(Integer value) {
        return value != null && value == 1;
    }

    private int flag(boolean value) {
        return value ? 1 : 0;
    }
}
