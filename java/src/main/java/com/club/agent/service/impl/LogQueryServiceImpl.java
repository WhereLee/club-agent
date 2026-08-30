package com.club.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.club.agent.entity.LoginLog;
import com.club.agent.entity.OperLog;
import com.club.agent.mapper.LoginLogMapper;
import com.club.agent.mapper.OperLogMapper;
import com.club.agent.service.LogQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 日志查询域实现（从 TeacherController 迁入，恢复 Controller 只依赖 Service 的分层规范）。
 * 老师身份校验（requireTeacher）留在 Controller——SecurityUtils 用户判断属于接口层职责。
 */
@Service
@RequiredArgsConstructor
public class LogQueryServiceImpl implements LogQueryService {

    private final OperLogMapper operLogMapper;
    private final LoginLogMapper loginLogMapper;

    @Override
    public IPage<OperLog> pageOperLogs(long page, long size) {
        return operLogMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<OperLog>().orderByDesc(OperLog::getCreatedAt));
    }

    @Override
    public IPage<LoginLog> pageLoginLogs(long page, long size) {
        return loginLogMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<LoginLog>().orderByDesc(LoginLog::getCreatedAt));
    }
}
