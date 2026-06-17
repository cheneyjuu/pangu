package com.pangu.infrastructure.persistence.mapper;

import com.pangu.infrastructure.persistence.entity.VoteItemRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * t_vote_item Mapper。本期仅暴露结算流程所需的最小读侧 API。
 */
@Mapper
public interface VoteItemMapper {

    /**
     * 加载某议题的全部投票（不在 SQL 内做去重，去重交由领域引擎）。
     */
    List<VoteItemRow> selectBySubjectId(@Param("subjectId") Long subjectId);
}
