// 关联业务：业主端可单独授权微信昵称和头像用于资料展示，二者不参与身份、产权或投票资格判断。
package com.pangu.interfaces.web.controller.dto;

import jakarta.validation.constraints.Size;

/** 微信展示资料同步请求。 */
public record WeChatProfileRequest(
        @Size(max = 64, message = "微信昵称长度不能超过 64 个字符") String nickname,
        @Size(max = 512, message = "微信头像地址长度不能超过 512 个字符") String avatarUrl) {
}
