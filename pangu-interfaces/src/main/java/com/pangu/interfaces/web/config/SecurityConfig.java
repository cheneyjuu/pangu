package com.pangu.interfaces.web.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 安全配置类
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

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
                .requestMatchers("/api/v1/auth/login").permitAll()
                // 其他接口暂时放行，由自定义租户拦截器解析 Token、后续阶段加入的具体认证过滤器做精细拦截
                .anyRequest().permitAll()
            );

        return http.build();
    }
}
