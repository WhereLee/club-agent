package com.club.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 修改密码请求（新密码走注册同款格式策略）。
 */
@Data
public class UpdatePasswordDTO {

    @NotBlank(message = "原密码不能为空")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Pattern(regexp = "^[A-Za-z0-9@#$%^&*._\\-]{8,32}$",
            message = "新密码限 8-32 位字母/数字/常见符号")
    private String newPassword;
}
