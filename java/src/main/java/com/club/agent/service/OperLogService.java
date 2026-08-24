package com.club.agent.service;

import com.club.agent.entity.OperLog;

/**
 * 操作日志服务。
 */
public interface OperLogService {

    /** 异步落库（logExecutor 线程池），失败不阻塞主流程 */
    void saveAsync(OperLog operLog);
}
