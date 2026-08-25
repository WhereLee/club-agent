package com.club.agent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 任命管理层请求：只进空位（在职者先离职）。
 */
@Data
public class AppointDTO {

    /** president / vice_president */
    @NotBlank(message = "目标角色不能为空")
    private String role;
}
