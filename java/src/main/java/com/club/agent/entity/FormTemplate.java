package com.club.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 动态表单模板（块 B 问卷；块 D 正式文件/执行记录复用同套引擎表）。
 * type：survey=问卷 file=正式文件 record=执行记录；一个活动每种类型只有一份（唯一约束）。
 * status：1=进行中 2=已截止/已关闭。
 */
@Data
@TableName("form_template")
public class FormTemplate {

    public static final String TYPE_SURVEY = "survey";
    public static final String TYPE_FILE = "file";
    public static final String TYPE_RECORD = "record";

    public static final int STATUS_OPEN = 1;
    public static final int STATUS_CLOSED = 2;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long activityId;

    /** survey / file / record */
    private String type;

    private String title;

    /** 问卷截止时间（发起人定；file/record 无） */
    private LocalDateTime deadline;

    /** 1=进行中 2=已截止/已关闭 */
    private Integer status;

    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}