package com.club.agent.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改资料请求（昵称必填，邮箱可改）。
 */
@Data
public class UpdateProfileDTO {

    @NotBlank(message = "昵称不能为空")
    @Size(max = 50, message = "昵称过长")
    private String nickname;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱过长")
    private String email;
}
