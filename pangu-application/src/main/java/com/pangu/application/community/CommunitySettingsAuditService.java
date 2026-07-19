// 关联业务：生成、持久化并解析社区设置变更记录，保证业务文案与审计载荷可追溯。
package com.pangu.application.community;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.model.community.CommunityLedgerStats;
import com.pangu.domain.model.community.CommunitySettingsAudit;
import com.pangu.domain.model.community.CommunitySettingsOperation;
import com.pangu.domain.model.community.DenominatorReviewRequest;
import com.pangu.domain.model.community.TenantCommunity;
import com.pangu.domain.repository.CommunitySettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * 社区设置变更记录服务。
 *
 * <p>写侧把变更前后值和操作身份快照放入既有 JSONB 审计载荷；读侧兼容历史原始命令载荷，
 * 不因旧数据缺少结构化字段而阻断社区设置页面。</p>
 */
@Service
@RequiredArgsConstructor
public class CommunitySettingsAuditService {

    private static final int PAYLOAD_SCHEMA_VERSION = 1;

    private static final Map<String, String> FIELD_LABELS = fieldLabels();

    private final CommunitySettingsRepository repository;
    private final ObjectMapper objectMapper;

    public void recordOrganizationChange(TenantCommunity before, TenantCommunity after, UserContext actor) {
        writeChange(
                CommunitySettingsOperation.UPDATE_ORGANIZATION,
                before.tenantId(),
                "已更新社区行政归属与组织备案",
                null,
                diff(before, after, List.of(
                        field("tenantName", "小区名称", TenantCommunity::tenantName),
                        field("provinceCode", "省份代码", TenantCommunity::provinceCode),
                        field("provinceName", "省份名称", TenantCommunity::provinceName),
                        field("cityCode", "城市代码", TenantCommunity::cityCode),
                        field("cityName", "城市名称", TenantCommunity::cityName),
                        field("districtCode", "区县代码", TenantCommunity::districtCode),
                        field("districtName", "区县名称", TenantCommunity::districtName),
                        field("streetCode", "街道代码", TenantCommunity::streetCode),
                        field("streetName", "街道名称", TenantCommunity::streetName),
                        field("communityCode", "社区代码", TenantCommunity::communityCode),
                        field("communityName", "社区名称", TenantCommunity::communityName),
                        field("address", "物业地址", TenantCommunity::address),
                        field("ownersAssemblyEstablished", "业主大会备案", value -> yesNo(value.ownersAssemblyEstablished())),
                        field("committeeEstablished", "业主委员会备案", value -> yesNo(value.committeeEstablished())),
                        field("currentCommitteeTermName", "当前业委会届次", TenantCommunity::currentCommitteeTermName),
                        field("transitionOrgType", "过渡期管理组织", TenantCommunity::transitionOrgType),
                        field("transitionOrgStatus", "过渡期组织状态", TenantCommunity::transitionOrgStatus)
                )),
                actor,
                false);
    }

    public void recordAssetLedgerChange(TenantCommunity before, TenantCommunity after, UserContext actor) {
        writeChange(
                CommunitySettingsOperation.UPDATE_ASSET_LEDGER,
                before.tenantId(),
                "已更新物业区域与建筑名册基数",
                null,
                diff(before, after, List.of(
                        field("propertyAreaName", "物业区域名称", TenantCommunity::propertyAreaName),
                        field("propertyAreaCode", "物业区域编码", TenantCommunity::propertyAreaCode),
                        field("developerName", "建设单位", TenantCommunity::developerName),
                        field("developerAccountId", "建设单位账号", value -> text(value.developerAccountId())),
                        field("plannedHouseholdCount", "规划户数", value -> text(value.plannedHouseholdCount())),
                        field("deliveredHouseholdCount", "已交付户数", value -> text(value.deliveredHouseholdCount())),
                        field("registeredPropertyUnitCount", "登记单元数", value -> text(value.registeredPropertyUnitCount())),
                        field("totalPlannedBuildingArea", "规划建筑面积", value -> decimal(value.totalPlannedBuildingArea())),
                        field("totalExclusiveArea", "法定专有面积", value -> decimal(value.totalExclusiveArea())),
                        field("registeredVotingTotalArea", "登记投票面积", value -> decimal(value.registeredVotingTotalArea())),
                        field("excludedParkingArea", "应扣除车位面积", value -> decimal(value.excludedParkingArea())),
                        field("publicArea", "公共服务空间面积", value -> decimal(value.publicArea())),
                        field("buildingCount", "楼栋数量", value -> text(value.buildingCount())),
                        field("unitCount", "单元数量", value -> text(value.unitCount())),
                        field("parkingSpaceCount", "车位数量", value -> text(value.parkingSpaceCount())),
                        field("plotRatio", "容积率", value -> decimal(value.plotRatio()))
                )),
                actor,
                false);
    }

