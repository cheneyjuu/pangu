package com.pangu.infrastructure.persistence.mapper;

import com.pangu.infrastructure.persistence.entity.CandidatePoolCount;
import com.pangu.infrastructure.persistence.entity.WaiverPolicyRow;
import com.pangu.infrastructure.persistence.entity.WaiverSnapshotComparisonRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 党员比例策略 Mapper（DefaultPartyRatioPolicyResolver 用）。
 *
 * <p>承担三件事：
 * <ul>
 *   <li>查询当前 APPROVED 状态的 waiver（可被对账与自动撤销）；</li>
 *   <li>临门一脚 COUNT 候选人池党员/合格人数；</li>
 *   <li>断路器自动撤销 + 写对账审计。</li>
 * </ul>
 *
 * <p>注：{@link com.pangu.domain.model.waiver.WaiverStatus#APPLIED} 是终态，
 * 表示议题结算已生效快照、不再允许任何流转，因此本 Mapper 不再纳入 APPLIED 进行对账。
 */
@Mapper
public interface PartyRatioPolicyMapper {

    /**
     * 查找当前 APPROVED 状态的 waiver。APPLIED 已是终态，不参与对账。
     */
    WaiverPolicyRow selectEffectiveWaiver(@Param("subjectId") Long subjectId,
                                           @Param("approvedStatus") int approvedStatus);

    /**
     * 即时统计候选人池：仅纳入 qualification_status = APPROVED(2) 的候选人。
     */
    CandidatePoolCount countCurrentCandidatePool(@Param("subjectId") Long subjectId);

    /**
     * 系统自动撤销 APPROVED 状态的 waiver（断路器触发）。WHERE 限制 status = APPROVED 形成
     * 软乐观锁，多次触发不会重复扣减 version；APPLIED 终态不可再撤销。
     */
    int revokeWaiverBySystem(@Param("waiverId") Long waiverId,
                              @Param("revokedStatus") int revokedStatus,
                              @Param("approvedStatus") int approvedStatus);

    int insertComparison(WaiverSnapshotComparisonRow row);
}
