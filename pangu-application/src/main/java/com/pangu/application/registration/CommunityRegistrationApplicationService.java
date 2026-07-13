// 关联业务：编排小区注册申请、材料、审核、租户开通和初始最小授权闭环。
package com.pangu.application.registration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.application.registration.command.ReviewCommunityRegistrationCommand;
import com.pangu.application.registration.command.UploadCommunityRegistrationMaterialCommand;
import com.pangu.application.registration.command.UpsertCommunityRegistrationCommand;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.community.PropertyManagementMode;
import com.pangu.domain.model.registration.CommunityApplicantIdentity;
import com.pangu.domain.model.registration.CommunityHousingTag;
import com.pangu.domain.model.registration.CommunityRegistrationApplication;
import com.pangu.domain.model.registration.CommunityRegistrationDecision;
import com.pangu.domain.model.registration.CommunityRegistrationMaterial;
import com.pangu.domain.model.registration.CommunityRegistrationReview;
import com.pangu.domain.model.registration.CommunityRegistrationReviewMode;
import com.pangu.domain.model.registration.CommunityRegistrationStatus;
import com.pangu.domain.repository.AuthAccountRepository;
import com.pangu.domain.repository.CommunityProvisioningRepository;
import com.pangu.domain.repository.CommunityRegistrationMaterialStorage;
import com.pangu.domain.repository.CommunityRegistrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.pangu.application.registration.CommunityRegistrationApplicationException.Reason.CONCURRENT_MODIFICATION;
import static com.pangu.application.registration.CommunityRegistrationApplicationException.Reason.DUPLICATE_APPLICATION;
import static com.pangu.application.registration.CommunityRegistrationApplicationException.Reason.FORBIDDEN;
import static com.pangu.application.registration.CommunityRegistrationApplicationException.Reason.INVALID_STATUS;
import static com.pangu.application.registration.CommunityRegistrationApplicationException.Reason.MATERIAL_REQUIRED;
import static com.pangu.application.registration.CommunityRegistrationApplicationException.Reason.NOT_FOUND;
import static com.pangu.application.registration.CommunityRegistrationApplicationException.Reason.PARAM_INVALID;
import static com.pangu.application.registration.CommunityRegistrationApplicationException.Reason.PROVISIONING_FAILED;
import static com.pangu.application.registration.CommunityRegistrationApplicationException.Reason.STORAGE_UNAVAILABLE;
import static com.pangu.application.registration.CommunityRegistrationApplicationException.Reason.UNAUTHORIZED;

