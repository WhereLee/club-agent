package com.club.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建社团请求（老师）。
 */
@Data
public class ClubCreateDTO {

    @NotBlank(message = "社团名称不能为空")
    @Size(max = 100, message = "社团名称过长")
    private String name;

    @Size(max = 500, message = "社团简介过长")
    private String description;
}
