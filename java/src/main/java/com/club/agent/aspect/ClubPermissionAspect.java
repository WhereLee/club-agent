package com.club.agent.aspect;

import com.club.agent.annotation.ClubPermission;
import com.club.agent.common.ResultCode;
import com.club.agent.exception.BizException;
import com.club.agent.service.ClubSecurityService;
import com.club.agent.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

/**
 * 社团上下文权限切面：@ClubPermission 标注的方法在进入前校验
 * "当前用户在指定社团内是否有对应权限点"。
 * clubId 通过 SpEL 从方法参数解析（如 #clubId），避免约定位置参数的脆弱性。
 */
@Aspect
@Component
@RequiredArgsConstructor
public class ClubPermissionAspect {

    private final ClubSecurityService clubSecurityService;
    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    @Around("@annotation(clubPermission)")
    public Object around(ProceedingJoinPoint pjp, ClubPermission clubPermission) throws Throwable {
        Long clubId = resolveClubId(pjp, clubPermission.clubId());
        Long userId = SecurityUtils.getUserId();
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        clubSecurityService.checkPermission(userId, clubId, clubPermission.permission());
        return pjp.proceed();
    }

    /** 用方法参数名解析 SpEL 表达式得到 clubId；注解误配置按权限错误处理（403 而非 500） */
    private Long resolveClubId(ProceedingJoinPoint pjp, String spel) {
        try {
            MethodSignature signature = (MethodSignature) pjp.getSignature();
            String[] paramNames = parameterNameDiscoverer.getParameterNames(signature.getMethod());
            Object[] args = pjp.getArgs();
            StandardEvaluationContext context = new StandardEvaluationContext();
            if (paramNames != null) {
                for (int i = 0; i < paramNames.length; i++) {
                    context.setVariable(paramNames[i], args[i]);
                }
            }
            Object value = parser.parseExpression(spel).getValue(context);
            return value == null ? null : Long.valueOf(value.toString());
        } catch (Exception e) {
            // SpEL 语法错/参数名取不到/类型转换失败：配置错误与真实故障分开
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "权限配置错误，请联系管理员");
        }
    }
}
