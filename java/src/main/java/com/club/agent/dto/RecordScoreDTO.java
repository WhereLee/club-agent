package com.club.agent.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 留痕打分（管理层）：对指定成员的留痕打分 0-100 */
@Data
public class RecordScoreDTO {

    @NotNull(message = "被评人必填")
    private Long userId;

    @NotNull(message = "分数必填")
    @Min(value = 0, message = "分数范围 0-100")
    @Max(value = 100, message = "分数范围 0-100")
    private Integer score;
}
