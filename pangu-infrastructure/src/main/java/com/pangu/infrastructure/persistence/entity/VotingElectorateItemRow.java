// 关联业务：映射冻结名册中一个专有部分、唯一表决代表及共有人清单。
package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class VotingElectorateItemRow {
    private Long snapshotItemId;
    private Long snapshotId;
    private Long rosterId;
    private Long roomId;
    private Long buildingId;
    private BigDecimal certifiedArea;
    private Long representativeOpid;
    private Long representativeUid;
    private String coOwnerUidsJson;
    private String rowHash;
}
