package com.pangu.infrastructure.persistence.mapper;

import com.pangu.infrastructure.persistence.entity.DisputeEvidenceRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** t_dispute_evidence Mapper。 */
@Mapper
public interface DisputeEvidenceMapper {

    int insert(DisputeEvidenceRow row);

    List<DisputeEvidenceRow> selectByDisputeId(@Param("disputeId") Long disputeId);
}
