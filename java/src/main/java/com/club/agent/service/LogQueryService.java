package com.club.agent.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.club.agent.entity.LoginLog;
import com.club.agent.entity.OperLog;

/**
 * 日志查询域（仅老师，log:view 全局权限点）：操作日志/登录日志分页。
 */
public interface LogQueryService {

    /** 操作日志分页（时间倒序） */
    IPage<OperLog> pageOperLogs(long page, long size);

    /** 登录日志分页（时间倒序） */
    IPage<LoginLog> pageLoginLogs(long page, long size);
}
