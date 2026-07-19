// 关联业务：把已启用议事规则映射为本次纸质、互联网或混合表决的可执行约束与计票策略。
package com.pangu.application.voting;

import com.pangu.domain.model.assembly.OwnersAssemblyRule;
import com.pangu.domain.model.assembly.OwnersAssemblyRuleConfiguration;
import com.pangu.domain.model.voting.SubjectType;
import com.pangu.domain.model.voting.VotingDecisionRule;
import com.pangu.domain.model.voting.VotingExecutionPackage;
import com.pangu.domain.model.voting.VotingSettlementPolicy;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 正式表决规则闸门。
 *
 * <p>业主大会和维修工程都必须先通过此处，避免不同业务适配层各自解释渠道、未反馈和计票阈值。
 */
@Component
public class FormalVotingRulePolicy {

    public ExecutableRule requireExecutable(OwnersAssemblyRule rule,
                                            VotingExecutionPackage.CollectionMode collectionMode,
                                            Instant preparedAt,
                                            Instant voteStartAt) {
        if (rule == null || rule.status() != OwnersAssemblyRule.Status.ACTIVE) {
            throw new UnsupportedRuleException("本小区尚无已启用的维修事项表决依据");
        }
        if (rule.effectiveDate() == null || rule.effectiveDate().isAfter(LocalDate.now())) {
            throw new UnsupportedRuleException("本小区当前表决依据尚未生效");
        }
        if (rule.configurationSha256() == null || !rule.configurationSha256().matches("[0-9a-fA-F]{64}")) {
            throw new UnsupportedRuleException("当前表决依据缺少可核验的结构化配置摘要");
        }
        OwnersAssemblyRuleConfiguration configuration = rule.configuration();
        if (configuration == null) {
            throw new UnsupportedRuleException("当前表决依据缺少已确认的办理规则");
        }
        requireSupportedSharedRules(configuration);
        requireMode(configuration, collectionMode);

        int publicityDays = requireNonNegative(configuration.planPublicityDays(), "方案公示期限");
        int noticeDays = requireNonNegative(configuration.meetingNoticeDays(), "会议通知期限");
        int preparationDays = Math.max(publicityDays, noticeDays);
        if (preparedAt == null || voteStartAt == null) {
            throw new UnsupportedRuleException("请填写表决准备时间和开始时间");
        }
        Instant earliestStartAt = preparedAt.plus(preparationDays, ChronoUnit.DAYS);
        if (voteStartAt.isBefore(earliestStartAt)) {
            throw new UnsupportedRuleException(
                    "表决开始时间应在方案公示和会议通知期限届满后，最早为 " + earliestStartAt);
        }
        return new ExecutableRule(rule, collectionMode, earliestStartAt);
    }

    public VotingSettlementPolicy settlementPolicy(OwnersAssemblyRule rule, SubjectType subjectType) {
        if (subjectType == null || subjectType == SubjectType.ELECTION) {
            throw new UnsupportedRuleException("当前规则结算只支持一般或重大共同决定事项");
        }
        OwnersAssemblyRuleConfiguration.DecisionType decisionType = subjectType == SubjectType.GENERAL
                ? OwnersAssemblyRuleConfiguration.DecisionType.GENERAL
                : OwnersAssemblyRuleConfiguration.DecisionType.MAJOR;
        OwnersAssemblyRuleConfiguration.CountingRule countingRule = rule.configuration()
                .countingRules().get(decisionType);
        if (countingRule == null) {
            throw new UnsupportedRuleException("当前表决依据缺少 " + decisionType + " 事项计票口径");
        }
        VotingDecisionRule decisionRule = countingRule.toVotingDecisionRule();
        try {
            decisionRule.requireExecutable();
        } catch (IllegalStateException ex) {
            throw new UnsupportedRuleException("当前表决依据的 " + decisionType + " 事项计票口径不完整", ex);
        }
        return new VotingSettlementPolicy(decisionRule, rule.ruleId(), rule.configurationSha256());
    }

