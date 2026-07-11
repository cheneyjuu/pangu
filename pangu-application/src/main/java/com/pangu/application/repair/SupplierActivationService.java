package com.pangu.application.repair;

import com.pangu.application.auth.SmsCodeVerifier;
import com.pangu.application.repair.command.ActivateSupplierAccountCommand;
import com.pangu.application.repair.command.CreateSupplierActivationInvitationCommand;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.repair.RepairSupplierOrganization;
import com.pangu.domain.model.repair.SupplierActivationInvitation;
import com.pangu.domain.model.role.SysRole;
import com.pangu.domain.model.user.WorkIdentityShadow;
import com.pangu.domain.repository.RepairWorkOrderRepository;
import com.pangu.domain.repository.SysRoleRepository;
import com.pangu.domain.repository.WorkIdentityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Set;

import static com.pangu.application.repair.SupplierActivationApplicationException.Reason.ACCOUNT_DISABLED;
import static com.pangu.application.repair.SupplierActivationApplicationException.Reason.FORBIDDEN;
import static com.pangu.application.repair.SupplierActivationApplicationException.Reason.IDENTITY_CONFLICT;
import static com.pangu.application.repair.SupplierActivationApplicationException.Reason.INVITATION_EXPIRED;
import static com.pangu.application.repair.SupplierActivationApplicationException.Reason.INVITATION_NOT_FOUND;
import static com.pangu.application.repair.SupplierActivationApplicationException.Reason.INVITATION_UNAVAILABLE;
import static com.pangu.application.repair.SupplierActivationApplicationException.Reason.PARAM_INVALID;
import static com.pangu.application.repair.SupplierActivationApplicationException.Reason.SMS_CODE_INVALID;
import static com.pangu.application.repair.SupplierActivationApplicationException.Reason.SYSTEM_CONFIG_ERROR;

@Service
@RequiredArgsConstructor
public class SupplierActivationService {

    private static final String STAFF_ROLE = "SERVICE_PROVIDER_STAFF";
    private static final Set<String> SUPPLIER_ROLES = Set.of(
            "SERVICE_PROVIDER_MANAGER", STAFF_ROLE);
    private static final int DEFAULT_VALID_HOURS = 72;
    private static final int MAX_VALID_HOURS = 168;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RepairWorkOrderRepository repairRepository;
    private final WorkIdentityRepository identityRepository;
    private final SysRoleRepository roleRepository;
    private final UserContextHolder userContextHolder;
    private final SmsCodeVerifier smsCodeVerifier;

    @Transactional
    public SupplierActivationInvitation createInvitation(
            Long supplierDeptId, CreateSupplierActivationInvitationCommand command) {
        UserContext ctx = requirePropertyManager();
        RepairSupplierOrganization supplier = requireSupplier(ctx.tenantId(), supplierDeptId);
        String contactName = optionalText(command.contactName(), supplier.contactName());
        String contactPhone = optionalText(command.contactPhone(), supplier.contactPhone());
        validateContact(contactName, contactPhone);
        int validHours = command.validHours() == null ? DEFAULT_VALID_HOURS : command.validHours();
        if (validHours < 1 || validHours > MAX_VALID_HOURS) {
            throw new SupplierActivationApplicationException(PARAM_INVALID,
                    "validHours 必须在 1 至 " + MAX_VALID_HOURS + " 之间");
        }
        if (repairRepository.supplierHasActiveIdentity(supplierDeptId, contactPhone)) {
            throw new SupplierActivationApplicationException(INVITATION_UNAVAILABLE,
                    "该手机号已激活当前供应商组织账号");
        }
        repairRepository.cancelPendingSupplierActivationInvitations(supplierDeptId, contactPhone);
        return insertInvitation(ctx.tenantId(), supplier, null, contactName, contactPhone,
                LocalDateTime.now().plusHours(validHours), ctx.userId());
    }

    @Transactional
    public SupplierActivationInvitation ensureContactInvitation(
            Long tenantId, Long supplierDeptId, Long workOrderId, Long invitedByUserId) {
        RepairSupplierOrganization supplier = requireSupplier(tenantId, supplierDeptId);
        if (!hasText(supplier.contactName()) || !hasText(supplier.contactPhone())) {
            return null;
        }
        validateContact(supplier.contactName(), supplier.contactPhone());
        if (repairRepository.supplierHasActiveIdentity(supplierDeptId, supplier.contactPhone())) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        SupplierActivationInvitation reusable = repairRepository.findReusableSupplierActivationInvitation(
                supplierDeptId, supplier.contactPhone(), now).orElse(null);
        if (reusable != null) {
            return reusable;
        }
        repairRepository.cancelPendingSupplierActivationInvitations(supplierDeptId, supplier.contactPhone());
        return insertInvitation(tenantId, supplier, workOrderId,
                supplier.contactName(), supplier.contactPhone(),
                now.plusHours(DEFAULT_VALID_HOURS), invitedByUserId);
    }

