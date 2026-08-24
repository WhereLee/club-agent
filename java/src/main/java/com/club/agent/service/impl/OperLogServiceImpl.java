package com.club.agent.service.impl;

import com.club.agent.entity.OperLog;
import com.club.agent.mapper.OperLogMapper;
import com.club.agent.service.OperLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 操作日志实现：异步写入，日志失败不影响业务响应。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperLogServiceImpl implements OperLogService {

    private final OperLogMapper operLogMapper;

    @Override
    @Async("logExecutor")
    public void saveAsync(OperLog operLog) {
        try {
            operLogMapper.insert(operLog);
        } catch (Exception e) {
            log.error("操作日志落库失败: {}", e.getMessage());
        }
    }
}
