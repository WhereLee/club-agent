package com.club.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录日志表（成功/失败留痕，安全审计 + 防爆破分析）。
 */
@Data
@TableName("login_log")
public class LoginLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String username;

    private String ip;

    /** 1=成功 0=失败 */
    private Integer status;

    /** 失败原因 */
    private String message;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
