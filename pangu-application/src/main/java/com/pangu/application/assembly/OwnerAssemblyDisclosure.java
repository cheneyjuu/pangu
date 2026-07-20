// 关联业务：定义 C 端业主可查看的业主大会公示、办理方式和本人参与状态，不含任何表决选择。
package com.pangu.application.assembly;

import com.pangu.application.voting.VotingDecisionResultProjector;
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
        String votingInstruction,
        Participation participation
) {

    public enum Stage {
        PUBLIC_NOTICE,
        PAPER_VOTING,
        ONLINE_VOTING,
        PAPER_AND_ONLINE_VOTING,
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
            String status,
            Result result
    ) {
    }

    /** 结算后向有权业主公开的结构化结果，不包含任何逐户票面。 */
    public record Result(
            boolean quorumSatisfied,
            boolean passed,
            java.math.BigDecimal totalArea,
            long totalOwnerCount,
            java.math.BigDecimal participatingArea,
            long participatingOwnerCount,
            java.math.BigDecimal supportArea,
            Long supportOwnerCount,
            java.math.BigDecimal againstArea,
            Long againstOwnerCount,
            java.math.BigDecimal abstainArea,
            Long abstainOwnerCount,
            VotingDecisionResultProjector.NonResponseSummary nonResponse
    ) {
    }

    /** 冻结规则中与本次实际办理方式有关的可读口径。 */
    public record Rule(
            String meetingForm,
            String votingChannel,
            Integer planPublicityDays,
            Integer meetingNoticeDays,
            Integer resultAnnouncementDays,
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
            String packageHash,
            PaperProgress paper,
            List<PropertyProgress> properties
    ) {
        public Participation {
            properties = properties == null ? List.of() : List.copyOf(properties);
        }
    }

    /** 纸质办理只返回本人汇总进度，不返回票面选择、原件或其他业主信息。 */
    public record PaperProgress(
            String deliveryStatus,
            String ballotStatus
    ) {
    }

    /** 本人每个符合资格专有部分的办理状态；回执不包含具体选择。 */
    public record PropertyProgress(
            Long opid,
            Long buildingId,
            Long roomId,
            String buildingName,
            String unitName,
            String roomName,
            boolean onlineAcknowledged,
            boolean submitted,
            Long receiptId,
            String confirmationHash,
            Instant submittedAt,
            Long paperAssistanceRequestId,
            String paperAssistanceStatus,
            boolean participated,
            String participationChannel,
            String paperDeliveryStatus,
            String paperBallotStatus
    ) {
    }
}
