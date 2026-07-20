// 关联业务：把已启用议事规则映射为本次纸质、互联网或混合表决的可执行约束与计票策略。
package com.pangu.application.voting;

import com.pangu.domain.model.assembly.OwnersAssemblyRule;
import com.pangu.domain.model.assembly.OwnersAssemblyRuleConfiguration;
import com.pangu.domain.model.voting.SubjectType;
import com.pangu.domain.model.voting.VotingDecisionRule;
import com.pangu.domain.model.voting.VotingExecutionPackage;
import com.pangu.domain.model.voting.VotingSettlementPolicy;
import com.pangu.domain.model.voting.VotingNonResponsePolicy;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
        BaseConstraints base = requireBaseConstraints(rule, preparedAt);
        requireMode(base.configuration(), collectionMode);
        if (voteStartAt == null) {
            throw new UnsupportedRuleException("请填写表决开始时间");
        }
        if (voteStartAt.isBefore(base.earliestVoteStartAt())) {
            throw new UnsupportedRuleException(
                    "表决开始时间应在方案公示和会议通知期限届满后，最早为 "
                            + base.earliestVoteStartAt());
        }
        return new ExecutableRule(rule, collectionMode, base.earliestVoteStartAt());
    }

    /**
     * 返回当前有效规则真正可办理的单次方式，供管理端展示选择范围；客户端不能自行解释规则组合。
     */
    public PreparationOptions preparationOptions(OwnersAssemblyRule rule, Instant preparedAt) {
        List<ReadinessIssue> blockingItems = new ArrayList<>();
        OwnersAssemblyRuleConfiguration configuration = assessBaseConstraints(
                rule, preparedAt, blockingItems);
        Instant earliestVoteStartAt = earliestVoteStartAt(configuration, preparedAt);
        if (blockingItems.isEmpty()) {
            ChannelCapability channelCapability = assessChannelCapability(configuration);
            blockingItems.addAll(channelCapability.blockingItems());
            if (channelCapability.allowedModes().isEmpty() && channelCapability.blockingItems().isEmpty()) {
                blockingItems.add(new ReadinessIssue(
                        "NO_SUPPORTED_COLLECTION_MODE", "当前表决依据没有系统可办理的表决方式"));
            }
        }
        Set<VotingExecutionPackage.CollectionMode> supported = blockingItems.isEmpty()
                ? assessChannelCapability(configuration).allowedModes()
                : Set.of();
        return new PreparationOptions(
                blockingItems.isEmpty(),
                List.copyOf(blockingItems),
                Collections.unmodifiableSet(supported),
                earliestVoteStartAt,
                configuration == null ? Set.of() : configuration.validDeliveryMethods(),
                configuration == null ? null : configuration.paperBallotSealRequired(),
                configuration == null ? null : configuration.proxyVotingPolicy());
    }

    private BaseConstraints requireBaseConstraints(OwnersAssemblyRule rule, Instant preparedAt) {
        PreparationOptions options = preparationOptions(rule, preparedAt);
        if (!options.ready()) {
            throw new UnsupportedRuleException("当前不能准备正式表决：" + options.blockingItems().stream()
                    .map(ReadinessIssue::message)
                    .collect(Collectors.joining("；")));
        }
        return new BaseConstraints(rule.configuration(), options.earliestVoteStartAt());
    }

    /**
     * 一次性检查共享规则问题，避免修复一个字段后才在业务办理页暴露下一个技术闸门。
     */
    private OwnersAssemblyRuleConfiguration assessBaseConstraints(
            OwnersAssemblyRule rule, Instant preparedAt, List<ReadinessIssue> issues) {
        if (rule == null || rule.status() != OwnersAssemblyRule.Status.ACTIVE) {
            issues.add(new ReadinessIssue("NO_ACTIVE_RULE", "本小区尚无已启用的维修事项表决依据"));
            return null;
        }
        if (rule.effectiveDate() == null || rule.effectiveDate().isAfter(LocalDate.now())) {
            issues.add(new ReadinessIssue("RULE_NOT_EFFECTIVE", "本小区当前表决依据尚未生效"));
        }
        if (rule.configurationSha256() == null || !rule.configurationSha256().matches("[0-9a-fA-F]{64}")) {
            issues.add(new ReadinessIssue(
                    "MISSING_CONFIGURATION_HASH", "当前表决依据缺少可核验的结构化配置摘要"));
        }
        OwnersAssemblyRuleConfiguration configuration = rule.configuration();
        if (configuration == null) {
            issues.add(new ReadinessIssue("MISSING_CONFIGURATION", "当前表决依据缺少已确认的办理规则"));
            return null;
        }
        assessSharedRules(configuration, issues);
        if (preparedAt == null) {
            issues.add(new ReadinessIssue("MISSING_PREPARED_AT", "缺少本次表决准备时间"));
        }
        return configuration;
    }

    private Instant earliestVoteStartAt(
            OwnersAssemblyRuleConfiguration configuration,
            Instant preparedAt) {
        if (configuration == null || preparedAt == null
                || configuration.planPublicityDays() == null || configuration.planPublicityDays() < 0
                || configuration.meetingNoticeDays() == null || configuration.meetingNoticeDays() < 0) {
            return null;
        }
        int preparationDays = Math.max(
                configuration.planPublicityDays(), configuration.meetingNoticeDays());
        return preparedAt.plus(preparationDays, ChronoUnit.DAYS);
    }

    private void assessSharedRules(
            OwnersAssemblyRuleConfiguration configuration, List<ReadinessIssue> issues) {
        assessNonNegative(configuration.planPublicityDays(),
                "MISSING_PUBLICITY_PERIOD", "INVALID_PUBLICITY_PERIOD", "方案公示期限", issues);
        assessNonNegative(configuration.meetingNoticeDays(),
                "MISSING_NOTICE_PERIOD", "INVALID_NOTICE_PERIOD", "会议通知期限", issues);
        if (configuration.validDeliveryMethods().isEmpty()) {
            issues.add(new ReadinessIssue("MISSING_DELIVERY_METHOD", "当前表决依据未明确有效送达方式"));
        }
        if (configuration.nonResponsePolicy() == null) {
            issues.add(new ReadinessIssue(
                    "MISSING_NON_RESPONSE_POLICY", "当前表决依据未明确未反馈表决票的认定方式"));
        }
        if (configuration.proxyVotingPolicy() == null) {
            issues.add(new ReadinessIssue("MISSING_PROXY_POLICY", "当前表决依据未明确是否允许书面委托"));
        }
        for (OwnersAssemblyRuleConfiguration.DecisionType decisionType
                : OwnersAssemblyRuleConfiguration.DecisionType.values()) {
            OwnersAssemblyRuleConfiguration.CountingRule countingRule =
                    configuration.countingRules().get(decisionType);
            String prefix = decisionType == OwnersAssemblyRuleConfiguration.DecisionType.GENERAL
                    ? "GENERAL" : "MAJOR";
            if (countingRule == null) {
                issues.add(new ReadinessIssue(
                        "MISSING_" + prefix + "_COUNTING_RULE",
                        "当前表决依据缺少 " + decisionType + " 事项的计票口径"));
                continue;
            }
            try {
                countingRule.toVotingDecisionRule().requireExecutable();
            } catch (IllegalStateException ex) {
                issues.add(new ReadinessIssue(
                        "INVALID_" + prefix + "_COUNTING_RULE",
                        "当前表决依据的 " + decisionType + " 事项计票口径不完整"));
            }
        }
    }

    private void assessNonNegative(
            Integer value,
            String missingCode,
            String invalidCode,
            String label,
            List<ReadinessIssue> issues) {
        if (value == null) {
            issues.add(new ReadinessIssue(missingCode, label + "未在表决依据中明确"));
        } else if (value < 0) {
            issues.add(new ReadinessIssue(invalidCode, label + "不能小于 0 天"));
        }
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
        return new VotingSettlementPolicy(
                decisionRule,
                rule.ruleId(),
                rule.configurationSha256(),
                VotingNonResponsePolicy.valueOf(rule.configuration().nonResponsePolicy().name()),
                rule.configuration().validDeliveryMethods().stream()
                        .map(Enum::name)
                        .collect(Collectors.toUnmodifiableSet()));
    }

    private void requireMode(OwnersAssemblyRuleConfiguration configuration,
                             VotingExecutionPackage.CollectionMode mode) {
        if (mode == null) {
            throw new UnsupportedRuleException("请选择本次表决办理方式");
        }
        ChannelCapability capability = assessChannelCapability(configuration);
        if (!capability.blockingItems().isEmpty()) {
            throw new UnsupportedRuleException(capability.blockingItems().stream()
                    .map(ReadinessIssue::message)
                    .collect(Collectors.joining("；")));
        }
        if (!capability.allowedModes().contains(mode)) {
            throw new UnsupportedRuleException(switch (mode) {
                case PAPER -> "当前表决依据未确认纸质书面征询";
                case ONLINE_WITH_PAPER_ASSISTANCE ->
                        "当前表决依据未确认互联网表决并为有困难业主提供纸质协助";
                case PAPER_AND_ONLINE -> "当前表决依据未明确允许纸质与线上并行";
            });
        }
    }

    /**
     * 同一份能力判断同时供规则启用、表决准备和冻结快照复核使用，避免登记成功后才出现新的渠道闸门。
     */
    public ChannelCapability assessChannelCapability(OwnersAssemblyRuleConfiguration configuration) {
        if (configuration == null) {
            return new ChannelCapability(Set.of(), List.of(
                    new ReadinessIssue("MISSING_CHANNEL_CONFIGURATION", "当前表决依据缺少办理方式配置")));
        }
        List<ReadinessIssue> issues = new ArrayList<>();
        EnumSet<VotingExecutionPackage.CollectionMode> allowedModes =
                EnumSet.noneOf(VotingExecutionPackage.CollectionMode.class);
        Set<OwnersAssemblyRuleConfiguration.MeetingForm> forms = configuration.allowedMeetingForms();
        OwnersAssemblyRuleConfiguration.VotingChannelPolicy channelPolicy =
                configuration.votingChannelPolicy();
        if (channelPolicy == null) {
            issues.add(new ReadinessIssue("MISSING_CHANNEL_POLICY", "当前表决依据未明确收票渠道"));
            return new ChannelCapability(Set.of(), issues);
        }
        boolean written = forms.contains(OwnersAssemblyRuleConfiguration.MeetingForm.WRITTEN_CONSULTATION);
        boolean online = forms.contains(OwnersAssemblyRuleConfiguration.MeetingForm.INTERNET);
        boolean parallel = forms.contains(OwnersAssemblyRuleConfiguration.MeetingForm.ONLINE_AND_OFFLINE);
        if (written && channelPolicy == OwnersAssemblyRuleConfiguration.VotingChannelPolicy.ONLINE_ONLY
                || online && channelPolicy == OwnersAssemblyRuleConfiguration.VotingChannelPolicy.PAPER_ONLY
                || parallel && channelPolicy != OwnersAssemblyRuleConfiguration.VotingChannelPolicy.PAPER_AND_ONLINE
                || written && online
                && channelPolicy != OwnersAssemblyRuleConfiguration.VotingChannelPolicy.PAPER_AND_ONLINE) {
            issues.add(new ReadinessIssue(
                    "CONFLICTING_COLLECTION_CHANNELS", "规则允许的表决形式与收票渠道约定相互矛盾"));
        }
        if ((online || parallel
                || channelPolicy != OwnersAssemblyRuleConfiguration.VotingChannelPolicy.PAPER_ONLY)
                && !Boolean.TRUE.equals(configuration.onlineIdentityVerificationRequired())) {
            issues.add(new ReadinessIssue(
                    "MISSING_ONLINE_IDENTITY_VERIFICATION", "线上表决必须明确实名身份和房屋表决权核验"));
        }
        boolean hybridChannel = channelPolicy
                == OwnersAssemblyRuleConfiguration.VotingChannelPolicy.PAPER_AND_ONLINE;
        boolean supportedDuplicatePolicy = configuration.duplicateVotePolicy()
                == OwnersAssemblyRuleConfiguration.DuplicateVotePolicy.FIRST_VALID_WINS
                || configuration.duplicateVotePolicy()
                == OwnersAssemblyRuleConfiguration.DuplicateVotePolicy.ONLINE_PREVAILS
                || configuration.duplicateVotePolicy()
                == OwnersAssemblyRuleConfiguration.DuplicateVotePolicy.PAPER_PREVAILS;
        if (hybridChannel && !supportedDuplicatePolicy) {
            issues.add(new ReadinessIssue(
                    "MISSING_DUPLICATE_BALLOT_POLICY", "纸质与线上并行时必须明确重复票处理方式"));
        } else if (!hybridChannel && configuration.duplicateVotePolicy()
                != OwnersAssemblyRuleConfiguration.DuplicateVotePolicy.NOT_APPLICABLE) {
            issues.add(new ReadinessIssue(
                    "UNEXPECTED_DUPLICATE_BALLOT_POLICY", "单一收票渠道不应设置跨渠道重复票处理方式"));
        }
        if (!issues.isEmpty()) {
            return new ChannelCapability(Set.of(), issues);
        }
        if (written && channelPolicy != OwnersAssemblyRuleConfiguration.VotingChannelPolicy.ONLINE_ONLY) {
            allowedModes.add(VotingExecutionPackage.CollectionMode.PAPER);
        }
        if (online && channelPolicy != OwnersAssemblyRuleConfiguration.VotingChannelPolicy.PAPER_ONLY) {
            allowedModes.add(VotingExecutionPackage.CollectionMode.ONLINE_WITH_PAPER_ASSISTANCE);
        }
        if (parallel && hybridChannel) {
            allowedModes.add(VotingExecutionPackage.CollectionMode.PAPER_AND_ONLINE);
        }
        return new ChannelCapability(Collections.unmodifiableSet(allowedModes), List.of());
    }

    public record ExecutableRule(
            OwnersAssemblyRule rule,
            VotingExecutionPackage.CollectionMode collectionMode,
            Instant earliestVoteStartAt
    ) {
    }

    public record PreparationOptions(
            boolean ready,
            List<ReadinessIssue> blockingItems,
            Set<VotingExecutionPackage.CollectionMode> allowedModes,
            Instant earliestVoteStartAt,
            Set<OwnersAssemblyRuleConfiguration.DeliveryMethod> validDeliveryMethods,
            Boolean paperBallotSealRequired,
            OwnersAssemblyRuleConfiguration.ProxyVotingPolicy proxyVotingPolicy
    ) {
        public PreparationOptions {
            blockingItems = blockingItems == null ? List.of() : List.copyOf(blockingItems);
            allowedModes = allowedModes == null ? Set.of() : Set.copyOf(allowedModes);
            validDeliveryMethods = validDeliveryMethods == null ? Set.of() : Set.copyOf(validDeliveryMethods);
        }
    }

    /** 规则配置能够支持的单次实际办理方式及需要一次性补齐的问题。 */
    public record ChannelCapability(
            Set<VotingExecutionPackage.CollectionMode> allowedModes,
            List<ReadinessIssue> blockingItems
    ) {
        public ChannelCapability {
            allowedModes = allowedModes == null ? Set.of() : Set.copyOf(allowedModes);
            blockingItems = blockingItems == null ? List.of() : List.copyOf(blockingItems);
        }
    }

    /** 管理端可直接展示的办理前置问题，不暴露异常或内部状态机。 */
    public record ReadinessIssue(String code, String message) {
    }

    private record BaseConstraints(
            OwnersAssemblyRuleConfiguration configuration,
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
