package com.pangu.interfaces.web.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.domain.common.Page;
import com.pangu.domain.security.NameDecryptor;
import com.pangu.infrastructure.persistence.mapper.AccountMapper;
import com.pangu.infrastructure.persistence.mapper.PropertyBindingMapper;
import com.pangu.interfaces.web.controller.dto.owner.PropertyBindingClaimRequest;
import com.pangu.interfaces.web.controller.dto.owner.PropertyRosterImportRequest;
import com.pangu.interfaces.web.exception.AppException;
import com.pangu.interfaces.web.exception.CommonErrorCode;
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
public class PropertyBindingService {

    private static final String MATCH_EXACT = "EXACT";
    private static final String MATCH_MISMATCH = "MISMATCH";
    private static final String STATUS_AUTO_APPROVED = "AUTO_APPROVED";
    private static final String STATUS_PENDING_VERIFY = "PENDING_VERIFY";
    private static final String STATUS_APPROVED = "APPROVED";

    private final PropertyBindingMapper mapper;
    private final AccountMapper accountMapper;
    private final NameDecryptor nameDecryptor;
    private final ObjectMapper objectMapper;

    public RosterOptionsResponse listRosterOptions(Long tenantId) {
        List<PropertyBindingMapper.RosterOptionRow> rows = mapper.selectRosterOptions(tenantId);
        Map<Long, CommunityOption> communities = new LinkedHashMap<>();
        for (PropertyBindingMapper.RosterOptionRow row : rows) {
            CommunityOption community = communities.computeIfAbsent(row.getTenantId(), id ->
                    new CommunityOption(id, row.getCommunityName(), new ArrayList<>()));
            BuildingOption building = community.buildings().stream()
                    .filter(item -> Objects.equals(item.buildingId(), row.getBuildingId()))
                    .findFirst()
                    .orElseGet(() -> {
                        BuildingOption created = new BuildingOption(
                                row.getBuildingId(), row.getBuildingName(), new ArrayList<>());
                        community.buildings().add(created);
                        return created;
                    });
            UnitOption unit = building.units().stream()
                    .filter(item -> Objects.equals(item.unitName(), row.getUnitName()))
                    .findFirst()
                    .orElseGet(() -> {
                        UnitOption created = new UnitOption(row.getUnitName(), new ArrayList<>());
                        building.units().add(created);
                        return created;
                    });
            unit.rooms().add(new RoomOption(row.getRosterId(), row.getRoomId(), row.getRoomName(), row.getBuildArea()));
        }
        return new RosterOptionsResponse(new ArrayList<>(communities.values()));
    }

    @Transactional
    public ImportResult importRoster(Long currentTenantId, Long operatorUserId, PropertyRosterImportRequest request) {
        Long tenantId = resolveTenantId(currentTenantId, request.tenantId());
        if (tenantId == null) {
            throw new AppException(CommonErrorCode.PARAM_ERROR, "街道跨租户账号导入名册时必须指定 tenantId");
        }
        String batchNo = "ROSTER-" + tenantId + "-" + System.currentTimeMillis();
        int imported = 0;
        for (PropertyRosterImportRequest.Row input : request.rows()) {
            if (input.tenantId() != null && !Objects.equals(input.tenantId(), tenantId)) {
                throw new AppException(CommonErrorCode.FORBIDDEN, "导入行 tenantId 与当前操作小区不一致");
            }
            String buildingName = required(input.buildingName(), "buildingName");
            String unitName = required(input.unitName(), "unitName");
            String roomName = required(input.roomName(), "roomName");
            Long buildingId = mapper.selectBuildingIdByName(tenantId, buildingName);
            if (buildingId == null) {
                buildingId = mapper.selectNextBuildingId(tenantId);
            }
            Long roomId = mapper.selectRoomIdByPath(tenantId, buildingName, unitName, roomName);
            if (roomId == null) {
                roomId = mapper.selectNextRoomId(buildingId);
            }

            PropertyBindingMapper.RosterImportRow row = new PropertyBindingMapper.RosterImportRow();
            row.setTenantId(tenantId);
            row.setCommunityName(resolveCommunityName(tenantId));
            row.setBuildingId(buildingId);
            row.setBuildingName(buildingName);
            row.setUnitName(unitName);
            row.setRoomId(roomId);
            row.setRoomName(roomName);
            row.setBuildArea(input.buildArea() == null ? BigDecimal.ZERO : input.buildArea());
            row.setRegisteredOwnerName(required(input.registeredOwnerName(), "registeredOwnerName"));
            row.setRegisteredOwnerPhone(required(input.registeredOwnerPhone(), "registeredOwnerPhone"));
            row.setImportBatchNo(batchNo);
            row.setImportedBy(operatorUserId);
            mapper.upsertRoster(row);
            imported++;
        }
        return new ImportResult(batchNo, imported);
    }