    public void recordRulesChange(TenantCommunity before, TenantCommunity after, UserContext actor) {
        writeChange(
                CommunitySettingsOperation.UPDATE_RULES,
                before.tenantId(),
                "已更新维修项目筹备要求",
                null,
                diff(before, after, List.of(
                        field("repairEstimateRequired", "前期询价前编制参考估算", value -> enabled(value.repairEstimateRequired())),
                        field("buildingRepairDefaultDecisionChannel", "系统内表决收集方式", value -> decisionChannel(value.buildingRepairDefaultDecisionChannel()))
                )),
                actor,
                false);
    }

    public void recordDenominatorRecalculation(TenantCommunity before,
                                               CommunityLedgerStats after,
                                               long statisticsVersion,
                                               UserContext actor) {
        List<StoredChange> changes = List.of(
                change("totalExclusiveArea", "法定专有面积", decimal(before.totalExclusiveArea()), decimal(after.totalArea())),
                change("registeredVotingOwnerCount", "登记投票业主", text(before.registeredVotingOwnerCount()), text(after.ownerCount())),
                change("registeredPropertyUnitCount", "登记单元数", text(before.registeredPropertyUnitCount()), text(after.unitCount())),
                change("statisticsVersion", "统计版本", text(before.statisticsVersion()), text(statisticsVersion))
        );
        writeChange(
                CommunitySettingsOperation.RECALCULATE_DENOMINATOR,
                before.tenantId(),
                "计票基数已重新对账并发布为第 " + statisticsVersion + " 版",
                null,
                changes,
                actor,
                true);
    }

    public void recordReviewSubmission(TenantCommunity current,
                                       long requestId,
                                       BigDecimal requestedArea,
                                       long requestedOwnerCount,
                                       long requestedUnitCount,
                                       String reason,
                                       UserContext actor) {
        writeChange(
                CommunitySettingsOperation.SUBMIT_DENOMINATOR_REVIEW,
                current.tenantId(),
                "已提交计票基数复核申请 #" + requestId,
                reason,
                List.of(
                        change("totalExclusiveArea", "申请法定专有面积", decimal(current.totalExclusiveArea()), decimal(requestedArea)),
                        change("registeredVotingOwnerCount", "申请投票业主数", text(current.registeredVotingOwnerCount()), text(requestedOwnerCount)),
                        change("registeredPropertyUnitCount", "申请登记单元数", text(current.registeredPropertyUnitCount()), text(requestedUnitCount))
                ),
                actor,
                true);
    }

    public void recordReviewDecision(TenantCommunity current,
                                     DenominatorReviewRequest request,
                                     boolean approved,
                                     String reviewComment,
                                     Long statisticsVersion,
                                     UserContext actor) {
        List<StoredChange> changes = new ArrayList<>();
        changes.add(change("reviewStatus", "复核结论", "待复核", approved ? "已通过" : "已驳回"));
        if (approved) {
            changes.add(change("totalExclusiveArea", "法定专有面积",
                    decimal(current.totalExclusiveArea()), decimal(request.requestedTotalArea())));
            changes.add(change("registeredVotingOwnerCount", "登记投票业主",
                    text(current.registeredVotingOwnerCount()), text(request.requestedOwnerCount())));
            changes.add(change("registeredPropertyUnitCount", "登记单元数",
                    text(current.registeredPropertyUnitCount()), text(request.requestedUnitCount())));
            changes.add(change("statisticsVersion", "统计版本",
                    text(current.statisticsVersion()), text(statisticsVersion)));
        }
        writeChange(
                CommunitySettingsOperation.REVIEW_DENOMINATOR_REQUEST,
                current.tenantId(),
                (approved ? "已通过" : "已驳回") + "计票基数复核申请 #" + request.requestId(),
                reviewComment,
                changes,
                actor,
                true);
    }

