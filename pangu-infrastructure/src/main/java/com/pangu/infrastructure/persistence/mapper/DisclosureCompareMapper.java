package com.pangu.infrastructure.persistence.mapper;

import com.pangu.infrastructure.persistence.entity.DisclosureCompareRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * t_disclosure_audit_compare Mapper。仅写入 + 按 (prev, curr) 幂等查询。
 */
@Mapper
public interface DisclosureCompareMapper {

    DisclosureCompareRow selectByPair(@Param("prevSnapshotId") Long prevSnapshotId,
                                      @Param("currSnapshotId") Long currSnapshotId);

    int insert(DisclosureCompareRow row);
}
