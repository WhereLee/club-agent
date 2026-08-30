package com.club.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.club.agent.common.ResultCode;
import com.club.agent.dto.AiDraftDTO;
import com.club.agent.entity.Club;
import com.club.agent.entity.ConceptDraftSession;
import com.club.agent.entity.ConceptSession;
import com.club.agent.entity.ConceptTrace;
import com.club.agent.exception.BizException;
import com.club.agent.config.PythonClientFactory;
import com.club.agent.mapper.ClubMapper;
import com.club.agent.mapper.ConceptDraftSessionMapper;
import com.club.agent.mapper.ConceptSessionMapper;
import com.club.agent.mapper.ConceptTraceMapper;
import com.club.agent.mapper.MembershipMapper;
import com.club.agent.mapper.SysUserMapper;
import com.club.agent.service.ConceptAiService;
import com.club.agent.service.ConceptService;
import com.club.agent.vo.ClubContextVO;
import com.club.agent.vo.ConceptVO;
import com.club.agent.vo.DraftMessageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;



import java.util.List;
import java.util.Map;

/**
 * 概念 AI 起草代理。
 *
 * 设计要点：
 * 1. 权限永远在 Java：发起人本人 + 概念起草中（status=1）才可对话；Python 无状态、不鉴权
 * 2. 会话表是业务事实源：先落 user 消息再调 Python；AI 失败保留 user 消息（审计完整），
 *    前端提示 1035 后可手动填表（主流程零依赖 AI）
 * 3. 失败不落 assistant：避免重试产生"半截对话"；重放永远成对（user 后有 assistant）
 * 4. 身份透传：chat 请求的 Authorization 原样转给 Python，工具回调 Java 时携带（D2 起）
 * 5. AI 无写权限：落表只能由人触发（applyAiDraft 前端按钮），trace ai_draft 留"人采纳"证据
 * 6. ai_brief（D3）：提交后异步生成（@Async），不阻塞提交主流程
 * 7. 经验域 → ExperienceService；SKILL 域 → SkillService（本类只保留 AI 会话门面）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConceptAiServiceImpl implements ConceptAiService {

    private final ConceptSessionMapper conceptSessionMapper;
    private final ConceptDraftSessionMapper draftSessionMapper;
    private final ConceptTraceMapper conceptTraceMapper;
    private final ClubMapper clubMapper;
    private final MembershipMapper membershipMapper;
    private final SysUserMapper sysUserMapper;
    private final ConceptService conceptService;
    private final PythonClientFactory pythonClient;

    @Value("${ai.draft.enabled:true}")
    private boolean aiDraftEnabled;

    @Value("${ai.draft.checkpoint-ttl-days:30}")
    private int checkpointTtlDays;

    @Override
    public List<DraftMessageVO> chat(Long clubId, Long conceptId, Long userId, String message, String authHeader) {
        if (!aiDraftEnabled) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        ConceptSession concept = conceptSessionMapper.selectById(conceptId);
        if (concept == null || !concept.getClubId().equals(clubId)) {
            throw new BizException(ResultCode.BIZ_CONCEPT_NOT_FOUND);
        }
        if (!concept.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        if (concept.getStatus() != ConceptSession.STATUS_DRAFTING) {
            throw new BizException(ResultCode.BIZ_CONCEPT_STATE_FORBIDDEN);
        }

        // 1) 落 user 消息（先落：无论 AI 成败，发起人的输入都有留痕）
        ConceptDraftSession userMsg = new ConceptDraftSession();
        userMsg.setConceptId(conceptId);
        userMsg.setUserId(userId);
        userMsg.setRole(ConceptDraftSession.ROLE_USER);
        userMsg.setContent(message);
        draftSessionMapper.insert(userMsg);

        // 2) 调 Python（120s 超时；失败抛 1035，user 消息保留）
        PythonChatResult result = callPython(clubId, conceptId, message, authHeader);

        // 3) 落工具调用记录（role=tool：工具名/入参/输出，前端草案卡片与审计共用）
        for (Map<String, Object> t : result.tools()) {
            String toolArgs = String.valueOf(t.get("tool_args"));
            ConceptDraftSession toolMsg = new ConceptDraftSession();
            toolMsg.setId(IdWorker.getId());
            toolMsg.setConceptId(conceptId);
            toolMsg.setUserId(userId);
            toolMsg.setRole(ConceptDraftSession.ROLE_TOOL);
            toolMsg.setToolName(String.valueOf(t.get("tool_name")));
            // JSONB 列：空串置 null（CAST 空串会失败），非空走自定义 insert 的 CAST
            toolMsg.setToolArgs(StringUtils.hasText(toolArgs) ? toolArgs : null);
            toolMsg.setContent(String.valueOf(t.get("tool_result")));
            draftSessionMapper.insertToolMessage(toolMsg);
        }

        // 4) 落 assistant 消息
        ConceptDraftSession assistantMsg = new ConceptDraftSession();
        assistantMsg.setConceptId(conceptId);
        assistantMsg.setUserId(userId);
        assistantMsg.setRole(ConceptDraftSession.ROLE_ASSISTANT);
        assistantMsg.setContent(result.reply());
        draftSessionMapper.insert(assistantMsg);

        return listMessages(conceptId);
    }

    @Override
    public List<DraftMessageVO> session(Long clubId, Long conceptId, Long userId) {
        ConceptSession concept = conceptSessionMapper.selectById(conceptId);
        if (concept == null || !concept.getClubId().equals(clubId)) {
            throw new BizException(ResultCode.BIZ_CONCEPT_NOT_FOUND);
        }
        if (!concept.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        return listMessages(conceptId);
    }

    @Override
    public ClubContextVO context(Long clubId, Long userId) {
        Club club = clubMapper.selectById(clubId);
        if (club == null) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        ClubContextVO vo = new ClubContextVO();
        vo.setClubId(clubId);
        vo.setClubName(club.getName());
        vo.setDescription(club.getDescription());
        vo.setManagers(membershipMapper.selectManagersByClub(clubId));
        vo.setPastConcepts(conceptSessionMapper.selectList(
                new LambdaQueryWrapper<ConceptSession>()
                        .eq(ConceptSession::getClubId, clubId)
                        .eq(ConceptSession::getStatus, ConceptSession.STATUS_APPROVED)
                        .orderByDesc(ConceptSession::getUpdatedAt)
                        .last("LIMIT 5")).stream()
                .map(c -> {
                    ClubContextVO.PastConceptVO p = new ClubContextVO.PastConceptVO();
                    p.setId(c.getId());
                    p.setPlannedTime(c.getPlannedTime());
                    p.setPlannedLocation(c.getPlannedLocation());
                    p.setContent(c.getContent());
                    return p;
                })
                .toList());
        return vo;
    }

    /**
     * A1：LangGraph checkpoint TTL 清理（每天 03:30）。
     * 终态概念（已通过/作废）超过 checkpointTtlDays 天后，清理其 checkpoint 三表（writes→blobs→checkpoints），
     * 防 PostgresSaver 无限膨胀；删除行数为 0 时静默，避免噪音日志。
     */
    @Scheduled(cron = "0 30 3 * * ?")
    @Transactional
    public void cleanupExpiredCheckpoints() {
        int ttl = checkpointTtlDays;
        int writes = conceptSessionMapper.cleanupCheckpointWrites(ttl);
        int blobs = conceptSessionMapper.cleanupCheckpointBlobs(ttl);
        int checkpoints = conceptSessionMapper.cleanupCheckpoints(ttl);
        if (writes + blobs + checkpoints > 0) {
            log.info("checkpoint TTL 清理：writes={} blobs={} checkpoints={}（终态超 {} 天）",
                    writes, blobs, checkpoints, ttl);
        }
    }

    @Override
    @Async("logExecutor")
    public void asyncGenerateBrief(Long conceptId) {
        try {
            ConceptSession concept = conceptSessionMapper.selectById(conceptId);
            if (concept == null || concept.getStatus() != ConceptSession.STATUS_SUBMITTED) {
                return;  // 状态已变化（撤回/作废），不生成
            }
            // 拉完整会话（user/assistant/tool 全部，业务事实源）
            List<ConceptDraftSession> msgs = draftSessionMapper.selectByConceptId(conceptId);
            if (msgs.isEmpty()) {
                return;
            }
            Map<String, Object> body = Map.of(
                    "messages", msgs.stream().map(m -> Map.of(
                            "role", m.getRole(),
                            "tool_name", m.getToolName() == null ? "" : m.getToolName(),
                            "content", m.getContent() == null ? "" : m.getContent())).toList(),
                    "form", Map.of(
                            "reason", concept.getReason() == null ? "" : concept.getReason(),
                            "plannedTime", concept.getPlannedTime() == null ? "" : concept.getPlannedTime(),
                            "plannedLocation", concept.getPlannedLocation() == null ? "" : concept.getPlannedLocation(),
                            "content", concept.getContent() == null ? "" : concept.getContent()));
            Map<?, ?> resp = pythonClient.get().post().uri("/ai/brief")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve().body(Map.class);
            Object brief = resp == null ? null : resp.get("brief");
            if (brief == null || brief.toString().isBlank()) {
                throw new BizException(ResultCode.BIZ_AI_UNAVAILABLE);
            }
            ConceptSession upd = new ConceptSession();
            upd.setId(conceptId);
            upd.setAiBrief(brief.toString());
            conceptSessionMapper.updateById(upd);
            // 留痕：AI 简析已冻结（提交时刻的会话）
            ConceptTrace t = new ConceptTrace();
            t.setConceptId(conceptId);
            t.setOperatorId(concept.getUserId());
            t.setOperatorName("系统");
            t.setAction(ConceptTrace.ACTION_AI_BRIEF);
            t.setDetail("AI 生成发起人想法简析");
            conceptTraceMapper.insert(t);
        } catch (Exception e) {
            log.warn("生成 ai_brief 失败（提交不受影响）：concept={} err={}", conceptId, e.getMessage());
        }
    }

    @Override
    public ConceptVO applyAiDraft(Long clubId, Long conceptId, Long userId, AiDraftDTO dto) {
        ConceptSession session = conceptSessionMapper.selectById(conceptId);
        if (session == null || !session.getClubId().equals(clubId)) {
            throw new BizException(ResultCode.BIZ_CONCEPT_NOT_FOUND);
        }
        if (!session.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        if (session.getStatus() != ConceptSession.STATUS_DRAFTING) {
            throw new BizException(ResultCode.BIZ_CONCEPT_STATE_FORBIDDEN);
        }

        // 只更新非 null 字段（MyBatis-Plus NOT_NULL 策略天然支持；AI 产出经人确认后写入）
        ConceptSession upd = new ConceptSession();
        upd.setId(conceptId);
        if (dto.getReason() != null) {
            upd.setReason(dto.getReason());
        }
        if (dto.getPlannedTime() != null) {
            upd.setPlannedTime(dto.getPlannedTime());
        }
        if (dto.getPlannedLocation() != null) {
            upd.setPlannedLocation(dto.getPlannedLocation());
        }
        if (dto.getContent() != null) {
            upd.setContent(dto.getContent());
        }
        conceptSessionMapper.updateById(upd);

        // 留痕：人采纳了 AI 产出（trace detail = 决策说明/采纳摘要）
        ConceptTrace t = new ConceptTrace();
        t.setConceptId(conceptId);
        t.setOperatorId(userId);
        t.setOperatorName(nicknameOf(userId));
        t.setAction(ConceptTrace.ACTION_AI_DRAFT);
        t.setDetail(StringUtils.hasText(dto.getNote()) ? dto.getNote() : "发起人采纳 AI 起草草案");
        conceptTraceMapper.insert(t);

        return conceptService.detail(clubId, conceptId);
    }

    private String nicknameOf(Long userId) {
        return sysUserMapper.selectById(userId) == null ? "发起人" : sysUserMapper.selectById(userId).getNickname();
    }

    /** Python 响应：最终回复 + 本轮工具调用记录 */
    private record PythonChatResult(String reply, List<Map<String, Object>> tools) {
    }

    @SuppressWarnings("unchecked")
    private PythonChatResult callPython(Long clubId, Long conceptId, String message, String authHeader) {
        try {
            RestClient.RequestBodySpec spec = pythonClient.get().post()
                    .uri("/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("club_id", String.valueOf(clubId), "concept_id", String.valueOf(conceptId), "message", message));
            if (StringUtils.hasText(authHeader)) {
                spec = spec.header("Authorization", authHeader);
            }
            Map<?, ?> resp = spec.retrieve().body(Map.class);
            Object reply = resp == null ? null : resp.get("reply");
            if (reply == null || reply.toString().isBlank()) {
                throw new BizException(ResultCode.BIZ_AI_UNAVAILABLE);
            }
            Object toolsObj = resp == null ? null : resp.get("tools");
            List<Map<String, Object>> tools = toolsObj instanceof List<?> list
                    ? list.stream().map(x -> (Map<String, Object>) x).toList()
                    : List.of();
            return new PythonChatResult(reply.toString(), tools);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("调用 Python agent-draft 失败：concept={} err={}", conceptId, e.getMessage());
            throw new BizException(ResultCode.BIZ_AI_UNAVAILABLE);
        }
    }

    private List<DraftMessageVO> listMessages(Long conceptId) {
        return draftSessionMapper.selectByConceptId(conceptId).stream()
                .map(m -> {
                    DraftMessageVO vo = new DraftMessageVO();
                    vo.setId(m.getId());
                    vo.setConceptId(m.getConceptId());
                    vo.setUserId(m.getUserId());
                    vo.setRole(m.getRole());
                    vo.setContent(m.getContent());
                    vo.setToolName(m.getToolName());
                    vo.setToolArgs(m.getToolArgs());
                    vo.setCreatedAt(m.getCreatedAt());
                    return vo;
                })
                .toList();
    }
}
