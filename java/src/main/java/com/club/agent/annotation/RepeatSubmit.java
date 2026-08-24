package com.club.agent.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 防重复提交注解：基于 Redis SETNX 幂等标记，间隔内重复请求直接拒绝。
 * 典型场景：表单提交（防双击）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RepeatSubmit {

    /** 间隔秒数 */
    long intervalSeconds() default 5;
}
