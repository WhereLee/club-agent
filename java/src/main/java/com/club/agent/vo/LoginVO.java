package com.club.agent.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 登录成功响应。
 */
@Data
@Builder
public class LoginVO {

    private String token;

    private Long userId;

    private String username;

    private String nickname;

    private String avatarUrl;
}
