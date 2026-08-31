package com.club.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.club.agent.common.ResultCode;
import com.club.agent.config.QaPythonClientFactory;
import com.club.agent.entity.QaMessage;
import com.club.agent.entity.QaSession;
import com.club.agent.exception.BizException;
import com.club.agent.mapper.QaMessageMapper;
import com.club.agent.mapper.QaSessionMapper;
import com.club.agent.service.QaService;
import com.club.agent.vo.QaMessageVO;
import com.club.agent.vo.QaSessionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 管理层经验问答实现（J3）。模式对齐 ConceptAiServiceImpl.chat：
 * 先落 user 消息（无论 AI 成败，提问留痕）→ 调 Python（失败抛 1035，user 消息保留）
 * → 落工具记录 → 落 assistant → 返回完整会话（前端重放即事实源）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QaServiceImpl implements QaService {

    private final QaSessionMapper sessionMapper;
    private final QaMessageMapper messageMapper;
    private final QaPythonClientFactory qaPythonClient;

    /** 问答服务总开关：关闭时会话接口仍可用，问答拒绝（与 ai.draft.enabled 同语义） */
    @Value("${qa.enabled:true}")
    private boolean qaEnabled;

    @Override
    public QaSessionVO createSession(Long clubId, Long userId, String title) {
        QaSession s = new QaSession();
        s.setClubId(clubId);
        s.setUserId(userId);
        String t = StringUtils.hasText(title) ? title.trim() : QaSession.DEFAULT_TITLE;
        // title 列 VARCHAR(100)：截断防超长直接 DB 异常 500（与首问命名截 20 字同思路）
        s.setTitle(t.length() > 100 ? t.substring(0, 100) : t);
        s.setStatus(QaSession.STATUS_VALID);
        s.setCreatedAt(LocalDateTime.now());
        s.setUpdatedAt(LocalDateTime.now());
        sessionMapper.insert(s);
        return toVO(s);
    }

    @Override
    public List<QaSessionVO> listSessions(Long clubId, Long userId) {
        return sessionMapper.selectList(new LambdaQueryWrapper<QaSession>()
                        .eq(QaSession::getClubId, clubId)
                        .eq(QaSession::getUserId, userId)
                        .eq(QaSession::getStatus, QaSession.STATUS_VALID)
                        .orderByDesc(QaSession::getUpdatedAt))
                .stream().map(this::toVO).toList();
    }

    @Override
    public void deleteSession(Long clubId, Long userId, Long sessionId) {
        QaSession s = owned(clubId, userId, sessionId);
        s.setStatus(QaSession.STATUS_DELETED);
        s.setUpdatedAt(LocalDateTime.now());
        sessionMapper.updateById(s);
    }

    @Override
    public List<QaMessageVO> chat(Long clubId, Long userId, Long sessionId, String message, String authHeader) {
        if (!qaEnabled) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        QaSession s = owned(clubId, userId, sessionId);

        // 1) 落 user 消息（先落：无论 AI 成败，提问都有留痕）
        QaMessage userMsg = new QaMessage();
        userMsg.setSessionId(sessionId);
        userMsg.setUserId(userId);
        userMsg.setRole(QaMessage.ROLE_USER);
        userMsg.setContent(message);
        messageMapper.insert(userMsg);

        // 首问自动命名会话（默认标题 → 问题前 20 字，列表可读性）
        if (QaSession.DEFAULT_TITLE.equals(s.getTitle())) {
            s.setTitle(message.length() > 20 ? message.substring(0, 20) : message);
        }
        s.setUpdatedAt(LocalDateTime.now());
        sessionMapper.updateById(s);

        // 2) 调 Python 问答 Agent（120s 超时；失败抛 1035，user 消息保留）
        Map<String, Object> resp;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> r = qaPythonClient.get().post()
                    .uri("/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", authHeader == null ? "" : authHeader)
                    .body(Map.of(
                            "club_id", String.valueOf(clubId),
                            "session_id", String.valueOf(sessionId),
                            "message", message))
                    .retrieve().body(Map.class);
            resp = r;
        } catch (Exception e) {
            log.warn("问答 Agent 调用失败: session={} err={}", sessionId, e.getMessage());
            throw new BizException(ResultCode.BIZ_AI_UNAVAILABLE);
        }
        if (resp == null) {
            throw new BizException(ResultCode.BIZ_AI_UNAVAILABLE);
        }

        // 3) 落工具调用记录（role=tool：工具名/入参/输出，审计与前端溯源共用）
        Object toolsObj = resp.get("tools");
        if (toolsObj instanceof List<?> tools) {
            for (Object t : tools) {
                if (!(t instanceof Map<?, ?> tm)) {
                    continue;
                }
                QaMessage toolMsg = new QaMessage();
                toolMsg.setSessionId(sessionId);
                toolMsg.setUserId(userId);
                toolMsg.setRole(QaMessage.ROLE_TOOL);
                toolMsg.setToolName(String.valueOf(tm.get("tool_name")));
                Object args = tm.get("tool_args");
                toolMsg.setToolArgs(args == null ? "" : String.valueOf(args));
                Object result = tm.get("tool_result");
                toolMsg.setContent(result == null ? "" : String.valueOf(result));
                messageMapper.insert(toolMsg);
            }
        }

        // 4) 落 assistant 消息
        QaMessage assistantMsg = new QaMessage();
        assistantMsg.setSessionId(sessionId);
        assistantMsg.setUserId(userId);
        assistantMsg.setRole(QaMessage.ROLE_ASSISTANT);
        assistantMsg.setContent(String.valueOf(resp.getOrDefault("reply", "")));
        messageMapper.insert(assistantMsg);

        return listMessages(sessionId);
    }

    @Override
    public List<QaMessageVO> messages(Long clubId, Long userId, Long sessionId) {
        owned(clubId, userId, sessionId);
        return listMessages(sessionId);
    }

    /** 会话归属校验：存在 + 归属社团 + 本人 + 有效 */
    private QaSession owned(Long clubId, Long userId, Long sessionId) {
        QaSession s = sessionMapper.selectById(sessionId);
        if (s == null || !s.getClubId().equals(clubId)
                || s.getStatus() == null || s.getStatus() != QaSession.STATUS_VALID) {
            throw new BizException(ResultCode.BIZ_QA_SESSION_NOT_FOUND);
        }
        if (!s.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        return s;
    }

    private List<QaMessageVO> listMessages(Long sessionId) {
        return messageMapper.selectList(new LambdaQueryWrapper<QaMessage>()
                        .eq(QaMessage::getSessionId, sessionId)
                        .orderByAsc(QaMessage::getCreatedAt)
                        .orderByAsc(QaMessage::getId))
                .stream().map(m -> {
                    QaMessageVO vo = new QaMessageVO();
                    vo.setId(m.getId());
                    vo.setRole(m.getRole());
                    vo.setContent(m.getContent());
                    vo.setToolName(m.getToolName());
                    vo.setToolArgs(m.getToolArgs());
                    vo.setCreatedAt(m.getCreatedAt());
                    return vo;
                }).toList();
    }

    private QaSessionVO toVO(QaSession s) {
        QaSessionVO vo = new QaSessionVO();
        vo.setId(s.getId());
        vo.setTitle(s.getTitle());
        vo.setCreatedAt(s.getCreatedAt());
        vo.setUpdatedAt(s.getUpdatedAt());
        return vo;
    }
}
