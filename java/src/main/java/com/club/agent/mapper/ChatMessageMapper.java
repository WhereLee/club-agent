package com.club.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.club.agent.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

/** 讨论消息 Mapper */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}