package com.club.agent.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户信息（对外展示结构，不含密码）。
 */
@Data
@Builder
public class UserInfoVO {

    private Long id;

    private String username;

    private String email;

    private String nickname;

    private String avatarUrl;

    private Integer status;

    private LocalDateTime createdAt;
}