    public List<CommunitySettingsView.AuditLog> toView(List<CommunitySettingsAudit> records) {
        return records.stream().map(this::toView).toList();
    }

    private CommunitySettingsView.AuditLog toView(CommunitySettingsAudit record) {
        CommunitySettingsOperation operation = CommunitySettingsOperation.fromCode(record.operationType()).orElse(null);
        ParsedPayload payload = parsePayload(record.payloadJson(), operation);
        StoredActor actor = payload.actor();
        return new CommunitySettingsView.AuditLog(
                record.auditId(),
                record.operationType(),
                operation == null ? record.operationType() : operation.label(),
                operation == null ? "OTHER" : operation.sectionCode(),
                payload.summary(),
                payload.changes().stream()
                        .map(change -> new CommunitySettingsView.AuditChange(
                                change.fieldCode(), change.fieldLabel(), change.beforeValue(), change.afterValue()))
                        .toList(),
                payload.reason(),
                actor != null && actor.accountId() != null ? actor.accountId() : record.operatorAccountId(),
                actor != null && actor.userId() != null ? actor.userId() : record.operatorUserId(),
                record.operatorName(),
                actor == null ? null : actor.roleKey(),
                record.operatorRoleName(),
                record.createTime());
    }

    private void writeChange(CommunitySettingsOperation operation,
                             Long tenantId,
                             String summary,
                             String reason,
                             List<StoredChange> changes,
                             UserContext actor,
                             boolean retainEmptyChanges) {
        List<StoredChange> actualChanges = changes.stream()
                .filter(change -> !Objects.equals(change.beforeValue(), change.afterValue()))
                .toList();
        if (actualChanges.isEmpty() && !retainEmptyChanges) {
            return;
        }
        StoredPayload payload = new StoredPayload(
                PAYLOAD_SCHEMA_VERSION,
                summary,
                blankToNull(reason),
                actualChanges,
                new StoredActor(actor.accountId(), actor.userId(), actor.roleKey()));
        try {
            repository.insertAudit(
                    tenantId,
                    operation.code(),
                    objectMapper.writeValueAsString(payload),
                    actor.userId());
        } catch (JsonProcessingException exception) {
            throw new CommunitySettingsApplicationException(
                    CommunitySettingsApplicationException.Reason.PARAM_INVALID,
                    "社区设置变更记录序列化失败",
                    exception);
        }
    }

    private ParsedPayload parsePayload(String payloadJson, CommunitySettingsOperation operation) {
        String fallbackSummary = operation == null ? "社区设置发生变更" : operation.label();
        if (payloadJson == null || payloadJson.isBlank()) {
            return new ParsedPayload(fallbackSummary, null, List.of(), null);
        }
        try {
            JsonNode root = objectMapper.readTree(payloadJson);
            if (root.path("schemaVersion").asInt(0) == PAYLOAD_SCHEMA_VERSION) {
                StoredPayload stored = objectMapper.treeToValue(root, StoredPayload.class);
                return new ParsedPayload(
                        blankToDefault(stored.summary(), fallbackSummary),
                        blankToNull(stored.reason()),
                        stored.changes() == null ? List.of() : stored.changes(),
                        stored.actor());
            }
            return parseLegacyPayload(root, fallbackSummary);
        } catch (JsonProcessingException ignored) {
            return new ParsedPayload(fallbackSummary, null, List.of(), null);
        }
    }

