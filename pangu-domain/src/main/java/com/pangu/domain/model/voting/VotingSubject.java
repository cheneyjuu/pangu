package com.pangu.domain.model.voting;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 投票表决议题基类（泛型高层抽象）。
 *
 * <p>新增字段：
 * <ul>
 *   <li>{@link #scope}：分母范围（社区/楼栋/单元），决定 {@link VotingDenominatorResolver} 计算多大空间</li>
 *   <li>{@link #scopeReferenceId}：scope 关联实体 ID（楼栋 / 单元 ID）</li>
 *   <li>{@link #partyRatioFloor}：党员比例下限（默认 0.50；放宽通过后由 application 写入实际值）</li>
 * </ul>
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class VotingSubject {

    /** 议题/表决事项 ID。 */
    private Long subjectId;

    /** 租户/小区 ID。 */
    private Long tenantId;

    /** 议题名称/表决标题。 */
    private String title;

    /** 议题类型（决定结算引擎）。 */
    private SubjectType subjectType;

    /** 议题状态（DRAFT/PUBLISHED/VOTING/CLOSED/SETTLED）。 */
    private SubjectStatus status;

    /** 分母范围（默认社区级）。 */
    private VotingScope scope;

    /** 范围引用 ID：scope=BUILDING 时为 building_id；UNIT 时为单元 ID；COMMUNITY 可为 null。 */
    private Long scopeReferenceId;

    /**
     * 党员比例下限（默认 0.50）。
     *
     * <p>设计原则：本字段是「应用层断路器结果」的载体——
     * application 在结算前调用 {@link PartyRatioPolicyResolver} 解析出实际值，
     * 写入本字段后再交给引擎；引擎自身只读不算。
     */
    private BigDecimal partyRatioFloor;

    /** 乐观锁版本号（来自 t_voting_subject.version）。 */
    private long version;

    /** 投票开放时间（M3-2：scheduler 监听此字段从 PUBLISHED 翻 VOTING）。 */
    private Instant voteStartAt;

    /** 投票截止时间。 */
    private Instant voteEndAt;

    /** Clock Suspend 生效时间；非空表示投票倒计时已因 HANDOVER_LOCK 暂停。 */
    private Instant clockSuspendedAt;

    /** 触发 Clock Suspend 的换届选举议题 ID。 */
    private Long clockSuspendedBySubjectId;

    /** 议题发起人 sys_user.user_id（M3-2 起记录；旧记录可为 null）。 */
    private Long proposedByUserId;

    /** 撤回时间（仅 status=CANCELLED 必填）。 */
    private Instant cancelledAt;

    /** 撤回操作人 sys_user.user_id（仅 status=CANCELLED 必填）。 */
    private Long cancelledByUserId;

    /** 撤回原因（仅 status=CANCELLED 必填）。 */
    private String cancelReason;

    /**
     * 应选名额（仅 ELECTION 必填，>=1；GENERAL/MAJOR 为 null）。
     *
     * <p>本字段是「持久化透传载体」：propose 时 application 从 command 写入，
     * {@code toRow} 直接落 t_voting_subject.max_winners；{@code toAggregate} 对
     * ELECTION 构造 {@link ElectionSubject} 时回填 {@code maxWinners}。基类持有
     * 该字段，避免 {@code VotingSubjectActions.open} 只产基类、却要在 toRow 强转
     * ElectionSubject 才能取值的分叉。
     */
    private Integer maxWinners;

    /**
     * @return 安全获取 partyRatioFloor，未设置时返回默认 0.50
     */
    public BigDecimal getEffectivePartyRatioFloor() {
        return partyRatioFloor != null ? partyRatioFloor : new BigDecimal("0.50");
    }
}
