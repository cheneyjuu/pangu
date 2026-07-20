// 关联业务：登记维修事项纸质表决材料的逐户送达情况和项目内原始凭证。
package com.pangu.interfaces.web.controller.dto.repair;

import com.pangu.application.repair.RepairProjectVotingChannelService;
import com.pangu.domain.model.assembly.OwnersAssemblyRuleConfiguration;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record RecordRepairVotingDeliveryRequest(
        @NotNull Long opid,
        Long proxyAuthorizationId,
        @NotBlank String recipientName,
        @NotNull OwnersAssemblyRuleConfiguration.DeliveryMethod deliveryMethod,
        @NotNull Long evidenceAttachmentId,
        @NotNull Instant deliveredAt
) {
    public RepairProjectVotingChannelService.RecordDeliveryCommand toCommand() {
        return new RepairProjectVotingChannelService.RecordDeliveryCommand(
                opid, proxyAuthorizationId, recipientName, deliveryMethod, evidenceAttachmentId, deliveredAt);
    }
}
