package com.club.agent.annotation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 昵称校验：限中文字符/英文字母/数字，按显示宽度折算（汉字计 2、英文数字计 1），
 * 总宽度 4-24（即纯中文 2-12 字、纯英文 4-24 字符、混合按宽度折算）。
 * 表情符号、空格、标点、控制字符一律不允许。
 * 空值不在此校验（由 @NotBlank 负责）。
 */
@Documented
@Constraint(validatedBy = NicknameValidator.class)
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Nickname {

    String message() default "昵称限 2-12 个汉字或 4-24 位英文/数字（混合按显示宽度折算），不支持表情符号";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
