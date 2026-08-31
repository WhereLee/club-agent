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

    /** 问答会话并发互斥锁（同一会话同时只能一个提问在跑，防 PostgresSaver 同 thread 并发写） */
    public static final String QA_CHAT_LOCK = PREFIX + "qa:chat:";

    /** 资料列表懒同步节流标记（parsing 记录 30s 内最多查一次 rag 解析状态，防列表 N+1 外部调用） */
    public static final String FILE_LIB_SYNC = PREFIX + "filelib:sync:";

    /** 总结报告入 rag 并发单飞锁（归档触发/重生成/调度补偿并发时同一活动只允许一个入库任务） */
    public static final String SUMMARY_RAG_SYNC = PREFIX + "summary:rag-sync:";

    private RedisKeys() {
    }
}
