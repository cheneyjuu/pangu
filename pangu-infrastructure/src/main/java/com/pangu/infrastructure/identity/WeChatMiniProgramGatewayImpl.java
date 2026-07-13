// 关联业务：服务端安全对接微信小程序登录与手机号授权，避免 AppSecret、session_key 和原始 openid 进入客户端或业务层。
package com.pangu.infrastructure.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.domain.gateway.identity.WeChatMiniProgramGateway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;

/**
 * 微信小程序授权的真实 HTTP 适配器。
 *
 * <p>身份交换仅在服务端完成：先以 {@code code2Session} 获得当前小程序主体，
 * 再以 {@code getuserphonenumber} 取得用户显式授权的手机号。数据库只保存不可逆主体散列，
 * 从而避免在业务表持久化原始 openid。</p>
 */
@Component
public class WeChatMiniProgramGatewayImpl implements WeChatMiniProgramGateway {

    private static final String API_HOST = "https://api.weixin.qq.com";
    private static final Set<Integer> CONFIGURATION_ERROR_CODES = Set.of(40013, 40125);
    private static final Set<Integer> INVALID_AUTHORIZATION_CODES = Set.of(40029, 40163);
    private static final Set<Integer> INVALID_ACCESS_TOKEN_CODES = Set.of(40001, 40014, 42001);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Clock clock;
    private final String appId;
    private final String appSecret;
    private final Duration timeout;
    private volatile CachedAccessToken cachedAccessToken;

    @Autowired
    public WeChatMiniProgramGatewayImpl(
            ObjectMapper objectMapper,
            @Value("${platform.identity.wechat-mini-program.app-id:}") String appId,
            @Value("${platform.identity.wechat-mini-program.app-secret:}") String appSecret,
            @Value("${platform.identity.wechat-mini-program.request-timeout-millis:5000}") long timeoutMillis) {
        this(objectMapper, appId, appSecret, timeoutMillis, Clock.systemUTC(), null);
    }

    WeChatMiniProgramGatewayImpl(
            ObjectMapper objectMapper,
            String appId,
            String appSecret,
            long timeoutMillis,
            Clock clock,
            HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.appId = trimToEmpty(appId);
        this.appSecret = trimToEmpty(appSecret);
        this.timeout = Duration.ofMillis(Math.max(1000L, timeoutMillis));
        this.clock = clock;
        this.httpClient = httpClient == null
                ? HttpClient.newBuilder().connectTimeout(this.timeout).build()
                : httpClient;
    }

    @Override
    public String miniProgramAppId() {
        return appId;
    }

    @Override
    public WeChatPhoneIdentity exchangePhoneAuthorization(String loginCode, String phoneCode) {
        requireConfiguration();
        if (!StringUtils.hasText(loginCode) || !StringUtils.hasText(phoneCode)) {
            throw new WeChatAuthorizationException(FailureType.INVALID_AUTHORIZATION, "微信授权凭证不能为空");
        }

        WeChatSession session = exchangeSession(loginCode.trim());
        PhoneLookup phoneLookup = exchangePhoneNumber(phoneCode.trim(), currentAccessToken());
        if (phoneLookup.accessTokenExpired()) {
            invalidateAccessToken();
            phoneLookup = exchangePhoneNumber(phoneCode.trim(), currentAccessToken());
        }
        if (!StringUtils.hasText(phoneLookup.phoneNumber())) {
            throw new WeChatAuthorizationException(
                    FailureType.TEMPORARILY_UNAVAILABLE, "微信手机号授权服务暂不可用");
        }
        if (!phoneLookup.phoneNumber().matches("^1\\d{10}$")) {
            throw new WeChatAuthorizationException(
                    FailureType.INVALID_AUTHORIZATION, "微信授权手机号格式不正确");
        }
        return new WeChatPhoneIdentity(phoneLookup.phoneNumber(), subjectHash(session.openId()));
    }

    private WeChatSession exchangeSession(String loginCode) {
        URI uri = URI.create(API_HOST + "/sns/jscode2session?appid=" + encode(appId)
                + "&secret=" + encode(appSecret)
                + "&js_code=" + encode(loginCode)
                + "&grant_type=authorization_code");
        JsonNode response = get(uri);
        raiseForError(response);
        String openId = text(response, "openid");
        if (!StringUtils.hasText(openId)) {
            throw new WeChatAuthorizationException(
                    FailureType.TEMPORARILY_UNAVAILABLE, "微信登录服务未返回有效身份");
        }
        return new WeChatSession(openId);
    }

