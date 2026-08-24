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
 * 用户表（全局身份：一个人一个账号）。
 */
@Data
@TableName("sys_user")
public class SysUser {

    /** 主键：雪花算法（应用层赋值） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 登录名 */
    private String username;

    /** BCrypt 密码哈希 */
    private String passwordHash;

    /** 邮箱（全局唯一） */
    private String email;

    /** 昵称 */
    private String nickname;

    /** 头像 URL（对象存储引用，不存文件本体） */
    private String avatarUrl;

    /** 状态：1=正常 0=禁用 */
    private Integer status;

    /** 最后登录时间 */
    private LocalDateTime lastLoginAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除：0=正常 1=已删 */
    @TableLogic
    private Integer deleted;
}
