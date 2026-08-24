package com.club.agent.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 请求 IP 获取：优先代理头（X-Forwarded-For / X-Real-IP），兜底 remoteAddr。
 * 注：代理头可伪造，若直接暴露公网需在网关层可信代理改写，此处仅取首个 IP。
 */
public final class IpUtils {

    private static final String UNKNOWN = "unknown";

    private IpUtils() {
    }

    public static String getIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (blank(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (blank(ip)) {
            ip = request.getRemoteAddr();
        }
        // X-Forwarded-For 形如 "client, proxy1, proxy2"，取最左侧真实客户端
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank() || UNKNOWN.equalsIgnoreCase(s);
    }
}
