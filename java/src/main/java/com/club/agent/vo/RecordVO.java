package com.club.agent.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 执行留痕成员视图：模板字段定义 + 本人已提交内容（回显） */
@Data
public class RecordVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long templateId;

    private String title;

    private Integer status;

    private List<FieldVO> fields;

    private List<AnswerVO> answers;

    private LocalDateTime updatedAt;

    @Data
    public static class FieldVO {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long fieldId;
        private String label;
        private String fieldType;
        private Integer required;
        private List<String> options;
        private Integer sortOrder;
    }

    @Data
    public static class AnswerVO {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long fieldId;
        private String label;
        private String value;
    }
}
