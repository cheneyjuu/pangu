// 关联业务：编排房屋产权基础名册导入、结构展示和业主房产绑定审核流程。
package com.pangu.application.owner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.domain.common.Page;
import com.pangu.domain.repository.AuthAccountRepository;
import com.pangu.domain.repository.PropertyBindingRepository;
import com.pangu.domain.security.NameDecryptor;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PropertyBindingApplicationService {

    private static final String MATCH_EXACT = "EXACT";
    private static final String MATCH_MISMATCH = "MISMATCH";
    private static final String STATUS_AUTO_APPROVED = "AUTO_APPROVED";
    private static final String STATUS_PENDING_VERIFY = "PENDING_VERIFY";
    private static final String STATUS_APPROVED = "APPROVED";

    private final PropertyBindingRepository repository;
    private final AuthAccountRepository authAccountRepository;
    private final NameDecryptor nameDecryptor;
    private final ObjectMapper objectMapper;

    public RosterOptionsResponse listRosterOptions(Long tenantId) {
        List<PropertyBindingRepository.RosterOption> rows = repository.findRosterOptions(tenantId);
        Map<Long, CommunityOption> communities = new LinkedHashMap<>();
        for (PropertyBindingRepository.RosterOption row : rows) {
            CommunityOption community = communities.computeIfAbsent(row.tenantId(), id ->
                    new CommunityOption(id, row.communityName(), new ArrayList<>()));
            BuildingOption building = community.buildings().stream()
                    .filter(item -> Objects.equals(item.buildingId(), row.buildingId()))
                    .findFirst()
                    .orElseGet(() -> {
                        BuildingOption created = new BuildingOption(
                                row.buildingId(), row.buildingName(), new ArrayList<>());
                        community.buildings().add(created);
                        return created;
                    });
            UnitOption unit = building.units().stream()
                    .filter(item -> Objects.equals(item.unitName(), row.unitName()))
                    .findFirst()
                    .orElseGet(() -> {
                        UnitOption created = new UnitOption(row.unitName(), new ArrayList<>());
                        building.units().add(created);
                        return created;
                    });
            unit.rooms().add(new RoomOption(row.rosterId(), row.roomId(), row.roomName(), row.buildArea()));
        }
        return new RosterOptionsResponse(new ArrayList<>(communities.values()));
    }

    /**
     * 返回当前工作小区已录入的房屋产权基础名册结构。
     *
     * <p>这里的户数和面积只反映当前名册录入结果，法定计票基数仍须走独立的核验、对账和发布流程。
     */
    public RosterTopologyResponse getRosterTopology(Long tenantId) {
        if (tenantId == null) {
            throw new PropertyBindingApplicationException(
                    PropertyBindingApplicationException.Reason.FORBIDDEN,
                    "当前工作身份未绑定小区，无法查看房屋基础名册");
        }
        List<PropertyBindingRepository.RosterTopology> rows = repository.findRosterTopology(tenantId);
        Map<Long, List<PropertyBindingRepository.RosterTopology>> unitsByBuilding = new LinkedHashMap<>();
        for (PropertyBindingRepository.RosterTopology row : rows) {
            unitsByBuilding.computeIfAbsent(row.buildingId(), ignored -> new ArrayList<>()).add(row);
        }

        List<BuildingTopologyResponse> buildings = unitsByBuilding.values().stream()
                .map(this::toBuildingTopology)
                .toList();
        long householdCount = buildings.stream()
                .mapToLong(BuildingTopologyResponse::householdCount)
                .sum();
        BigDecimal totalArea = buildings.stream()
                .map(BuildingTopologyResponse::totalArea)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new RosterTopologyResponse(
                tenantId,
                resolveCommunityName(tenantId),
                householdCount,
                totalArea,
                buildings);
    }

    /**
     * 以导入时登记的姓名和手机号归并房屋，用于管理端核对导入结果。
     *
     * <p>该视图不将导入记录表述为已认证业主，也不补造认证、账户、委员或开发商状态。
     */
    public List<RegisteredOwnerResponse> listRegisteredOwners(Long tenantId) {
        if (tenantId == null) {
            throw new PropertyBindingApplicationException(
                    PropertyBindingApplicationException.Reason.FORBIDDEN,
                    "当前工作身份未绑定小区，无法查看产权登记名册");
        }
        Map<RegisteredOwnerKey, List<PropertyBindingRepository.Roster>> rostersByOwner = new LinkedHashMap<>();
        for (PropertyBindingRepository.Roster roster : repository.findActiveRosters(tenantId)) {
            RegisteredOwnerKey owner = new RegisteredOwnerKey(
                    roster.registeredOwnerName(), roster.registeredOwnerPhone());
            rostersByOwner.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(roster);
        }
        return rostersByOwner.entrySet().stream()
                .map(entry -> toRegisteredOwner(entry.getKey(), entry.getValue()))
                .toList();
    }

    @Transactional
    public ImportResult importRoster(Long currentTenantId, Long operatorUserId, PropertyRosterImportCommand command) {
        Long tenantId = resolveTenantId(currentTenantId, command.tenantId());
        if (tenantId == null) {
            throw new PropertyBindingApplicationException(
                    PropertyBindingApplicationException.Reason.PARAM_INVALID,
                    "街道跨租户账号导入名册时必须指定 tenantId");
        }
        String batchNo = "ROSTER-" + tenantId + "-" + System.currentTimeMillis();
        int imported = 0;
        for (PropertyRosterImportCommand.Row input : command.rows()) {
            if (input.tenantId() != null && !Objects.equals(input.tenantId(), tenantId)) {
                throw new PropertyBindingApplicationException(
                        PropertyBindingApplicationException.Reason.FORBIDDEN,
                        "导入行 tenantId 与当前操作小区不一致");
            }
            String buildingName = required(input.buildingName(), "buildingName");
            String unitName = required(input.unitName(), "unitName");
            String roomName = required(input.roomName(), "roomName");
            Long buildingId = repository.findBuildingIdByName(tenantId, buildingName);
            if (buildingId == null) {
                buildingId = repository.nextBuildingId(tenantId);
            }
            Long roomId = repository.findRoomIdByPath(tenantId, buildingName, unitName, roomName);
            if (roomId == null) {
                roomId = repository.nextRoomId(buildingId);
            }

            PropertyBindingRepository.RosterImportDraft row = new PropertyBindingRepository.RosterImportDraft(
                    tenantId,
                    resolveCommunityName(tenantId),
                    buildingId,
                    buildingName,
                    unitName,
                    roomId,
                    roomName,
                    input.buildArea() == null ? BigDecimal.ZERO : input.buildArea(),
                    required(input.registeredOwnerName(), "registeredOwnerName"),
                    required(input.registeredOwnerPhone(), "registeredOwnerPhone"),
                    batchNo,
                    operatorUserId);
            repository.upsertRoster(row);
            imported++;
        }
        return new ImportResult(batchNo, imported);
    }

    @Transactional
    public BindingSubmitResponse submitClaim(Long accountId, Long uid, PropertyBindingClaimCommand command) {
        if (accountId == null || uid == null) {
            throw new PropertyBindingApplicationException(
                    PropertyBindingApplicationException.Reason.FORBIDDEN,
                    "未识别到业主身份，禁止绑定房产");
        }
        AuthAccountRepository.AccountIdentitySnapshot account = authAccountRepository.findIdentityByAccountId(accountId);
        if (account == null || account.status() == null || account.status() != 1) {
            throw new PropertyBindingApplicationException(
                    PropertyBindingApplicationException.Reason.UNAUTHORIZED,
                    "账号已被禁用或注销，请联系管理员");
        }
        String realName = normalizeRealName(nameDecryptor.safeDecrypt(account.realNameCipher()));
        if (realName == null || account.realNameVerified() == null || account.realNameVerified() != 1) {
            throw new PropertyBindingApplicationException(
                    PropertyBindingApplicationException.Reason.FORBIDDEN,
                    "请先完成实名认证后再绑定房产");
        }
        PropertyBindingRepository.Roster roster = repository.findRosterById(command.rosterId());
        if (roster == null || !"ACTIVE".equals(roster.status())) {
            throw new PropertyBindingApplicationException(
                    PropertyBindingApplicationException.Reason.NOT_FOUND,
                    "房产名册记录不存在或已停用");
        }
        boolean phoneMatched = Objects.equals(account.phone(), roster.registeredOwnerPhone());
        boolean nameMatched = Objects.equals(realName, normalizeRealName(roster.registeredOwnerName()));
        String matchResult = phoneMatched && nameMatched ? MATCH_EXACT : MATCH_MISMATCH;
        boolean joint = Boolean.TRUE.equals(command.jointOwnership());
        boolean delegate = command.votingDelegate() == null || Boolean.TRUE.equals(command.votingDelegate());
        String proofJson = proofJson(command.proofType(), command.proofImagesBase64());

        if (MATCH_EXACT.equals(matchResult)) {
            Long opid = bindOwnership(uid, roster, joint, delegate, "ROSTER_AUTO", "PROPERTY_BASE_ROSTER", null);
            authAccountRepository.updateCUserLastActiveTenant(uid, roster.tenantId());
            Long claimId = repository.insertClaim(toClaim(
                    accountId, uid, roster, realName, account.phone(), matchResult,
                    STATUS_AUTO_APPROVED, joint, delegate, command.proofType(), proofJson, opid));
            return new BindingSubmitResponse("BOUND", claimId, opid,
                    "名册信息已自动吻合，房产绑定成功");
        }

        if (isBlank(command.proofType()) || command.proofImagesBase64() == null || command.proofImagesBase64().isEmpty()) {
            return new BindingSubmitResponse("NEED_EVIDENCE", null, null,
                    "系统未查到您的登记信息，请补充物权证明材料进入人工审核");
        }
        PropertyBindingRepository.ClaimDraft claim = toClaim(
                accountId, uid, roster, realName, account.phone(), matchResult,
                STATUS_PENDING_VERIFY, joint, delegate, command.proofType(), proofJson, null);
        Long claimId;
        try {
            claimId = repository.insertClaim(claim);
        } catch (DuplicateKeyException ex) {
            throw new PropertyBindingApplicationException(
                    PropertyBindingApplicationException.Reason.BAD_REQUEST,
                    "该房产已有待审核申报，请等待审核结果",
                    ex);
        }
        return new BindingSubmitResponse("PENDING_VERIFY", claimId, null,
                "房产绑定申请已提交，等待居委会/业委会人工核销");
    }

    public List<ClaimResponse> listMyClaims(Long uid) {
        if (uid == null) {
            throw new PropertyBindingApplicationException(
                    PropertyBindingApplicationException.Reason.FORBIDDEN,
                    "未识别到业主身份");
        }
        return repository.findClaimsForUid(uid).stream().map(this::toResponse).toList();
    }

    public Page<ClaimResponse> pageAdminClaims(Long tenantId, String status, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int offset = (safePage - 1) * safeSize;
        String normalizedStatus = isBlank(status) || "ALL".equalsIgnoreCase(status) ? null : status.trim();
        List<ClaimResponse> items = repository.findAdminClaims(tenantId, normalizedStatus, safeSize, offset)
                .stream()
                .map(this::toResponse)
                .toList();
        long total = repository.countAdminClaims(tenantId, normalizedStatus);
        return new Page<>(items, total, safePage, safeSize);
    }

    @Transactional
    public ClaimResponse approve(Long claimId, Long reviewerUserId) {
        PropertyBindingRepository.Claim claim = requirePendingClaim(claimId);
        PropertyBindingRepository.Roster roster = repository.findRosterById(claim.rosterId());
        if (roster == null) {
            throw new PropertyBindingApplicationException(
                    PropertyBindingApplicationException.Reason.NOT_FOUND,
                    "关联房产名册不存在");
        }
        Long opid = bindOwnership(
                claim.uid(),
                roster,
                claim.jointOwnership(),
                claim.votingDelegate(),
                "MANUAL",
                "PROPERTY_BINDING_REVIEW",
                reviewerUserId);
        authAccountRepository.updateCUserLastActiveTenant(claim.uid(), claim.tenantId());
        repository.markClaimApproved(claimId, STATUS_APPROVED, opid, reviewerUserId);
        return toResponse(repository.findClaimById(claimId));
    }

    @Transactional
    public ClaimResponse reject(Long claimId, Long reviewerUserId, String reasonCode, String reason) {
        requirePendingClaim(claimId);
        String normalizedReason = required(reason, "reason");
        int changed = repository.markClaimRejected(claimId, trimToNull(reasonCode), normalizedReason, reviewerUserId);
        if (changed != 1) {
            throw new PropertyBindingApplicationException(
                    PropertyBindingApplicationException.Reason.BAD_REQUEST,
                    "该申报单当前不可驳回");
        }
        return toResponse(repository.findClaimById(claimId));
    }

    private Long bindOwnership(Long uid,
                               PropertyBindingRepository.Roster roster,
                               boolean joint,
                               boolean delegate,
                               String verifyType,
                               String verifySource,
                               Long verifiedBy) {
        if (!joint) {
            repository.supersedeOtherOwnerships(uid, roster.tenantId(), roster.roomId(),
                    "冷启动房产绑定通过，旧产权关系被新核验结果覆盖");
        }
        if (delegate) {
            repository.clearOtherVotingDelegates(uid, roster.tenantId(), roster.roomId());
        }
        PropertyBindingRepository.OwnershipDraft row = new PropertyBindingRepository.OwnershipDraft(
                uid,
                roster.tenantId(),
                roster.buildingId(),
                roster.roomId(),
                roster.buildArea(),
                joint,
                delegate,
                verifyType,
                verifySource,
                verifiedBy);
        repository.upsertOwnership(row);
        Long opid = repository.findOwnershipOpid(uid, roster.tenantId(), roster.roomId());
        if (opid == null) {
            throw new PropertyBindingApplicationException(
                    PropertyBindingApplicationException.Reason.SYSTEM_ERROR,
                    "房产绑定结果未落库");
        }
        return opid;
    }

    private PropertyBindingRepository.Claim requirePendingClaim(Long claimId) {
        PropertyBindingRepository.Claim claim = repository.findClaimById(claimId);
        if (claim == null) {
            throw new PropertyBindingApplicationException(
                    PropertyBindingApplicationException.Reason.NOT_FOUND,
                    "房产绑定申报单不存在");
        }
        if (!STATUS_PENDING_VERIFY.equals(claim.claimStatus())) {
            throw new PropertyBindingApplicationException(
                    PropertyBindingApplicationException.Reason.BAD_REQUEST,
                    "该申报单当前不在待审核状态");
        }
        return claim;
    }

    private PropertyBindingRepository.ClaimDraft toClaim(Long accountId,
                                                         Long uid,
                                                         PropertyBindingRepository.Roster roster,
                                                         String realName,
                                                         String phone,
                                                         String matchResult,
                                                         String status,
                                                         boolean joint,
                                                         boolean delegate,
                                                         String proofType,
                                                         String proofJson,
                                                         Long opid) {
        return new PropertyBindingRepository.ClaimDraft(
                accountId,
                uid,
                roster.tenantId(),
                roster.rosterId(),
                roster.buildingId(),
                roster.buildingName(),
                roster.unitName(),
                roster.roomId(),
                roster.roomName(),
                realName,
                phone,
                roster.registeredOwnerName(),
                roster.registeredOwnerPhone(),
                matchResult,
                status,
                joint,
                delegate,
                trimToNull(proofType),
                proofJson,
                opid);
    }

    private BuildingTopologyResponse toBuildingTopology(List<PropertyBindingRepository.RosterTopology> unitRows) {
        PropertyBindingRepository.RosterTopology first = unitRows.get(0);
        List<UnitTopologyResponse> units = unitRows.stream()
                .map(row -> new UnitTopologyResponse(
                        row.unitName(),
                        numberOrZero(row.householdCount()),
                        amountOrZero(row.totalArea())))
                .toList();
        long householdCount = units.stream().mapToLong(UnitTopologyResponse::householdCount).sum();
        BigDecimal totalArea = units.stream()
                .map(UnitTopologyResponse::totalArea)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new BuildingTopologyResponse(
                first.buildingId(),
                first.buildingName(),
                householdCount,
                totalArea,
                units);
    }

    private RegisteredOwnerResponse toRegisteredOwner(
            RegisteredOwnerKey owner,
            List<PropertyBindingRepository.Roster> rosters) {
        List<RegisteredPropertyResponse> properties = rosters.stream()
                .map(roster -> new RegisteredPropertyResponse(
                        roster.buildingName(),
                        roster.unitName(),
                        roster.roomName(),
                        amountOrZero(roster.buildArea())))
                .toList();
        BigDecimal totalBuildArea = properties.stream()
                .map(RegisteredPropertyResponse::buildArea)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new RegisteredOwnerResponse(
                owner.name(),
                owner.phone(),
                properties.size(),
                totalBuildArea,
                properties);
    }

    private ClaimResponse toResponse(PropertyBindingRepository.Claim row) {
        return new ClaimResponse(
                row.claimId(),
                row.tenantId(),
                row.buildingName(),
                row.unitName(),
                row.roomName(),
                row.applicantRealName(),
                row.applicantPhone(),
                row.rosterOwnerName(),
                row.rosterOwnerPhone(),
                row.matchResult(),
                row.claimStatus(),
                row.jointOwnership(),
                row.votingDelegate(),
                row.proofType(),
                row.proofMaterialJson(),
                row.rejectReasonCode(),
                row.rejectReason(),
                row.boundOpid(),
                row.createTime(),
                row.reviewedAt());
    }

    private String proofJson(String proofType, List<String> proofImagesBase64) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("proofType", trimToNull(proofType));
        List<Map<String, String>> images = new ArrayList<>();
        if (proofImagesBase64 != null) {
            for (String image : proofImagesBase64) {
                String value = trimToNull(image);
                if (value != null) {
                    images.add(Map.of(
                            "sha256", sha256(value),
                            "base64", value
                    ));
                }
            }
        }
        payload.put("images", images);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new PropertyBindingApplicationException(
                    PropertyBindingApplicationException.Reason.PARAM_INVALID,
                    "物权证明材料格式错误",
                    e);
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private Long resolveTenantId(Long currentTenantId, Long requestedTenantId) {
        return currentTenantId != null ? currentTenantId : requestedTenantId;
    }

    private String resolveCommunityName(Long tenantId) {
        String name = trimToNull(repository.findTenantName(tenantId));
        return name == null ? "小区 " + tenantId : name;
    }

    private String normalizeRealName(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) return null;
        return trimmed.startsWith("MOCK_") ? trimmed.substring("MOCK_".length()) : trimmed;
    }

    private String required(String value, String field) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new PropertyBindingApplicationException(
                    PropertyBindingApplicationException.Reason.PARAM_INVALID,
                    field + " 不能为空");
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private long numberOrZero(Long value) {
        return value == null ? 0L : value;
    }

    private BigDecimal amountOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public record PropertyRosterImportCommand(Long tenantId, List<Row> rows) {
        public record Row(
                Long tenantId,
                String buildingName,
                String unitName,
                String roomName,
                BigDecimal buildArea,
                String registeredOwnerName,
                String registeredOwnerPhone
        ) {
        }
    }

    public record PropertyBindingClaimCommand(
            Long rosterId,
            Boolean jointOwnership,
            Boolean votingDelegate,
            String proofType,
            List<String> proofImagesBase64
    ) {
    }

    public record RosterOptionsResponse(List<CommunityOption> communities) {
    }

    public record RosterTopologyResponse(
            Long tenantId,
            String communityName,
            long householdCount,
            BigDecimal totalArea,
            List<BuildingTopologyResponse> buildings) {
    }

    public record BuildingTopologyResponse(
            Long buildingId,
            String buildingName,
            long householdCount,
            BigDecimal totalArea,
            List<UnitTopologyResponse> units) {
    }

    public record UnitTopologyResponse(
            String unitName,
            long householdCount,
            BigDecimal totalArea) {
    }

    public record CommunityOption(Long tenantId, String communityName, List<BuildingOption> buildings) {
    }

    public record BuildingOption(Long buildingId, String buildingName, List<UnitOption> units) {
    }

    public record UnitOption(String unitName, List<RoomOption> rooms) {
    }

    public record RoomOption(Long rosterId, Long roomId, String roomName, BigDecimal buildArea) {
    }

    public record ImportResult(String importBatchNo, int importedCount) {
    }

    public record BindingSubmitResponse(String status, Long claimId, Long opid, String message) {
    }

    public record ClaimResponse(
            Long claimId,
            Long tenantId,
            String buildingName,
            String unitName,
            String roomName,
            String applicantRealName,
            String applicantPhone,
            String rosterOwnerName,
            String rosterOwnerPhone,
            String matchResult,
            String claimStatus,
            boolean jointOwnership,
            boolean votingDelegate,
            String proofType,
            String proofMaterialJson,
            String rejectReasonCode,
            String rejectReason,
            Long boundOpid,
            LocalDateTime createTime,
            LocalDateTime reviewedAt
    ) {
    }

    public record RegisteredOwnerResponse(
            String registeredOwnerName,
            String registeredOwnerPhone,
            long propertyCount,
            BigDecimal totalBuildArea,
            List<RegisteredPropertyResponse> properties
    ) {
    }

    public record RegisteredPropertyResponse(
            String buildingName,
            String unitName,
            String roomName,
            BigDecimal buildArea
    ) {
    }

    private record RegisteredOwnerKey(String name, String phone) {
    }
}
