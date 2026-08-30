package com.club.agent.service;

import com.club.agent.vo.ActivityContextVO;
import com.club.agent.vo.FileDraftMessageVO;

import java.util.List;

/**
 * 正式文件撰写 AI 服务（活动前 Agent，E1）：
 * 对话代理（Java 权限校验 + 业务会话落库，AI 在 Python/LangGraph）+ 活动上下文数据接口。
 * 边界：AI 产出不落库（章节草稿经人"采纳"后由前端写入），与概念阶段一致。
 */
public interface ActivityFileAiService {

    /** 单轮对话（发起人本人 + 讨论中；user/tool/assistant 全量落库后返回消息列表） */
    List<FileDraftMessageVO> chat(Long clubId, Long activityId, Long userId, String message, String authHeader);

    /** 会话回放（页面刷新/新设备恢复） */
    List<FileDraftMessageVO> session(Long clubId, Long activityId, Long userId);

    /** 活动前置上下文（get_activity_context 工具数据源）：概念批复 + 讨论群 + 问卷统计 */
    ActivityContextVO context(Long clubId, Long activityId, Long userId);
}