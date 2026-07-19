// 关联业务：验证一份实际允许多种表决形式的议事规则，可供单次会议选择纸质、互联网或并行方式。
package com.pangu.bootstrap.voting;

import com.pangu.application.voting.FormalVotingRulePolicy;
import com.pangu.domain.model.assembly.OwnersAssemblyRule;
import com.pangu.domain.model.assembly.OwnersAssemblyRuleConfiguration;
import com.pangu.domain.model.voting.VotingExecutionPackage;
import com.pangu.domain.model.voting.VotingThreshold;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FormalVotingRulePolicyTest {

    private final FormalVotingRulePolicy policy = new FormalVotingRulePolicy();

    @Test
    void mixedCapableRuleAllowsEachRecordedMeetingFormAsOneActualMode() {
        OwnersAssemblyRule rule = activeRule(
                Set.of(
                        OwnersAssemblyRuleConfiguration.MeetingForm.WRITTEN_CONSULTATION,
                        OwnersAssemblyRuleConfiguration.MeetingForm.INTERNET,
                        OwnersAssemblyRuleConfiguration.MeetingForm.ONLINE_AND_OFFLINE),
                OwnersAssemblyRuleConfiguration.VotingChannelPolicy.PAPER_AND_ONLINE,
                OwnersAssemblyRuleConfiguration.DuplicateVotePolicy.FIRST_VALID_WINS);
        Instant preparedAt = Instant.now();

        assertDoesNotThrow(() -> policy.requireExecutable(
                rule, VotingExecutionPackage.CollectionMode.PAPER, preparedAt, preparedAt));
        assertDoesNotThrow(() -> policy.requireExecutable(
                rule, VotingExecutionPackage.CollectionMode.ONLINE_WITH_PAPER_ASSISTANCE,
                preparedAt, preparedAt));
        assertDoesNotThrow(() -> policy.requireExecutable(
                rule, VotingExecutionPackage.CollectionMode.PAPER_AND_ONLINE, preparedAt, preparedAt));
    }

    @Test
    void selectedModeMustStillAppearInRecordedMeetingForms() {
        OwnersAssemblyRule rule = activeRule(
                Set.of(OwnersAssemblyRuleConfiguration.MeetingForm.ONLINE_AND_OFFLINE),
                OwnersAssemblyRuleConfiguration.VotingChannelPolicy.PAPER_AND_ONLINE,
                OwnersAssemblyRuleConfiguration.DuplicateVotePolicy.FIRST_VALID_WINS);

        assertThrows(FormalVotingRulePolicy.UnsupportedRuleException.class, () -> policy.requireExecutable(
                rule, VotingExecutionPackage.CollectionMode.PAPER, Instant.now(), Instant.now()));
    }

    @Test
    void unsupportedNonResponsePolicyNamesTheRecordedRuleInsteadOfShowingTechnicalLanguage() {
        OwnersAssemblyRule rule = activeRule(
                Set.of(OwnersAssemblyRuleConfiguration.MeetingForm.INTERNET),
                OwnersAssemblyRuleConfiguration.VotingChannelPolicy.ONLINE_ONLY,
                OwnersAssemblyRuleConfiguration.DuplicateVotePolicy.NOT_APPLICABLE,
                OwnersAssemblyRuleConfiguration.NonResponsePolicy.FOLLOW_MAJORITY);

        var exception = assertThrows(FormalVotingRulePolicy.UnsupportedRuleException.class,
                () -> policy.preparationOptions(rule, Instant.now()));

        assertTrue(exception.getMessage().contains("未反馈按多数意见认定"));
        assertTrue(exception.getMessage().contains("请业委会根据议事规则原件核对"));
    }

    private OwnersAssemblyRule activeRule(
            Set<OwnersAssemblyRuleConfiguration.MeetingForm> forms,
            OwnersAssemblyRuleConfiguration.VotingChannelPolicy channelPolicy,
            OwnersAssemblyRuleConfiguration.DuplicateVotePolicy duplicateVotePolicy) {
        return activeRule(forms, channelPolicy, duplicateVotePolicy,
                OwnersAssemblyRuleConfiguration.NonResponsePolicy.NOT_PARTICIPATED);
    }

    private OwnersAssemblyRule activeRule(
            Set<OwnersAssemblyRuleConfiguration.MeetingForm> forms,
            OwnersAssemblyRuleConfiguration.VotingChannelPolicy channelPolicy,
            OwnersAssemblyRuleConfiguration.DuplicateVotePolicy duplicateVotePolicy,
            OwnersAssemblyRuleConfiguration.NonResponsePolicy nonResponsePolicy) {
        VotingThreshold threshold = new VotingThreshold(1, 2, VotingThreshold.Comparison.GREATER_THAN);
        OwnersAssemblyRuleConfiguration.CountingRule countingRule =
                new OwnersAssemblyRuleConfiguration.CountingRule(
                        threshold, threshold, threshold, threshold);
        OwnersAssemblyRuleConfiguration configuration = new OwnersAssemblyRuleConfiguration(
                forms,
                0,
                0,
                Set.of(OwnersAssemblyRuleConfiguration.DeliveryMethod.DOOR_TO_DOOR),
                nonResponsePolicy,
                OwnersAssemblyRuleConfiguration.ProxyVotingPolicy.NOT_ALLOWED,
                channelPolicy,
                true,
                true,
                duplicateVotePolicy,
                Map.of(
                        OwnersAssemblyRuleConfiguration.DecisionType.GENERAL, countingRule,
                        OwnersAssemblyRuleConfiguration.DecisionType.MAJOR, countingRule),
                3,
                Map.of());
        OwnersAssemblyRule rule = mock(OwnersAssemblyRule.class);
        when(rule.status()).thenReturn(OwnersAssemblyRule.Status.ACTIVE);
        when(rule.effectiveDate()).thenReturn(LocalDate.now());
        when(rule.configurationSha256()).thenReturn("a".repeat(64));
        when(rule.configuration()).thenReturn(configuration);
        return rule;
    }
}
