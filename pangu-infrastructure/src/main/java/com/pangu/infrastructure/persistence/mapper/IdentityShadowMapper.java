package com.pangu.infrastructure.persistence.mapper;

import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 管理端工作分身查询 Mapper。
 */
@Mapper
public interface IdentityShadowMapper {

    List<SysUserShadowRow> listSysUserShadows(@Param("accountId") Long accountId);

    SysUserShadowRow selectSysUserShadow(@Param("accountId") Long accountId,
                                         @Param("userId") Long userId);

    @Data
    class SysUserShadowRow {
        private Long userId;
        private Long accountId;
        private Long deptId;
        private Long tenantId;
        private String userName;
        private String nickName;
        private Integer deptType;
        private String deptCategory;
        private String deptName;
        private Long roleId;
        private String roleKey;
        private String roleName;
        private String effectiveDataScope;
    }
}
