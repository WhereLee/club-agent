package com.club.agent.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应体 R&lt;T&gt;：所有接口返回值统一包装。
 * code 约定：200 成功 / 400 参数 / 401 未认证 / 403 无权限 / 500 失败 / 1xxx 业务失败。
 */
@Data
public class R<T> implements Serializable {

    public static final int CODE_SUCCESS = 200;

    private int code;
    private String message;
    private T data;
    private long timestamp = System.currentTimeMillis();

    public static <T> R<T> ok() {
        return ok(null);
    }

    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.code = CODE_SUCCESS;
        r.message = "操作成功";
        r.data = data;
        return r;
    }

    public static <T> R<T> fail(String message) {
        return fail(ResultCode.FAIL.getCode(), message);
    }

    public static <T> R<T> fail(int code, String message) {
        R<T> r = new R<>();
        r.code = code;
        r.message = message;
        return r;
    }

    public static <T> R<T> fail(ResultCode rc) {
        return fail(rc.getCode(), rc.getMessage());
    }
}
