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
 * 权限点表（业务动作/菜单/按钮级，如 club:member:approve）。
 */
@Data
@TableName("rbac_permission")
public class RbacPermission {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 权限编码（如 club:member:approve） */
    private String code;

    private String name;

    /** MENU / BUTTON / ACTION */
    private String type;

    private Long parentId;

    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
