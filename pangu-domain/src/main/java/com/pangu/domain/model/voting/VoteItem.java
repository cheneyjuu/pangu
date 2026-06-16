package com.pangu.domain.model.voting;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;

/**
 * 业主投票明细记录项 (领域实体)
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoteItem {

    /** 投票人业主身份 ID (OPID) */
    private Long opid;

    /** 关联的全局自然人 ID (UID) */
    private Long uid;

    /** 投票人选择的目标（如候选人 ID，或决议选项 ID） */
    private Long targetId;

    /** 投票对应的房产计票面积 */
    private BigDecimal propertyArea;

    /** 投票人选择 */
    private VoteChoice choice;
}
