package com.club.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.club.agent.entity.OperLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作日志 Mapper。
 */
@Mapper
public interface OperLogMapper extends BaseMapper<OperLog> {
}
