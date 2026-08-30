package com.club.agent.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 发布问卷（发起人，公示中→问卷中）：截止时间 + 自定义题（"是否感兴趣"为系统内置自动带） */
@Data
public class SurveyPublishDTO {

    /** 截止时间（发起人定，必须晚于当前） */
    @NotNull(message = "截止时间必填")
    @Future(message = "截止时间必须晚于当前时间")
    private LocalDateTime deadline;

    /** 自定义题（可为空=纯兴趣摸底；label/fieldType 必填） */
    private List<@Valid FieldDef> fields;

    @Data
    public static class FieldDef {
        @NotBlank(message = "题目内容必填")
        private String label;

        @NotBlank(message = "题目类型必填")
        private String fieldType;

        /** 1=必填 */
        private Integer required;

        /** radio/select/checkbox 的选项 */
        private List<String> options;
    }
}