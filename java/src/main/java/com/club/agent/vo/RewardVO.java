package com.club.agent.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/** 奖励统计（管理层视图）：频率分 + 质量分（建议采纳 + 留痕）+ 等级 */
@Data
public class RewardVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private String nickname;

    /** 高频讨论参与分（频率标准） */
    private Integer freqScore;

    /** 建议采纳分（质量标准） */
    private Integer suggestionScore;

    /** 留痕分（质量标准） */
    private Integer recordScore;

    private Integer totalScore;

    private String levelName;
}
