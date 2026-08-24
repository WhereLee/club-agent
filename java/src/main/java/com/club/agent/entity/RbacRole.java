package com.club.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色表（动态，支持运行时增删改，非写死枚举）。
 */
@Data
@TableName("rbac_role")
public class RbacRole {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 角色编码（teacher/president/vice_president/member） */
    private String code;

    private String name;

    /** 是否管理层（支撑"一人不能管多个社团"约束） */
    private Boolean isManagement;

    private Integer sort;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
