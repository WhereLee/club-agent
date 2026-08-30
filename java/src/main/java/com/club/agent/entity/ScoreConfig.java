package com.club.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 奖励分值配置（块 H）：suggestion_score 建议采纳分 / record_score 留痕分 / freq_score 高频参与分。
 * uk(club_id, cfg_key)，可配。
 */
@Data
@TableName("score_config")
public class ScoreConfig {

    public static final String KEY_SUGGESTION = "suggestion_score";
    public static final String KEY_RECORD = "record_score";
    public static final String KEY_FREQ = "freq_score";

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long clubId;

    private String cfgKey;

    private Integer cfgValue;

    private String remark;

    private LocalDateTime updatedAt;
}
