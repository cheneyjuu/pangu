package com.pangu.interfaces.security;

import com.pangu.domain.context.UserContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Spring 启动时把 {@link UserContextHolder} 注入 {@link SecurityUtils} 静态字段，
 * 让静态工具方法可以读取 ThreadLocal 上下文。
 */
@Component
public class SecurityUtilsHolderInjector {

    @Autowired
    private UserContextHolder userContextHolder;

    @PostConstruct
    public void init() {
        SecurityUtils.inject(userContextHolder);
    }
}
