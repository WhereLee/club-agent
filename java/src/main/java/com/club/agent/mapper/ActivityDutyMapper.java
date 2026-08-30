package com.club.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.club.agent.entity.ActivityDuty;
import org.apache.ibatis.annotations.Mapper;

/** 活动分工 Mapper（assigned_members JSONB 列：insert 走 XML CAST，K23 先例） */
@Mapper
public interface ActivityDutyMapper extends BaseMapper<ActivityDuty> {

    int insertWithMembers(ActivityDuty duty);
}