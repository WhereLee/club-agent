package com.club.agent.service;

import com.club.agent.vo.QaMessageVO;
import com.club.agent.vo.QaSessionVO;

import java.util.List;

/**
 * 管理层经验问答服务（双项目集成阶段2 · J3）：
 * - 会话管理：创建/列表/软删（会话私有，创建人本人可用）
 * - 问答：落 user 消息 → 调 Python 问答 Agent（独立服务）→ 落工具与 assistant 消息
 * - 业务事实源：qa_session/qa_message；Python 侧 checkpoint 只是运行态缓存
 */
public interface QaService {

    /** 创建问答会话（管理层本人；title 可空走默认） */
    QaSessionVO createSession(Long clubId, Long userId, String title);

    /** 本人有效会话列表（最近活跃在前） */
    List<QaSessionVO> listSessions(Long clubId, Long userId);

    /** 软删会话（本人） */
    void deleteSession(Long clubId, Long userId, Long sessionId);

    /** 单轮问答：权限与归属校验 → 三方消息留痕 → 返回完整会话消息 */
    List<QaMessageVO> chat(Long clubId, Long userId, Long sessionId, String message, String authHeader);

    /** 会话重放（页面刷新恢复） */
    List<QaMessageVO> messages(Long clubId, Long userId, Long sessionId);
}