    @Transactional(noRollbackFor = SupplierActivationApplicationException.class)
    public SupplierActivationResult activate(ActivateSupplierAccountCommand command) {
        if (command.invitationId() == null) {
            throw new SupplierActivationApplicationException(PARAM_INVALID, "invitationId 必填");
        }
        String phone = requirePhone(command.phone());
        String operatorName = requireName(command.operatorName());
        SupplierActivationInvitation invitation = repairRepository
                .findSupplierActivationInvitationForUpdate(command.invitationId())
                .orElseThrow(() -> new SupplierActivationApplicationException(
                        INVITATION_NOT_FOUND, "激活邀请不存在"));
        if (!phone.equals(invitation.contactPhone())) {
            throw new SupplierActivationApplicationException(INVITATION_UNAVAILABLE,
                    "手机号与激活邀请不一致");
        }
        if (!"PENDING".equals(invitation.status())) {
            throw new SupplierActivationApplicationException(INVITATION_UNAVAILABLE,
                    "激活邀请已使用或已撤销");
        }
        LocalDateTime now = LocalDateTime.now();
        if (!invitation.expiresAt().isAfter(now)) {
            repairRepository.markSupplierActivationInvitationExpired(invitation.invitationId());
            throw new SupplierActivationApplicationException(INVITATION_EXPIRED, "激活邀请已过期");
        }
        verifySmsCode(phone, command.smsCode());

        WorkIdentityRepository.AccountCandidate account = identityRepository.findAccountByPhone(phone)
                .orElseGet(() -> {
                    Long accountId = identityRepository.insertAccount(phone, operatorName, 0);
                    return identityRepository.findAccount(accountId)
                            .orElseThrow(() -> new IllegalStateException("供应商自然人账号写入后不可见"));
                });
        if (account.status() != 1) {
            throw new SupplierActivationApplicationException(ACCOUNT_DISABLED,
                    "该手机号对应账号已被禁用或注销");
        }

        WorkIdentityShadow identity = identityRepository.listShadows(account.accountId()).stream()
                .filter(item -> invitation.supplierDeptId().equals(item.deptId()))
                .findFirst()
                .orElse(null);
        Long userId;
        String roleKey;
        if (identity != null) {
            if (!SUPPLIER_ROLES.contains(identity.roleKey())) {
                throw new SupplierActivationApplicationException(IDENTITY_CONFLICT,
                        "该手机号在当前组织已有其他职责，不能覆盖原角色");
            }
            userId = identity.userId();
            roleKey = identity.roleKey();
        } else {
            SysRole role = roleRepository.findByRoleKey(STAFF_ROLE)
                    .orElseThrow(() -> new IllegalStateException("缺少供应商员工系统角色"));
            userId = identityRepository.insertSysUser(
                    account.accountId(), invitation.supplierDeptId(),
                    "supplier_" + account.accountId() + "_" + invitation.supplierDeptId(), operatorName);
            identityRepository.insertSysUserRole(userId, role.roleId(), effectiveScope(role),
                    invitation.invitedByUserId());
            roleKey = role.roleKey();
        }
        identityRepository.updateLastActiveIdentity(account.accountId(), userId, UserContext.IdentityType.SYS_USER.name());
        repairRepository.markSupplierActivationInvitationActivated(
                invitation.invitationId(), account.accountId(), userId, now);
        return new SupplierActivationResult(
                invitation.invitationId(), invitation.supplierDeptId(), invitation.supplierLegalName(),
                account.accountId(), userId, phone, roleKey);
    }

    private SupplierActivationInvitation insertInvitation(
            Long tenantId,
            RepairSupplierOrganization supplier,
            Long workOrderId,
            String contactName,
            String contactPhone,
            LocalDateTime expiresAt,
            Long invitedByUserId) {
        return repairRepository.insertSupplierActivationInvitation(
                tenantId, supplier.supplierDeptId(), workOrderId, contactName, contactPhone,
                randomTokenHash(), expiresAt, invitedByUserId);
    }

    private UserContext requirePropertyManager() {
        UserContext ctx = userContextHolder.current();
        if (ctx == null || !ctx.isSysUser() || !"PROPERTY_MANAGER".equals(ctx.roleKey())
                || ctx.tenantId() == null || ctx.userId() == null) {
            throw new SupplierActivationApplicationException(FORBIDDEN, "只有物业经理可以发送供应商账号激活邀请");
        }
        return ctx;
    }

    private RepairSupplierOrganization requireSupplier(Long tenantId, Long supplierDeptId) {
        if (tenantId == null || supplierDeptId == null) {
            throw new SupplierActivationApplicationException(PARAM_INVALID, "tenantId 和 supplierDeptId 必填");
        }
        return repairRepository.findSupplierOrganization(tenantId, supplierDeptId)
                .orElseThrow(() -> new SupplierActivationApplicationException(
                        INVITATION_NOT_FOUND, "供应商组织不存在或不属于当前小区"));
    }

    private void verifySmsCode(String phone, String smsCode) {
        if (smsCode == null || smsCode.isBlank()) {
            throw new SupplierActivationApplicationException(SMS_CODE_INVALID, "短信验证码不能为空");
        }
        try {
            if (!smsCodeVerifier.verify(phone, smsCode)) {
                throw new SupplierActivationApplicationException(SMS_CODE_INVALID, "短信验证码错误或已失效");
            }
        } catch (IllegalStateException ex) {
            throw new SupplierActivationApplicationException(
                    SYSTEM_CONFIG_ERROR, "短信验证码服务不可用", ex);
        }
    }

    private static void validateContact(String contactName, String contactPhone) {
        requireName(contactName);
        requirePhone(contactPhone);
    }

    private static String requireName(String value) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty() || normalized.length() > 50) {
            throw new SupplierActivationApplicationException(PARAM_INVALID, "经办人姓名必填且不能超过 50 个字符");
        }
        return normalized;
    }

    private static String requirePhone(String value) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || !normalized.matches("1[3-9][0-9]{9}")) {
            throw new SupplierActivationApplicationException(PARAM_INVALID, "手机号格式不正确");
        }
        return normalized;
    }

    private static String optionalText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String effectiveScope(SysRole role) {
        return role.fixedDataScope() == null ? role.defaultDataScope() : role.fixedDataScope();
    }

    private static String randomTokenHash() {
        byte[] token = new byte[32];
        SECURE_RANDOM.nextBytes(token);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
