package com.club.agent.util;

/**
 * Redis key 统一管理：集中前缀，避免散落魔法字符串。
 */
public final class RedisKeys {

    private static final String PREFIX = "club:";

    /** 登录失败计数（防爆破锁定） */
    public static final String LOGIN_FAIL = PREFIX + "login:fail:";

    /** 图形验证码 */
    public static final String CAPTCHA = PREFIX + "captcha:";

    /** 登出 token 黑名单（TTL = 剩余有效期） */
    public static final String TOKEN_BLACKLIST = PREFIX + "token:black:";

    /** 接口限流计数 */
    public static final String RATE_LIMIT = PREFIX + "rate:";

    /** 防重复提交标记 */
    public static final String REPEAT_SUBMIT = PREFIX + "repeat:";

    private RedisKeys() {
    }
}
