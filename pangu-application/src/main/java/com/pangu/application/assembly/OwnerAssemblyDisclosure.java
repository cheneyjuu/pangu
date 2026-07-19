// 关联业务：定义 C 端业主可查看的业主大会公示、纸质投票规则和本人参与状态，不含任何表决选择。
package com.pangu.application.assembly;

import com.pangu.domain.model.assembly.OwnersAssemblyRuleConfiguration;
import com.pangu.domain.model.voting.SubjectType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 已发布业主大会对当前业主的受控披露投影。
 *
 * <p>该对象刻意不包含票面选择、其他业主参与记录、送达凭证和内部对象存储地址；纸质选票由
 * 物业核验录入，C 端只获知本人是否已经形成有效的系统参与记录。
 */
public record OwnerAssemblyDisclosure(
        Long packageId,
        String assemblyTitle,
        Stage stage,
        PublicNotice publicNotice,
        List<Material> planAttachments,
        Material paperBallotTemplate,
        List<Subject> subjects,
        Rule rule,
        Instant publicNoticeStartAt,
        Instant publicNoticeEndAt,
        Instant voteStartAt,
        Instant voteEndAt,
        String paperVotingInstruction,
        Participation participation
) {

    public enum Stage {
        PUBLIC_NOTICE,
        PAPER_VOTING,
        RESULT_FORMED
    }

    public record PublicNotice(
            Long materialId,
            String fileName,
            String contentType,
            Long fileSize,
            String contentSha256,
            Instant publishedAt
    ) {
    }

    /** 已锁定公开材料元数据；原文件必须另取受控下载票据。 */
    public record Material(
            Long materialId,
            String fileName,
            String contentType,
            Long fileSize,
            String contentSha256
    ) {
    }

    /** 正式表决事项不返回内部拟定人、范围 ID 或任何计票原始数据。 */
    public record Subject(
            Long subjectId,
            SubjectType subjectType,
            String title,
            String content,
            String status
    ) {
    }

    /** 冻结规则中与当前纸质书面征询有关的可读口径。 */
    public record Rule(
            String meetingForm,
            String votingChannel,
            Integer planPublicityDays,
            Set<OwnersAssemblyRuleConfiguration.DeliveryMethod> validDeliveryMethods,
            OwnersAssemblyRuleConfiguration.NonResponsePolicy nonResponsePolicy,
            Boolean paperBallotSealRequired,
            Map<OwnersAssemblyRuleConfiguration.DecisionType,
                    OwnersAssemblyRuleConfiguration.CountingRule> countingRules
    ) {
    }

    /** 只描述当前业主自身的资格和有效回收录票状态。 */
    public record Participation(
            boolean eligible,
            boolean participated,
            Instant participatedAt,
            int eligiblePropertyCount,
            int expectedDecisionCount,
            int countedDecisionCount,
            PaperProgress paper
    ) {
    }

    /** 纸质办理只返回本人汇总进度，不返回票面选择、原件或其他业主信息。 */
    public record PaperProgress(
            String deliveryStatus,
            String ballotStatus
    ) {
    }
}
