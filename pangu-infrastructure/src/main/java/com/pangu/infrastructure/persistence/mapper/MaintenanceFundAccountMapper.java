// 关联业务：查询和变更专项维修资金账户；维修项目按可信共有范围读取账户账簿快照。
package com.pangu.infrastructure.persistence.mapper;

import com.pangu.infrastructure.persistence.entity.MaintenanceFundAccountRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.Instant;

@Mapper
public interface MaintenanceFundAccountMapper {

    MaintenanceFundAccountRow selectByIdForUpdate(@Param("accountId") Long accountId);

    MaintenanceFundAccountRow selectByScopeForUpdate(@Param("tenantId") Long tenantId,
                                                      @Param("accountLevel") int accountLevel,
                                                      @Param("referenceId") Long referenceId);

    int debit(@Param("accountId") Long accountId,
              @Param("amount") BigDecimal amount,
              @Param("expectedVersion") long expectedVersion);

    int credit(@Param("accountId") Long accountId,
               @Param("amount") BigDecimal amount,
               @Param("expectedVersion") long expectedVersion);

    int insertLedgerEntry(@Param("accountId") Long accountId,
                          @Param("businessType") int businessType,
                          @Param("direction") int direction,
                          @Param("amount") BigDecimal amount,
                          @Param("balanceAfter") BigDecimal balanceAfter,
                          @Param("occurredAt") Instant occurredAt,
                          @Param("businessRefId") Long businessRefId,
                          @Param("operatorId") Long operatorId,
                          @Param("auditHash") String auditHash);

    int countConfirmedLedgerEntry(@Param("accountId") Long accountId,
                                  @Param("businessType") int businessType,
                                  @Param("businessRefId") Long businessRefId);
}
