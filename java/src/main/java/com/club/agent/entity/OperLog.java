package com.club.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志表（@Log 注解落库，审计留痕）。
 */
@Data
@TableName("oper_log")
public class OperLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 模块名 */
    private String module;

    /** 操作描述 */
    private String operation;

    private String requestMethod;

    private String requestUri;

    /** 全限定方法名 */
    private String javaMethod;

    /** 请求参数（JSON） */
    private String params;

    /** 1=成功 0=失败 */
    private Integer result;

    private String errorMsg;

    private Long operatorId;

    private String operatorName;

    /** 耗时（毫秒） */
    private Long costTime;

    private String ip;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
