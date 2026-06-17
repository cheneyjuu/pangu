package com.pangu.infrastructure.persistence.mapper;

import com.pangu.infrastructure.persistence.entity.PartyRatioWaiverRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Waiver 聚合根全字段 Mapper（{@code PartyRatioWaiverRepositoryImpl} 用）。
 *
 * <p>与 {@link PartyRatioPolicyMapper} 区别：
 * <ul>
 *   <li>{@link PartyRatioPolicyMapper}：断路器视图，只读最小字段、按状态精准过滤；</li>
 *   <li>本 Mapper：聚合根 CRUD，承担 application 层 use case 编排所需的全字段读写。</li>
 * </ul>
 */
@Mapper
public interface PartyRatioWaiverMapper {

    /** 按主键读（不加锁）。 */
    PartyRatioWaiverRow selectById(@Param("waiverId") Long waiverId);

    /** 按主键加 {@code FOR UPDATE} 行锁。 */
    PartyRatioWaiverRow selectByIdForUpdate(@Param("waiverId") Long waiverId);

    /**
     * 查询同议题的活跃 waiver 并加 {@code FOR UPDATE} 行锁。活跃状态 = 状态码 1~4。
     * 返回 null 表示当前议题没有活跃申请。
     */
    PartyRatioWaiverRow selectActiveBySubjectIdForUpdate(@Param("subjectId") Long subjectId);

    /** 新增；返回受影响行数。MyBatis 通过 {@code useGeneratedKeys} 回填 waiver_id。 */
    int insert(PartyRatioWaiverRow row);

    /**
     * 更新整行；带 version 乐观锁 —— 仅当 version 与传入相同才更新。
     * 返回受影响行数；0 表示乐观锁失败。
     */
    int update(PartyRatioWaiverRow row);
}
