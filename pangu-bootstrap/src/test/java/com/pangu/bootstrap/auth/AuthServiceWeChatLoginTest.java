// 关联业务：验证微信小程序手机号授权与头像昵称资料授权只建立本人会话，不把展示资料作为实名或产权依据。
package com.pangu.bootstrap.auth;

import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextLoader;
import com.pangu.domain.gateway.PropertyGateway;
import com.pangu.domain.gateway.identity.IdCardOcrGateway;
import com.pangu.domain.gateway.identity.WeChatMiniProgramGateway;
import com.pangu.domain.model.user.AuthenticationLevel;
import com.pangu.domain.model.user.DataScopeType;
import com.pangu.domain.model.user.WorkIdentityShadow;
import com.pangu.domain.repository.AuthAccountRepository;
import com.pangu.domain.repository.CommunitySettingsRepository;
import com.pangu.domain.repository.GovernmentManagedCommunityRepository;
import com.pangu.domain.repository.IdentityShadowRepository;
import com.pangu.domain.repository.NavigationMenuRepository;
import com.pangu.domain.repository.OwnerIdentityVerificationRepository;
import com.pangu.domain.security.NameDecryptor;
import com.pangu.interfaces.security.JwtTokenProvider;
import com.pangu.interfaces.web.controller.dto.LoginRequest;
import com.pangu.interfaces.web.controller.dto.WeChatPhoneLoginRequest;
import com.pangu.interfaces.web.controller.dto.WeChatProfileRequest;
import com.pangu.interfaces.web.service.AuthService;
import com.pangu.interfaces.web.service.SmsVerificationStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

/** 微信授权会话服务的单元测试。 */
@ExtendWith(MockitoExtension.class)
class AuthServiceWeChatLoginTest {

    private static final Long ACCOUNT_ID = 810001L;
    private static final Long UID = 910001L;
    private static final Long TENANT_ID = 10001L;
    private static final String APP_ID = "wx-test";
    private static final String PHONE = "13800001001";
    private static final String SUBJECT_HASH = "5d4c3b2a1f";

    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private AuthAccountRepository authAccountRepository;
    @Mock
    private CommunitySettingsRepository communitySettingsRepository;
    @Mock
    private IdentityShadowRepository identityShadowRepository;
    @Mock
    private NavigationMenuRepository navigationMenuRepository;
    @Mock
    private GovernmentManagedCommunityRepository governmentManagedCommunityRepository;
    @Mock
    private UserContextLoader userContextLoader;
    @Mock
    private PropertyGateway propertyGateway;
    @Mock
    private SmsVerificationStrategy smsVerificationStrategy;
    @Mock
    private OwnerIdentityVerificationRepository ownerIdentityVerificationRepository;
    @Mock
    private IdCardOcrGateway idCardOcrGateway;
    @Mock
    private NameDecryptor nameDecryptor;
    @Mock
    private WeChatMiniProgramGateway weChatMiniProgramGateway;

    private AuthService service() {
        return new AuthService(
                jwtTokenProvider,
                authAccountRepository,
                communitySettingsRepository,
                identityShadowRepository,
                navigationMenuRepository,
                governmentManagedCommunityRepository,
                userContextLoader,
                propertyGateway,
                smsVerificationStrategy,
                ownerIdentityVerificationRepository,
                idCardOcrGateway,
                nameDecryptor,
                weChatMiniProgramGateway);
    }

