package com.club.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.club.agent.entity.ActivityChatMember;
import org.apache.ibatis.annotations.Mapper;

/** 讨论群成员快照 Mapper */
@Mapper
public interface ActivityChatMemberMapper extends BaseMapper<ActivityChatMember> {
}