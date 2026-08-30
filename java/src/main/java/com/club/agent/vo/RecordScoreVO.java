package com.club.agent.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/** 留痕打分（管理层视图）：最终分 + AI 预评并列 */
@Data
public class RecordScoreVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private String nickname;

    /** 是否已提交留痕（未提交不可打分） */
    private Boolean submitted;

    /** 是否已签到（AI 预评参考） */
    private Boolean checkedIn;

    private Integer score;

    private Integer aiScore;

    private String aiReason;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long scoreBy;

    private LocalDateTime scoreAt;
}
