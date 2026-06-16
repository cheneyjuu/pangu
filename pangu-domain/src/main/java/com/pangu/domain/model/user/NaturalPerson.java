package com.pangu.domain.model.user;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 自然人领域模型 (UID 聚合根)
 * 记录全网唯一的自然人身份及实名信息
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NaturalPerson {

    /** 全局唯一的自然人ID */
    private Long uid;

    /** 用户手机号（全网唯一登录凭证） */
    private String phone;

    /** 真实姓名（国密SM4加密密文，业务中通过解密使用明文） */
    private String realName;

    /** 证件类型：1-身份证, 2-港澳通行证, 3-涉外护照 */
    private Integer idCardType;

    /** 证件号码（国密SM4加密密文） */
    private String idCardNo;

    /** 认证等级 */
    private AuthenticationLevel authLevel;

    /** 活体特征状态：0-未采集, 1-已采集 */
    private Integer faceStatus;

    /** 账户注册时间 */
    private LocalDateTime createTime;

    /**
     * 判断当前用户是否达到指定的认证等级
     * @param targetLevel 目标认证等级
     * @return 是否符合
     */
    public boolean hasReachedLevel(AuthenticationLevel targetLevel) {
        if (this.authLevel == null) {
            return false;
        }
        return this.authLevel.getValue() >= targetLevel.getValue();
    }
}
