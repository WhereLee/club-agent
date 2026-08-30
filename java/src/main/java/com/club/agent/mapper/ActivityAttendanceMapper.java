package com.club.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.club.agent.entity.ActivityAttendance;
import org.apache.ibatis.annotations.Mapper;

/** 活动签到 Mapper */
@Mapper
public interface ActivityAttendanceMapper extends BaseMapper<ActivityAttendance> {
}
