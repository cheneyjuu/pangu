package com.pangu.bootstrap.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 自定义 Redisson 客户端构造。
 *
 * <p>本类替换 {@code redisson-spring-boot-starter} 的默认 auto-config，目的是绕开
 * 「password=空串时仍发送 AUTH 命令」的已知问题（导致无密码的 Redis 实例返回
 * "ERR AUTH called without any password configured" 让连接失败）。
 *
 * <p>规则：仅当 {@code spring.data.redis.password} 解析后非 null 且非空白时，才把
 * 它写入 Redisson 单节点配置；否则不设置 password，避免发送 AUTH。
 *
 * <p>本 Bean 在 {@code spring.data.redis.host} 配置为非空时启用；测试或离线环境可通过
 * 设置 {@code spring.data.redis.host=} 完全跳过 Redisson 装配，此时
 * {@code RedissonDistributedLockTemplate} 会降级为 JVM 本地锁。
 */
@Configuration
@ConditionalOnProperty(name = "spring.data.redis.host")
public class RedissonConfig {

    @Value("${spring.data.redis.host}")
    private String host;

    @Value("${spring.data.redis.port:6379}")
    private int port;

    @Value("${spring.data.redis.password:}")
    private String password;

    @Value("${spring.data.redis.database:0}")
    private int database;

    @Value("${spring.data.redis.timeout:5000ms}")
    private String timeout;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        SingleServerConfig single = config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setDatabase(database)
                .setConnectionMinimumIdleSize(1)
                .setConnectionPoolSize(8);

        if (password != null && !password.isBlank()) {
            single.setPassword(password);
        }
        return Redisson.create(config);
    }
}
