package com.pangu.interfaces.web.controller.dto.voting;

import com.pangu.domain.model.voting.SubjectType;
import com.pangu.domain.model.voting.VotingScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * B/G 端立项请求体（M3-2）。
 *
 * <p>角色路由原则：
 * <ul>
 *   <li>{@link SubjectType#ELECTION}：本期一律拒绝（M3-3 放开）；</li>
 *   <li>{@link SubjectType#MAJOR}：街道办 / 业委会主任；</li>
 *   <li>{@link SubjectType#GENERAL}：街道办 / 业委会主任 / 物业经理。</li>
 * </ul>
 * 角色与 SubjectType 的最终匹配由 service 层依据 {@code voting:subject:create} 角色绑定判定。
 */
public record ProposeRequest(
        @NotBlank @Size(max = 200) String title,
        @NotNull SubjectType subjectType,
        @NotNull VotingScope scope,
        Long scopeReferenceId,
        @NotNull Instant voteStartAt,
        @NotNull Instant voteEndAt,
        BigDecimal partyRatioFloor
) {
}
