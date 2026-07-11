package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * t_vote_item 行映射。
 */
@Data
public class VoteItemRow {

    private Long voteId;
    private Long subjectId;
    private Long opid;
    private Long uid;
    private Long targetId;
    private BigDecimal propertyArea;

    /** 1-SUPPORT, 2-OPPOSE, 3-ABSTAIN。 */
    private Integer choice;

    private Instant votedAt;
    private String signatureHash;

    /** 1-ONLINE, 2-PAPER, 3-OFFLINE_PROXY。 */
    private Integer voteChannel;

    /** 1=有效计票，0=已作废但保留审计。 */
    private Integer validFlag;
    private String invalidReason;
    private Instant invalidatedAt;
}
