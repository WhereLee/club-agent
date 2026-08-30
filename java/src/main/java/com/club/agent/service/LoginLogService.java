package com.club.agent.service;

/**
 * 登录日志服务：登录成功/失败留痕（审计）。
 * 异步落库（@Async("logExecutor")）——独立组件避免同类自调用绕过代理导致异步失效。
 */
public interface LoginLogService {

    /**
     * 记录登录日志（异步，失败仅告警不影响登录主流程）。
     *
     * @param username 登录用户名
     * @param ip       来源 IP
     * @param status   1=成功 0=失败
     * @param message  结果描述（验证码错误/账号锁定/密码错误等）
     */
    void saveAsync(String username, String ip, int status, String message);
}
