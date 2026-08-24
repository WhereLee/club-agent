package com.club.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.club.agent.entity.LoginLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 登录日志 Mapper。
 */
@Mapper
public interface LoginLogMapper extends BaseMapper<LoginLog> {
}
