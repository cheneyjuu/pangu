package com.pangu.infrastructure.persistence.mapper;

import com.pangu.infrastructure.persistence.entity.VoteItemRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * t_vote_item Mapper。
 */
@Mapper
public interface VoteItemMapper {

    /**
     * 加载某议题的全部投票（不在 SQL 内做去重，去重交由领域引擎）。
     */
    List<VoteItemRow> selectBySubjectId(@Param("subjectId") Long subjectId);

    /**
     * M3-2 业主投票提交：写入一票。useGeneratedKeys 回填 vote_id 到 row.voteId。
     * UNIQUE(subject_id, opid, COALESCE(target_id, 0)) 冲突由 Spring 翻 DuplicateKeyException。
     */
    int insert(VoteItemRow row);
}