    private void requireSupportedSharedRules(OwnersAssemblyRuleConfiguration configuration) {
        if (configuration.validDeliveryMethods().isEmpty()) {
            throw new UnsupportedRuleException("当前表决依据未明确有效送达方式");
        }
        if (configuration.nonResponsePolicy()
                != OwnersAssemblyRuleConfiguration.NonResponsePolicy.NOT_PARTICIPATED) {
            throw new UnsupportedRuleException("当前系统尚不能按本小区的未反馈表决票认定方式完成可审计计票");
        }
        if (configuration.proxyVotingPolicy()
                != OwnersAssemblyRuleConfiguration.ProxyVotingPolicy.NOT_ALLOWED) {
            throw new UnsupportedRuleException("当前系统尚未接入书面委托代理的核验和存证，不能发起正式表决");
        }
        if (configuration.countingRules().get(OwnersAssemblyRuleConfiguration.DecisionType.GENERAL) == null) {
            throw new UnsupportedRuleException("当前表决依据缺少一般共同决定事项的计票口径");
        }
        settlementPolicyFromConfiguration(configuration, OwnersAssemblyRuleConfiguration.DecisionType.GENERAL);
    }

    private void requireMode(OwnersAssemblyRuleConfiguration configuration,
                             VotingExecutionPackage.CollectionMode mode) {
        if (mode == null) {
            throw new UnsupportedRuleException("请选择本次表决办理方式");
        }
        switch (mode) {
            case PAPER -> {
                requirePair(configuration, OwnersAssemblyRuleConfiguration.MeetingForm.WRITTEN_CONSULTATION,
                        OwnersAssemblyRuleConfiguration.VotingChannelPolicy.PAPER_ONLY,
                        "当前表决依据未确认纸质书面征询");
                if (configuration.duplicateVotePolicy()
                        != OwnersAssemblyRuleConfiguration.DuplicateVotePolicy.NOT_APPLICABLE) {
                    throw new UnsupportedRuleException("纸质单渠道表决的重复投票规则应为不适用");
                }
            }
            case ONLINE_WITH_PAPER_ASSISTANCE -> {
                requirePair(configuration, OwnersAssemblyRuleConfiguration.MeetingForm.INTERNET,
                        OwnersAssemblyRuleConfiguration.VotingChannelPolicy.ONLINE_ONLY,
                        "当前表决依据未确认互联网表决并为有困难业主提供纸质协助");
                requireOnlineIdentity(configuration);
                if (configuration.duplicateVotePolicy()
                        != OwnersAssemblyRuleConfiguration.DuplicateVotePolicy.NOT_APPLICABLE) {
                    throw new UnsupportedRuleException("互联网表决的重复投票规则应为不适用");
                }
            }
            case PAPER_AND_ONLINE -> {
                requirePair(configuration, OwnersAssemblyRuleConfiguration.MeetingForm.ONLINE_AND_OFFLINE,
                        OwnersAssemblyRuleConfiguration.VotingChannelPolicy.PAPER_AND_ONLINE,
                        "当前表决依据未明确允许纸质与线上并行");
                requireOnlineIdentity(configuration);
                if (configuration.duplicateVotePolicy()
                        != OwnersAssemblyRuleConfiguration.DuplicateVotePolicy.FIRST_VALID_WINS) {
                    throw new UnsupportedRuleException("当前纸质与线上并行只支持首张有效票生效");
                }
            }
        }
    }

    private void requirePair(OwnersAssemblyRuleConfiguration configuration,
                             OwnersAssemblyRuleConfiguration.MeetingForm meetingForm,
                             OwnersAssemblyRuleConfiguration.VotingChannelPolicy channelPolicy,
                             String message) {
        if (!configuration.allowedMeetingForms().contains(meetingForm)
                || configuration.votingChannelPolicy() != channelPolicy) {
            throw new UnsupportedRuleException(message);
        }
    }

    private void requireOnlineIdentity(OwnersAssemblyRuleConfiguration configuration) {
        if (!Boolean.TRUE.equals(configuration.onlineIdentityVerificationRequired())) {
            throw new UnsupportedRuleException("互联网表决必须启用实名身份和房屋表决权核验");
        }
    }

    private void settlementPolicyFromConfiguration(
            OwnersAssemblyRuleConfiguration configuration,
            OwnersAssemblyRuleConfiguration.DecisionType decisionType) {
        try {
            configuration.countingRules().get(decisionType).toVotingDecisionRule().requireExecutable();
        } catch (IllegalStateException ex) {
            throw new UnsupportedRuleException("当前表决依据的一般事项计票口径不完整", ex);
        }
    }

    private int requireNonNegative(Integer value, String label) {
        if (value == null || value < 0) {
            throw new UnsupportedRuleException(label + "未在表决依据中明确");
        }
        return value;
    }

    public record ExecutableRule(
            OwnersAssemblyRule rule,
            VotingExecutionPackage.CollectionMode collectionMode,
            Instant earliestVoteStartAt
    ) {
    }

    public static class UnsupportedRuleException extends RuntimeException {
        public UnsupportedRuleException(String message) {
            super(message);
        }

        public UnsupportedRuleException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
