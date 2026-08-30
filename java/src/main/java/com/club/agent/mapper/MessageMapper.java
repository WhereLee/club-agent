package com.club.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.club.agent.entity.Message;
import org.apache.ibatis.annotations.Mapper;

/**
 * 站内消息 Mapper。
 */
@Mapper
public interface MessageMapper extends BaseMapper<Message> {
}