    @Transactional
    public BindingSubmitResponse submitClaim(Long accountId, Long uid, PropertyBindingClaimRequest request) {
        if (accountId == null || uid == null) {
            throw new AppException(CommonErrorCode.FORBIDDEN, "未识别到业主身份，禁止绑定房产");
        }
        AccountMapper.AccountIdentityRow account = accountMapper.selectIdentityByAccountId(accountId);
        if (account == null || account.getStatus() == null || account.getStatus() != 1) {
            throw new AppException(CommonErrorCode.UNAUTHORIZED, "账号已被禁用或注销，请联系管理员");
        }
        String realName = normalizeRealName(nameDecryptor.safeDecrypt(account.getRealNameCipher()));
        if (realName == null || account.getRealNameVerified() == null || account.getRealNameVerified() != 1) {
            throw new AppException(CommonErrorCode.FORBIDDEN, "请先完成实名认证后再绑定房产");
        }
        PropertyBindingMapper.RosterRow roster = mapper.selectRosterById(request.rosterId());
        if (roster == null || !"ACTIVE".equals(roster.getStatus())) {
            throw new AppException(CommonErrorCode.NOT_FOUND, "房产名册记录不存在或已停用");
        }
        boolean phoneMatched = Objects.equals(account.getPhone(), roster.getRegisteredOwnerPhone());
        boolean nameMatched = Objects.equals(realName, normalizeRealName(roster.getRegisteredOwnerName()));
        String matchResult = phoneMatched && nameMatched ? MATCH_EXACT : MATCH_MISMATCH;
        boolean joint = Boolean.TRUE.equals(request.jointOwnership());
        boolean delegate = request.votingDelegate() == null || Boolean.TRUE.equals(request.votingDelegate());
        String proofJson = proofJson(request.proofType(), request.proofImagesBase64());

        if (MATCH_EXACT.equals(matchResult)) {
            Long opid = bindOwnership(uid, roster, joint, delegate, "ROSTER_AUTO", "PROPERTY_BASE_ROSTER", null);
            accountMapper.updateCUserLastActiveTenant(uid, roster.getTenantId());
            PropertyBindingMapper.ClaimInsertRow claim = toClaim(
                    accountId, uid, roster, realName, account.getPhone(), matchResult,
                    STATUS_AUTO_APPROVED, joint, delegate, request.proofType(), proofJson, opid);
            mapper.insertClaim(claim);
            return new BindingSubmitResponse("BOUND", claim.getClaimId(), opid,
                    "名册信息已自动吻合，房产绑定成功");
        }

        if (isBlank(request.proofType()) || request.proofImagesBase64() == null || request.proofImagesBase64().isEmpty()) {
            return new BindingSubmitResponse("NEED_EVIDENCE", null, null,
                    "系统未查到您的登记信息，请补充物权证明材料进入人工审核");
        }
        PropertyBindingMapper.ClaimInsertRow claim = toClaim(
                accountId, uid, roster, realName, account.getPhone(), matchResult,
                STATUS_PENDING_VERIFY, joint, delegate, request.proofType(), proofJson, null);
        try {
            mapper.insertClaim(claim);
        } catch (DuplicateKeyException ex) {
            throw new AppException(CommonErrorCode.BAD_REQUEST, "该房产已有待审核申报，请等待审核结果");
        }
        return new BindingSubmitResponse("PENDING_VERIFY", claim.getClaimId(), null,
                "房产绑定申请已提交，等待居委会/业委会人工核销");
    }

    public List<ClaimResponse> listMyClaims(Long uid) {
        if (uid == null) {
            throw new AppException(CommonErrorCode.FORBIDDEN, "未识别到业主身份");
        }
        return mapper.selectClaimsForUid(uid).stream().map(this::toResponse).toList();
    }

    public Page<ClaimResponse> pageAdminClaims(Long tenantId, String status, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int offset = (safePage - 1) * safeSize;
        String normalizedStatus = isBlank(status) || "ALL".equalsIgnoreCase(status) ? null : status.trim();
        List<ClaimResponse> items = mapper.selectAdminClaims(tenantId, normalizedStatus, safeSize, offset)
                .stream()
                .map(this::toResponse)
                .toList();
        long total = mapper.countAdminClaims(tenantId, normalizedStatus);
        return new Page<>(items, total, safePage, safeSize);
    }

    @Transactional
    public ClaimResponse approve(Long claimId, Long reviewerUserId) {
        PropertyBindingMapper.ClaimRow claim = requirePendingClaim(claimId);
        PropertyBindingMapper.RosterRow roster = mapper.selectRosterById(claim.getRosterId());
        if (roster == null) {
            throw new AppException(CommonErrorCode.NOT_FOUND, "关联房产名册不存在");
        }
        Long opid = bindOwnership(
                claim.getUid(),
                roster,
                claim.getJointOwnership() == 1,
                claim.getVotingDelegate() == 1,
                "MANUAL",
                "PROPERTY_BINDING_REVIEW",
                reviewerUserId);
        accountMapper.updateCUserLastActiveTenant(claim.getUid(), claim.getTenantId());
        mapper.updateClaimApproved(claimId, STATUS_APPROVED, opid, reviewerUserId);
        return toResponse(mapper.selectClaimById(claimId));
    }

