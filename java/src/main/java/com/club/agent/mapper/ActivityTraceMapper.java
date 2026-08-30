package com.club.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.club.agent.entity.ActivityTrace;
import org.apache.ibatis.annotations.Mapper;

/** 活动留痕时间线 Mapper */
@Mapper
public interface ActivityTraceMapper extends BaseMapper<ActivityTrace> {
}