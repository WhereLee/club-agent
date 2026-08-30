package com.club.agent.aspect;

import com.club.agent.annotation.Log;
import com.club.agent.entity.OperLog;
import com.club.agent.entity.SysUser;
import com.club.agent.service.OperLogService;
import com.club.agent.util.IpUtils;
import com.club.agent.util.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 操作日志切面：@Log 标注的方法自动记录并异步落库（oper_log）。
 * 记录：操作人/模块/方法/参数/结果/耗时/IP。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class LogAspect {

    private static final int PARAMS_MAX_LEN = 2000;

    /** 敏感字段名（小写匹配）：password 覆盖 oldPassword/newPassword，secret 覆盖内部密钥 */
    private static final List<String> SENSITIVE_KEYS = List.of("password", "secret");

    private final OperLogService operLogService;
    private final ObjectMapper objectMapper;

    @Around("@annotation(logAnno)")
    public Object around(ProceedingJoinPoint pjp, Log logAnno) throws Throwable {
        long start = System.currentTimeMillis();
        OperLog record = new OperLog();
        record.setModule(logAnno.module());
        record.setOperation(logAnno.operation());
        record.setJavaMethod(pjp.getSignature().toLongString());
        record.setParams(buildParams(pjp.getArgs()));

        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            record.setRequestMethod(request.getMethod());
            record.setRequestUri(request.getRequestURI());
            record.setIp(IpUtils.getIp(request));
        }
        SysUser operator = SecurityUtils.getUser();
        if (operator != null) {
            record.setOperatorId(operator.getId());
            record.setOperatorName(operator.getUsername());
        }

        try {
            Object result = pjp.proceed();
            record.setResult(1);
            return result;
        } catch (Throwable e) {
            record.setResult(0);
            record.setErrorMsg(truncate(e.getMessage(), 1000));
            throw e;
        } finally {
            record.setCostTime(System.currentTimeMillis() - start);
            operLogService.saveAsync(record);
        }
    }

    /** 参数序列化：跳过 Servlet/文件流对象，敏感字段（password/secret）脱敏，超长截断（防日志膨胀） */
    private String buildParams(Object[] args) {
        try {
            List<Object> list = new ArrayList<>();
            for (Object arg : args) {
                if (arg == null || arg instanceof HttpServletRequest
                        || arg instanceof HttpServletResponse
                        || arg instanceof MultipartFile) {
                    continue;
                }
                list.add(desensitize(arg));
            }
            String json = objectMapper.writeValueAsString(list);
            return truncate(json, PARAMS_MAX_LEN);
        } catch (Exception e) {
            return "参数序列化失败";
        }
    }

    /** 递归脱敏：Map/Collection 按字段名展开；POJO 先转 Map 再递归（Jackson 按字段名展开，嵌套同样覆盖） */
    @SuppressWarnings("unchecked")
    private Object desensitize(Object obj) {
        if (obj == null || obj instanceof String || obj instanceof Number || obj instanceof Boolean) {
            return obj;
        }
        if (obj instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                String key = String.valueOf(e.getKey());
                out.put(key, isSensitive(key) ? "******" : desensitize(e.getValue()));
            }
            return out;
        }
        if (obj instanceof Collection<?> coll) {
            List<Object> out = new ArrayList<>();
            for (Object item : coll) {
                out.add(desensitize(item));
            }
            return out;
        }
        return desensitize(objectMapper.convertValue(obj, Map.class));
    }

    private boolean isSensitive(String key) {
        String lower = key.toLowerCase();
        return SENSITIVE_KEYS.stream().anyMatch(lower::contains);
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
