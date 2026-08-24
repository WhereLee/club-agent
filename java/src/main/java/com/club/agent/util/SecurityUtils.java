package com.club.agent.util;

import com.club.agent.entity.SysUser;
import com.club.agent.security.LoginUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 安全上下文工具：JwtAuthenticationFilter 认证通过后，业务层从这里取当前登录用户。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /** 当前登录用户；未登录返回 null（白名单接口内调用） */
    public static LoginUser getLoginUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser;
        }
        return null;
    }

    public static Long getUserId() {
        LoginUser loginUser = getLoginUser();
        return loginUser == null ? null : loginUser.getUserId();
    }

    public static SysUser getUser() {
        LoginUser loginUser = getLoginUser();
        return loginUser == null ? null : loginUser.getUser();
    }
}
