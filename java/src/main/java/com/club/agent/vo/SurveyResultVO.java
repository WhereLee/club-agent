package com.club.agent.vo;

import lombok.Data;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;


import java.time.LocalDateTime;
import java.util.List;

/** 问卷结果（发起人/管理层视角）：提交总数 + 每题统计（选项题按选项计数，文本题列出答案） */
@Data
public class SurveyResultVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long templateId;
    private LocalDateTime deadline;

    /** 已提交人数 */
    private Long totalSubmissions;

    private List<FieldStatVO> fields;

    @Data
    public static class FieldStatVO {
        @JsonSerialize(using = ToStringSerializer.class)
    private Long fieldId;
        private String label;
        private String fieldType;

        /** 选项题：每个选项的票数 */
        private List<OptionCountVO> counts;

        /** 文本题：全部答案 */
        private List<String> texts;
    }

    @Data
    public static class OptionCountVO {
        private String option;
        private Long count;
    }
}