    @Transactional
    public ClaimResponse reject(Long claimId, Long reviewerUserId, String reasonCode, String reason) {
        requirePendingClaim(claimId);
        String normalizedReason = required(reason, "reason");
        int changed = mapper.updateClaimRejected(claimId, trimToNull(reasonCode), normalizedReason, reviewerUserId);
        if (changed != 1) {
            throw new AppException(CommonErrorCode.BAD_REQUEST, "该申报单当前不可驳回");
        }
        return toResponse(mapper.selectClaimById(claimId));
    }

    private Long bindOwnership(Long uid,
                               PropertyBindingMapper.RosterRow roster,
                               boolean joint,
                               boolean delegate,
                               String verifyType,
                               String verifySource,
                               Long verifiedBy) {
        if (!joint) {
            mapper.supersedeOtherOwnerships(uid, roster.getTenantId(), roster.getRoomId(),
                    "冷启动房产绑定通过，旧产权关系被新核验结果覆盖");
        }
        if (delegate) {
            mapper.clearOtherVotingDelegates(uid, roster.getTenantId(), roster.getRoomId());
        }
        PropertyBindingMapper.OwnershipUpsertRow row = new PropertyBindingMapper.OwnershipUpsertRow();
        row.setUid(uid);
        row.setTenantId(roster.getTenantId());
        row.setBuildingId(roster.getBuildingId());
        row.setRoomId(roster.getRoomId());
        row.setBuildArea(roster.getBuildArea());
        row.setJointOwnership(joint ? 1 : 0);
        row.setVotingDelegate(delegate ? 1 : 0);
        row.setVerifyType(verifyType);
        row.setVerifySource(verifySource);
        row.setVerifiedBy(verifiedBy);
        mapper.upsertOwnership(row);
        Long opid = mapper.selectOwnershipOpid(uid, roster.getTenantId(), roster.getRoomId());
        if (opid == null) {
            throw new AppException(CommonErrorCode.SYSTEM_ERROR, "房产绑定结果未落库");
        }
        return opid;
    }

    private PropertyBindingMapper.ClaimRow requirePendingClaim(Long claimId) {
        PropertyBindingMapper.ClaimRow claim = mapper.selectClaimById(claimId);
        if (claim == null) {
            throw new AppException(CommonErrorCode.NOT_FOUND, "房产绑定申报单不存在");
        }
        if (!STATUS_PENDING_VERIFY.equals(claim.getClaimStatus())) {
            throw new AppException(CommonErrorCode.BAD_REQUEST, "该申报单当前不在待审核状态");
        }
        return claim;
    }

    private PropertyBindingMapper.ClaimInsertRow toClaim(Long accountId,
                                                         Long uid,
                                                         PropertyBindingMapper.RosterRow roster,
                                                         String realName,
                                                         String phone,
                                                         String matchResult,
                                                         String status,
                                                         boolean joint,
                                                         boolean delegate,
                                                         String proofType,
                                                         String proofJson,
                                                         Long opid) {
        PropertyBindingMapper.ClaimInsertRow claim = new PropertyBindingMapper.ClaimInsertRow();
        claim.setAccountId(accountId);
        claim.setUid(uid);
        claim.setTenantId(roster.getTenantId());
        claim.setRosterId(roster.getRosterId());
        claim.setBuildingId(roster.getBuildingId());
        claim.setBuildingName(roster.getBuildingName());
        claim.setUnitName(roster.getUnitName());
        claim.setRoomId(roster.getRoomId());
        claim.setRoomName(roster.getRoomName());
        claim.setApplicantRealName(realName);
        claim.setApplicantPhone(phone);
        claim.setRosterOwnerName(roster.getRegisteredOwnerName());
        claim.setRosterOwnerPhone(roster.getRegisteredOwnerPhone());
        claim.setMatchResult(matchResult);
        claim.setClaimStatus(status);
        claim.setJointOwnership(joint ? 1 : 0);
        claim.setVotingDelegate(delegate ? 1 : 0);
        claim.setProofType(trimToNull(proofType));
        claim.setProofMaterialJson(proofJson);
        claim.setBoundOpid(opid);
        return claim;
    }

    private ClaimResponse toResponse(PropertyBindingMapper.ClaimRow row) {
        return new ClaimResponse(
                row.getClaimId(),
                row.getTenantId(),
                row.getBuildingName(),
                row.getUnitName(),
                row.getRoomName(),
                row.getApplicantRealName(),
                row.getApplicantPhone(),
                row.getRosterOwnerName(),
                row.getRosterOwnerPhone(),
                row.getMatchResult(),
                row.getClaimStatus(),
                row.getJointOwnership() == 1,
                row.getVotingDelegate() == 1,
                row.getProofType(),
                row.getProofMaterialJson(),
                row.getRejectReasonCode(),
                row.getRejectReason(),
                row.getBoundOpid(),
                row.getCreateTime(),
                row.getReviewedAt());
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
            throw new AppException(CommonErrorCode.PARAM_ERROR, "物权证明材料格式错误");
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
        String name = trimToNull(mapper.selectTenantName(tenantId));
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
            throw new AppException(CommonErrorCode.PARAM_ERROR, field + " 不能为空");
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

    public record RosterOptionsResponse(List<CommunityOption> communities) {
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
}
