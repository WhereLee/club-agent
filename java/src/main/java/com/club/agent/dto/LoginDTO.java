package com.club.agent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求（密码只校验非空，不校验格式——兼容历史账号）。
 */
@Data
public class LoginDTO {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    /** 验证码唯一标识（captcha 接口下发） */
    @NotBlank(message = "验证码标识不能为空")
    private String captchaKey;

    /** 用户输入的验证码 */
    @NotBlank(message = "验证码不能为空")
    private String captchaCode;
}