    private ParsedPayload parseLegacyPayload(JsonNode root, String fallbackSummary) {
        if (!root.isObject()) {
            return new ParsedPayload(fallbackSummary, null, List.of(), null);
        }
        List<StoredChange> changes = new ArrayList<>();
        String reason = null;
        var fields = root.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if (entry.getValue() == null || entry.getValue().isNull()) {
                continue;
            }
            if ("reason".equals(entry.getKey()) || "reviewComment".equals(entry.getKey())) {
                reason = entry.getValue().asText();
                continue;
            }
            String label = FIELD_LABELS.get(entry.getKey());
            if (label != null) {
                changes.add(change(entry.getKey(), label, null, jsonValue(entry.getValue())));
            }
        }
        return new ParsedPayload(fallbackSummary + "（历史记录）", blankToNull(reason), changes, null);
    }

    private <T> List<StoredChange> diff(T before, T after, List<AuditField<T>> fields) {
        return fields.stream()
                .map(field -> change(
                        field.fieldCode(),
                        field.fieldLabel(),
                        field.reader().apply(before),
                        field.reader().apply(after)))
                .filter(change -> !Objects.equals(change.beforeValue(), change.afterValue()))
                .toList();
    }

    private static <T> AuditField<T> field(String code, String label, Function<T, String> reader) {
        return new AuditField<>(code, label, reader);
    }

    private static StoredChange change(String code, String label, String before, String after) {
        return new StoredChange(code, label, blankToNull(before), blankToNull(after));
    }

    private static String jsonValue(JsonNode value) {
        if (value.isTextual()) {
            return value.asText();
        }
        if (value.isBoolean()) {
            return value.asBoolean() ? "是" : "否";
        }
        return value.toString();
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String decimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private static String yesNo(boolean value) {
        return value ? "已备案" : "未备案";
    }

    private static String enabled(boolean value) {
        return value ? "已启用" : "未启用";
    }

    private static String decisionChannel(String value) {
        return switch (value == null ? "" : value) {
            case "ONLINE" -> "线上实名投票";
            case "WECHAT" -> "历史微信材料记录";
            default -> value;
        };
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static Map<String, String> fieldLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("tenantName", "小区名称");
        labels.put("provinceCode", "省份代码");
        labels.put("provinceName", "省份名称");
        labels.put("cityCode", "城市代码");
        labels.put("cityName", "城市名称");
        labels.put("districtCode", "区县代码");
        labels.put("districtName", "区县名称");
        labels.put("streetCode", "街道代码");
        labels.put("streetName", "街道名称");
        labels.put("communityCode", "社区代码");
        labels.put("communityName", "社区名称");
        labels.put("address", "物业地址");
        labels.put("ownersAssemblyEstablished", "业主大会备案");
        labels.put("committeeEstablished", "业主委员会备案");
        labels.put("currentCommitteeTermName", "当前业委会届次");
        labels.put("transitionOrgType", "过渡期管理组织");
        labels.put("transitionOrgStatus", "过渡期组织状态");
        labels.put("propertyAreaName", "物业区域名称");
        labels.put("propertyAreaCode", "物业区域编码");
        labels.put("developerName", "建设单位");
        labels.put("developerAccountId", "建设单位账号");
        labels.put("plannedHouseholdCount", "规划户数");
        labels.put("deliveredHouseholdCount", "已交付户数");
        labels.put("registeredPropertyUnitCount", "登记单元数");
        labels.put("totalPlannedBuildingArea", "规划建筑面积");
        labels.put("totalExclusiveArea", "法定专有面积");
        labels.put("registeredVotingTotalArea", "登记投票面积");
        labels.put("excludedParkingArea", "应扣除车位面积");
        labels.put("publicArea", "公共服务空间面积");
        labels.put("buildingCount", "楼栋数量");
        labels.put("unitCount", "单元数量");
        labels.put("parkingSpaceCount", "车位数量");
        labels.put("plotRatio", "容积率");
        labels.put("repairEstimateRequired", "前期询价前编制参考估算");
        labels.put("buildingRepairDefaultDecisionChannel", "系统内表决收集方式");
        return Map.copyOf(labels);
    }

    private record AuditField<T>(String fieldCode, String fieldLabel, Function<T, String> reader) {
    }

    private record StoredChange(String fieldCode, String fieldLabel, String beforeValue, String afterValue) {
    }

    private record StoredActor(Long accountId, Long userId, String roleKey) {
    }

    private record StoredPayload(
            int schemaVersion,
            String summary,
            String reason,
            List<StoredChange> changes,
            StoredActor actor
    ) {
    }

    private record ParsedPayload(
            String summary,
            String reason,
            List<StoredChange> changes,
            StoredActor actor
    ) {
    }
}
