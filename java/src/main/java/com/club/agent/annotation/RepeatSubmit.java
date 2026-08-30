package com.club.agent.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 防重复提交注解（真防抖）：基于 Redis SETNX 幂等标记，
 * 请求成功后 intervalSeconds 秒内拒绝相同请求（防双击/连点）；失败路径立即放行可重试。
 * 典型场景：表单提交、状态机推进等幂等写操作。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RepeatSubmit {

    /** 间隔秒数 */
    long intervalSeconds() default 5;
}
