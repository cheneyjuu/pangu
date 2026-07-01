package com.pangu.infrastructure.persistence.mapper;

import com.pangu.infrastructure.persistence.entity.VotingMobilizationPermissionRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

@Mapper
public interface VotingMobilizationPermissionMapper {

    int activateForSubject(@Param("subjectId") Long subjectId,
                           @Param("tenantId") Long tenantId,
                           @Param("scope") Integer scope,
                           @Param("scopeReferenceId") Long scopeReferenceId,
                           @Param("activatedAt") Instant activatedAt,
                           @Param("expiresAt") Instant expiresAt);

    int deactivateForSubject(@Param("subjectId") Long subjectId,
                             @Param("deactivatedAt") Instant deactivatedAt);

    List<VotingMobilizationPermissionRow> selectActiveBySubjectAndUser(
            @Param("subjectId") Long subjectId,
            @Param("tenantId") Long tenantId,
            @Param("userId") Long userId,
            @Param("now") Instant now);
}
