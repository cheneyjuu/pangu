package com.pangu.domain.repository;

import com.pangu.domain.model.community.GovernmentManagedCommunity;

import java.util.List;

/**
 * 关联业务：提供街镇或平台根组织的辖区小区授权范围，供管理端会话切换使用。
 */
public interface GovernmentManagedCommunityRepository {

    /**
     * 返回组织树和已生效管辖范围共同决定的可监管小区。
     */
    List<GovernmentManagedCommunity> listManagedCommunities(Long governmentDeptId);

    /**
     * 判断目标小区是否仍在该政府组织的有效监管范围内。
     */
    boolean canManageCommunity(Long governmentDeptId, Long tenantId);
}
