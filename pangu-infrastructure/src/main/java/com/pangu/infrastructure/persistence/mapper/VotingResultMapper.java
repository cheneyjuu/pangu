package com.pangu.infrastructure.persistence.mapper;

import com.pangu.infrastructure.persistence.entity.VotingResultRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * t_voting_result Mapper。一议题最多一行（subject_id 唯一），重新结算时递增 statistics_version。
 */
@Mapper
public interface VotingResultMapper {

    VotingResultRow selectBySubjectId(@Param("subjectId") Long subjectId);

    int insert(VotingResultRow row);

    /**
     * 重新结算时整体替换：递增 statistics_version，覆盖统计字段、result_payload、attestation_tx_hash、settled_at。
     */
    int updateSnapshot(VotingResultRow row);
}
