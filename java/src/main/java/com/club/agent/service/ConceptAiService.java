package com.club.agent.service;

import com.club.agent.dto.AiDraftDTO;
import com.club.agent.vo.ClubContextVO;
import com.club.agent.vo.ConceptVO;
import com.club.agent.vo.DraftMessageVO;

import java.util.List;

/**
 * 概念 AI 起草助手：Java 代理（权限与事实源在 Java，Python 只做对话生成）。
 * 经验域 → ExperienceService；SKILL 域 → SkillService（本接口只保留 AI 会话门面）。
 */
public interface ConceptAiService {

    /**
     * 单轮对话：校验（发起人本人 + 起草中）→ 落 user 消息 → 调 Python（身份透传）→
     * 落 assistant 消息；Python 不可用抛 1035（user 消息保留，审计完整）。
     *
     * @param authHeader 当前请求的 Authorization 头（透传给 Python，供工具回调 Java 鉴权）
     * @return 本轮后完整会话（时间升序，前端直接渲染）
     */
    List<DraftMessageVO> chat(Long clubId, Long conceptId, Long userId, String message, String authHeader);

    /** 会话重放（页面刷新/换设备） */
    List<DraftMessageVO> session(Long clubId, Long conceptId, Long userId);

    /** 社团上下文（get_club_context 工具数据源：简介/管理层/往届概念） */
    ClubContextVO context(Long clubId, Long userId);

    /**
     * 提交后异步生成"发起人思路"简析（@Async，不阻塞提交）：
     * 拉取完整会话 + 表单 → 调 Python /ai/brief → 成功写 ai_brief + trace(ai_brief)；失败仅日志，提交不受影响。
     */
    void asyncGenerateBrief(Long conceptId);

    /**
     * AI 草案采纳（人确认前置：前端按钮触发，不是 Agent 工具）：
     * 校验发起人 + 起草中 → 更新非 null 字段 → trace(ai_draft, note) → 返回最新详情。
     */
    ConceptVO applyAiDraft(Long clubId, Long conceptId, Long userId, AiDraftDTO dto);
}
