package com.pangu.infrastructure.gateway;

import com.pangu.domain.gateway.UserGateway;
import com.pangu.domain.model.user.NaturalPerson;
import com.pangu.infrastructure.persistence.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

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
}
