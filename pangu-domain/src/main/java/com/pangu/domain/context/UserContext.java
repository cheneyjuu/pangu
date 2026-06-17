package com.pangu.domain.context;

import com.pangu.domain.model.user.AuthenticationLevel;
import com.pangu.domain.model.user.DataScopeType;

import java.util.List;

/**
 * 跨层用户上下文（不可变值对象）。
 *
 * <p>application 层不能依赖 interfaces，因此以本 record 作为
 * 跨层「最低限度」的用户视图。infrastructure 适配器 {@code SecurityUtilsUserContextAdapter}
 * 负责从 {@code SecurityContextHolder} 提取并注入本 record。
 *
 * @param uid             自然人 UID
 * @param tenantId        当前租户 ID
 * @param userId          B/G 端管理用户 ID（C 端直登场景为 null）
 * @param deptId          所属部门 ID（C 端为 null）
 * @param deptType        部门类型（1-街道办,2-居委会,3-物业公司,4-业委会,5-网格片区；C 端为 null）
 * @param dataScopeType   行级数据权限范围
 * @param authLevel       认证等级（L1/L3/L4）
 * @param roles           角色 key 列表
 */
public record UserContext(
        Long uid,
        Long tenantId,
        Long userId,
        Long deptId,
        Integer deptType,
        DataScopeType dataScopeType,
        AuthenticationLevel authLevel,
        List<String> roles
) {
    public UserContext {
        // 防御性拷贝，避免外部修改
        roles = roles == null ? List.of() : List.copyOf(roles);
    }

    /**
     * @return 是否居委会用户（dept_type = 2）
     */
    public boolean isCommitteeUser() {
        return deptType != null && deptType == 2;
    }

    /**
     * @return 是否街道办用户（dept_type = 1）
     */
    public boolean isStreetUser() {
        return deptType != null && deptType == 1;
    }
}
