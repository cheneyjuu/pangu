package com.pangu.infrastructure.persistence.mapper;

import com.pangu.infrastructure.persistence.entity.DisputeDecisionRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * t_dispute_review_decision Mapper。
 *
 * <p>{@link #insert} 命中 {@code uk_decision_dispute_level} 唯一索引时由 Spring 抛
 * {@link org.springframework.dao.DuplicateKeyException}，由 RepositoryImpl 翻译为领域端口异常。
 * V2.8 trigger 11 在 BEFORE INSERT 校验主表 status / review_level 一致性。
 */
@Mapper
public interface DisputeDecisionMapper {

    int insert(DisputeDecisionRow row);

    List<DisputeDecisionRow> selectByDisputeId(@Param("disputeId") Long disputeId);
}
