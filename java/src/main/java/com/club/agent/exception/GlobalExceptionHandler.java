package com.club.agent.exception;

import com.club.agent.common.R;
import com.club.agent.common.ResultCode;
import jakarta.validation.ConstraintViolation;
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
import org.springframework.web.servlet.resource.NoResourceFoundException;

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

    /** @Valid 校验失败（DTO 字段级）——message 均含完整语义，直接透出，不带字段名前缀 */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public R<Void> handleValid(BindException e) {
        FieldError fe = e.getBindingResult().getFieldError();
        String msg = fe == null ? "参数校验失败" : fe.getDefaultMessage();
        return R.fail(ResultCode.PARAM_ERROR.getCode(), msg);
    }

    /** 方法参数级校验失败（@Validated 单参，如分页 page/size）——message 均含完整语义，直接透出 */
    @ExceptionHandler(ConstraintViolationException.class)
    public R<Void> handleConstraintViolation(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .findFirst()
                .map(ConstraintViolation::getMessage)
                .orElse("参数校验失败");
        return R.fail(ResultCode.PARAM_ERROR.getCode(), msg);
    }

    /** 参数级异常（@RequestParam / @PathVariable / JSON 解析） */
    @ExceptionHandler({MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    public R<Void> handleBadParam(Exception e) {
        return R.fail(ResultCode.PARAM_ERROR.getCode(), "参数错误: " + e.getMessage());
    }

    /** 唯一约束冲突（并发注册等场景的数据库兜底） */
    @ExceptionHandler(DuplicateKeyException.class)
    public R<Void> handleDuplicate(DuplicateKeyException e) {
        log.warn("唯一约束冲突: {}", e.getMessage());
        // 概念唯一性（并发发起时部分唯一索引兜底）翻译为业务码
        if (e.getMessage() != null && e.getMessage().contains("uk_concept_active")) {
            return R.fail(ResultCode.BIZ_CONCEPT_ACTIVE_EXISTS);
        }
        // 概念投票唯一性（并发双投时 (concept_id, round, voter_id) 兜底）翻译为"已投过"
        if (e.getMessage() != null && e.getMessage().contains("concept_vote_concept_id_round_voter_id_key")) {
            return R.fail(ResultCode.BIZ_CONCEPT_ALREADY_VOTED);
        }
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

    /** 静态资源不存在（头像被删/路径错）：404 而非 500（否则会被 Exception 兜底误报系统异常） */
    @ExceptionHandler(NoResourceFoundException.class)
    public R<Void> handleNoResource(NoResourceFoundException e) {
        return R.fail(ResultCode.NOT_FOUND);
    }

    /** 兜底：未知异常不泄露堆栈 */
    @ExceptionHandler(Exception.class)
    public R<Void> handleOther(Exception e) {
        log.error("系统异常", e);
        return R.fail(ResultCode.FAIL);
    }
}
