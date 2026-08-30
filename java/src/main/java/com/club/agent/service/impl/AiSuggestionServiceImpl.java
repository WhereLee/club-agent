package com.club.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.club.agent.common.ResultCode;
import com.club.agent.entity.Activity;
import com.club.agent.entity.ActivitySuggestion;
import com.club.agent.entity.ChatMessage;
import com.club.agent.entity.SysUser;
import com.club.agent.exception.BizException;
import com.club.agent.mapper.ActivityMapper;
import com.club.agent.mapper.ActivitySuggestionMapper;
import com.club.agent.mapper.ChatMessageMapper;
import com.club.agent.mapper.SysUserMapper;
import com.club.agent.service.AiSuggestionService;
import com.club.agent.vo.SuggestionVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 讨论建议服务实现（块 H）：
 * - 数据源：讨论关闭后的高质量消息（is_low_quality=false，10 字阈值已过滤短回复）
 * - Java AI 单次线性提炼：输出 [{messageId, summary}]，messageId 归属校验防串活动
 * - 计分链：messageId → senderId（建议人），采纳即质量分
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiSuggestionServiceImpl implements AiSuggestionService {

    private static final String EXTRACT_PROMPT = "你是社团活动的讨论建议提炼助手。请阅读以下活动讨论群的高质量消息，归纳出其中值得发起人采纳的活动建议。\n"
            + "要求：\n"
            + "1. 只输出有实质内容的建议（忽略寒暄、确认、附和等无信息量内容）\n"
            + "2. 一条消息最多输出一条建议\n"
            + "3. 严格输出 JSON 数组，格式：[{\"messageId\": 123, \"summary\": \"一句话要点\"}]\n"
            + "4. 不要输出任何其他文字（不要代码块标记）";

    private final ActivityMapper activityMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ActivitySuggestionMapper suggestionMapper;
    private final SysUserMapper sysUserMapper;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public List<SuggestionVO> extract(Long clubId, Long activityId) {
        Activity a = requireDiscussionClosed(clubId, activityId);
        // 幂等：已提炼过直接返回（AI 消耗敏感，不重复调用）
        List<ActivitySuggestion> existed = suggestionMapper.selectList(
                new LambdaQueryWrapper<ActivitySuggestion>().eq(ActivitySuggestion::getActivityId, activityId));
        if (!existed.isEmpty()) {
            return toVOList(existed);
        }
        // 高质量消息（低质量短回复已剔除）
        List<ChatMessage> msgs = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getActivityId, activityId)
                .eq(ChatMessage::getLowQuality, false)
                .orderByAsc(ChatMessage::getCreatedAt));
        if (msgs.isEmpty()) {
            return List.of();
        }
        Map<Long, ChatMessage> msgMap = msgs.stream().collect(Collectors.toMap(ChatMessage::getId, Function.identity()));
        StringBuilder user = new StringBuilder("消息列表（消息id: 发送人: 内容）：\n");
        for (ChatMessage m : msgs) {
            user.append(m.getId()).append(": ").append(m.getSenderName()).append(": ")
                    .append(m.getContent()).append('\n');
        }
        String raw = chatClient.prompt()
                .system(EXTRACT_PROMPT)
                .user(user.toString())
                .call().content();
        log.info("AI 建议提炼返回：{}", truncate(raw, 300));
        // 解析 JSON 数组（剥离可能的 markdown 代码块）
        JsonNode arr = parseArray(raw);
        if (arr == null) {
            throw new BizException(ResultCode.FAIL);
        }
        List<ActivitySuggestion> saved = new ArrayList<>();
        for (JsonNode n : arr) {
            long messageId = n.path("messageId").asLong();
            String summary = n.path("summary").asText("");
            ChatMessage src = msgMap.get(messageId);
            if (src == null || !StringUtils.hasText(summary)) {
                continue;  // messageId 不在本活动消息集（AI 幻觉）或要点为空 → 丢弃
            }
            ActivitySuggestion s = new ActivitySuggestion();
            s.setId(IdWorker.getId());
            s.setActivityId(activityId);
            s.setMessageId(messageId);
            s.setSenderId(src.getSenderId());
            s.setSummary(summary.trim());
            s.setContent(src.getContent());
            s.setAdopted(false);
            s.setCreatedAt(LocalDateTime.now());
            suggestionMapper.insert(s);
            saved.add(s);
        }
        return toVOList(saved);
    }

    @Override
    @Transactional
    public void adopt(Long clubId, Long activityId, Long userId, Long suggestionId) {
        requireDiscussionClosed(clubId, activityId);
        ActivitySuggestion s = suggestionMapper.selectById(suggestionId);
        if (s == null || !s.getActivityId().equals(activityId)) {
            throw new BizException(ResultCode.BIZ_SUGGESTION_NOT_FOUND);
        }
        if (Boolean.TRUE.equals(s.getAdopted())) {
            throw new BizException(ResultCode.BIZ_SUGGESTION_DUPLICATE);
        }
        s.setAdopted(true);
        s.setAdoptedAt(LocalDateTime.now());
        s.setAdoptedBy(userId);
        suggestionMapper.updateById(s);
    }

    @Override
    public List<SuggestionVO> list(Long clubId, Long activityId) {
        requireDiscussionClosed(clubId, activityId);
        return toVOList(suggestionMapper.selectList(
                new LambdaQueryWrapper<ActivitySuggestion>()
                        .eq(ActivitySuggestion::getActivityId, activityId)
                        .orderByAsc(ActivitySuggestion::getCreatedAt)));
    }

    // ---- 私有 ----

    private Activity requireDiscussionClosed(Long clubId, Long activityId) {
        Activity a = activityMapper.selectById(activityId);
        if (a == null || !a.getClubId().equals(clubId)) {
            throw new BizException(ResultCode.BIZ_ACTIVITY_NOT_FOUND);
        }
        if (a.getDiscussionClosedAt() == null || a.getStatus() == Activity.STATUS_CANCELLED) {
            throw new BizException(ResultCode.BIZ_ACTIVITY_STATE_FORBIDDEN);
        }
        return a;
    }

    private List<SuggestionVO> toVOList(List<ActivitySuggestion> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Map<Long, String> nicks = new HashMap<>();
        for (Long uid : list.stream().map(ActivitySuggestion::getSenderId).distinct().toList()) {
            SysUser u = sysUserMapper.selectById(uid);
            nicks.put(uid, u == null ? "未知" : u.getNickname());
        }
        return list.stream().map(s -> {
            SuggestionVO vo = new SuggestionVO();
            vo.setId(s.getId());
            vo.setMessageId(s.getMessageId());
            vo.setSenderId(s.getSenderId());
            vo.setSenderNickname(nicks.getOrDefault(s.getSenderId(), ""));
            vo.setSummary(s.getSummary());
            vo.setContent(s.getContent());
            vo.setAdopted(s.getAdopted());
            vo.setAdoptedAt(s.getAdoptedAt());
            vo.setCreatedAt(s.getCreatedAt());
            return vo;
        }).toList();
    }

    private JsonNode parseArray(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String s = raw.trim();
        int start = s.indexOf('[');
        int end = s.lastIndexOf(']');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            return objectMapper.readTree(s.substring(start, end + 1));
        } catch (Exception e) {
            log.warn("AI 提炼 JSON 解析失败：{}", truncate(raw, 200));
            return null;
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }
}