/**
 * 小区注册第一阶段应用服务。
 *
 * <p>注册人先通过既有 C 端短信登录获得基础账号，再创建申请。申请通过前不创建租户内
 * 高权限身份；审核通过时在同一事务内创建租户、组织、冷启动工作区和经核验身份。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommunityRegistrationApplicationService {

    private static final Pattern ADMIN_DIVISION_CODE = Pattern.compile("\\d{6}");
    private static final Set<String> MATERIAL_CONTENT_TYPES = Set.of(
            "application/pdf", "image/jpeg", "image/png", "image/webp");
    private static final long MAX_MATERIAL_SIZE = 20L * 1024 * 1024;
    private static final Duration PREVIEW_VALIDITY = Duration.ofMinutes(10);
    private static final DateTimeFormatter APPLICATION_DATE = DateTimeFormatter.ofPattern("yyyyMMdd")
            .withZone(ZoneId.of("Asia/Shanghai"));
    private static final String STREET_REVIEW_PERMISSION = "community:registration:review";
    private static final String PLATFORM_REVIEW_PERMISSION = "community:registration:platform-review";
    private static final String STREET_REVIEW_ROLE = "GOV_SUPER_ADMIN";
    private static final String PLATFORM_REVIEW_ROLE = "PLATFORM_OPERATOR";

    private final CommunityRegistrationRepository repository;
    private final CommunityProvisioningRepository provisioningRepository;
    private final CommunityRegistrationMaterialStorage materialStorage;
    private final AuthAccountRepository accountRepository;
    private final UserContextHolder userContextHolder;
    private final ObjectMapper objectMapper;

    @Transactional
    public CommunityRegistrationDetails create(UpsertCommunityRegistrationCommand command) {
        UserContext actor = requireAuthenticatedAccount();
        AuthAccountRepository.AccountSnapshot account = requireActiveAccount(actor.accountId());
        NormalizedRegistration normalized = normalize(command);
        assertNoDuplicate(normalized.fingerprint(), null, normalized);
        Instant now = Instant.now();
        CommunityRegistrationApplication application = new CommunityRegistrationApplication(
                null,
                applicationNo(now),
                actor.accountId(),
                normalized.applicantName(),
                account.phone(),
                normalized.claimedIdentity(),
                normalized.provinceCode(),
                normalized.provinceName(),
                normalized.cityCode(),
                normalized.cityName(),
                normalized.districtCode(),
                normalized.districtName(),
                normalized.communityName(),
                normalized.communityAddress(),
                normalized.declaredHouseholdCount(),
                normalized.housingTags(),
                normalized.declaredPropertyMode(),
                normalized.fingerprint(),
                CommunityRegistrationStatus.DRAFT,
                null, null, null, null, null, null, null,
                0, null, null, now, now);
        try {
            application = repository.insert(application);
        } catch (CommunityRegistrationRepository.DuplicateRegistrationException ex) {
            throw new CommunityRegistrationApplicationException(
                    DUPLICATE_APPLICATION, "该小区已有未结束的注册申请", ex);
        }
        repository.insertAudit(application.applicationId(), actor.accountId(), actor.userId(), actor.deptId(),
                "APPLICATION_CREATED", null, CommunityRegistrationStatus.DRAFT,
                toJson(Map.of("applicationNo", application.applicationNo(),
                        "claimedIdentity", application.claimedIdentity().name(),
                        "declaredPropertyMode", application.declaredPropertyMode().name())));
        return details(application);
    }

    @Transactional
    public CommunityRegistrationDetails revise(Long applicationId, UpsertCommunityRegistrationCommand command) {
        UserContext actor = requireAuthenticatedAccount();
        CommunityRegistrationApplication current = requireOwnedApplication(applicationId, actor.accountId());
        int expectedVersion = requireExpectedVersion(command.expectedVersion());
        if (expectedVersion != current.version()) {
            throw concurrentModification();
        }
        NormalizedRegistration normalized = normalize(command);
        assertNoDuplicate(normalized.fingerprint(), current.applicationId(), normalized);
        CommunityRegistrationApplication revised;
        try {
            revised = current.revise(
                    normalized.applicantName(), normalized.claimedIdentity(),
                    normalized.provinceCode(), normalized.provinceName(),
                    normalized.cityCode(), normalized.cityName(),
                    normalized.districtCode(), normalized.districtName(),
                    normalized.communityName(), normalized.communityAddress(),
                    normalized.declaredHouseholdCount(), normalized.housingTags(),
                    normalized.declaredPropertyMode(),
                    normalized.fingerprint(), Instant.now());
        } catch (IllegalStateException ex) {
            throw invalidStatus(ex);
        }
        updateOrThrow(revised, expectedVersion);
        repository.insertAudit(current.applicationId(), actor.accountId(), actor.userId(), actor.deptId(),
                "APPLICATION_REVISED", current.status(), revised.status(),
                toJson(Map.of("expectedVersion", expectedVersion)));
        return details(requireApplication(applicationId));
    }

    @Transactional
    public CommunityRegistrationDetails submit(Long applicationId, int expectedVersion) {
        UserContext actor = requireAuthenticatedAccount();
        CommunityRegistrationApplication current = requireOwnedApplication(applicationId, actor.accountId());
        assertVersion(current, expectedVersion);
        if (current.declaredPropertyMode() == null) {
            throw new CommunityRegistrationApplicationException(
                    PARAM_INVALID, "请先补充并保存物业管理模式后再提交审核");
        }
        if (repository.countActiveMaterials(applicationId) < 1) {
            throw new CommunityRegistrationApplicationException(
                    MATERIAL_REQUIRED, "提交审核前至少上传一份小区或注册人证明材料");
        }
        assertNoDuplicate(current.communityFingerprint(), current.applicationId(),
                new NormalizedRegistration(
                        current.applicantName(), current.claimedIdentity(), current.provinceCode(),
                        current.provinceName(), current.cityCode(), current.cityName(), current.districtCode(),
                        current.districtName(), current.communityName(), current.communityAddress(),
                        current.declaredHouseholdCount(), current.housingTags(), current.declaredPropertyMode(),
                        current.communityFingerprint()));
        CommunityRegistrationApplication submitted;
        try {
            submitted = current.submit(Instant.now());
        } catch (IllegalStateException ex) {
            throw invalidStatus(ex);
        }
        updateOrThrow(submitted, expectedVersion);
        repository.insertAudit(applicationId, actor.accountId(), actor.userId(), actor.deptId(),
                "APPLICATION_SUBMITTED", current.status(), CommunityRegistrationStatus.SUBMITTED,
                toJson(Map.of("materialCount", repository.countActiveMaterials(applicationId))));
        return details(requireApplication(applicationId));
    }

    @Transactional
    public CommunityRegistrationDetails withdraw(Long applicationId, int expectedVersion) {
        UserContext actor = requireAuthenticatedAccount();
        CommunityRegistrationApplication current = requireOwnedApplication(applicationId, actor.accountId());
        assertVersion(current, expectedVersion);
        CommunityRegistrationApplication withdrawn;
        try {
            withdrawn = current.withdraw(Instant.now());
        } catch (IllegalStateException ex) {
            throw invalidStatus(ex);
        }
        updateOrThrow(withdrawn, expectedVersion);
        repository.insertAudit(applicationId, actor.accountId(), actor.userId(), actor.deptId(),
                "APPLICATION_WITHDRAWN", current.status(), CommunityRegistrationStatus.WITHDRAWN, "{}");
        return details(requireApplication(applicationId));
    }

    @Transactional
    public CommunityRegistrationMaterial uploadMaterial(
            Long applicationId,
            UploadCommunityRegistrationMaterialCommand command) {
        UserContext actor = requireAuthenticatedAccount();
        CommunityRegistrationApplication application = requireOwnedApplication(applicationId, actor.accountId());
        requireEditable(application);
        if (command == null || command.materialType() == null) {
            throw new CommunityRegistrationApplicationException(PARAM_INVALID, "材料类型不能为空");
        }
        String contentType = normalizeContentType(command.contentType());
        byte[] content = command.content() == null ? new byte[0] : command.content();
        if (!MATERIAL_CONTENT_TYPES.contains(contentType)) {
            throw new CommunityRegistrationApplicationException(
                    PARAM_INVALID, "注册材料仅支持 PDF、JPG、PNG 或 WebP");
        }
        if (content.length == 0 || content.length > MAX_MATERIAL_SIZE) {
            throw new CommunityRegistrationApplicationException(
                    PARAM_INVALID, "注册材料大小必须大于 0 且不超过 20MB");
        }
        String fileName = normalizeFileName(command.originalFileName());
        String objectKey = materialObjectKey(application, contentType);
        CommunityRegistrationMaterialStorage.StoredObjectMetadata stored;
        try {
            stored = materialStorage.put(objectKey, content, contentType, digestBase64("MD5", content));
        } catch (RuntimeException ex) {
            throw new CommunityRegistrationApplicationException(STORAGE_UNAVAILABLE, "注册材料上传失败", ex);
        }
        if (stored.size() != content.length || stored.etag() == null || stored.etag().isBlank()) {
            deleteStoredObjectQuietly(objectKey);
            throw new CommunityRegistrationApplicationException(
                    STORAGE_UNAVAILABLE, "注册材料对象完整性校验失败");
        }
        try {
            CommunityRegistrationMaterial material = repository.insertMaterial(new CommunityRegistrationMaterial(
                    null, applicationId, command.materialType(), objectKey, fileName, contentType,
                    content.length, stored.etag(), digestHex("SHA-256", content), actor.accountId(),
                    "ACTIVE", Instant.now()));
            repository.insertAudit(applicationId, actor.accountId(), actor.userId(), actor.deptId(),
                    "MATERIAL_UPLOADED", application.status(), application.status(),
                    toJson(Map.of("materialId", material.materialId(),
                            "materialType", material.materialType().name(),
                            "sha256", material.sha256())));
            return material;
        } catch (RuntimeException ex) {
            deleteStoredObjectQuietly(objectKey);
            throw ex;
        }
    }

    @Transactional
    public void deleteMaterial(Long applicationId, Long materialId) {
        UserContext actor = requireAuthenticatedAccount();
        CommunityRegistrationApplication application = requireOwnedApplication(applicationId, actor.accountId());
        requireEditable(application);
        CommunityRegistrationMaterial material = repository.findMaterial(applicationId, materialId)
                .orElseThrow(() -> new CommunityRegistrationApplicationException(NOT_FOUND, "注册材料不存在"));
        if (repository.deactivateMaterial(applicationId, materialId, actor.accountId()) != 1) {
            throw new CommunityRegistrationApplicationException(INVALID_STATUS, "注册材料状态已变化，请刷新后重试");
        }
        try {
            materialStorage.delete(material.objectKey());
        } catch (RuntimeException ex) {
            throw new CommunityRegistrationApplicationException(STORAGE_UNAVAILABLE, "删除注册材料失败", ex);
        }
        repository.insertAudit(applicationId, actor.accountId(), actor.userId(), actor.deptId(),
                "MATERIAL_REMOVED", application.status(), application.status(),
                toJson(Map.of("materialId", materialId, "sha256", material.sha256())));
    }

    @Transactional(readOnly = true)
    public CommunityRegistrationMaterialPreviewTicket createMaterialPreviewTicket(
            Long applicationId,
            Long materialId) {
        UserContext actor = requireAuthenticatedAccount();
        CommunityRegistrationApplication application = requireApplication(applicationId);
        assertCanView(actor, application);
        CommunityRegistrationMaterial material = repository.findMaterial(applicationId, materialId)
                .orElseThrow(() -> new CommunityRegistrationApplicationException(NOT_FOUND, "注册材料不存在"));
        Instant expiresAt = Instant.now().plus(PREVIEW_VALIDITY);
        try {
            return new CommunityRegistrationMaterialPreviewTicket(
                    material.materialId(), material.originalFileName(), material.contentType(), material.fileSize(),
                    materialStorage.createPreviewUrl(
                            material.objectKey(), material.originalFileName(), PREVIEW_VALIDITY).toString(),
                    expiresAt);
        } catch (RuntimeException ex) {
            throw new CommunityRegistrationApplicationException(STORAGE_UNAVAILABLE, "生成注册材料预览地址失败", ex);
        }
    }

    @Transactional(readOnly = true)
    public List<CommunityRegistrationDetails> listMine(int limit) {
        UserContext actor = requireAuthenticatedAccount();
        return repository.listByApplicant(actor.accountId(), normalizeLimit(limit)).stream()
                .map(this::details)
                .toList();
    }

    @Transactional(readOnly = true)
    public CommunityRegistrationDetails get(Long applicationId) {
        UserContext actor = requireAuthenticatedAccount();
        CommunityRegistrationApplication application = requireApplication(applicationId);
        assertCanView(actor, application);
        return details(application);
    }

    @Transactional(readOnly = true)
    public List<CommunityRegistrationDetails> listForReview(CommunityRegistrationStatus status, int limit) {
        requireAnyReviewer();
        CommunityRegistrationStatus queryStatus = status == null
                ? CommunityRegistrationStatus.SUBMITTED
                : status;
        return repository.listForReview(queryStatus, normalizeLimit(limit)).stream()
                .map(this::details)
                .toList();
    }

    @Transactional
    public CommunityRegistrationDetails review(
            Long applicationId,
            ReviewCommunityRegistrationCommand command) {
        UserContext reviewer = requireReviewer(command);
        CommunityRegistrationApplication current = requireApplication(applicationId);
        if (current.applicantAccountId().equals(reviewer.accountId())) {
            throw new CommunityRegistrationApplicationException(FORBIDDEN, "注册申请人不得审核自己的申请");
        }
        if (command == null || command.decision() == null || command.reviewMode() == null) {
            throw new CommunityRegistrationApplicationException(PARAM_INVALID, "审核决定和审核方式不能为空");
        }
        int expectedVersion = requireExpectedVersion(command.expectedVersion());
        if (current.status() == CommunityRegistrationStatus.APPROVED
                && command.decision() == CommunityRegistrationDecision.APPROVE) {
            return details(current);
        }
        assertVersion(current, expectedVersion);
        if (current.status() != CommunityRegistrationStatus.SUBMITTED) {
            throw new CommunityRegistrationApplicationException(
                    INVALID_STATUS, "只有待审核申请可以处理，当前状态=" + current.status());
        }
        String comment = normalizeReviewComment(command.decision(), command.reviewComment());
        String fallbackReason = normalizeFallbackReason(command.reviewMode(), command.fallbackReason());
        Instant now = Instant.now();
        CommunityRegistrationApplication reviewed;
        try {
            reviewed = switch (command.decision()) {
                case RETURN -> current.returnForSupplement(
                        command.reviewMode(), reviewer.accountId(), reviewer.userId(), reviewer.deptId(),
                        comment, fallbackReason, now);
                case REJECT -> current.reject(
                        command.reviewMode(), reviewer.accountId(), reviewer.userId(), reviewer.deptId(),
                        comment, fallbackReason, now);
                case APPROVE -> approveAndProvision(current, reviewer, command.reviewMode(),
                        comment, fallbackReason, now);
            };
        } catch (IllegalStateException ex) {
            throw invalidStatus(ex);
        } catch (CommunityProvisioningRepository.ProvisioningConsistencyException ex) {
            throw new CommunityRegistrationApplicationException(PROVISIONING_FAILED, ex.getMessage(), ex);
        }
        updateOrThrow(reviewed, expectedVersion);
        repository.insertReview(new CommunityRegistrationReview(
                null, applicationId, command.decision(), command.reviewMode(), reviewer.accountId(),
                reviewer.userId(), reviewer.deptId(), comment, fallbackReason, now));
        repository.insertAudit(applicationId, reviewer.accountId(), reviewer.userId(), reviewer.deptId(),
                "APPLICATION_" + command.decision().name(), current.status(), reviewed.status(),
                toJson(Map.of(
                        "reviewMode", command.reviewMode().name(),
                        "reviewComment", comment == null ? "" : comment,
                        "fallbackReason", fallbackReason == null ? "" : fallbackReason,
                        "tenantId", reviewed.provisionedTenantId() == null ? 0 : reviewed.provisionedTenantId())));
        if (command.decision() == CommunityRegistrationDecision.APPROVE) {
            var workspace = repository.findOnboardingWorkspace(applicationId)
                    .orElseThrow(() -> new CommunityRegistrationApplicationException(
                            PROVISIONING_FAILED, "租户已创建但冷启动工作区未能回读"));
            repository.insertAudit(applicationId, reviewer.accountId(), reviewer.userId(), reviewer.deptId(),
                    "TENANT_PROVISIONED", current.status(), reviewed.status(),
                    toJson(Map.of(
                            "tenantId", workspace.tenantId(),
                            "initializationDeptId", workspace.initializationDeptId(),
                            "committeeDeptId", workspace.committeeDeptId() == null ? 0 : workspace.committeeDeptId(),
                            "applicantWorkUserId", workspace.applicantWorkUserId() == null
                                    ? 0 : workspace.applicantWorkUserId(),
                            "claimedIdentity", current.claimedIdentity().name(),
                            "officialAffiliationStatus", workspace.officialAffiliationStatus())));
        }
        log.info("Community registration reviewed application={} decision={} mode={} reviewer={} tenant={}",
                applicationId, command.decision(), command.reviewMode(), reviewer.userId(),
                reviewed.provisionedTenantId());
        return details(requireApplication(applicationId));
    }

    private CommunityRegistrationApplication approveAndProvision(
            CommunityRegistrationApplication current,
            UserContext reviewer,
            CommunityRegistrationReviewMode mode,
            String comment,
            String fallbackReason,
            Instant now) {
        if (current.declaredPropertyMode() == null) {
            throw new CommunityRegistrationApplicationException(
                    PARAM_INVALID, "该申请未声明物业管理模式，请退回申请人补充后再审核");
        }
        CommunityProvisioningRepository.ProvisioningResult provisioned =
                provisioningRepository.provision(current, reviewer, mode);
        return current.approve(
                mode, reviewer.accountId(), reviewer.userId(), reviewer.deptId(), comment, fallbackReason,
                provisioned.tenantId(), now);
    }

    private UserContext requireReviewer(ReviewCommunityRegistrationCommand command) {
        if (command == null || command.reviewMode() == null) {
            throw new CommunityRegistrationApplicationException(PARAM_INVALID, "审核方式不能为空");
        }
        UserContext ctx = requireAnyReviewer();
        if (command.reviewMode() == CommunityRegistrationReviewMode.STREET) {
            if (!STREET_REVIEW_ROLE.equals(ctx.roleKey()) || !ctx.hasPermission(STREET_REVIEW_PERMISSION)) {
                throw new CommunityRegistrationApplicationException(FORBIDDEN, "仅属地街镇审核身份可使用街镇审核路径");
            }
        } else if (!PLATFORM_REVIEW_ROLE.equals(ctx.roleKey())
                || !ctx.hasPermission(PLATFORM_REVIEW_PERMISSION)) {
            throw new CommunityRegistrationApplicationException(FORBIDDEN, "仅平台运营审核身份可使用代审路径");
        }
        return ctx;
    }

    private UserContext requireAnyReviewer() {
        UserContext ctx = requireAuthenticatedAccount();
        boolean structuralRole = ctx.isSysUser()
                && ctx.deptCategory() == UserContext.DeptCategory.G
                && ctx.deptType() != null
                && ctx.deptType() == 1;
        boolean permission = ctx.hasPermission(STREET_REVIEW_PERMISSION)
                || ctx.hasPermission(PLATFORM_REVIEW_PERMISSION);
        if (!structuralRole || !permission) {
            throw new CommunityRegistrationApplicationException(FORBIDDEN, "当前身份无权审核小区注册申请");
        }
        return ctx;
    }

    private void assertCanView(UserContext actor, CommunityRegistrationApplication application) {
        if (application.applicantAccountId().equals(actor.accountId())) {
            return;
        }
        boolean reviewer = actor.isSysUser()
                && actor.deptCategory() == UserContext.DeptCategory.G
                && actor.deptType() != null
                && actor.deptType() == 1
                && (actor.hasPermission(STREET_REVIEW_PERMISSION)
                || actor.hasPermission(PLATFORM_REVIEW_PERMISSION));
        if (!reviewer) {
            throw new CommunityRegistrationApplicationException(FORBIDDEN, "无权查看该小区注册申请");
        }
    }

    private UserContext requireAuthenticatedAccount() {
        UserContext ctx = userContextHolder.current();
        if (ctx == null || ctx.accountId() == null) {
            throw new CommunityRegistrationApplicationException(UNAUTHORIZED, "未识别到登录账号");
        }
        return ctx;
    }

    private AuthAccountRepository.AccountSnapshot requireActiveAccount(Long accountId) {
        AuthAccountRepository.AccountSnapshot account = accountRepository.findById(accountId);
        if (account == null || account.status() == null || account.status() != 1) {
            throw new CommunityRegistrationApplicationException(UNAUTHORIZED, "登录账号不存在或已停用");
        }
        return account;
    }

    private CommunityRegistrationApplication requireOwnedApplication(Long applicationId, Long accountId) {
        CommunityRegistrationApplication application = requireApplication(applicationId);
        if (!application.applicantAccountId().equals(accountId)) {
            throw new CommunityRegistrationApplicationException(FORBIDDEN, "只能维护本人提交的注册申请");
        }
        return application;
    }

    private CommunityRegistrationApplication requireApplication(Long applicationId) {
        if (applicationId == null) {
            throw new CommunityRegistrationApplicationException(PARAM_INVALID, "applicationId 不能为空");
        }
        return repository.findById(applicationId)
                .orElseThrow(() -> new CommunityRegistrationApplicationException(NOT_FOUND, "小区注册申请不存在"));
    }

    private CommunityRegistrationDetails details(CommunityRegistrationApplication application) {
        return new CommunityRegistrationDetails(
                application,
                repository.listMaterials(application.applicationId()),
                repository.listReviews(application.applicationId()),
                repository.findOnboardingWorkspace(application.applicationId()).orElse(null));
    }

    private void requireEditable(CommunityRegistrationApplication application) {
        if (application.status() != CommunityRegistrationStatus.DRAFT
                && application.status() != CommunityRegistrationStatus.RETURNED) {
            throw new CommunityRegistrationApplicationException(
                    INVALID_STATUS, "只有草稿或退回补充状态可以维护材料");
        }
    }

    private void updateOrThrow(CommunityRegistrationApplication changed, int expectedVersion) {
        try {
            if (repository.update(changed, expectedVersion) != 1) {
                throw concurrentModification();
            }
        } catch (CommunityRegistrationRepository.DuplicateRegistrationException ex) {
            throw new CommunityRegistrationApplicationException(
                    DUPLICATE_APPLICATION, "该小区已有未结束的注册申请", ex);
        }
    }

    private void assertVersion(CommunityRegistrationApplication current, int expectedVersion) {
        if (expectedVersion < 0 || current.version() != expectedVersion) {
            throw concurrentModification();
        }
    }

    private int requireExpectedVersion(Integer expectedVersion) {
        if (expectedVersion == null || expectedVersion < 0) {
            throw new CommunityRegistrationApplicationException(PARAM_INVALID, "expectedVersion 不能为空且不得小于 0");
        }
        return expectedVersion;
    }

    private void assertNoDuplicate(
            String fingerprint,
            Long excludeApplicationId,
            NormalizedRegistration normalized) {
        if (repository.existsActiveFingerprint(fingerprint, excludeApplicationId)
                || repository.existsProvisionedCommunity(
                fingerprint, normalized.districtCode(), normalized.communityName(), normalized.communityAddress())) {
            throw new CommunityRegistrationApplicationException(
                    DUPLICATE_APPLICATION, "该行政区内同名同址小区已注册或正在审核");
        }
    }

    private NormalizedRegistration normalize(UpsertCommunityRegistrationCommand command) {
        if (command == null || command.claimedIdentity() == null || command.declaredPropertyMode() == null) {
            throw new CommunityRegistrationApplicationException(
                    PARAM_INVALID, "注册信息、申报身份和物业管理模式不能为空");
        }
        String provinceCode = normalizeDivisionCode(command.provinceCode(), "省份代码");
        String cityCode = normalizeDivisionCode(command.cityCode(), "城市代码");
        String districtCode = normalizeDivisionCode(command.districtCode(), "区县代码");
        if (!cityCode.substring(0, 2).equals(provinceCode.substring(0, 2))
                || !districtCode.substring(0, 4).equals(cityCode.substring(0, 4))) {
            throw new CommunityRegistrationApplicationException(PARAM_INVALID, "省、市、区行政区划代码层级不一致");
        }
        String applicantName = normalizeText(command.applicantName(), "注册人姓名", 2, 50);
        String provinceName = normalizeText(command.provinceName(), "省份名称", 2, 64);
        String cityName = normalizeText(command.cityName(), "城市名称", 2, 64);
        String districtName = normalizeText(command.districtName(), "区县名称", 2, 64);
        String communityName = normalizeText(command.communityName(), "小区名称", 2, 128);
        String communityAddress = normalizeText(command.communityAddress(), "小区地址", 5, 256);
        if (command.declaredHouseholdCount() == null
                || command.declaredHouseholdCount() < 1
                || command.declaredHouseholdCount() > 1_000_000) {
            throw new CommunityRegistrationApplicationException(PARAM_INVALID, "户数必须在 1 至 1000000 之间");
        }
        Set<CommunityHousingTag> tags = command.housingTags() == null
                ? Set.of()
                : Set.copyOf(command.housingTags());
        if (tags.isEmpty()) {
            throw new CommunityRegistrationApplicationException(PARAM_INVALID, "至少选择一种小区房屋概况标签");
        }
        String fingerprint = fingerprint(districtCode, communityName, communityAddress);
        return new NormalizedRegistration(
                applicantName, command.claimedIdentity(), provinceCode, provinceName, cityCode, cityName,
                districtCode, districtName, communityName, communityAddress,
                command.declaredHouseholdCount(), tags, command.declaredPropertyMode(), fingerprint);
    }

    private String normalizeReviewComment(CommunityRegistrationDecision decision, String comment) {
        String normalized = trimToNull(comment);
        if ((decision == CommunityRegistrationDecision.RETURN
                || decision == CommunityRegistrationDecision.REJECT)
                && (normalized == null || normalized.length() < 2)) {
            throw new CommunityRegistrationApplicationException(PARAM_INVALID, "退回或拒绝必须填写审核意见");
        }
        if (normalized != null && normalized.length() > 1000) {
            throw new CommunityRegistrationApplicationException(PARAM_INVALID, "审核意见不能超过 1000 个字符");
        }
        return normalized;
    }

    private String normalizeFallbackReason(CommunityRegistrationReviewMode mode, String reason) {
        String normalized = trimToNull(reason);
        if (mode == CommunityRegistrationReviewMode.PLATFORM_FALLBACK
                && (normalized == null || normalized.length() < 10)) {
            throw new CommunityRegistrationApplicationException(
                    PARAM_INVALID, "平台代审必须说明街镇未接入情况和代审依据，至少 10 个字符");
        }
        if (normalized != null && normalized.length() > 1000) {
            throw new CommunityRegistrationApplicationException(PARAM_INVALID, "代审原因不能超过 1000 个字符");
        }
        return normalized;
    }

    private String normalizeDivisionCode(String value, String fieldName) {
        String normalized = trimToNull(value);
        if (normalized == null || !ADMIN_DIVISION_CODE.matcher(normalized).matches()) {
            throw new CommunityRegistrationApplicationException(PARAM_INVALID, fieldName + "必须是 6 位行政区划代码");
        }
        return normalized;
    }

    private String normalizeText(String value, String fieldName, int minLength, int maxLength) {
        String normalized = trimToNull(value);
        if (normalized == null || normalized.length() < minLength || normalized.length() > maxLength) {
            throw new CommunityRegistrationApplicationException(
                    PARAM_INVALID, fieldName + "长度必须在 " + minLength + " 至 " + maxLength + " 个字符之间");
        }
        return normalized;
    }

    private String fingerprint(String districtCode, String communityName, String communityAddress) {
        String canonical = districtCode + "|" + canonicalText(communityName) + "|" + canonicalText(communityAddress);
        return digestHex("SHA-256", canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String canonicalText(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "");
    }

    private String applicationNo(Instant now) {
        return "CR-" + APPLICATION_DATE.format(now) + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
    }

    private String materialObjectKey(CommunityRegistrationApplication application, String contentType) {
        String extension = switch (contentType) {
            case "application/pdf" -> "pdf";
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "bin";
        };
        return "community-registration/" + application.applicationId() + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + extension;
    }

    private String normalizeContentType(String contentType) {
        String normalized = trimToNull(contentType);
        return normalized == null ? "" : normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizeFileName(String fileName) {
        String normalized = trimToNull(fileName);
        if (normalized == null) {
            return "registration-material";
        }
        normalized = normalized.replace('\\', '/');
        normalized = normalized.substring(normalized.lastIndexOf('/') + 1);
        if (normalized.length() > 255) {
            normalized = normalized.substring(normalized.length() - 255);
        }
        return normalized;
    }

    private String digestBase64(String algorithm, byte[] content) {
        return Base64.getEncoder().encodeToString(digest(algorithm, content));
    }

    private String digestHex(String algorithm, byte[] content) {
        return HexFormat.of().formatHex(digest(algorithm, content));
    }

    private byte[] digest(String algorithm, byte[] content) {
        try {
            return MessageDigest.getInstance(algorithm).digest(content);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JVM 缺少摘要算法：" + algorithm, ex);
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("注册审计数据序列化失败", ex);
        }
    }

    private int normalizeLimit(int limit) {
        return limit <= 0 ? 50 : Math.min(limit, 100);
    }

    private CommunityRegistrationApplicationException invalidStatus(IllegalStateException ex) {
        return new CommunityRegistrationApplicationException(INVALID_STATUS, ex.getMessage(), ex);
    }

    private CommunityRegistrationApplicationException concurrentModification() {
        return new CommunityRegistrationApplicationException(
                CONCURRENT_MODIFICATION, "申请已被其他操作更新，请刷新后重试");
    }

    private void deleteStoredObjectQuietly(String objectKey) {
        try {
            materialStorage.delete(objectKey);
        } catch (RuntimeException cleanupError) {
            log.warn("Failed to clean community registration material objectKey={}", objectKey, cleanupError);
        }
    }

    private record NormalizedRegistration(
            String applicantName,
            CommunityApplicantIdentity claimedIdentity,
            String provinceCode,
            String provinceName,
            String cityCode,
            String cityName,
            String districtCode,
            String districtName,
            String communityName,
            String communityAddress,
            int declaredHouseholdCount,
            Set<CommunityHousingTag> housingTags,
            PropertyManagementMode declaredPropertyMode,
            String fingerprint) {
    }
}
