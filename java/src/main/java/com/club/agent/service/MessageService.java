package com.club.agent.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.club.agent.vo.MessageVO;

/**
 * 站内消息域：我的消息分页/未读数/标记已读（只能操作自己的消息）。
 */
public interface MessageService {

    /** 我的消息分页（可选按已读状态筛选；VO 层 id 字符串化防 JS 精度丢失） */
    IPage<MessageVO> list(Long userId, long page, long size, Integer readFlag);

    /** 未读消息数（顶栏红点） */
    long unreadCount(Long userId);

    /** 标记已读（只能操作自己的消息；不存在/非本人抛 404） */
    void markRead(Long userId, Long id);
}
