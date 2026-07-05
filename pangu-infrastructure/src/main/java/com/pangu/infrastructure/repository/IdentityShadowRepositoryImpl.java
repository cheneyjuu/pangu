package com.pangu.infrastructure.repository;

import com.pangu.domain.model.user.WorkIdentityShadow;
import com.pangu.domain.repository.IdentityShadowRepository;
import com.pangu.infrastructure.persistence.mapper.IdentityShadowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class IdentityShadowRepositoryImpl implements IdentityShadowRepository {

    private final IdentityShadowMapper identityShadowMapper;

    @Override
    public List<WorkIdentityShadow> listSysUserShadows(Long accountId) {
        return identityShadowMapper.listSysUserShadows(accountId).stream()
                .map(this::toShadow)
                .toList();
    }

    @Override
    public WorkIdentityShadow findSysUserShadow(Long accountId, Long userId) {
        return toShadow(identityShadowMapper.selectSysUserShadow(accountId, userId));
    }

    private WorkIdentityShadow toShadow(IdentityShadowMapper.SysUserShadowRow row) {
        if (row == null) {
            return null;
        }
        return new WorkIdentityShadow(
                row.getUserId(),
                row.getAccountId(),
                row.getDeptId(),
                row.getTenantId(),
                row.getUserName(),
                row.getNickName(),
                row.getDeptType(),
                row.getDeptCategory(),
                row.getDeptName(),
                row.getRoleId(),
                row.getRoleKey(),
                row.getRoleName(),
                row.getEffectiveDataScope(),
                List.of(),
                List.of());
    }
}
