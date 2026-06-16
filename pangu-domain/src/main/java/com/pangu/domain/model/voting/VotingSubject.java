package com.pangu.domain.model.voting;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 投票表决议题基类 (泛型高层抽象)
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class VotingSubject {

    /** 议题/表决事项 ID */
    private Long subjectId;

    /** 租户/小区 ID */
    private Long tenantId;

    /** 议题名称/表决标题 */
    private String title;
}