    @Test
    void weChatPhoneLogin_binds_hashed_subject_and_issues_owner_session() {
        AuthAccountRepository.AccountSnapshot account = new AuthAccountRepository.AccountSnapshot(
                ACCOUNT_ID, PHONE, 1, UID, UserContext.IdentityType.C_USER.name());
        UserContext context = ownerContext();
        when(weChatMiniProgramGateway.miniProgramAppId()).thenReturn(APP_ID);
        when(weChatMiniProgramGateway.exchangePhoneAuthorization("login-code", "phone-code"))
                .thenReturn(new WeChatMiniProgramGateway.WeChatPhoneIdentity(PHONE, SUBJECT_HASH));
        when(authAccountRepository.findAccountIdByWeChatSubjectHash(APP_ID, SUBJECT_HASH)).thenReturn(null);
        when(authAccountRepository.findByPhone(PHONE)).thenReturn(account);
        when(authAccountRepository.findWeChatIdentity(ACCOUNT_ID, APP_ID)).thenReturn(null);
        when(authAccountRepository.bindWeChatIdentity(any())).thenReturn(1);
        when(authAccountRepository.ensureCUserIdentity(ACCOUNT_ID)).thenReturn(UID);
        when(userContextLoader.load(ACCOUNT_ID, UserContext.IdentityType.C_USER, UID, null)).thenReturn(context);
        when(jwtTokenProvider.generateToken(ACCOUNT_ID, "C_USER", UID, TENANT_ID)).thenReturn("owner-token");
        when(authAccountRepository.findIdentityByAccountId(ACCOUNT_ID)).thenReturn(
                new AuthAccountRepository.AccountIdentitySnapshot(ACCOUNT_ID, PHONE, null, 0, 1));
        when(communitySettingsRepository.findCommunity(TENANT_ID)).thenReturn(Optional.empty());

        Map<String, Object> result = service().weChatPhoneLogin(
                new WeChatPhoneLoginRequest("login-code", "phone-code"));

        assertEquals("owner-token", result.get("access_token"));
        @SuppressWarnings("unchecked")
        Map<String, Object> userInfo = (Map<String, Object>) result.get("user_info");
        assertNotNull(userInfo);
        assertEquals(ACCOUNT_ID, userInfo.get("account_id"));
        assertEquals(PHONE, userInfo.get("phone"));
        verify(authAccountRepository).bindWeChatIdentity(new AuthAccountRepository.WeChatIdentityBinding(
                ACCOUNT_ID, APP_ID, SUBJECT_HASH));
        verify(authAccountRepository).touchWeChatIdentityLogin(ACCOUNT_ID, APP_ID);
    }

    @Test
    void weChatPhoneLogin_createsBaseOwnerIdentityForExistingAccountWithoutProperty() {
        AuthAccountRepository.AccountSnapshot account = new AuthAccountRepository.AccountSnapshot(
                ACCOUNT_ID, PHONE, 1, 800001L, UserContext.IdentityType.SYS_USER.name());
        UserContext context = ownerContextWithoutProperty();
        when(weChatMiniProgramGateway.miniProgramAppId()).thenReturn(APP_ID);
        when(weChatMiniProgramGateway.exchangePhoneAuthorization("login-code", "phone-code"))
                .thenReturn(new WeChatMiniProgramGateway.WeChatPhoneIdentity(PHONE, SUBJECT_HASH));
        when(authAccountRepository.findAccountIdByWeChatSubjectHash(APP_ID, SUBJECT_HASH)).thenReturn(null);
        when(authAccountRepository.findByPhone(PHONE)).thenReturn(account);
        when(authAccountRepository.findWeChatIdentity(ACCOUNT_ID, APP_ID)).thenReturn(null);
        when(authAccountRepository.bindWeChatIdentity(any())).thenReturn(1);
        when(authAccountRepository.ensureCUserIdentity(ACCOUNT_ID)).thenReturn(UID);
        when(userContextLoader.load(ACCOUNT_ID, UserContext.IdentityType.C_USER, UID, null)).thenReturn(context);
        when(jwtTokenProvider.generateToken(ACCOUNT_ID, "C_USER", UID, null)).thenReturn("base-owner-token");
        when(authAccountRepository.findIdentityByAccountId(ACCOUNT_ID)).thenReturn(
                new AuthAccountRepository.AccountIdentitySnapshot(ACCOUNT_ID, PHONE, null, 0, 1));

        Map<String, Object> result = service().weChatPhoneLogin(
                new WeChatPhoneLoginRequest("login-code", "phone-code"));

        assertEquals("base-owner-token", result.get("access_token"));
        @SuppressWarnings("unchecked")
        Map<String, Object> userInfo = (Map<String, Object>) result.get("user_info");
        assertEquals("C_USER", userInfo.get("identity_type"));
        assertNull(userInfo.get("tenant_id"));
        verify(authAccountRepository).ensureCUserIdentity(ACCOUNT_ID);
        verify(userContextLoader).load(ACCOUNT_ID, UserContext.IdentityType.C_USER, UID, null);
        verifyNoInteractions(propertyGateway);
    }

