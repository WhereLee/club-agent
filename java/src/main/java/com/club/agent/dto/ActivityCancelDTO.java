package com.club.agent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 活动取消（发起人本人；必填理由留痕） */
@Data
public class ActivityCancelDTO {

    @NotBlank(message = "取消理由必填")
    private String reason;
}