package com.club.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 成员关系表：用户在某个社团里的身份。
 * status：0=申请中 1=已通过 2=已拒绝。
 */
@Data
@TableName("membership")
public class Membership {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_APPROVED = 1;
    public static final int STATUS_REJECTED = 2;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long clubId;

    /** 引用 rbac_role（社团内角色） */
    private Long roleId;

    /** 0=申请中 1=已通过 2=已拒绝 */
    private Integer status;

    private LocalDateTime appliedAt;

    private LocalDateTime approvedAt;

    /** 审批人（老师/管理层） */
    private Long approvedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
