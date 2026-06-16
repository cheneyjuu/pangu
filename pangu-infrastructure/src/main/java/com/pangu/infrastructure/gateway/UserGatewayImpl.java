package com.pangu.infrastructure.gateway;

import com.pangu.domain.gateway.UserGateway;
import com.pangu.domain.model.user.NaturalPerson;
import com.pangu.infrastructure.persistence.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.ArrayList;

/**
 * 自然人用户领域网关 MyBatis 基础设施层具体实现
 */
@Repository
public class UserGatewayImpl implements UserGateway {

    @Autowired
    private UserMapper userMapper;

    @Override
    public NaturalPerson getByUid(Long uid) {
        return userMapper.selectByUid(uid);
    }

    @Override
    public NaturalPerson getByPhone(String phone) {
        return userMapper.selectByPhone(phone);
    }

    @Override
    public void save(NaturalPerson person) {
        if (person.getUid() == null) {
            userMapper.insert(person);
        } else {
            userMapper.updateAuthLevel(person.getUid(), person.getAuthLevel().getValue());
        }
    }

    @Override
    public List<String> getRolesByUid(Long uid) {
        return userMapper.selectRolesByUid(uid);
    }

    @Override
    public List<String> getPermissionsByRoles(List<String> roles) {
        List<String> permissions = new ArrayList<>();
        permissions.add("election:vote"); // 默认拥有业主投票权
        if (roles != null) {
            for (String role : roles) {
                if ("grid_manager".equals(role)) {
                    if (!permissions.contains("repair:view")) {
                        permissions.add("repair:view");
                    }
                } else if ("admin".equals(role)) {
                    if (!permissions.contains("*:*")) {
                        permissions.add("*:*");
                    }
                }
            }
        }
        return permissions;
    }
}