    private PhoneLookup exchangePhoneNumber(String phoneCode, String accessToken) {
        URI uri = URI.create(API_HOST + "/wxa/business/getuserphonenumber?access_token=" + encode(accessToken));
        JsonNode response = post(uri, "{\"code\":\"" + jsonEscape(phoneCode) + "\"}");
        int errorCode = response.path("errcode").asInt(0);
        if (INVALID_ACCESS_TOKEN_CODES.contains(errorCode)) {
            return PhoneLookup.expiredAccessToken();
        }
        raiseForError(response);
        String phoneNumber = text(response.path("phone_info"), "phoneNumber");
        return new PhoneLookup(phoneNumber, false);
    }

    private synchronized String currentAccessToken() {
        CachedAccessToken cached = cachedAccessToken;
        if (cached != null && cached.expiresAt().isAfter(clock.instant().plusSeconds(60))) {
            return cached.accessToken();
        }
        URI uri = URI.create(API_HOST + "/cgi-bin/token?grant_type=client_credential&appid="
                + encode(appId) + "&secret=" + encode(appSecret));
        JsonNode response = get(uri);
        raiseForError(response);
        String accessToken = text(response, "access_token");
        if (!StringUtils.hasText(accessToken)) {
            throw new WeChatAuthorizationException(
                    FailureType.TEMPORARILY_UNAVAILABLE, "微信授权服务未返回访问令牌");
        }
        long expiresIn = Math.max(120L, response.path("expires_in").asLong(7200L));
        long cacheSeconds = Math.max(60L, expiresIn - 300L);
        cachedAccessToken = new CachedAccessToken(accessToken, clock.instant().plusSeconds(cacheSeconds));
        return accessToken;
    }

    private void invalidateAccessToken() {
        cachedAccessToken = null;
    }

    private JsonNode get(URI uri) {
        return execute(HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .GET()
                .build());
    }

    private JsonNode post(URI uri, String body) {
        return execute(HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build());
    }

    private JsonNode execute(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new WeChatAuthorizationException(
                        FailureType.TEMPORARILY_UNAVAILABLE, "微信授权服务暂不可用");
            }
            return objectMapper.readTree(response.body() == null ? "{}" : response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WeChatAuthorizationException(
                    FailureType.TEMPORARILY_UNAVAILABLE, "微信授权服务暂不可用", e);
        } catch (IOException e) {
            throw new WeChatAuthorizationException(
                    FailureType.TEMPORARILY_UNAVAILABLE, "微信授权服务暂不可用", e);
        }
    }

    private void raiseForError(JsonNode response) {
        int errorCode = response.path("errcode").asInt(0);
        if (errorCode == 0) {
            return;
        }
        if (CONFIGURATION_ERROR_CODES.contains(errorCode)) {
            throw new WeChatAuthorizationException(
                    FailureType.CONFIGURATION, "微信小程序服务配置错误");
        }
        if (INVALID_AUTHORIZATION_CODES.contains(errorCode)) {
            throw new WeChatAuthorizationException(
                    FailureType.INVALID_AUTHORIZATION, "微信授权已失效，请重新授权");
        }
        throw new WeChatAuthorizationException(
                FailureType.TEMPORARILY_UNAVAILABLE, "微信授权服务暂不可用");
    }

    private void requireConfiguration() {
        if (!StringUtils.hasText(appId) || !StringUtils.hasText(appSecret)) {
            throw new WeChatAuthorizationException(
                    FailureType.CONFIGURATION, "微信小程序服务尚未配置");
        }
    }

    private String subjectHash(String openId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] value = digest.digest((appId + "\n" + openId).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(value);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", e);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() && StringUtils.hasText(value.asText()) ? value.asText().trim() : null;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private record WeChatSession(String openId) {
    }

    private record CachedAccessToken(String accessToken, Instant expiresAt) {
    }

    private record PhoneLookup(String phoneNumber, boolean accessTokenExpired) {
        private static PhoneLookup expiredAccessToken() {
            return new PhoneLookup(null, true);
        }
    }
}
