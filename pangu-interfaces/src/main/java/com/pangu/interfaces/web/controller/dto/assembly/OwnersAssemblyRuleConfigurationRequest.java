// 关联业务：接收从业主大会议事规则原件人工核对出的结构化配置，草稿阶段允许未完成。
package com.pangu.interfaces.web.controller.dto.assembly;

import com.pangu.domain.model.assembly.OwnersAssemblyRuleConfiguration;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/** 管理端录入的业主大会议事规则配置，不接受任何平台默认值。 */
public record OwnersAssemblyRuleConfigurationRequest(
        Set<OwnersAssemblyRuleConfiguration.MeetingForm> allowedMeetingForms,
        Integer planPublicityDays,
        Integer meetingNoticeDays,
        Set<OwnersAssemblyRuleConfiguration.DeliveryMethod> validDeliveryMethods,
        OwnersAssemblyRuleConfiguration.NonResponsePolicy nonResponsePolicy,
        OwnersAssemblyRuleConfiguration.ProxyVotingPolicy proxyVotingPolicy,
        OwnersAssemblyRuleConfiguration.VotingChannelPolicy votingChannelPolicy,
        Boolean onlineIdentityVerificationRequired,
        Boolean paperBallotSealRequired,
        OwnersAssemblyRuleConfiguration.DuplicateVotePolicy duplicateVotePolicy,
        Map<OwnersAssemblyRuleConfiguration.DecisionType, CountingRuleRequest> countingRules,
        Integer resultAnnouncementDays,
        Map<OwnersAssemblyRuleConfiguration.RuleConfigurationField, RuleSourceReferenceRequest> sourceClauseReferences
    ) {

    public OwnersAssemblyRuleConfiguration toDomain() {
        Map<OwnersAssemblyRuleConfiguration.DecisionType, OwnersAssemblyRuleConfiguration.CountingRule> rules =
                toCountingRules(countingRules);
        Map<OwnersAssemblyRuleConfiguration.RuleConfigurationField, OwnersAssemblyRuleConfiguration.RuleSourceReference> references =
                toSourceReferences(sourceClauseReferences);
        return new OwnersAssemblyRuleConfiguration(
                allowedMeetingForms,
                planPublicityDays,
                meetingNoticeDays,
                validDeliveryMethods,
                nonResponsePolicy,
                proxyVotingPolicy,
                votingChannelPolicy,
                onlineIdentityVerificationRequired,
                paperBallotSealRequired,
                duplicateVotePolicy,
                rules,
                resultAnnouncementDays,
                references);
    }

    private static Map<OwnersAssemblyRuleConfiguration.DecisionType, OwnersAssemblyRuleConfiguration.CountingRule>
    toCountingRules(Map<OwnersAssemblyRuleConfiguration.DecisionType, CountingRuleRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return null;
        }
        Map<OwnersAssemblyRuleConfiguration.DecisionType, OwnersAssemblyRuleConfiguration.CountingRule> rules =
                new EnumMap<>(OwnersAssemblyRuleConfiguration.DecisionType.class);
        requests.forEach((type, request) -> {
            if (type != null && request != null) {
                rules.put(type, request.toDomain());
            }
        });
        return rules;
    }

    private static Map<OwnersAssemblyRuleConfiguration.RuleConfigurationField,
            OwnersAssemblyRuleConfiguration.RuleSourceReference>
    toSourceReferences(Map<OwnersAssemblyRuleConfiguration.RuleConfigurationField, RuleSourceReferenceRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return null;
        }
        Map<OwnersAssemblyRuleConfiguration.RuleConfigurationField,
                OwnersAssemblyRuleConfiguration.RuleSourceReference> references =
                new EnumMap<>(OwnersAssemblyRuleConfiguration.RuleConfigurationField.class);
        requests.forEach((field, request) -> {
            if (field != null && request != null) {
                references.put(field, request.toDomain());
            }
        });
        return references;
    }

    public record CountingRuleRequest(
            BigDecimal participationOwnerRatio,
            BigDecimal participationAreaRatio,
            BigDecimal approvalOwnerRatio,
            BigDecimal approvalAreaRatio
    ) {
        OwnersAssemblyRuleConfiguration.CountingRule toDomain() {
            return new OwnersAssemblyRuleConfiguration.CountingRule(
                    participationOwnerRatio,
                    participationAreaRatio,
                    approvalOwnerRatio,
                    approvalAreaRatio);
        }
    }

    public record RuleSourceReferenceRequest(Integer pageNumber, String clause) {
        OwnersAssemblyRuleConfiguration.RuleSourceReference toDomain() {
            return new OwnersAssemblyRuleConfiguration.RuleSourceReference(pageNumber, clause);
        }
    }
}