    @Test
    void weChatPhoneLogin_autoRegistersNewPhoneBeforeIssuingOwnerSession() {
        AuthAccountRepository.AccountSnapshot account = new AuthAccountRepository.AccountSnapshot(
                ACCOUNT_ID, PHONE, 1, UID, UserContext.IdentityType.C_USER.name());
        UserContext context = ownerContextWithoutProperty();
        when(weChatMiniProgramGateway.miniProgramAppId()).thenReturn(APP_ID);
        when(weChatMiniProgramGateway.exchangePhoneAuthorization("login-code", "phone-code"))
                .thenReturn(new WeChatMiniProgramGateway.WeChatPhoneIdentity(PHONE, SUBJECT_HASH));
        when(authAccountRepository.findAccountIdByWeChatSubjectHash(APP_ID, SUBJECT_HASH)).thenReturn(null);
        when(authAccountRepository.findByPhone(PHONE)).thenReturn(null);
        when(authAccountRepository.createColdStartOwnerAccount(PHONE)).thenReturn(account);
        when(authAccountRepository.findWeChatIdentity(ACCOUNT_ID, APP_ID)).thenReturn(null);
        when(authAccountRepository.bindWeChatIdentity(any())).thenReturn(1);
        when(authAccountRepository.ensureCUserIdentity(ACCOUNT_ID)).thenReturn(UID);
        when(userContextLoader.load(ACCOUNT_ID, UserContext.IdentityType.C_USER, UID, null)).thenReturn(context);
        when(jwtTokenProvider.generateToken(ACCOUNT_ID, "C_USER", UID, null)).thenReturn("new-owner-token");
        when(authAccountRepository.findIdentityByAccountId(ACCOUNT_ID)).thenReturn(
                new AuthAccountRepository.AccountIdentitySnapshot(ACCOUNT_ID, PHONE, null, 0, 1));

        Map<String, Object> result = service().weChatPhoneLogin(
                new WeChatPhoneLoginRequest("login-code", "phone-code"));

        assertEquals("new-owner-token", result.get("access_token"));
        verify(authAccountRepository).createColdStartOwnerAccount(PHONE);
        verify(authAccountRepository).ensureCUserIdentity(ACCOUNT_ID);
        verifyNoInteractions(propertyGateway);
    }

    @Test
    void managementLogin_usesWorkIdentityWhenOwnerIdentityWasLastActive() {
        Long propertyStaffUserId = 920001L;
        AuthAccountRepository.AccountSnapshot account = new AuthAccountRepository.AccountSnapshot(
                ACCOUNT_ID, PHONE, 1, UID, UserContext.IdentityType.C_USER.name());
        UserContext context = managementContext(propertyStaffUserId);
        LoginRequest request = new LoginRequest();
        request.setUsername(PHONE);
        request.setSmsCode("123456");
        request.setClientPortal("B");
        when(authAccountRepository.findByPhone(PHONE)).thenReturn(account);
        when(identityShadowRepository.listSysUserShadows(ACCOUNT_ID)).thenReturn(List.of(
                new WorkIdentityShadow(propertyStaffUserId, ACCOUNT_ID, 930001L, TENANT_ID,
                        "物业员工", null, 4, "S", "求是花园项目部", 940001L,
                        "PROPERTY_STAFF", "物业员工", "ORG_ONLY", List.of(), List.of())));
        when(userContextLoader.load(ACCOUNT_ID, UserContext.IdentityType.SYS_USER, propertyStaffUserId, null))
                .thenReturn(context);
        when(jwtTokenProvider.generateToken(ACCOUNT_ID, "SYS_USER", propertyStaffUserId, TENANT_ID))
                .thenReturn("management-token");
        when(authAccountRepository.findIdentityByAccountId(ACCOUNT_ID)).thenReturn(
                new AuthAccountRepository.AccountIdentitySnapshot(ACCOUNT_ID, PHONE, null, 0, 1));
        when(communitySettingsRepository.findCommunity(TENANT_ID)).thenReturn(Optional.empty());
        when(navigationMenuRepository.findGrantedMenusByUserId(propertyStaffUserId)).thenReturn(List.of());

        Map<String, Object> result = service().login(request);

        assertEquals("management-token", result.get("access_token"));
        @SuppressWarnings("unchecked")
        Map<String, Object> userInfo = (Map<String, Object>) result.get("user_info");
        assertEquals("SYS_USER", userInfo.get("identity_type"));
        assertEquals("PROPERTY_STAFF", userInfo.get("role_key"));
        verify(userContextLoader, never()).load(ACCOUNT_ID, UserContext.IdentityType.C_USER, UID, null);
        verify(authAccountRepository).updateLastActiveIdentity(
                ACCOUNT_ID, propertyStaffUserId, UserContext.IdentityType.SYS_USER.name());
    }

