package com.club.agent.vo;

import lombok.Data;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;


import java.time.LocalDateTime;
import java.util.List;

/** 问卷详情（成员视角：字段定义 + 我的提交状态；不含他人答案） */
@Data
public class SurveyVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long activityId;
    private String title;

    /** 截止时间 */
    private LocalDateTime deadline;

    /** 1=进行中 2=已截止 */
    private Integer status;

    /** 我是否已提交 */
    private Boolean submitted;

    private List<FieldVO> fields;

    @Data
    public static class FieldVO {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        private String label;
        private String fieldType;

        /** 1=必填 */
        private Integer required;

        /** JSON 数组字符串（前端解析展示） */
        private String options;

        private Integer sortOrder;

        /** 1=系统内置（是否感兴趣，不可删） */
        private Integer systemFlag;
    }
}