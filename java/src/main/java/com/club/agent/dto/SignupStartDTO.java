package com.club.agent.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/** 开始报名：报名截止时间（必填，超时锁报名） */
@Data
public class SignupStartDTO {

    @NotNull(message = "报名截止时间必填")
    private LocalDateTime deadline;
}
