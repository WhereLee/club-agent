package com.club.agent.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解：基于 Redis 计数窗口（Lua 原子操作），按用户/IP + 方法维度限流。
 * 典型场景：登录接口防暴力尝试。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimiter {

    /** 窗口内最大次数 */
    int limit() default 10;

    /** 窗口秒数 */
    int windowSeconds() default 60;
}
