package com.club.agent.dto;

import com.club.agent.annotation.Nickname;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求。
 * 密码策略：8-32 位，限字母/数字/常见符号（BCrypt 只取前 72 字节，
 * ASCII 字符集下 32 字符 = 32 字节，天然规避静默截断）。
 */
@Data
public class RegisterDTO {

    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_]{3,20}$", message = "用户名限 3-20 位字母/数字/下划线")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Pattern(regexp = "^[A-Za-z0-9@#$%^&*._\\-]{8,32}$",
            message = "密码限 8-32 位字母/数字/常见符号")
    private String password;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱过长")
    private String email;

    @NotBlank(message = "昵称不能为空")
    @Nickname
    private String nickname;
}
