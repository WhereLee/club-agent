package com.club.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 字段答案（多选 value 存 JSON 数组字符串，如 ["选项A","选项B"]）。
 */
@Data
@TableName("form_answer")
public class FormAnswer {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long submissionId;

    private Long fieldId;

    private String value;

    private LocalDateTime createdAt;
}