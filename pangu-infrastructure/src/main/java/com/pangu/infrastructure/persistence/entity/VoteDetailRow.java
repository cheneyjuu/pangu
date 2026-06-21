package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 逐户投票明细行 entity（只读投影，对应 {@code VoteDetailQueryRepository.VoteDetailRow}）。
 *
 * <p>以分母范围内的应投房产（c_owner_property，account_status=1）为左表，
 * 左连接 t_vote_item（同 subject + opid）得出每户是否已投及其选项。
 * 不解密 real_name（SM4 密文 + dev 明文种子冲突，见端口文档）。
 */
@Data
public class VoteDetailRow {

    private Long opid;
    private Long uid;
    private Long buildingId;
    private Long roomId;
    private BigDecimal propertyArea;
    private Integer authLevel;
    /** 投票主键；为 null 表示该户未投。 */
    private Long voteId;
    /** 投票选项 dbValue（1/2/3）；未投时为 null。 */
    private Integer choice;
    private Instant votedAt;
}
