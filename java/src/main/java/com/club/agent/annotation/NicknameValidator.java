package com.club.agent.annotation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/**
 * 昵称校验实现：字符集（中文/英文/数字）+ 显示宽度折算（汉字 2、英文数字 1，总宽 4-24）。
 */
public class NicknameValidator implements ConstraintValidator<Nickname, String> {

    private static final Pattern ALLOWED = Pattern.compile("^[\\u4e00-\\u9fa5A-Za-z0-9]+$");
    private static final int MIN_WIDTH = 4;
    private static final int MAX_WIDTH = 24;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // 空值由 @NotBlank 负责
        if (value == null) {
            return true;
        }
        if (!ALLOWED.matcher(value).matches()) {
            return false;
        }
        int width = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            width += (c >= '\u4e00' && c <= '\u9fa5') ? 2 : 1;
        }
        return width >= MIN_WIDTH && width <= MAX_WIDTH;
    }
}
