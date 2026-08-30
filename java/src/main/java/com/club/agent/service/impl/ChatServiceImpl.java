package com.club.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.club.agent.common.ResultCode;
import com.club.agent.entity.Activity;
import com.club.agent.entity.ActivityChatMember;
import com.club.agent.entity.ChatMessage;
import com.club.agent.entity.FormAnswer;
import com.club.agent.entity.FormField;
import com.club.agent.entity.FormSubmission;
import com.club.agent.entity.FormTemplate;
import com.club.agent.entity.Membership;
import com.club.agent.entity.Message;
import com.club.agent.entity.RbacRole;
import com.club.agent.entity.SysUser;
import com.club.agent.exception.BizException;
import com.club.agent.mapper.ActivityChatMemberMapper;
import com.club.agent.mapper.ActivityMapper;
import com.club.agent.mapper.ChatMessageMapper;
import com.club.agent.mapper.FormAnswerMapper;
import com.club.agent.mapper.FormFieldMapper;
import com.club.agent.mapper.FormSubmissionMapper;
import com.club.agent.mapper.FormTemplateMapper;
import com.club.agent.mapper.MembershipMapper;
import com.club.agent.mapper.MessageMapper;
import com.club.agent.mapper.RbacRoleMapper;
import com.club.agent.mapper.SysUserMapper;
import com.club.agent.service.ChatService;
import com.club.agent.vo.ChatMessageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 讨论群域实现（块 C）。
 * 边界：非群成员一律 1042（订阅/发送/历史）；活动状态 != 讨论中 禁止发送（发布后只读）。
 * 消息必达：先落库后广播；广播失败（断连）不阻塞业务——历史拉取兜底。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ActivityMapper activityMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ActivityChatMemberMapper chatMemberMapper;
    private final FormTemplateMapper formTemplateMapper;
    private final FormFieldMapper formFieldMapper;
    private final FormSubmissionMapper formSubmissionMapper;
    private final FormAnswerMapper formAnswerMapper;
    private final MembershipMapper membershipMapper;
    private final RbacRoleMapper rbacRoleMapper;
    private final SysUserMapper sysUserMapper;
    private final MessageMapper messageMapper;
    private final SimpMessagingTemplate messagingTemplate;

    /** 低质量消息字数阈值（去空白后 < 该值即低质量，默认 10 字） */
    @org.springframework.beans.factory.annotation.Value("${discussion.low-quality-min-words:10}")
    private int lowQualityMinWords;

    @Override
    @Transactional
    public void syncMembers(Long clubId, Long activityId) {
        Long existed = chatMemberMapper.selectCount(new LambdaQueryWrapper<ActivityChatMember>()
                .eq(ActivityChatMember::getActivityId, activityId));
        if (existed != null && existed > 0) {
            return;  // 幂等：名单已生成过（重复 close 或重试）
        }
        Set<Long> members = new HashSet<>();
        // ① 问卷"是否感兴趣"= 感兴趣 的成员（system_flag=1 的系统字段）
        FormTemplate survey = formTemplateMapper.selectOne(new LambdaQueryWrapper<FormTemplate>()
                .eq(FormTemplate::getActivityId, activityId)
                .eq(FormTemplate::getType, FormTemplate.TYPE_SURVEY));
        if (survey != null) {
            FormField interest = formFieldMapper.selectOne(new LambdaQueryWrapper<FormField>()
                    .eq(FormField::getTemplateId, survey.getId())
                    .eq(FormField::getSystemFlag, FormField.SYSTEM_FLAG_INTEREST));
            if (interest != null) {
                List<Long> subIds = formAnswerMapper.selectList(new LambdaQueryWrapper<FormAnswer>()
                                .eq(FormAnswer::getFieldId, interest.getId())
                                .eq(FormAnswer::getValue, "感兴趣"))
                        .stream().map(FormAnswer::getSubmissionId).toList();
                if (!subIds.isEmpty()) {
                    members.addAll(formSubmissionMapper.selectList(new LambdaQueryWrapper<FormSubmission>()
                                    .in(FormSubmission::getId, subIds))
                            .stream().map(FormSubmission::getUserId).toList());
                }
            }
        }
        // ② 管理层（不管是否答感兴趣；老师不在内——teacher 无 club membership）
        List<Long> manageRoleIds = rbacRoleMapper.selectList(new LambdaQueryWrapper<RbacRole>()
                        .eq(RbacRole::getIsManagement, true))
                .stream().map(RbacRole::getId).toList();
        if (!manageRoleIds.isEmpty()) {
            members.addAll(membershipMapper.selectList(new LambdaQueryWrapper<Membership>()
                            .eq(Membership::getClubId, clubId)
                            .eq(Membership::getStatus, Membership.STATUS_APPROVED)
                            .in(Membership::getRoleId, manageRoleIds))
                    .stream().map(Membership::getUserId).toList());
        }
        // ③ 快照落库（去重）+ 入群通知
        for (Long uid : members) {
            ActivityChatMember m = new ActivityChatMember();
            m.setId(IdWorker.getId());
            m.setActivityId(activityId);
            m.setUserId(uid);
            try {
                chatMemberMapper.insert(m);
            } catch (Exception e) {
                log.warn("入群成员已存在（并发/重复）：activity={} user={}", activityId, uid);
            }
        }
        Activity a = activityMapper.selectById(activityId);
        for (Long uid : members) {
            Message msg = new Message();
            msg.setRecipientId(uid);
            msg.setType(Message.TYPE_ACTIVITY_DISCUSS);
            msg.setTitle("已加入活动讨论群");
            msg.setContent("「" + briefOf(a) + "」讨论群已开启，可进入活动详情参与讨论（仅本群成员可见）");
            msg.setRefActivityId(activityId);
            msg.setReadFlag(0);
            messageMapper.insert(msg);
        }
    }

    @Override
    public boolean isMember(Long activityId, Long userId) {
        if (userId == null) {
            return false;
        }
        Long c = chatMemberMapper.selectCount(new LambdaQueryWrapper<ActivityChatMember>()
                .eq(ActivityChatMember::getActivityId, activityId)
                .eq(ActivityChatMember::getUserId, userId));
        return c != null && c > 0;
    }

    @Override
    @Transactional
    public ChatMessageVO send(Long activityId, Long userId, String content) {
        // 名单校验通过即视为本社团成员（快照按 clubId 生成）；STOMP 无 clubId 路径参数
        if (!isMember(activityId, userId)) {
            throw new BizException(ResultCode.BIZ_CHAT_FORBIDDEN);
        }
        Activity a = activityMapper.selectById(activityId);
        if (a == null) {
            throw new BizException(ResultCode.BIZ_ACTIVITY_NOT_FOUND);
        }
        if (a.getStatus() != Activity.STATUS_DISCUSSING
                || a.getDiscussionClosedAt() != null) {
            // 讨论已关闭/已发布/已取消 → 讨论群只读
            throw new BizException(ResultCode.BIZ_ACTIVITY_STATE_FORBIDDEN);
        }
        if (!StringUtils.hasText(content) || content.trim().length() > 2000) {
            throw new BizException(ResultCode.PARAM_ERROR);
        }
        SysUser user = sysUserMapper.selectById(userId);
        ChatMessage msg = new ChatMessage();
        msg.setId(IdWorker.getId());
        msg.setActivityId(activityId);
        msg.setSenderId(userId);
        msg.setSenderName(user == null ? "未知" : user.getNickname());
        msg.setContent(content.trim());
        // 讨论质量预处理：插入时计算字数与低质量标记（<10 字短回复不进文件 Agent 参考集）
        int words = content.trim().replaceAll("\\s+", "").length();
        msg.setWordCount(words);
        msg.setLowQuality(words < lowQualityMinWords);
        chatMessageMapper.insert(msg);
        ChatMessageVO vo = toVO(msg);
        // 先落库后广播：广播失败不影响留痕（历史拉取兜底）
        try {
            messagingTemplate.convertAndSend("/topic/activity/" + activityId, vo);
        } catch (Exception e) {
            log.warn("讨论消息广播失败（不影响落库）：activity={}", activityId, e);
        }
        return vo;
    }

    @Override
    public IPage<ChatMessageVO> history(Long clubId, Long activityId, Long userId, long page, long size) {
        if (!isMember(activityId, userId)) {
            throw new BizException(ResultCode.BIZ_CHAT_FORBIDDEN);
        }
        Activity a = activityMapper.selectById(activityId);
        if (a == null || !a.getClubId().equals(clubId)) {
            throw new BizException(ResultCode.BIZ_ACTIVITY_NOT_FOUND);
        }
        IPage<ChatMessage> p = chatMessageMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getActivityId, activityId)
                        .orderByDesc(ChatMessage::getCreatedAt));
        IPage<ChatMessageVO> vo = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        vo.setRecords(p.getRecords().stream().map(this::toVO).toList());
        return vo;
    }

    @Override
    public List<Long> memberIds(Long activityId) {
        return chatMemberMapper.selectList(new LambdaQueryWrapper<ActivityChatMember>()
                        .eq(ActivityChatMember::getActivityId, activityId))
                .stream().map(ActivityChatMember::getUserId).toList();
    }

    private ChatMessageVO toVO(ChatMessage m) {
        ChatMessageVO vo = new ChatMessageVO();
        vo.setId(m.getId());
        vo.setActivityId(m.getActivityId());
        vo.setSenderId(m.getSenderId());
        vo.setSenderName(m.getSenderName());
        vo.setContent(m.getContent());
        vo.setCreatedAt(m.getCreatedAt());
        return vo;
    }

    private static String briefOf(Activity a) {
        String t = a.getPlannedTime();
        String loc = a.getPlannedLocation();
        if (!isBlank(t) && !isBlank(loc)) {
            return t + " " + loc;
        }
        String c = a.getContent();
        if (isBlank(c)) {
            return "活动";
        }
        return c.length() > 20 ? c.substring(0, 20) + "…" : c;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}