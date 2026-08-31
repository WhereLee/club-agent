package com.club.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.club.agent.common.ResultCode;
import com.club.agent.entity.Activity;
import com.club.agent.entity.ActivityDiscussionSummary;
import com.club.agent.entity.ActivitySummary;
import com.club.agent.entity.ChatMessage;
import com.club.agent.entity.ActivityTrace;
import com.club.agent.entity.ConceptSession;
import com.club.agent.entity.FormField;
import com.club.agent.entity.FormTemplate;
import com.club.agent.entity.Membership;
import com.club.agent.entity.Message;
import com.club.agent.entity.SysUser;
import com.club.agent.exception.BizException;
import com.club.agent.mapper.ActivityDiscussionSummaryMapper;
import com.club.agent.mapper.ActivityMapper;
import com.club.agent.mapper.ActivitySummaryMapper;
import com.club.agent.mapper.ChatMessageMapper;
import com.club.agent.mapper.FormFieldMapper;
import com.club.agent.mapper.FormTemplateMapper;
import com.club.agent.mapper.ActivityTraceMapper;
import com.club.agent.mapper.MembershipMapper;
import com.club.agent.mapper.MessageMapper;
import com.club.agent.mapper.SysUserMapper;
import com.club.agent.service.ActivityOwnership;
import com.club.agent.service.ActivityService;
import com.club.agent.service.SummaryService;
import com.club.agent.service.SummaryRagSyncService;
import com.club.agent.vo.ActivityVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 活动域实现（块 A）。
 * 边界：概念通过（status=5）由 ConceptService 同事务调用创建活动；
 * 取消 = 发起人本人 + 条件 SET 原子流转（CAS，防并发双取消）+ 全员通知附理由。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityServiceImpl implements ActivityService {

    private final ActivityMapper activityMapper;
    private final ActivityTraceMapper activityTraceMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ActivityDiscussionSummaryMapper discussionSummaryMapper;
    private final MembershipMapper membershipMapper;
    private final MessageMapper messageMapper;
    private final SysUserMapper sysUserMapper;
    private final FormTemplateMapper formTemplateMapper;
    private final FormFieldMapper formFieldMapper;
    private final ObjectMapper objectMapper;
    private final ActivitySummaryMapper summaryMapper;
    private final SummaryService summaryService;
    private final SummaryRagSyncService summaryRagSync;
    private final ActivityOwnership ownership;

    @Override
    @Transactional
    public void createFromConcept(ConceptSession concept) {
        Activity a = new Activity();
        a.setClubId(concept.getClubId());
        a.setConceptId(concept.getId());
        a.setUserId(concept.getUserId());
        a.setStatus(Activity.STATUS_ANNOUNCING);
        a.setPlannedTime(concept.getPlannedTime());
        a.setPlannedLocation(concept.getPlannedLocation());
        a.setContent(concept.getContent());
        activityMapper.insert(a);
        trace(a.getId(), null, ActivityTrace.ACTION_CREATE, "概念通过批复，活动成立，进入公示阶段");
        // 公示通知全员（概念通过 + 活动公示合并为一个通知：管理层与成员都收初稿）
        notifyAllMembers(concept.getClubId(), Message.TYPE_ACTIVITY_ANNOUNCE, "新活动公示",
                "「" + briefOf(concept) + "」已通过指导老师批复，进入活动公示阶段，请查看活动详情", a.getId());
    }

    @Override
    public IPage<ActivityVO> list(Long clubId, long page, long size, Integer status) {
        Page<Activity> p = activityMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Activity>()
                        .eq(Activity::getClubId, clubId)
                        .eq(status != null, Activity::getStatus, status)
                        .orderByDesc(Activity::getCreatedAt));
        Map<Long, String> nicknames = nicknamesOf(p.getRecords().stream().map(Activity::getUserId).distinct().toList());
        Page<ActivityVO> voPage = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        voPage.setRecords(p.getRecords().stream()
                .map(a -> toVO(a, nicknames.get(a.getUserId()), null))
                .toList());
        return voPage;
    }

    @Override
    public ActivityVO detail(Long clubId, Long activityId) {
        Activity a = ownership.getOwned(clubId, activityId);
        List<ActivityTrace> traces = activityTraceMapper.selectList(new LambdaQueryWrapper<ActivityTrace>()
                .eq(ActivityTrace::getActivityId, activityId)
                .orderByAsc(ActivityTrace::getCreatedAt));
        return toVO(a, ownership.nicknameOf(a.getUserId()), traces);
    }

    @Override
    @Transactional
    public void cancel(Long clubId, Long activityId, Long userId, String reason) {
        Activity a = ownership.getOwned(clubId, activityId);
        if (!a.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        // 条件 SET 原子流转（任意非终态 → 已取消）；并发下其他路径已处理则跳过
        int updated = activityMapper.update(null, new LambdaUpdateWrapper<Activity>()
                .eq(Activity::getId, activityId)
                .in(Activity::getStatus, Activity.STATUS_ANNOUNCING, Activity.STATUS_SURVEYING,
                        Activity.STATUS_DISCUSSING, Activity.STATUS_PUBLISHED,
                        Activity.STATUS_SIGNUP, Activity.STATUS_EXECUTING, Activity.STATUS_RECORDING)
                .set(Activity::getStatus, Activity.STATUS_CANCELLED)
                .set(Activity::getCancelReason, reason));
        if (updated == 0) {
            throw new BizException(ResultCode.BIZ_ACTIVITY_STATE_FORBIDDEN);
        }
        trace(activityId, userId, ActivityTrace.ACTION_CANCEL, reason);
        notifyAllMembers(clubId, Message.TYPE_ACTIVITY_CANCEL, "活动已取消",
                "「" + briefOf(a) + "」已被发起人取消，理由：" + reason, activityId);
    }

    /** 权限收口：活动存在 + 归属社团 + 发起人本人（状态机推进统一前置校验） */
    @Override
    @Transactional
    public void startSurvey(Long clubId, Long activityId, Long userId, LocalDateTime deadline) {
        // 条件 SET 原子流转（公示中 → 问卷中）；并发下其他路径已处理则拒绝
        int updated = activityMapper.update(null, new LambdaUpdateWrapper<Activity>()
                .eq(Activity::getId, activityId)
                .eq(Activity::getClubId, clubId)
                .eq(Activity::getStatus, Activity.STATUS_ANNOUNCING)
                .set(Activity::getStatus, Activity.STATUS_SURVEYING));
        if (updated == 0) {
            throw new BizException(ResultCode.BIZ_ACTIVITY_STATE_FORBIDDEN);
        }
        trace(activityId, userId, ActivityTrace.ACTION_SURVEY_PUBLISH,
                "发布问卷，截止时间：" + (deadline == null ? "未设置" : deadline));
    }

    @Override
    @Transactional
    public void startDiscuss(Long clubId, Long activityId, Long userId) {
        // 条件 SET 原子流转（问卷中 → 讨论中）
        int updated = activityMapper.update(null, new LambdaUpdateWrapper<Activity>()
                .eq(Activity::getId, activityId)
                .eq(Activity::getClubId, clubId)
                .eq(Activity::getStatus, Activity.STATUS_SURVEYING)
                .set(Activity::getStatus, Activity.STATUS_DISCUSSING));
        if (updated == 0) {
            throw new BizException(ResultCode.BIZ_ACTIVITY_STATE_FORBIDDEN);
        }
        trace(activityId, userId, ActivityTrace.ACTION_DISCUSS_START, "问卷已截止，进入讨论阶段");
    }

    @Override
    @Transactional
    public void publish(Long clubId, Long activityId, Long userId) {
        // 条件 SET 原子流转（讨论中 → 已发布）；活动确定，讨论群随之只读（send 校验状态）
        int updated = activityMapper.update(null, new LambdaUpdateWrapper<Activity>()
                .eq(Activity::getId, activityId)
                .eq(Activity::getClubId, clubId)
                .eq(Activity::getStatus, Activity.STATUS_DISCUSSING)
                .set(Activity::getStatus, Activity.STATUS_PUBLISHED)
                // 兑底：未显式结束讨论即发布时，自动补 closed_at（保持发布后讨论只读语义）
                .set(Activity::getDiscussionClosedAt, LocalDateTime.now()));
        if (updated == 0) {
            throw new BizException(ResultCode.BIZ_ACTIVITY_STATE_FORBIDDEN);
        }
        trace(activityId, userId, ActivityTrace.ACTION_FILE_PUBLISH, "正式文件发布，活动确定");
    }

    @Override
    @Transactional
    public void endDiscussion(Long clubId, Long activityId, Long userId) {
        ownership.requireOwner(clubId, activityId, userId);
        // 条件 SET：仅讨论中且未关闭可执行（幂等：已关闭则拒绝）
        int updated = activityMapper.update(null, new LambdaUpdateWrapper<Activity>()
                .eq(Activity::getId, activityId)
                .eq(Activity::getClubId, clubId)
                .eq(Activity::getStatus, Activity.STATUS_DISCUSSING)
                .isNull(Activity::getDiscussionClosedAt)
                .set(Activity::getDiscussionClosedAt, LocalDateTime.now()));
        if (updated == 0) {
            throw new BizException(ResultCode.BIZ_ACTIVITY_STATE_FORBIDDEN);
        }
        trace(activityId, userId, ActivityTrace.ACTION_END_DISCUSSION, "讨论结束，群转只读，进入文件撰写");
        buildDiscussionSummary(activityId);
    }

    /** 讨论质量快照：每成员消息数/高质量数 + 高频标记（频率标准数据源，快照语义） */
    private void buildDiscussionSummary(Long activityId) {
        List<ChatMessage> msgs = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getActivityId, activityId));
        Map<Long, long[]> agg = new java.util.HashMap<>();
        for (ChatMessage m : msgs) {
            agg.computeIfAbsent(m.getSenderId(), k -> new long[2])[0]++;
            if (!Boolean.TRUE.equals(m.getLowQuality())) {
                agg.get(m.getSenderId())[1]++;
            }
        }
        for (Map.Entry<Long, long[]> e : agg.entrySet()) {
            ActivityDiscussionSummary s = new ActivityDiscussionSummary();
            s.setActivityId(activityId);
            s.setUserId(e.getKey());
            s.setMsgCount((int) e.getValue()[0]);
            s.setQualityCount((int) e.getValue()[1]);
            s.setHighFreq(e.getValue()[0] >= highFreqMinMsgs);
            discussionSummaryMapper.insert(s);
        }
    }

    @Override
    @Transactional
    public void startSignup(Long clubId, Long activityId, Long userId, LocalDateTime deadline) {
        ownership.requireOwner(clubId, activityId, userId);
        if (deadline == null) {
            throw new BizException(ResultCode.PARAM_ERROR);
        }
        int updated = activityMapper.update(null, new LambdaUpdateWrapper<Activity>()
                .eq(Activity::getId, activityId)
                .eq(Activity::getClubId, clubId)
                .eq(Activity::getStatus, Activity.STATUS_PUBLISHED)
                .set(Activity::getStatus, Activity.STATUS_SIGNUP)
                .set(Activity::getSignupDeadline, deadline));
        if (updated == 0) {
            throw new BizException(ResultCode.BIZ_ACTIVITY_STATE_FORBIDDEN);
        }
        trace(activityId, userId, ActivityTrace.ACTION_START_SIGNUP, "开放报名，截止时间：" + deadline);
        notifyAllMembers(clubId, Message.TYPE_ACTIVITY_SIGNUP_OPEN, "活动开始报名",
                "「" + briefOf(activityMapper.selectById(activityId)) + "」已开放报名，请到活动详情页报名，截止时间：" + deadline, activityId);
    }

    @Override
    @Transactional
    public void startExecution(Long clubId, Long activityId, Long userId, LocalDateTime recordDeadline) {
        ownership.requireOwner(clubId, activityId, userId);
        int updated = activityMapper.update(null, new LambdaUpdateWrapper<Activity>()
                .eq(Activity::getId, activityId)
                .eq(Activity::getClubId, clubId)
                .eq(Activity::getStatus, Activity.STATUS_SIGNUP)
                .set(Activity::getStatus, Activity.STATUS_EXECUTING)
                .set(recordDeadline != null, Activity::getRecordDeadline, recordDeadline));
        if (updated == 0) {
            throw new BizException(ResultCode.BIZ_ACTIVITY_STATE_FORBIDDEN);
        }
        trace(activityId, userId, ActivityTrace.ACTION_START_EXECUTION,
                recordDeadline == null ? "报名截止，活动开始执行（未设留痕截止，由发起人手动关闭）"
                        : "报名截止，活动开始执行，留痕截止时间：" + recordDeadline);
    }

    @Override
    @Transactional
    public void completeExecution(Long clubId, Long activityId, Long userId) {
        ownership.requireOwner(clubId, activityId, userId);
        int updated = activityMapper.update(null, new LambdaUpdateWrapper<Activity>()
                .eq(Activity::getId, activityId)
                .eq(Activity::getClubId, clubId)
                .eq(Activity::getStatus, Activity.STATUS_EXECUTING)
                .set(Activity::getStatus, Activity.STATUS_RECORDING));
        if (updated == 0) {
            throw new BizException(ResultCode.BIZ_ACTIVITY_STATE_FORBIDDEN);
        }
        // 自动创建执行留痕模板（幂等：一个活动一份；成员在留痕中窗口提交）
        FormTemplate record = formTemplateMapper.selectOne(new LambdaQueryWrapper<FormTemplate>()
                .eq(FormTemplate::getActivityId, activityId)
                .eq(FormTemplate::getType, FormTemplate.TYPE_RECORD));
        if (record == null) {
            record = new FormTemplate();
            record.setId(IdWorker.getId());
            record.setActivityId(activityId);
            record.setType(FormTemplate.TYPE_RECORD);
            record.setTitle("执行留痕");
            record.setStatus(FormTemplate.STATUS_OPEN);
            record.setCreatedBy(userId);
            formTemplateMapper.insert(record);
            insertRecordField(record.getId(), "工作内容", "textarea", 1, null, 1);
            insertRecordField(record.getId(), "完成情况", "radio", 1, List.of("已完成", "进行中", "受阻"), 2);
            insertRecordField(record.getId(), "补充说明", "textarea", 0, null, 3);
        }
        trace(activityId, userId, ActivityTrace.ACTION_COMPLETE_EXECUTION, "执行完成，开放执行留痕提交");
        notifyAllMembers(clubId, Message.TYPE_ACTIVITY_RECORD_OPEN, "活动执行完成，请提交留痕",
                "「" + briefOf(activityMapper.selectById(activityId)) + "」执行结束，请参与成员在截止时间前提交执行留痕", activityId);
    }

    @Override
    @Transactional
    public void closeRecords(Long clubId, Long activityId, Long userId, boolean system) {
        if (!system) {
            ownership.requireOwner(clubId, activityId, userId);
        }
        // 系统扫描（userId=null）与发起人手动共用；留痕中 → 总结中（总结阶段：进入后自动生成活动总结）
        int updated = activityMapper.update(null, new LambdaUpdateWrapper<Activity>()
                .eq(Activity::getId, activityId)
                .eq(Activity::getClubId, clubId)
                .eq(Activity::getStatus, Activity.STATUS_RECORDING)
                .set(Activity::getStatus, Activity.STATUS_SUMMARIZING));
        if (updated == 0) {
            throw new BizException(ResultCode.BIZ_ACTIVITY_STATE_FORBIDDEN);
        }
        trace(activityId, system ? null : userId, ActivityTrace.ACTION_RECORD_CLOSE,
                system ? "留痕提交截止，自动进入总结" : "留痕关闭，进入总结");
        // 进入总结中自动触发总结生成（@Async，不阻塞状态推进；失败定时重试 + 手动重生成兜底）
        try {
            summaryService.generate(clubId, activityId, null);
        } catch (TaskRejectedException e) {
            // C3：aiExecutor 满拒绝——此时 generate 方法体未执行、summary 无行，调度扫不到，必须落 failed 行
            log.warn("总结生成提交被拒（aiExecutor 满），落失败行待调度重试: activity={}", activityId);
            upsertFailedSummary(activityId);
        }
    }

    /** 总结生成被拒时的兜底：upsert 一条 failed 行（retryCount 不递增，不消耗重试名额） */
    private void upsertFailedSummary(Long activityId) {
        ActivitySummary s = summaryMapper.selectOne(new LambdaQueryWrapper<ActivitySummary>()
                .eq(ActivitySummary::getActivityId, activityId));
        if (s == null) {
            s = new ActivitySummary();
            s.setId(IdWorker.getId());
            s.setActivityId(activityId);
            s.setRetryCount(0);
            summaryMapper.insert(s);
        }
        s.setStatus(ActivitySummary.STATUS_FAILED);
        s.setUpdatedAt(LocalDateTime.now());
        summaryMapper.updateById(s);
    }

    @Override
    @Transactional
    public void archive(Long clubId, Long activityId, Long userId) {
        ownership.requireOwner(clubId, activityId, userId);
        // 前置：总结必须已生成（归档不能收一个没总结的活动）
        ActivitySummary s = summaryMapper.selectOne(new LambdaQueryWrapper<ActivitySummary>()
                .eq(ActivitySummary::getActivityId, activityId));
        if (s == null || !ActivitySummary.STATUS_SUCCESS.equals(s.getStatus())) {
            throw new BizException(ResultCode.BIZ_SUMMARY_NOT_GENERATED);
        }
        int updated = activityMapper.update(null, new LambdaUpdateWrapper<Activity>()
                .eq(Activity::getId, activityId)
                .eq(Activity::getClubId, clubId)
                .eq(Activity::getStatus, Activity.STATUS_SUMMARIZING)
                .set(Activity::getStatus, Activity.STATUS_ARCHIVED));
        if (updated == 0) {
            throw new BizException(ResultCode.BIZ_ARCHIVE_STATE_FORBIDDEN);
        }
        trace(activityId, userId, ActivityTrace.ACTION_ARCHIVE, "活动总结完成，活动归档");
        notifyAllMembers(clubId, Message.TYPE_ACTIVITY_ARCHIVED, "活动已归档",
                "「" + briefOf(activityMapper.selectById(activityId)) + "」已完成总结并归档，可查看总结报告", activityId);
        // J1：归档即定稿，总结报告推入 rag 知识库（异步，失败不阻断）。
        // 池满时 @Async 提交在调用方线程抛 TaskRejectedException，必须吞掉，否则归档事务回滚
        try {
            summaryRagSync.syncToRag(clubId, activityId);
        } catch (Exception e) {
            log.warn("总结报告 rag 同步提交失败（不阻断归档主流程）: activity={} err={}", activityId, e.getMessage());
        }
    }

    /** 流水留痕（operatorId=null 表示系统动作，如概念转活动） */
    private void trace(Long activityId, Long operatorId, String action, String detail) {
        ActivityTrace t = new ActivityTrace();
        t.setActivityId(activityId);
        t.setOperatorId(operatorId);
        t.setOperatorName(operatorId == null ? "系统" : ownership.nicknameOf(operatorId));
        t.setAction(action);
        t.setDetail(detail);
        activityTraceMapper.insert(t);
    }

    /** 该社团全部已通过成员 */
    /** 站内消息：通知该社团全部成员（公示/取消；老师不接收——无审批职责，看活动列表即可） */
    private void notifyAllMembers(Long clubId, String type, String title, String content, Long refActivityId) {
        for (Membership m : ownership.approvedMembers(clubId)) {
            Message msg = new Message();
            msg.setRecipientId(m.getUserId());
            msg.setType(type);
            msg.setTitle(title);
            msg.setContent(content);
            msg.setRefActivityId(refActivityId);
            msg.setReadFlag(0);
            messageMapper.insert(msg);
        }
    }

    /** 高频讨论者阈值：讨论期消息数 >= 该值即高频（奖励频率标准，可配） */
    @org.springframework.beans.factory.annotation.Value("${discussion.high-freq-min-msgs:3}")
    private int highFreqMinMsgs;

    private Map<Long, String> nicknamesOf(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return sysUserMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getNickname, (a, b) -> a));
    }

    /** 活动简述（通知标题用，取发起理由前 20 字；活动无独立名称字段） */
    private static String briefOf(ConceptSession c) {
        String r = c.getReason();
        return isBlank(r) ? "活动" : (r.length() > 20 ? r.substring(0, 20) + "…" : r);
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

    private ActivityVO toVO(Activity a, String requesterNickname, List<ActivityTrace> traces) {
        ActivityVO vo = new ActivityVO();
        vo.setId(a.getId());
        vo.setClubId(a.getClubId());
        vo.setConceptId(a.getConceptId());
        vo.setUserId(a.getUserId());
        vo.setRequesterNickname(requesterNickname);
        vo.setStatus(a.getStatus());
        vo.setPlannedTime(a.getPlannedTime());
        vo.setPlannedLocation(a.getPlannedLocation());
        vo.setContent(a.getContent());
        vo.setCancelReason(a.getCancelReason());
        vo.setSignupDeadline(a.getSignupDeadline());
        vo.setRecordDeadline(a.getRecordDeadline());
        vo.setDiscussionClosedAt(a.getDiscussionClosedAt());
        vo.setCreatedAt(a.getCreatedAt());
        vo.setUpdatedAt(a.getUpdatedAt());
        vo.setTraces(traces);
        return vo;
    }

    /** 内置留痕字段（JSONB options 走 insertWithOptions，K23 先例） */
    private void insertRecordField(Long templateId, String label, String fieldType,
                                   int required, List<String> options, int sortOrder) {
        FormField f = new FormField();
        f.setId(IdWorker.getId());
        f.setTemplateId(templateId);
        f.setLabel(label);
        f.setFieldType(fieldType);
        f.setRequired(required);
        f.setSortOrder(sortOrder);
        f.setSystemFlag(0);
        if (options != null && !options.isEmpty()) {
            try {
                f.setOptions(objectMapper.writeValueAsString(options));
            } catch (Exception e) {
                throw new BizException(ResultCode.FAIL);
            }
        }
        formFieldMapper.insertWithOptions(f);
    }
}