package com.club.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 表单字段定义（发起人/管理层自定义；system_flag=1 为系统内置如"是否感兴趣"，不可删）。
 * field_type：text/textarea/radio/select/checkbox/number；options 为 JSONB（实体 String，insert 走 XML CAST）。
 */
@Data
@TableName("form_field")
public class FormField {

    /** 系统内置字段标记：是否感兴趣（问卷必答，惩罚机制数据源） */
    public static final int SYSTEM_FLAG_INTEREST = 1;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long templateId;

    /** 题目/字段标签 */
    private String label;

    /** text/textarea/radio/select/checkbox/number */
    private String fieldType;

    /** 1=必填 */
    private Integer required;

    /** JSON 数组字符串（radio/select/checkbox 选项），如 ["感兴趣","不感兴趣"] */
    private String options;

    private Integer sortOrder;

    /** 1=系统内置 */
    private Integer systemFlag;

    private LocalDateTime createdAt;
}