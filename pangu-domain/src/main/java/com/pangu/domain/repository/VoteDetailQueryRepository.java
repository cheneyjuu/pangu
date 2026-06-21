package com.pangu.domain.repository;

import com.pangu.domain.common.Page;
import com.pangu.domain.model.voting.VoteChoice;
import com.pangu.domain.model.voting.VotingScope;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 逐户投票明细只读查询端口（领域定义；实现在 infrastructure）。
 *
 * <p>以分母范围内的「应投房产」为左表全量铺开，左连接投票表，使未投业主也出现在明细里
 * （{@code voted=false}）。供管理端进度页逐户核查投票情况，不参与结算。
 *
 * <p>安全边界：实现层按 {@code tenant_id} + 可选 building 过滤；不挂 {@code @DataScope}，
 * 由 endpoint 的 {@code @PreAuthorize} + 租户隔离共同保证（同 M4-1 列表范式）。
 */
public interface VoteDetailQueryRepository {

    /**
     * 分页查询某议题的逐户投票明细。
     *
     * @param tenantId         租户 ID
     * @param subjectId        议题 ID（用于左连接投票表）
     * @param scope            分母范围（UNIT 不支持，实现需拒绝）
     * @param scopeReferenceId BUILDING 时为 building_id；COMMUNITY 可为 null
     * @param page             页码（1-based）
     * @param size             页大小
     * @return 当前页明细 + 范围内应投房产总数
     */
    Page<VoteDetailRow> page(Long tenantId, Long subjectId, VotingScope scope,
                             Long scopeReferenceId, int page, int size);

    /**
     * 逐户投票明细行（只读投影）。
     *
     * <p>数据缺口（本期占位）：无房号标签表，{@code buildingId/roomId} 为裸 ID；
     * 业主姓名 {@code real_name} 为 SM4 密文且 dev 种子为明文，本期<strong>不解密</strong>，
     * 故无姓名字段，由前端以 {@code 业主#uid} 占位。
     *
     * @param opid         业主身份 ID
     * @param uid          全局自然人 ID
     * @param buildingId   楼栋 ID
     * @param roomId       房间 ID
     * @param propertyArea 房产计票面积
     * @param authLevel    认证等级（1=L1 / 2=L2 / 3=L3，来自 c_user.auth_level）
     * @param voted        是否已投票
     * @param choice       投票选项（未投时为 null）
     * @param votedAt      投票时间（未投时为 null）
     */
    record VoteDetailRow(
            Long opid,
            Long uid,
            Long buildingId,
            Long roomId,
            BigDecimal propertyArea,
            Integer authLevel,
            boolean voted,
            VoteChoice choice,
            Instant votedAt
    ) {
    }
}
