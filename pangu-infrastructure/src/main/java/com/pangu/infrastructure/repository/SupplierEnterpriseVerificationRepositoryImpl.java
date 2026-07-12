// 关联业务：实现供应商企业主体核验目标锁定、结论更新和审计历史持久化。
package com.pangu.infrastructure.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.domain.model.repair.SupplierEnterpriseVerificationMethod;
import com.pangu.domain.model.repair.SupplierEnterpriseVerificationRecord;
import com.pangu.domain.model.repair.SupplierEnterpriseVerificationResult;
import com.pangu.domain.model.repair.SupplierEnterpriseVerificationTarget;
import com.pangu.domain.repository.SupplierEnterpriseVerificationRepository;
import com.pangu.infrastructure.persistence.entity.SupplierEnterpriseVerificationRow;
import com.pangu.infrastructure.persistence.entity.SupplierEnterpriseVerificationTargetRow;
import com.pangu.infrastructure.persistence.mapper.SupplierEnterpriseVerificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class SupplierEnterpriseVerificationRepositoryImpl implements SupplierEnterpriseVerificationRepository {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final SupplierEnterpriseVerificationMapper mapper;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<SupplierEnterpriseVerificationTarget> findTarget(Long tenantId, Long supplierDeptId) {
        return Optional.ofNullable(mapper.findTarget(tenantId, supplierDeptId)).map(this::toDomain);
    }

    @Override
    public Optional<SupplierEnterpriseVerificationTarget> findTargetForUpdate(Long tenantId, Long supplierDeptId) {
        return Optional.ofNullable(mapper.findTargetForUpdate(tenantId, supplierDeptId)).map(this::toDomain);
    }

    @Override
    public Optional<Long> findSupplierDeptIdByUscc(String unifiedSocialCreditCode) {
        return Optional.ofNullable(mapper.findSupplierDeptIdByUscc(unifiedSocialCreditCode));
    }

    @Override
    public SupplierEnterpriseVerificationRecord insert(SupplierEnterpriseVerificationRecord record) {
        SupplierEnterpriseVerificationRow row = toRow(record);
        mapper.insert(row);
        return new SupplierEnterpriseVerificationRecord(
                row.getVerificationId(), record.tenantId(), record.supplierDeptId(), record.legalNameSnapshot(),
                record.unifiedSocialCreditCodeSnapshot(), record.verificationMethod(), record.providerCode(),
                record.sourceCode(), record.providerRequestId(), record.providerResultCode(),
                record.verificationResult(), record.businessStatus(), record.resultMessage(),
                record.inconsistentFields(), record.evidenceReference(), record.remark(),
                record.operatorAccountId(), record.operatorUserId(), record.operatorRoleKey(),
                record.simulated(), record.verifiedAt());
    }

    @Override
    public int applyCurrentResult(Long tenantId,
                                  Long supplierDeptId,
                                  String unifiedSocialCreditCode,
                                  Long verificationId,
                                  String verificationStatus,
                                  Long verifiedByUserId,
                                  LocalDateTime verifiedAt) {
        if (mapper.completeSupplierUscc(supplierDeptId, unifiedSocialCreditCode) != 1) {
            return 0;
        }
        return mapper.applyCurrentResult(tenantId, supplierDeptId, verificationId,
                verificationStatus, verifiedByUserId, verifiedAt);
    }

    @Override
    public List<SupplierEnterpriseVerificationRecord> list(Long tenantId, Long supplierDeptId) {
        return mapper.list(tenantId, supplierDeptId).stream().map(this::toDomain).toList();
    }

    private SupplierEnterpriseVerificationTarget toDomain(SupplierEnterpriseVerificationTargetRow row) {
        return new SupplierEnterpriseVerificationTarget(
                row.getTenantId(), row.getSupplierDeptId(), row.getLegalName(),
                row.getUnifiedSocialCreditCode(), row.getVerificationStatus());
    }

    private SupplierEnterpriseVerificationRecord toDomain(SupplierEnterpriseVerificationRow row) {
        try {
            List<String> inconsistentFields = objectMapper.readValue(
                    row.getInconsistentFieldsJson() == null ? "[]" : row.getInconsistentFieldsJson(), STRING_LIST);
            return new SupplierEnterpriseVerificationRecord(
                    row.getVerificationId(), row.getTenantId(), row.getSupplierDeptId(), row.getLegalNameSnapshot(),
                    row.getUnifiedSocialCreditCodeSnapshot(),
                    SupplierEnterpriseVerificationMethod.valueOf(row.getVerificationMethod()),
                    row.getProviderCode(), row.getSourceCode(), row.getProviderRequestId(),
                    row.getProviderResultCode(), SupplierEnterpriseVerificationResult.valueOf(row.getVerificationResult()),
                    row.getBusinessStatus(), row.getResultMessage(), inconsistentFields,
                    row.getEvidenceReference(), row.getRemark(), row.getOperatorAccountId(),
                    row.getOperatorUserId(), row.getOperatorRoleKey(), row.isSimulated(), row.getVerifiedAt());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("供应商企业核验不一致字段不是合法 JSON", ex);
        }
    }

    private SupplierEnterpriseVerificationRow toRow(SupplierEnterpriseVerificationRecord record) {
        SupplierEnterpriseVerificationRow row = new SupplierEnterpriseVerificationRow();
        row.setTenantId(record.tenantId());
        row.setSupplierDeptId(record.supplierDeptId());
        row.setLegalNameSnapshot(record.legalNameSnapshot());
        row.setUnifiedSocialCreditCodeSnapshot(record.unifiedSocialCreditCodeSnapshot());
        row.setVerificationMethod(record.verificationMethod().name());
        row.setProviderCode(record.providerCode());
        row.setSourceCode(record.sourceCode());
        row.setProviderRequestId(record.providerRequestId());
        row.setProviderResultCode(record.providerResultCode());
        row.setVerificationResult(record.verificationResult().name());
        row.setBusinessStatus(record.businessStatus());
        row.setResultMessage(record.resultMessage());
        try {
            row.setInconsistentFieldsJson(objectMapper.writeValueAsString(record.inconsistentFields()));
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("供应商企业核验不一致字段无法序列化", ex);
        }
        row.setEvidenceReference(record.evidenceReference());
        row.setRemark(record.remark());
        row.setOperatorAccountId(record.operatorAccountId());
        row.setOperatorUserId(record.operatorUserId());
        row.setOperatorRoleKey(record.operatorRoleKey());
        row.setSimulated(record.simulated());
        row.setVerifiedAt(record.verifiedAt());
        return row;
    }
}
