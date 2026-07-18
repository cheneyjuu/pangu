// 关联业务：向办理页面返回已确认的公示与表决安排，不暴露内部表决包标识和文件摘要。
package com.pangu.interfaces.web.controller.dto.assembly;

import com.pangu.domain.model.assembly.OwnersAssemblyPackage;

import java.time.Instant;

public record OwnersAssemblyArrangementResponse(
        String status,
        String votingChannelPolicy,
        Integer publicNoticeDays,
        Instant publicNoticeStartAt,
        Instant publicNoticeEndAt,
        Instant voteStartAt,
        Instant voteEndAt,
        Instant lockedAt
) {
    public static OwnersAssemblyArrangementResponse from(OwnersAssemblyPackage arrangement) {
        if (arrangement == null) {
            return null;
        }
        return new OwnersAssemblyArrangementResponse(
                arrangement.status(),
                arrangement.votingChannelPolicy(),
                arrangement.publicNoticeDays(),
                arrangement.publicNoticeStartAt(),
                arrangement.publicNoticeEndAt(),
                arrangement.voteStartAt(),
                arrangement.voteEndAt(),
                arrangement.lockedAt());
    }
}
