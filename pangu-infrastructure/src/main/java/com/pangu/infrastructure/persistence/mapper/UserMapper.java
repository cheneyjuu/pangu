package com.pangu.infrastructure.persistence.mapper;

import com.pangu.domain.model.user.NaturalPerson;
import java.util.List;
import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * C端自然人用户 MyBatis Mapper 接口
 */
@Mapper
public interface UserMapper {

    /**
     * 根据 UID 查询自然人
     */
    NaturalPerson selectByUid(@Param("uid") Long uid);

    /**
     * 根据手机号查询自然人
     */
    NaturalPerson selectByPhone(@Param("phone") String phone);

    /**
     * 插入新的自然人账户
     */
    int insert(NaturalPerson person);

    /**
     * 更新实名认证层级
     */
    int updateAuthLevel(@Param("uid") Long uid, @Param("authLevel") int authLevel);

    /**
     * 根据自然人 UID 查询其系统角色的 role_key 列表
     */
    List<String> selectRolesByUid(@Param("uid") Long uid);

    /**
     * 根据自然人 UID 查询其后台关联的系统用户信息及角色数据范围
     */
    SysUserDto selectSysUserByUid(@Param("uid") Long uid);

    /**
     * 根据后台用户 ID 查询其管辖的自定义楼栋 ID 列表
     */
    List<Long> selectBuildingIdsByUserId(@Param("userId") Long userId);

    /**
     * 根据角色 Key 列表查询关联的权限标识列表
     */
    List<String> selectPermissionsByRoles(@Param("roles") List<String> roles);

    @Data
    class SysUserDto {
        private Long userId;
        private Long deptId;
        /** sys_dept.dept_type：1=街道办、2=居委会、3=物业等。 */
        private Integer deptType;
        private String dataScope;
    }
}
