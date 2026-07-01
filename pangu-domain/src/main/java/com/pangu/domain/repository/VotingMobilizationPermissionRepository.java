package com.pangu.domain.repository;

import com.pangu.domain.model.voting.VotingMobilizationPermission;
import com.pangu.domain.model.voting.VotingScope;

import java.time.Instant;
import java.util.List;

public interface VotingMobilizationPermissionRepository {

    /**
     * 投票开始事件：按议题范围和责任楼栋生成动员权限。
     *
     * @return 新增或重新激活的权限行数
     */
    int activateForSubject(Long subjectId,
                           Long tenantId,
                           VotingScope scope,
                           Long scopeReferenceId,
                           Instant activatedAt,
                           Instant expiresAt);

    /**
     * 投票结束 / 取消事件：让该议题全部动员权限失效。
     */
    int deactivateForSubject(Long subjectId, Instant deactivatedAt);

    /**
     * 当前 sys_user 在某议题下仍生效的动员权限。
     */
    List<VotingMobilizationPermission> findActiveBySubjectAndUser(Long subjectId,
                                                                  Long tenantId,
                                                                  Long userId,
                                                                  Instant now);
}
