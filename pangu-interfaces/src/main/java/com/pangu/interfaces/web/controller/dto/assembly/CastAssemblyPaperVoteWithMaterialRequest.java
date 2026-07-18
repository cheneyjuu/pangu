// 关联业务：接收纸质选票录入，并将回收选票原件从会内材料库绑定到计票审计记录。
package com.pangu.interfaces.web.controller.dto.assembly;

import com.pangu.domain.model.voting.VoteChoice;
import jakarta.validation.constraints.NotNull;

public record CastAssemblyPaperVoteWithMaterialRequest(
        @NotNull Long subjectId,
        @NotNull Long opid,
        @NotNull VoteChoice choice,
        @NotNull Long ballotMaterialId
) {
}
