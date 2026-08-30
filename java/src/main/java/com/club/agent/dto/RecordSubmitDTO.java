package com.club.agent.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** 提交执行留痕（成员）：字段答案列表（必填校验在 Service，动态字段无法用注解） */
@Data
public class RecordSubmitDTO {

    private List<@Valid AnswerItem> answers;

    @Data
    public static class AnswerItem {
        @NotNull(message = "字段 id 必填")
        private Long fieldId;

        private String value;
    }
}
