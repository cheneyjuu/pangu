package com.pangu.infrastructure.persistence.mapper;

import com.pangu.domain.model.asset.PropertyOwnership;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 业主房产关系 MyBatis Mapper 接口 (OPID 实体)
 */
@Mapper
public interface OwnerPropertyMapper {

    /**
     * 根据自然人 ID 及小区租户 ID，查询其绑定的所有房产产权关系列表
     */
    List<PropertyOwnership> selectOwnershipsByUid(@Param("uid") Long uid, @Param("tenantId") Long tenantId);

    /**
     * 统计某个自然人用户在特定小区名下存在欠费记录的房产数量
     * 用于 C端 ABAC 资格审查 (账户状态 account_status != 1 视为非正常/欠费)
     */
    int countUnpaidProperties(@Param("uid") Long uid, @Param("tenantId") Long tenantId);

    /**
     * 根据 opid 查询特定房产绑定关系
     */
    PropertyOwnership selectByOpid(@Param("opid") Long opid);
}
