package com.club.agent.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 老师批复概念。
 */
@Data
public class ConceptReviewDTO {

    /** true=通过 false=否决 */
    @NotNull(message = "批复结果不能为空")
    private Boolean approve;

    /** 理由（否决必填，由 Service 校验；通过可选但建议填写，留痕透明） */
    private String comment;
}
