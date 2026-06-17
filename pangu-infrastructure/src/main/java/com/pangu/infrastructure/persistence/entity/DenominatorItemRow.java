package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 投票分母行级明细 entity。
 *
 * <p>与 {@code t_voting_denominator_item_snapshot} 一一对应；
 * 资格标记 {@code eligibilityFlag} 当前固化为 1=ELIGIBLE / 4=EXCLUDED_OTHER 二选一，
 * RESTRICTED_BY_FEE / EXCLUDED_PARKING 等更精细的分级待 PRD §6 物业费/车位规则上线后扩展。
 */
@Data
public class DenominatorItemRow {

    private Long roomId;
    private Long buildingId;
    private BigDecimal certifiedArea;
    private Long primaryOwnerUid;
    /** 共有产权人 UID 逗号分隔字符串（含 primary 自身），便于审计还原。 */
    private String coOwnerUids;
    /** 资格标记：1-ELIGIBLE, 2-RESTRICTED_BY_FEE, 3-EXCLUDED_PARKING, 4-EXCLUDED_OTHER。 */
    private Integer eligibilityFlag;
    /** 行级 SHA256 摘要（resolver 计算后回填）。 */
    private String rowHash;
}