    @Test
    void updateWeChatProfile_saves_explicit_display_data_after_wechat_phone_authorization() {
        UserContext context = ownerContext();
        AuthAccountRepository.WeChatIdentitySnapshot identity =
                new AuthAccountRepository.WeChatIdentitySnapshot(
                        ACCOUNT_ID, APP_ID, SUBJECT_HASH, "盘古业主", "https://wx.qlogo.cn/avatar.png");
        when(jwtTokenProvider.validateToken("owner-token")).thenReturn(true);
        when(jwtTokenProvider.getAccountIdFromToken("owner-token")).thenReturn(ACCOUNT_ID);
        when(jwtTokenProvider.getIdentityTypeFromToken("owner-token")).thenReturn("C_USER");
        when(jwtTokenProvider.getActiveIdentityIdFromToken("owner-token")).thenReturn(UID);
        when(jwtTokenProvider.getTenantIdFromToken("owner-token")).thenReturn(TENANT_ID);
        when(userContextLoader.load(ACCOUNT_ID, UserContext.IdentityType.C_USER, UID, TENANT_ID)).thenReturn(context);
        when(weChatMiniProgramGateway.miniProgramAppId()).thenReturn(APP_ID);
        when(authAccountRepository.findWeChatIdentity(ACCOUNT_ID, APP_ID)).thenReturn(identity);
        when(authAccountRepository.updateWeChatProfile(
                ACCOUNT_ID, APP_ID, "盘古业主", "https://wx.qlogo.cn/avatar.png")).thenReturn(1);
        when(authAccountRepository.findIdentityByAccountId(ACCOUNT_ID)).thenReturn(
                new AuthAccountRepository.AccountIdentitySnapshot(ACCOUNT_ID, PHONE, null, 0, 1));
        when(communitySettingsRepository.findCommunity(TENANT_ID)).thenReturn(Optional.empty());

        Map<String, Object> result = service().updateWeChatProfile(
                "Bearer owner-token",
                new WeChatProfileRequest("盘古业主", "https://wx.qlogo.cn/avatar.png"));

        @SuppressWarnings("unchecked")
        Map<String, Object> userInfo = (Map<String, Object>) result.get("user_info");
        assertEquals("盘古业主", userInfo.get("wechat_nickname"));
        assertEquals("https://wx.qlogo.cn/avatar.png", userInfo.get("wechat_avatar_url"));
        verify(authAccountRepository).updateWeChatProfile(
                eq(ACCOUNT_ID), eq(APP_ID), eq("盘古业主"), eq("https://wx.qlogo.cn/avatar.png"));
    }

    private UserContext ownerContext() {
        return new UserContext(
                ACCOUNT_ID,
                UserContext.IdentityType.C_USER,
                UID,
                TENANT_ID,
                null,
                null,
                null,
                DataScopeType.OWNER_GROUP,
                AuthenticationLevel.L1,
                null,
                Set.of(),
                Set.of(),
                Set.of());
    }

    private UserContext ownerContextWithoutProperty() {
        return new UserContext(
                ACCOUNT_ID,
                UserContext.IdentityType.C_USER,
                UID,
                null,
                null,
                null,
                null,
                DataScopeType.OWNER_GROUP,
                AuthenticationLevel.L1,
                null,
                Set.of(),
                Set.of(),
                Set.of());
    }

    private UserContext managementContext(Long userId) {
        return new UserContext(
                ACCOUNT_ID,
                UserContext.IdentityType.SYS_USER,
                userId,
                TENANT_ID,
                930001L,
                UserContext.DeptCategory.S,
                4,
                DataScopeType.ORG_ONLY,
                AuthenticationLevel.L1,
                "PROPERTY_STAFF",
                Set.of("repair:workorder:read"),
                Set.of(),
                Set.of());
    }
}
