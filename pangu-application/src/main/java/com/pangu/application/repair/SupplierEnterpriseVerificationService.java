// 关联业务：编排物业手工或第三方平台执行的供应商企业主体核验及租户级审计留痕。
package com.pangu.application.repair;

import com.pangu.application.repair.command.ManualSupplierEnterpriseVerificationCommand;
import com.pangu.application.repair.command.PlatformSupplierEnterpriseVerificationCommand;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.repair.EnterpriseVerificationProviderDescriptor;
import com.pangu.domain.model.repair.SupplierEnterpriseVerificationMethod;
import com.pangu.domain.model.repair.SupplierEnterpriseVerificationRecord;
import com.pangu.domain.model.repair.SupplierEnterpriseVerificationResult;
import com.pangu.domain.model.repair.SupplierEnterpriseVerificationTarget;
import com.pangu.domain.model.repair.SupplierManualVerificationSource;
import com.pangu.domain.repository.EnterpriseVerificationProvider;
import com.pangu.domain.repository.SupplierEnterpriseVerificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.EXTERNAL_VERIFICATION_UNAVAILABLE;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.FORBIDDEN;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.INVALID_STATUS;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.NOT_FOUND;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.PARAM_INVALID;

@Service
@RequiredArgsConstructor
public class SupplierEnterpriseVerificationService {

    private static final String VERIFY_PERMISSION = "repair:supplier:verify";
    private static final String PROPERTY_MANAGER_ROLE = "PROPERTY_MANAGER";
    private static final String USCC_PATTERN = "[0-9A-HJ-NPQRTUWXY]{18}";

    private final SupplierEnterpriseVerificationRepository repository;
    private final EnterpriseVerificationProvider provider;
    private final UserContextHolder userContextHolder;
    private final TransactionTemplate transactionTemplate;

    public EnterpriseVerificationProviderDescriptor providerDescriptor() {
        requireVerifier();
        return new EnterpriseVerificationProviderDescriptor(
                provider.providerCode(), provider.displayName(), provider.simulated());
    }

