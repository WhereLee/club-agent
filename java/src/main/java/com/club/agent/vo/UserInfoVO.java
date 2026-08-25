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

    /** 老师身份（前端按此显示创建社团入口） */
    private Boolean isTeacher;

    private Integer status;

    private LocalDateTime createdAt;
}
