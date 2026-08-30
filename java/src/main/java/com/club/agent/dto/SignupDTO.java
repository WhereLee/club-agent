package com.club.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** 报名请求：参加/不参加（+在线协助） */
@Data
public class SignupDTO {

    @NotBlank(message = "报名选择必填")
    @Pattern(regexp = "participate|not_participate", message = "choice 仅支持 participate / not_participate")
    private String choice;

    /** 不参加时勾选在线协助 → 通知发起人 */
    private Boolean onlineAssist;
}
