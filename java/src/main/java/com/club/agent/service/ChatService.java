package com.club.agent.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.club.agent.vo.ChatMessageVO;

import java.util.List;

/**
 * 讨论群域（块 C）：
 * 名单 = 问卷"感兴趣"成员 ∪ 管理层（close 时快照生成，老师不在内）；
 * 鉴权：订阅（ChannelInterceptor）/ 发送（Service）/ 拉历史（REST）三方共用快照；
 * 消息：先落库后广播（重连 REST 补拉）；活动已发布/取消后只读不可发送。
 */
public interface ChatService {

    /** 问卷截止统一生成入群名单快照 + 入群通知（幂等：已生成过则跳过） */
    void syncMembers(Long clubId, Long activityId);

    /** 是否在讨论群（订阅/发送/拉历史鉴权共用） */
    boolean isMember(Long activityId, Long userId);

    /** 发送消息（在群 + 活动讨论中；先落库后广播），返回落库后的 VO */
    ChatMessageVO send(Long activityId, Long userId, String content);

    /** 历史消息（分页倒序；在群才可见） */
    IPage<ChatMessageVO> history(Long clubId, Long activityId, Long userId, long page, long size);

    /** 群成员列表（userId + nickname；在群才可见） */
    List<Long> memberIds(Long activityId);
}