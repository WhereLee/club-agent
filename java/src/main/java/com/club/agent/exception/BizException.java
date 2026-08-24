package com.club.agent.exception;

import com.club.agent.common.ResultCode;
import lombok.Getter;

/**
 * 业务异常：Service 层抛出，由 GlobalExceptionHandler 统一翻译为 R 响应。
 * 携带业务码（默认 500），前端可按码展示提示。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String message) {
        this(ResultCode.FAIL.getCode(), message);
    }

    public BizException(ResultCode rc) {
        this(rc.getCode(), rc.getMessage());
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}
