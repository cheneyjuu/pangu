package com.pangu.domain.gateway;

import com.pangu.domain.model.user.NaturalPerson;

/**
 * 自然人用户领域网关接口 (Dependency Inversion Principle)
 */
public interface UserGateway {

    /**
     * 根据 UID 获取自然人信息
     */
    NaturalPerson getByUid(Long uid);

    /**
     * 根据手机号获取自然人信息
     */
    NaturalPerson getByPhone(String phone);

    /**
     * 保存/保存自然人用户信息
     */
    void save(NaturalPerson person);
}
