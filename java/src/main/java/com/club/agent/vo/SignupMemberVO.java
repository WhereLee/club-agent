package com.club.agent.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/** 报名名单成员项（管理层视图）：报名状态 + 不感兴趣拦截标记 */
@Data
public class SignupMemberVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private String nickname;

    /** participate / not_participate / null（未报名） */
    private String choice;

    private Boolean onlineAssist;

    /** 问卷标记不感兴趣（限制参加） */
    private Boolean blocked;

    private LocalDateTime signupAt;
}
