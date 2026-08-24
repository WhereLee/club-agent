package com.club.agent.exception;

import com.club.agent.common.R;
import com.club.agent.common.ResultCode;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 全局异常处理器：所有异常在此收敛为 R 响应，Controller 层不出现 try-catch。
 * - 业务异常：按业务码返回
 * - 参数异常：400 + 具体字段信息
 * - 未知异常：500，堆栈只进日志不返前端（不泄露内部信息）
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public R<Void> handleBiz(BizException e) {
        return R.fail(e.getCode(), e.getMessage());
    }

    /** @Valid 校验失败（DTO 字段级） */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public R<Void> handleValid(BindException e) {
        FieldError fe = e.getBindingResult().getFieldError();
        String msg = fe == null ? "参数校验失败" : fe.getField() + " " + fe.getDefaultMessage();
        return R.fail(ResultCode.PARAM_ERROR.getCode(), msg);
    }

    /** 参数级异常（@RequestParam / @PathVariable / JSON 解析 / @Validated 单参） */
    @ExceptionHandler({ConstraintViolationException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    public R<Void> handleBadParam(Exception e) {
        return R.fail(ResultCode.PARAM_ERROR.getCode(), "参数错误: " + e.getMessage());
    }

    /** 唯一约束冲突（并发注册等场景的数据库兜底） */
    @ExceptionHandler(DuplicateKeyException.class)
    public R<Void> handleDuplicate(DuplicateKeyException e) {
        log.warn("唯一约束冲突: {}", e.getMessage());
        return R.fail(ResultCode.PARAM_ERROR.getCode(), "数据已存在（唯一性冲突）");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public R<Void> handleAccessDenied(AccessDeniedException e) {
        return R.fail(ResultCode.FORBIDDEN);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public R<Void> handleBadCredentials(BadCredentialsException e) {
        return R.fail(ResultCode.BIZ_USERNAME_OR_PASSWORD_ERROR);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public R<Void> handleMaxUpload(MaxUploadSizeExceededException e) {
        return R.fail(ResultCode.BIZ_FILE_TOO_LARGE);
    }

    /** 兜底：未知异常不泄露堆栈 */
    @ExceptionHandler(Exception.class)
    public R<Void> handleOther(Exception e) {
        log.error("系统异常", e);
        return R.fail(ResultCode.FAIL);
    }
}
