package com.club.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 奖励等级区间（块 H）：表驱动，按总分落入区间定级 */
@Data
@TableName("score_level")
public class ScoreLevel {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long clubId;

    private String levelName;

    /** 区间下界（含） */
    private Integer minScore;

    /** 区间上界（含） */
    private Integer maxScore;

    private LocalDateTime updatedAt;
}
