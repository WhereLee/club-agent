package com.club.agent.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/** 签到名单项（管理层视图）：报名参加者 + 签到状态 */
@Data
public class AttendanceVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private String nickname;

    private Boolean signed;

    private LocalDateTime checkedAt;
}
