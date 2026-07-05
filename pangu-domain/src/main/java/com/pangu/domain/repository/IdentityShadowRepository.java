package com.pangu.domain.repository;

import com.pangu.domain.model.user.WorkIdentityShadow;

import java.util.List;

public interface IdentityShadowRepository {

    List<WorkIdentityShadow> listSysUserShadows(Long accountId);

    WorkIdentityShadow findSysUserShadow(Long accountId, Long userId);
}
