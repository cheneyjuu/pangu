package com.pangu.bootstrap.config;

import com.pangu.domain.policy.AbacPolicyEngine;
import com.pangu.domain.policy.impl.DefaultAbacPolicyEngine;
import com.pangu.domain.model.voting.ElectionVotingEngine;
import com.pangu.domain.model.voting.GeneralDecisionEngine;
import com.pangu.domain.model.voting.MajorDecisionEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 领域服务统一装配配置类
 * 负责在启动层以无侵入的 @Bean 形式，向 Spring 容器装配 domain 层的纯净服务实例
 */
@Configuration
public class DomainConfig {

    @Bean
    public AbacPolicyEngine abacPolicyEngine() {
        return new DefaultAbacPolicyEngine();
    }

    @Bean
    public GeneralDecisionEngine generalDecisionEngine() {
        return new GeneralDecisionEngine();
    }

    @Bean
    public MajorDecisionEngine majorDecisionEngine() {
        return new MajorDecisionEngine();
    }

    @Bean
    public ElectionVotingEngine electionVotingEngine() {
        return new ElectionVotingEngine();
    }
}
