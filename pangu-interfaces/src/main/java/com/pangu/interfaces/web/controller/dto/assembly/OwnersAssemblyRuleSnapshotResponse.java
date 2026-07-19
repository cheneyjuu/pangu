// 关联业务：向业主大会办理页提供已冻结议事规则的可读摘要，不暴露内部快照标识和文件存储地址。
package com.pangu.interfaces.web.controller.dto.assembly;

import com.pangu.domain.model.assembly.OwnersAssemblyRuleConfiguration;
import com.pangu.domain.model.assembly.OwnersAssemblyRuleSnapshot;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

/**
 * 已进入正式办理的业主大会所使用的议事规则摘要。
 *
 * <p>该响应只反映会次快照，不回读当前生效规则，避免管理员把后来版本误认为本次办理依据。
 */
public record OwnersAssemblyRuleSnapshotResponse(
        String ruleName,
        String ruleVersion,
        LocalDate effectiveDate,
        String sourceFileName,
        Integer planPublicityDays,
        Integer meetingNoticeDays,
        Integer resultAnnouncementDays,
        Set<OwnersAssemblyRuleConfiguration.DeliveryMethod> validDeliveryMethods,
        OwnersAssemblyRuleConfiguration.NonResponsePolicy nonResponsePolicy,
        OwnersAssemblyRuleConfiguration.ProxyVotingPolicy proxyVotingPolicy,
        OwnersAssemblyRuleConfiguration.VotingChannelPolicy votingChannelPolicy,
        Boolean paperBallotSealRequired,
        Map<OwnersAssemblyRuleConfiguration.DecisionType,
                OwnersAssemblyRuleConfiguration.CountingRule> countingRules
) {

    public static OwnersAssemblyRuleSnapshotResponse from(OwnersAssemblyRuleSnapshot snapshot) {
        if (snapshot == null || snapshot.configuration() == null) {
            return null;
        }
        OwnersAssemblyRuleConfiguration configuration = snapshot.configuration();
        return new OwnersAssemblyRuleSnapshotResponse(
                snapshot.ruleName(),
                snapshot.ruleVersion(),
                snapshot.effectiveDate(),
                snapshot.sourceFileName(),
                configuration.planPublicityDays(),
                configuration.meetingNoticeDays(),
                configuration.resultAnnouncementDays(),
                configuration.validDeliveryMethods(),
                configuration.nonResponsePolicy(),
                configuration.proxyVotingPolicy(),
                configuration.votingChannelPolicy(),
                configuration.paperBallotSealRequired(),
                configuration.countingRules());
    }
}
