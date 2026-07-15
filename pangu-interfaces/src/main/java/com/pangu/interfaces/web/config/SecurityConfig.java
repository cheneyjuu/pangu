// 关联业务：统一保护管理端和业主端接口，仅放行无需既有会话的微信小程序手机号授权入口。
package com.pangu.interfaces.web.config;

import com.pangu.interfaces.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 安全配置类
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 禁用 CSRF（因为采用 JWT 无状态会话，天然防御 CSRF 攻击）
            .csrf(AbstractHttpConfigurer::disable)
            // 采用 JWT 机制，关闭 HTTP 会话 Session 状态缓存
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 请求授权配置
            .authorizeHttpRequests(authorize -> authorize
                // 允许匿名公开登录接口
                .requestMatchers(
                        "/api/v1/auth/login",
                        "/api/v1/auth/wechat-phone-login",
                        "/api/v1/auth/refresh",
                        "/api/v1/supplier-activation/activate")
                .permitAll()
                // 其他接口均需要经过 JWT 身份认证
                .anyRequest().authenticated()
            )
            // 在 UsernamePasswordAuthenticationFilter 之前插入自定义的 JWT 认证过滤器
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
