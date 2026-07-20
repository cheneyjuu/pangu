// 关联业务：为表决等有法定办理期限的业务提供统一、可替换的可信时钟。
package com.pangu.bootstrap.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class BusinessTimeConfig {

    @Bean
    public Clock businessClock() {
        return Clock.systemUTC();
    }
}
