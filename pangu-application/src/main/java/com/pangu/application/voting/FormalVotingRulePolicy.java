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
        EnumSet<VotingExecutionPackage.CollectionMode> supported =
                EnumSet.noneOf(VotingExecutionPackage.CollectionMode.class);
        if (blockingItems.isEmpty()) {
            for (VotingExecutionPackage.CollectionMode mode : VotingExecutionPackage.CollectionMode.values()) {
                try {
                    requireMode(configuration, mode);
                    supported.add(mode);
                } catch (UnsupportedRuleException ignored) {
                    // 某种方式不在本小区规则允许范围内，不等同于规则本身不可执行。
                }
            }
        }
        if (supported.isEmpty() && blockingItems.isEmpty()) {
            blockingItems.add(new ReadinessIssue(
                    "NO_SUPPORTED_COLLECTION_MODE", "当前表决依据没有系统可办理的表决方式"));
        }
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
        switch (mode) {
            case PAPER -> {
                requirePair(configuration, OwnersAssemblyRuleConfiguration.MeetingForm.WRITTEN_CONSULTATION,
                        OwnersAssemblyRuleConfiguration.VotingChannelPolicy.PAPER_ONLY,
                        "当前表决依据未确认纸质书面征询");
                requireDuplicatePolicy(configuration, false);
            }
            case ONLINE_WITH_PAPER_ASSISTANCE -> {
                requirePair(configuration, OwnersAssemblyRuleConfiguration.MeetingForm.INTERNET,
                        OwnersAssemblyRuleConfiguration.VotingChannelPolicy.ONLINE_ONLY,
                        "当前表决依据未确认互联网表决并为有困难业主提供纸质协助");
                requireOnlineIdentity(configuration);
                requireDuplicatePolicy(configuration, false);
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
                || !allowsChannel(configuration.votingChannelPolicy(), channelPolicy)) {
            throw new UnsupportedRuleException(message);
        }
    }

    /**
     * 规则可同时允许多种会议形式；PAPER_AND_ONLINE 表达规则已经覆盖两类渠道，
     * 单次表决仍只能从 allowedMeetingForms 中选择一种实际办理方式。
     */
    private boolean allowsChannel(
            OwnersAssemblyRuleConfiguration.VotingChannelPolicy configured,
            OwnersAssemblyRuleConfiguration.VotingChannelPolicy requested) {
        return configured == requested
                || configured == OwnersAssemblyRuleConfiguration.VotingChannelPolicy.PAPER_AND_ONLINE
                && (requested == OwnersAssemblyRuleConfiguration.VotingChannelPolicy.PAPER_ONLY
                || requested == OwnersAssemblyRuleConfiguration.VotingChannelPolicy.ONLINE_ONLY);
    }

    private void requireDuplicatePolicy(
            OwnersAssemblyRuleConfiguration configuration,
            boolean parallelCollection) {
        OwnersAssemblyRuleConfiguration.DuplicateVotePolicy expected =
                configuration.votingChannelPolicy()
                        == OwnersAssemblyRuleConfiguration.VotingChannelPolicy.PAPER_AND_ONLINE
                        ? OwnersAssemblyRuleConfiguration.DuplicateVotePolicy.FIRST_VALID_WINS
                        : OwnersAssemblyRuleConfiguration.DuplicateVotePolicy.NOT_APPLICABLE;
        if (configuration.duplicateVotePolicy() != expected) {
            throw new UnsupportedRuleException(parallelCollection
                    ? "当前纸质与线上并行只支持首张有效票生效"
                    : "当前规则的重复投票处理与所登记的渠道能力不一致");
        }
    }

    private void requireOnlineIdentity(OwnersAssemblyRuleConfiguration configuration) {
        if (!Boolean.TRUE.equals(configuration.onlineIdentityVerificationRequired())) {
            throw new UnsupportedRuleException("互联网表决必须启用实名身份和房屋表决权核验");
        }
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
