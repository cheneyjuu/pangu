// 关联业务：管理业主自治组织电子印章台账、保管责任、届期有效性和用印记录。
package com.pangu.application.committee;

import com.pangu.application.committee.command.CreateMockCommitteeSealCommand;
import com.pangu.application.repair.RepairWorkOrderApplicationException;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.committee.CommitteeElectronicSeal;
import com.pangu.domain.model.committee.CommitteeSealStatus;
import com.pangu.domain.model.committee.CommitteeSealType;
import com.pangu.domain.model.committee.CommitteeSealUsageRecord;
import com.pangu.domain.model.community.TenantCommunity;
import com.pangu.domain.repository.CommitteeSealRepository;
import com.pangu.domain.repository.CommunitySettingsRepository;
import com.pangu.domain.repository.ElectronicSealProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.FORBIDDEN;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.INVALID_STATUS;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.NOT_FOUND;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.PARAM_INVALID;

@Service
@RequiredArgsConstructor
public class CommitteeSealService {

    private static final String READ_PERMISSION = "committee:seal:read";
    private static final String MANAGE_PERMISSION = "committee:seal:manage";
    private static final String USE_PERMISSION = "committee:seal:use";

    private final CommitteeSealRepository repository;
    private final CommunitySettingsRepository communitySettingsRepository;
    private final ElectronicSealProvider provider;
    private final UserContextHolder userContextHolder;

    @Transactional(readOnly = true)
    public List<CommitteeElectronicSeal> list() {
        UserContext context = requirePermission(READ_PERMISSION);
        return repository.listByTenant(context.tenantId());
    }

    @Transactional(readOnly = true)
    public List<CommitteeSealUsageRecord> listUsage(int limit) {
        UserContext context = requirePermission(READ_PERMISSION);
        return repository.listUsageByTenant(context.tenantId(), Math.max(1, Math.min(limit, 100)));
    }

    @Transactional
    public CommitteeElectronicSeal createMock(CreateMockCommitteeSealCommand command) {
        UserContext context = requirePermission(MANAGE_PERMISSION);
        if (!provider.simulated()) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "当前环境不是模拟电子印章提供方");
        }
        CommitteeSealType sealType = parseType(command.sealType());
        repository.listByTenant(context.tenantId()).stream()
                .filter(item -> item.sealType() == sealType && item.status() == CommitteeSealStatus.ACTIVE)
                .findFirst()
                .ifPresent(item -> {
                    throw new RepairWorkOrderApplicationException(PARAM_INVALID, "同类型有效印章已存在，请先停用原印章");
                });
        String termName = currentTermName(context.tenantId());
        String sealName = normalizeName(command.sealName(), defaultSealName(termName, sealType));
        ElectronicSealProvider.ProvisionedSeal provisioned = provider.provision(
                new ElectronicSealProvider.ProvisionRequest(context.tenantId(), sealName, sealType, termName));
        Long sealId = repository.insert(new CommitteeElectronicSeal(
                null, context.tenantId(), sealName, sealType, provider.providerCode(),
                provisioned.providerSealId(), provisioned.certificateSerial(),
                provisioned.validFrom(), provisioned.validUntil(), CommitteeSealStatus.ACTIVE,
                context.userId(), null, termName, true, context.userId(), null, null));
        return repository.findById(sealId, context.tenantId())
                .orElseThrow(() -> new RepairWorkOrderApplicationException(NOT_FOUND, "模拟电子印章创建后未找到"));
    }

    @Transactional
    public CommitteeElectronicSeal deactivate(Long sealId) {
        UserContext context = requirePermission(MANAGE_PERMISSION);
        CommitteeElectronicSeal seal = repository.findById(sealId, context.tenantId())
                .orElseThrow(() -> new RepairWorkOrderApplicationException(NOT_FOUND, "电子印章不存在"));
        if (seal.status() != CommitteeSealStatus.ACTIVE) {
            throw new RepairWorkOrderApplicationException(INVALID_STATUS, "仅有效电子印章可以停用");
        }
        if (repository.deactivate(sealId, context.tenantId(), context.userId()) != 1) {
            throw new RepairWorkOrderApplicationException(INVALID_STATUS, "电子印章状态已变化，请刷新后重试");
        }
        return repository.findById(sealId, context.tenantId())
                .orElseThrow(() -> new RepairWorkOrderApplicationException(NOT_FOUND, "电子印章不存在"));
    }

    @Transactional(readOnly = true)
    public CommitteeElectronicSeal requireUsableSeal(Long sealId, UserContext context) {
        if (context == null || !context.isSysUser() || !context.hasPermission(USE_PERMISSION)) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, "当前身份无权使用业主自治组织印章");
        }
        CommitteeElectronicSeal seal = repository.findById(sealId, context.tenantId())
                .orElseThrow(() -> new RepairWorkOrderApplicationException(NOT_FOUND, "电子印章不存在"));
        LocalDateTime now = LocalDateTime.now();
        if (seal.status() != CommitteeSealStatus.ACTIVE
                || now.isBefore(seal.validFrom()) || !now.isBefore(seal.validUntil())) {
            throw new RepairWorkOrderApplicationException(INVALID_STATUS, "电子印章已停用或证书不在有效期内");
        }
        if (!seal.custodianUserId().equals(context.userId())) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, "当前账号不是该印章登记保管人");
        }
        if (!seal.committeeTermName().equals(currentTermName(context.tenantId()))) {
            throw new RepairWorkOrderApplicationException(INVALID_STATUS, "电子印章不属于当前业委会届期");
        }
        if (!seal.providerCode().equals(provider.providerCode()) || seal.simulated() != provider.simulated()) {
            throw new RepairWorkOrderApplicationException(INVALID_STATUS, "电子印章与当前签章服务商不匹配");
        }
        return seal;
    }

    public ElectronicSealProvider provider() {
        return provider;
    }

    private UserContext requirePermission(String permission) {
        UserContext context = userContextHolder.current();
        if (context == null || !context.isSysUser() || context.userId() == null || context.tenantId() == null
                || !context.hasPermission(permission)) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, "当前身份无权处理电子印章业务");
        }
        return context;
    }

    private String currentTermName(Long tenantId) {
        TenantCommunity community = communitySettingsRepository.findCommunity(tenantId)
                .orElseThrow(() -> new RepairWorkOrderApplicationException(NOT_FOUND, "社区设置不存在"));
        String value = community.currentCommitteeTermName();
        if (value == null || value.isBlank()) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "请先在社区设置中维护当前业委会届期");
        }
        return value.trim();
    }

    private CommitteeSealType parseType(String value) {
        try {
            return CommitteeSealType.valueOf(value == null ? "" : value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "不支持的电子印章类型");
        }
    }

    private String normalizeName(String value, String fallback) {
        String normalized = value == null || value.isBlank() ? fallback : value.trim();
        if (normalized.length() > 120) {
            throw new RepairWorkOrderApplicationException(PARAM_INVALID, "印章名称不能超过 120 个字符");
        }
        return normalized;
    }

    private String defaultSealName(String termName, CommitteeSealType sealType) {
        String suffix = switch (sealType) {
            case OWNERS_ASSEMBLY -> "业主大会章";
            case OWNERS_COMMITTEE -> "业主委员会章";
            case FINANCIAL -> "业主大会财务专用章";
        };
        return termName + suffix + "（模拟）";
    }
}
