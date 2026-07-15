// 关联业务：微信小程序手机号授权发生在建立租户会话之前，拦截器不应要求尚不存在的租户上下文。
package com.pangu.interfaces.web.config;

import com.pangu.interfaces.web.interceptor.TenantContextInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 基础配置类
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private TenantContextInterceptor tenantContextInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册多租户上下文提取过滤器
        registry.addInterceptor(tenantContextInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/api/v1/auth/login",
                        "/api/v1/auth/wechat-phone-login",
                        "/api/v1/auth/refresh");
    }
}
