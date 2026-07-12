// 关联业务：在领域电子印章模型与 MyBatis 持久化结构之间完成转换。
package com.pangu.infrastructure.repository;

import com.pangu.domain.model.committee.CommitteeElectronicSeal;
import com.pangu.domain.model.committee.CommitteeSealStatus;
import com.pangu.domain.model.committee.CommitteeSealType;
import com.pangu.domain.model.committee.CommitteeSealUsageRecord;
import com.pangu.domain.repository.CommitteeSealRepository;
import com.pangu.infrastructure.persistence.entity.CommitteeElectronicSealRow;
import com.pangu.infrastructure.persistence.entity.CommitteeSealUsageRow;
import com.pangu.infrastructure.persistence.mapper.CommitteeSealMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CommitteeSealRepositoryImpl implements CommitteeSealRepository {

    private final CommitteeSealMapper mapper;

    @Override
    public List<CommitteeElectronicSeal> listByTenant(Long tenantId) {
        return mapper.selectByTenant(tenantId).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<CommitteeElectronicSeal> findById(Long sealId, Long tenantId) {
        return Optional.ofNullable(mapper.selectById(sealId, tenantId)).map(this::toDomain);
    }

    @Override
    public Long insert(CommitteeElectronicSeal seal) {
        CommitteeElectronicSealRow row = toRow(seal);
        mapper.insertSeal(row);
        return row.getElectronicSealId();
    }

    @Override
    public int deactivate(Long sealId, Long tenantId, Long operatorUserId) {
        return mapper.deactivate(sealId, tenantId, operatorUserId);
    }

    @Override
    public Long insertUsage(CommitteeSealUsageRecord usage) {
        CommitteeSealUsageRow row = toRow(usage);
        mapper.insertUsage(row);
        return row.getUsageId();
    }

    @Override
    public List<CommitteeSealUsageRecord> listUsageByTenant(Long tenantId, int limit) {
        return mapper.selectUsageByTenant(tenantId, limit).stream().map(this::toDomain).toList();
    }

    private CommitteeElectronicSeal toDomain(CommitteeElectronicSealRow row) {
        return new CommitteeElectronicSeal(
                row.getElectronicSealId(), row.getTenantId(), row.getSealName(),
                CommitteeSealType.valueOf(row.getSealType()), row.getProviderCode(), row.getProviderSealId(),
                row.getCertificateSerial(), row.getValidFrom(), row.getValidUntil(),
                CommitteeSealStatus.valueOf(row.getStatus()), row.getCustodianUserId(), row.getCustodianName(),
                row.getCommitteeTermName(), Integer.valueOf(1).equals(row.getSimulated()),
                row.getCreatedByUserId(), row.getCreateTime(), row.getUpdateTime());
    }

    private CommitteeElectronicSealRow toRow(CommitteeElectronicSeal seal) {
        CommitteeElectronicSealRow row = new CommitteeElectronicSealRow();
        row.setElectronicSealId(seal.sealId());
        row.setTenantId(seal.tenantId());
        row.setSealName(seal.sealName());
        row.setSealType(seal.sealType().name());
        row.setProviderCode(seal.providerCode());
        row.setProviderSealId(seal.providerSealId());
        row.setCertificateSerial(seal.certificateSerial());
        row.setValidFrom(seal.validFrom());
        row.setValidUntil(seal.validUntil());
        row.setStatus(seal.status().name());
        row.setCustodianUserId(seal.custodianUserId());
        row.setCommitteeTermName(seal.committeeTermName());
        row.setSimulated(seal.simulated() ? 1 : 0);
        row.setCreatedByUserId(seal.createdByUserId());
        return row;
    }

    private CommitteeSealUsageRecord toDomain(CommitteeSealUsageRow row) {
        return new CommitteeSealUsageRecord(
                row.getUsageId(), row.getTenantId(), row.getElectronicSealId(), row.getSealName(),
                row.getBusinessType(), row.getBusinessId(), row.getBusinessTitle(), row.getSealingMethod(),
                row.getSourceAttachmentId(), row.getSealedAttachmentId(), row.getSourceFileHash(),
                row.getSealedFileHash(), row.getProviderTransactionId(), row.getCertificateSerial(),
                row.getVerificationStatus(), Integer.valueOf(1).equals(row.getSimulated()),
                row.getOperatorUserId(), row.getOperatorName(), row.getRemark(), row.getCreateTime());
    }

    private CommitteeSealUsageRow toRow(CommitteeSealUsageRecord usage) {
        CommitteeSealUsageRow row = new CommitteeSealUsageRow();
        row.setUsageId(usage.usageId());
        row.setTenantId(usage.tenantId());
        row.setElectronicSealId(usage.electronicSealId());
        row.setSealName(usage.sealName());
        row.setBusinessType(usage.businessType());
        row.setBusinessId(usage.businessId());
        row.setSealingMethod(usage.sealingMethod());
        row.setSourceAttachmentId(usage.sourceAttachmentId());
        row.setSealedAttachmentId(usage.sealedAttachmentId());
        row.setSourceFileHash(usage.sourceFileHash());
        row.setSealedFileHash(usage.sealedFileHash());
        row.setProviderTransactionId(usage.providerTransactionId());
        row.setCertificateSerial(usage.certificateSerial());
        row.setVerificationStatus(usage.verificationStatus());
        row.setSimulated(usage.simulated() ? 1 : 0);
        row.setOperatorUserId(usage.operatorUserId());
        row.setRemark(usage.remark());
        return row;
    }
}
