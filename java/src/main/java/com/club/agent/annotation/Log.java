package com.club.agent.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解：标注在 Controller 方法上，由 LogAspect 记录操作并异步落库（oper_log）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Log {

    /** 模块名（如 认证 / 个人信息） */
    String module() default "";

    /** 操作描述（如 用户登录 / 修改头像） */
    String operation() default "";
}
