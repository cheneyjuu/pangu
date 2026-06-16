package com.pangu.infrastructure.config;

import com.pangu.infrastructure.persistence.handler.Sm4EncryptTypeHandler;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * 基础设施层统一配置类
 */
@Configuration
public class InfrastructureConfig {

    @Value("${platform.security.sm4-key-hex}")
    private String sm4KeyHex;

    @PostConstruct
    public void init() {
        // 将解密密钥注入到透明加解密类型处理器中
        Sm4EncryptTypeHandler.setSm4KeyHex(sm4KeyHex);
    }
}
