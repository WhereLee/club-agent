package com.club.agent.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 概念投票（首次/复议共用：轮次由概念当前状态决定）。
 */
@Data
public class ConceptVoteDTO {

    /** 1=赞成 0=拒绝 */
    @NotNull(message = "投票结果不能为空")
    @Min(value = 0, message = "投票结果不合法")
    @Max(value = 1, message = "投票结果不合法")
    private Integer result;

    /** 必填理由（留痕主体） */
    @NotBlank(message = "投票必须填写理由")
    private String comment;
}
