package com.club.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 表单填报记录（一人一份：uk(template_id, user_id)，重复提交拒绝）。
 */
@Data
@TableName("form_submission")
public class FormSubmission {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long templateId;

    private Long activityId;

    private Long userId;

    private LocalDateTime submittedAt;
}