    public SupplierEnterpriseVerificationRecord verifyManually(
            Long supplierDeptId,
            ManualSupplierEnterpriseVerificationCommand command) {
        UserContext context = requireVerifier();
        Long tenantId = requireTenant(context);
        String uscc = normalizeUscc(command.unifiedSocialCreditCode());
        SupplierManualVerificationSource source = parseSource(command.sourceCode());
        SupplierEnterpriseVerificationResult result = parseManualResult(command.verificationResult());
        String evidenceReference = trim(command.evidenceReference());
        String remark = trim(command.remark());
        if (source == SupplierManualVerificationSource.OTHER_GOVERNMENT_SOURCE && evidenceReference == null) {
            throw new RepairWorkOrderApplicationException(
                    PARAM_INVALID, "使用其他政府信息来源时必须填写查询来源或凭证编号");
        }
        if (result == SupplierEnterpriseVerificationResult.REJECTED && remark == null) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "手工核验不通过时必须填写原因");
        }

        LocalDateTime verifiedAt = LocalDateTime.now();
        return requireTransactionResult(transactionTemplate.execute(status -> {
            SupplierEnterpriseVerificationTarget target = lockTarget(tenantId, supplierDeptId);
            validateTargetUscc(target, uscc);
            assertUsccAvailable(target, uscc);
            SupplierEnterpriseVerificationRecord record = new SupplierEnterpriseVerificationRecord(
                    null, tenantId, supplierDeptId, target.legalName(), uscc,
                    SupplierEnterpriseVerificationMethod.PROPERTY_MANUAL,
                    null, source.name(), null, null, result,
                    null,
                    result == SupplierEnterpriseVerificationResult.PASSED
                            ? "物业已按所选政府信息来源核对企业名称与统一社会信用代码"
                            : "物业人工核验未通过",
                    List.of(), evidenceReference, remark,
                    context.accountId(), context.userId(), context.roleKey(), false, verifiedAt);
            return persistOutcome(record);
        }));
    }

    public SupplierEnterpriseVerificationRecord verifyWithPlatform(
            Long supplierDeptId,
            PlatformSupplierEnterpriseVerificationCommand command) {
        UserContext context = requireVerifier();
        Long tenantId = requireTenant(context);
        if (!command.supplierAuthorizationConfirmed()) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "调用企业核验平台前必须确认供应商授权");
        }
        String uscc = normalizeUscc(command.unifiedSocialCreditCode());
        SupplierEnterpriseVerificationTarget initialTarget = repository.findTarget(tenantId, supplierDeptId)
                .orElseThrow(() -> new RepairWorkOrderApplicationException(NOT_FOUND, "供应商不存在或不属于当前小区"));
        validateTargetUscc(initialTarget, uscc);
        assertUsccAvailable(initialTarget, uscc);

        EnterpriseVerificationProvider.VerificationResult providerResult;
        try {
            providerResult = provider.verify(new EnterpriseVerificationProvider.VerificationRequest(
                    tenantId, supplierDeptId, initialTarget.legalName(), uscc, true));
            validateProviderResult(providerResult);
        } catch (RuntimeException ex) {
            recordProviderError(context, initialTarget, uscc, ex);
            throw new RepairWorkOrderApplicationException(
                    EXTERNAL_VERIFICATION_UNAVAILABLE, "企业核验平台暂时不可用，请稍后重试", ex);
        }

        LocalDateTime verifiedAt = LocalDateTime.now();
        SupplierEnterpriseVerificationResult result = providerResult.matched()
                ? SupplierEnterpriseVerificationResult.PASSED
                : SupplierEnterpriseVerificationResult.REJECTED;
        return requireTransactionResult(transactionTemplate.execute(status -> {
            SupplierEnterpriseVerificationTarget lockedTarget = lockTarget(tenantId, supplierDeptId);
            if (!Objects.equals(initialTarget.legalName(), lockedTarget.legalName())) {
                throw new RepairWorkOrderApplicationException(INVALID_STATUS, "供应商企业名称已变化，请重新发起核验");
            }
            validateTargetUscc(lockedTarget, uscc);
            assertUsccAvailable(lockedTarget, uscc);
            SupplierEnterpriseVerificationRecord record = new SupplierEnterpriseVerificationRecord(
                    null, tenantId, supplierDeptId, lockedTarget.legalName(), uscc,
                    SupplierEnterpriseVerificationMethod.PLATFORM_API,
                    provider.providerCode(), null,
                    trimToLength(providerResult.providerRequestId(), 128),
                    trimToLength(providerResult.providerResultCode(), 64),
                    result,
                    trimToLength(providerResult.businessStatus(), 64),
                    trimToLength(providerResult.resultMessage(), 500),
                    providerResult.inconsistentFields(), null,
                    provider.simulated() ? "开发测试模拟平台核验" : "供应商授权后发起平台核验",
                    context.accountId(), context.userId(), context.roleKey(), provider.simulated(), verifiedAt);
            return persistOutcome(record);
        }));
    }

    @Transactional(readOnly = true)
    public List<SupplierEnterpriseVerificationRecord> listHistory(Long supplierDeptId) {
        UserContext context = requireVerifier();
        Long tenantId = requireTenant(context);
        repository.findTarget(tenantId, supplierDeptId)
                .orElseThrow(() -> new RepairWorkOrderApplicationException(NOT_FOUND, "供应商不存在或不属于当前小区"));
        return repository.list(tenantId, supplierDeptId);
    }

    private SupplierEnterpriseVerificationRecord persistOutcome(SupplierEnterpriseVerificationRecord record) {
        SupplierEnterpriseVerificationRecord saved = repository.insert(record);
        String currentStatus = switch (record.verificationResult()) {
            case PASSED -> "VERIFIED";
            case REJECTED -> "REJECTED";
            case ERROR -> null;
        };
        if (currentStatus != null && repository.applyCurrentResult(
                record.tenantId(), record.supplierDeptId(), record.unifiedSocialCreditCodeSnapshot(),
                saved.verificationId(), currentStatus, record.operatorUserId(), record.verifiedAt()) < 1) {
            throw new RepairWorkOrderApplicationException(INVALID_STATUS, "供应商核验状态已变化，请刷新后重试");
        }
        return saved;
    }

    private void recordProviderError(UserContext context,
                                     SupplierEnterpriseVerificationTarget target,
                                     String uscc,
                                     RuntimeException cause) {
        try {
            transactionTemplate.executeWithoutResult(status -> repository.insert(
                    new SupplierEnterpriseVerificationRecord(
                            null, target.tenantId(), target.supplierDeptId(), target.legalName(), uscc,
                            SupplierEnterpriseVerificationMethod.PLATFORM_API,
                            provider.providerCode(), null, null, "PROVIDER_ERROR",
                            SupplierEnterpriseVerificationResult.ERROR, null,
                            trimToLength(cause.getMessage(), 500), List.of(), null,
                            "企业核验平台调用失败",
                            context.accountId(), context.userId(), context.roleKey(),
                            provider.simulated(), LocalDateTime.now())));
        } catch (RuntimeException auditFailure) {
            cause.addSuppressed(auditFailure);
        }
    }

    private SupplierEnterpriseVerificationTarget lockTarget(Long tenantId, Long supplierDeptId) {
        return repository.findTargetForUpdate(tenantId, supplierDeptId)
                .orElseThrow(() -> new RepairWorkOrderApplicationException(NOT_FOUND, "供应商不存在或不属于当前小区"));
    }

    private void validateTargetUscc(SupplierEnterpriseVerificationTarget target, String uscc) {
        if (target.unifiedSocialCreditCode() != null
                && !target.unifiedSocialCreditCode().equalsIgnoreCase(uscc)) {
            throw new RepairWorkOrderApplicationException(
                    PARAM_INVALID, "核验代码与供应商已登记统一社会信用代码不一致");
        }
    }

    private void assertUsccAvailable(SupplierEnterpriseVerificationTarget target, String uscc) {
        repository.findSupplierDeptIdByUscc(uscc)
                .filter(existingSupplierDeptId -> !existingSupplierDeptId.equals(target.supplierDeptId()))
                .ifPresent(existingSupplierDeptId -> {
                    throw new RepairWorkOrderApplicationException(
                            PARAM_INVALID, "该统一社会信用代码已绑定其他供应商组织");
                });
    }

    private void validateProviderResult(EnterpriseVerificationProvider.VerificationResult result) {
        if (result == null || trim(result.providerRequestId()) == null || trim(result.providerResultCode()) == null) {
            throw new IllegalStateException("企业核验平台返回结果缺少审计标识");
        }
    }

    private SupplierManualVerificationSource parseSource(String value) {
        try {
            return SupplierManualVerificationSource.valueOf(requireText(value, "sourceCode").toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new RepairWorkOrderApplicationException(
                    PARAM_INVALID, "手工核验来源仅支持 GSXT_WEB 或 OTHER_GOVERNMENT_SOURCE", ex);
        }
    }

    private SupplierEnterpriseVerificationResult parseManualResult(String value) {
        try {
            SupplierEnterpriseVerificationResult result = SupplierEnterpriseVerificationResult.valueOf(
                    requireText(value, "verificationResult").toUpperCase());
            if (result == SupplierEnterpriseVerificationResult.ERROR) {
                throw new IllegalArgumentException("ERROR 不是手工核验结论");
            }
            return result;
        } catch (IllegalArgumentException ex) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "手工核验结论仅支持 PASSED 或 REJECTED", ex);
        }
    }

    private String normalizeUscc(String value) {
        String uscc = requireText(value, "unifiedSocialCreditCode").toUpperCase();
        if (!uscc.matches(USCC_PATTERN)) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "统一社会信用代码格式不正确");
        }
        return uscc;
    }

    private UserContext requireVerifier() {
        UserContext context = userContextHolder.current();
        if (context == null || !context.isSysUser() || context.accountId() == null || context.userId() == null) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, "未识别到管理端工作身份");
        }
        if (!PROPERTY_MANAGER_ROLE.equals(context.roleKey()) || !context.hasPermission(VERIFY_PERMISSION)) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, "仅获授权的物业经理可以核验供应商企业主体");
        }
        return context;
    }

    private Long requireTenant(UserContext context) {
        if (context.tenantId() == null) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, "未识别到租户上下文");
        }
        return context.tenantId();
    }

    private String requireText(String value, String field) {
        String trimmed = trim(value);
        if (trimmed == null) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, field + " 不能为空");
        }
        return trimmed;
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String trimToLength(String value, int maxLength) {
        String trimmed = trim(value);
        return trimmed == null || trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private <T> T requireTransactionResult(T value) {
        if (value == null) {
            throw new IllegalStateException("供应商企业核验事务未返回结果");
        }
        return value;
    }
}
