package com.club.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.club.agent.entity.Activity;
import org.apache.ibatis.annotations.Mapper;

/** 活动主表 Mapper（块 A：状态机主表；复杂查询后续块按需加 XML） */
@Mapper
public interface ActivityMapper extends BaseMapper<Activity> {
}