// 关联业务：以可回查原件条款的结构化配置表达业主大会会议、送达、表决和计票规则。
package com.pangu.domain.model.assembly;

import com.pangu.domain.model.voting.VotingDecisionRule;
import com.pangu.domain.model.voting.VotingThreshold;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 业主大会议事规则的可执行配置。
 *
 * <p>此对象允许在草稿阶段不完整；只有服务层在提交主任/副主任核对前校验全部字段。
 * 任何字段都不得由平台默认值补齐，生效版本只能来源于规则原件的人工核对结论。
 */
public record OwnersAssemblyRuleConfiguration(
        Set<MeetingForm> allowedMeetingForms,
        Integer planPublicityDays,
        Integer meetingNoticeDays,
        Set<DeliveryMethod> validDeliveryMethods,
        NonResponsePolicy nonResponsePolicy,
        ProxyVotingPolicy proxyVotingPolicy,
        VotingChannelPolicy votingChannelPolicy,
        Boolean onlineIdentityVerificationRequired,
        Boolean paperBallotSealRequired,
        DuplicateVotePolicy duplicateVotePolicy,
        Map<DecisionType, CountingRule> countingRules,
        Integer resultAnnouncementDays,
        Map<RuleConfigurationField, RuleSourceReference> sourceClauseReferences
    ) {

    public OwnersAssemblyRuleConfiguration {
        // EnumSet 保持自然顺序，确保同一份配置在审计摘要中不会因 Set 迭代顺序产生不同哈希。
        allowedMeetingForms = immutableEnumSet(allowedMeetingForms);
        validDeliveryMethods = immutableEnumSet(validDeliveryMethods);
        countingRules = countingRules == null ? Map.of() : Map.copyOf(countingRules);
        sourceClauseReferences = sourceClauseReferences == null ? Map.of() : Map.copyOf(sourceClauseReferences);
    }

    private static <E extends Enum<E>> Set<E> immutableEnumSet(Set<E> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(EnumSet.copyOf(values));
    }

    /** 规则允许的业主大会形式。 */
    public enum MeetingForm {
        WRITTEN_CONSULTATION,
        OFFLINE_MEETING
    }

    /** 规则认可的送达方式。 */
    public enum DeliveryMethod {
        DOOR_TO_DOOR,
        POSTAL,
        ELECTRONIC,
        PUBLIC_NOTICE_BOARD
    }

    /** 未表态业主在计票中的处理方式。 */
    public enum NonResponsePolicy {
        NOT_PARTICIPATED,
        FOLLOW_MAJORITY,
        ABSTAIN
    }

    /** 委托代理投票的约束。 */
    public enum ProxyVotingPolicy {
        NOT_ALLOWED,
        WRITTEN_AUTHORIZATION_REQUIRED
    }

    /** 纸质与线上表决的渠道约束。 */
    public enum VotingChannelPolicy {
        PAPER_ONLY,
        ONLINE_ONLY,
        PAPER_AND_ONLINE
    }

    /** 纸电并行时同一房屋重复投票的处理规则。 */
    public enum DuplicateVotePolicy {
        NOT_APPLICABLE,
        FIRST_VALID_WINS,
        PAPER_PREVAILS,
        ONLINE_PREVAILS
    }

    /** 与既有一般决议、重大决议一致的计票规则类别。 */
    public enum DecisionType {
        GENERAL,
        MAJOR
    }

    /**
     * 每类表决事项的参与与同意阈值，比例以分子、分母精确保存。
     *
     * <p>比例比较关系单独保存。旧版本若只有数值而没有比较关系，不得推定为“超过”或“达到”，
     * 只能重新核对后用于正式办理。
     */
    public record CountingRule(
            VotingThreshold participationOwnerThreshold,
            VotingThreshold participationAreaThreshold,
            VotingThreshold approvalOwnerThreshold,
            VotingThreshold approvalAreaThreshold
    ) {

        public VotingDecisionRule toVotingDecisionRule() {
            return new VotingDecisionRule(
                    participationOwnerThreshold,
                    participationAreaThreshold,
                    approvalOwnerThreshold,
                    approvalAreaThreshold);
        }
    }

    /** 指向规则原件页码和条款位置，供核对与争议复查使用。 */
    public record RuleSourceReference(Integer pageNumber, String clause) {
        public RuleSourceReference {
            clause = clause == null ? null : clause.trim();
        }
    }

    /** 每个结构化字段均应在提交确认时对应一条原件依据。 */
    public enum RuleConfigurationField {
        ALLOWED_MEETING_FORMS,
        PLAN_PUBLICITY_DAYS,
        MEETING_NOTICE_DAYS,
        VALID_DELIVERY_METHODS,
        NON_RESPONSE_POLICY,
        PROXY_VOTING_POLICY,
        VOTING_CHANNEL_POLICY,
        ONLINE_IDENTITY_VERIFICATION,
        PAPER_BALLOT_SEAL,
        DUPLICATE_VOTE_POLICY,
        COUNTING_RULES,
        RESULT_ANNOUNCEMENT_DAYS
    }
}
