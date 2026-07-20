// 关联业务：执行书面委托登记、异人核验、撤销和纸票使用查询的数据库读写。
package com.pangu.infrastructure.persistence.mapper;

import com.pangu.infrastructure.persistence.entity.VotingProxyAuthorizationRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

@Mapper
public interface VotingProxyAuthorizationMapper {

    int insert(VotingProxyAuthorizationRow row);

    VotingProxyAuthorizationRow selectById(@Param("authorizationId") Long authorizationId,
                                            @Param("packageId") Long packageId,
                                            @Param("tenantId") Long tenantId);

    VotingProxyAuthorizationRow selectByIdForUpdate(@Param("authorizationId") Long authorizationId,
                                                     @Param("packageId") Long packageId,
                                                     @Param("tenantId") Long tenantId);

    List<VotingProxyAuthorizationRow> selectByPackage(@Param("packageId") Long packageId,
                                                       @Param("tenantId") Long tenantId);

    int confirm(@Param("authorizationId") Long authorizationId,
                @Param("tenantId") Long tenantId,
                @Param("reviewedByUserId") Long reviewedByUserId,
                @Param("reviewedAt") Instant reviewedAt,
                @Param("reviewNote") String reviewNote,
                @Param("expectedVersion") long expectedVersion);

    int reject(@Param("authorizationId") Long authorizationId,
               @Param("tenantId") Long tenantId,
               @Param("reviewedByUserId") Long reviewedByUserId,
               @Param("reviewedAt") Instant reviewedAt,
               @Param("reviewNote") String reviewNote,
               @Param("expectedVersion") long expectedVersion);

    int revoke(@Param("authorizationId") Long authorizationId,
               @Param("tenantId") Long tenantId,
               @Param("revokedByUserId") Long revokedByUserId,
               @Param("revokedAt") Instant revokedAt,
               @Param("revokeReason") String revokeReason,
               @Param("expectedVersion") long expectedVersion);

    boolean isUsedByPaperRecord(@Param("authorizationId") Long authorizationId,
                                @Param("tenantId") Long tenantId);
}
