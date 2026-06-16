package com.pangu.bootstrap;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 智慧社区治理与业主自治数字化平台 - 后端服务启动类
 * 采用整洁架构设计，在此模块进行统一装配
 */
@SpringBootApplication(scanBasePackages = "com.pangu")
@MapperScan("com.pangu.infrastructure.persistence.mapper")
@EnableTransactionManagement
public class PanguApplication {

    public static void main(String[] args) {
        SpringApplication.run(PanguApplication.class, args);
    }
}
