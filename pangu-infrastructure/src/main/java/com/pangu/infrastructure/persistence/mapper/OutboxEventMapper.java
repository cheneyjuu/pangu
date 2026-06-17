package com.pangu.infrastructure.persistence.mapper;

import com.pangu.infrastructure.persistence.entity.OutboxEventRow;
import org.apache.ibatis.annotations.Mapper;

/**
 * 司法链 Outbox Mapper。本期仅 insert；异步消费器（后续 commit）会实现 select PENDING + update CONFIRMED 路径。
 */
@Mapper
public interface OutboxEventMapper {

    int insert(OutboxEventRow row);
}
