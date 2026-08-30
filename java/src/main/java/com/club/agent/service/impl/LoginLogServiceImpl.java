package com.club.agent.service.impl;

import com.club.agent.entity.LoginLog;
import com.club.agent.mapper.LoginLogMapper;
import com.club.agent.service.LoginLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 登录日志异步落库（审计不阻塞登录响应）。
 * 独立组件（非 AuthServiceImpl 内部方法）：@Async 需经 Spring 代理调用，同类自调用会静默退化为同步。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginLogServiceImpl implements LoginLogService {

    private final LoginLogMapper loginLogMapper;

    @Async("logExecutor")
    @Override
    public void saveAsync(String username, String ip, int status, String message) {
        try {
            LoginLog record = new LoginLog();
            record.setUsername(username);
            record.setIp(ip);
            record.setStatus(status);
            record.setMessage(message);
            loginLogMapper.insert(record);
        } catch (Exception e) {
            log.error("登录日志落库失败: {}", e.getMessage());
        }
    }
}
