package com.club.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.club.agent.common.ResultCode;
import com.club.agent.dto.ConceptDraftDTO;
import com.club.agent.dto.ConceptReviewDTO;
import com.club.agent.dto.ConceptVoteDTO;
import com.club.agent.entity.Club;
import com.club.agent.entity.ConceptSession;
import com.club.agent.entity.ConceptTrace;
import com.club.agent.entity.ConceptVote;
import com.club.agent.entity.Membership;
import com.club.agent.entity.Message;
import com.club.agent.entity.RbacRole;
import com.club.agent.entity.SysUser;
import com.club.agent.exception.BizException;
import com.club.agent.mapper.ClubMapper;
import com.club.agent.mapper.ConceptSessionMapper;
import com.club.agent.mapper.ConceptTraceMapper;
import com.club.agent.mapper.ConceptVoteMapper;
import com.club.agent.mapper.MembershipMapper;
import com.club.agent.mapper.MessageMapper;
import com.club.agent.mapper.RbacRoleMapper;
import com.club.agent.mapper.SysUserMapper;
import com.club.agent.service.ActivityService;
import com.club.agent.service.ActivityOwnership;
import com.club.agent.service.ConceptService;
import com.club.agent.util.RoleConstants;
import com.club.agent.vo.ConceptVO;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 概念生命周期：发起 → 起草 → 提交 → 投票/复议 → 老师批复 → 通过/作废。
 * 权限边界：Controller 层 @ClubPermission(activity:manage) 保证"管理层+老师"，
 * 本层再校验"发起者本人/非发起人投票/本社团老师 + 状态机 CAS"；并发兜底由部分唯一索引 + @Version 乐观锁承担。
 * 规则：发起人不投票；两票全赞成进入老师批复；首轮拒绝进入一轮复议；复议再拒立即作废；
 * 每个审批阶段 36h 超时由定时扫描自动作废。留痕：所有动作写 concept_trace（全量时间线）。
 */
@Service
@RequiredArgsConstructor
public class ConceptServiceImpl implements ConceptService {

    /** 审批时限：提交/进入老师批复起 36 小时 */
    private static final Duration STAGE_TIMEOUT = Duration.ofHours(36);

    private static final List<Integer> ACTIVE_STATUSES = List.of(
            ConceptSession.STATUS_DRAFTING, ConceptSession.STATUS_SUBMITTED,
            ConceptSession.STATUS_REVOTING, ConceptSession.STATUS_TEACHER_REVIEW);

    private final ConceptSessionMapper conceptSessionMapper;
    private final ActivityOwnership ownership;
    private final ConceptTraceMapper conceptTraceMapper;
    private final ConceptVoteMapper conceptVoteMapper;
    private final ClubMapper clubMapper;
    private final MembershipMapper membershipMapper;
    private final SysUserMapper sysUserMapper;
    private final RbacRoleMapper rbacRoleMapper;
    private final MessageMapper messageMapper;
    private final ActivityService activityService;

    @Override
    @Transactional
    public ConceptVO create(Long clubId, Long userId, ConceptDraftDTO dto) {
        if (clubMapper.selectById(clubId) == null) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        // 发起人定稿为社团管理层（社长/副社长）：老师有 activity:manage 但不发起活动
        Membership membership = membershipMapper.selectOne(new LambdaQueryWrapper<Membership>()
                .eq(Membership::getUserId, userId)
                .eq(Membership::getClubId, clubId)
                .eq(Membership::getStatus, Membership.STATUS_APPROVED));
        if (membership == null) {
            throw new BizException(ResultCode.BIZ_NOT_MANAGEMENT);
        }
        // 唯一性预检（并发穿透由 uk_concept_active 部分唯一索引兜底）
        Long activeCount = conceptSessionMapper.selectCount(new LambdaQueryWrapper<ConceptSession>()
                .eq(ConceptSession::getClubId, clubId)
                .in(ConceptSession::getStatus, ACTIVE_STATUSES));
        if (activeCount > 0) {
            throw new BizException(ResultCode.BIZ_CONCEPT_ACTIVE_EXISTS);
        }
        ConceptSession session = new ConceptSession();
        session.setClubId(clubId);
        session.setUserId(userId);
        session.setStatus(ConceptSession.STATUS_DRAFTING);
        applyDraft(session, dto);
        conceptSessionMapper.insert(session);
        trace(session.getId(), userId, ConceptTrace.ACTION_CREATE, null);
        return toVO(session, ownership.nicknameOf(userId));
    }

    @Override
    public IPage<ConceptVO> list(Long clubId, Long userId, long page, long size, Integer status) {
        return conceptSessionMapper.selectConceptPage(new Page<>(page, size), clubId, userId, status);
    }

    @Override
    public ConceptVO detail(Long clubId, Long id) {
        ConceptSession session = getOwned(clubId, id);
        ConceptVO vo = toVO(session, ownership.nicknameOf(session.getUserId()));
        // 详情附带投票记录 + 全量时间线（透明留痕，老师/管理层审阅数据源）
        vo.setVotes(conceptVoteMapper.selectVotesByConcept(id));
        vo.setTraces(conceptTraceMapper.selectList(new LambdaQueryWrapper<ConceptTrace>()
                .eq(ConceptTrace::getConceptId, id)
                .orderByAsc(ConceptTrace::getCreatedAt)));
        return vo;
    }

    @Override
    @Transactional
    public ConceptVO saveDraft(Long clubId, Long id, Long userId, ConceptDraftDTO dto) {
        ConceptSession session = getOwned(clubId, id);
        if (!session.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        if (session.getStatus() != ConceptSession.STATUS_DRAFTING) {
            throw new BizException(ResultCode.BIZ_CONCEPT_SUBMITTED);
        }
        applyDraft(session, dto);
        // @Version 乐观锁：并发编辑草稿时后写者更新 0 行 → 冲突提示
        if (conceptSessionMapper.updateById(session) == 0) {
            throw new BizException(ResultCode.BIZ_CONCEPT_DRAFT_CONFLICT);
        }
        trace(id, userId, ConceptTrace.ACTION_SAVE, null);
        return toVO(session, ownership.nicknameOf(userId));
    }

    @Override
    @Transactional
    public ConceptVO submit(Long clubId, Long id, Long userId) {
        ConceptSession session = getOwned(clubId, id);
        if (!session.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        if (isBlank(session.getReason())
                || isBlank(session.getPlannedTime())
                || isBlank(session.getPlannedLocation())
                || isBlank(session.getContent())) {
            throw new BizException(ResultCode.BIZ_CONCEPT_EMPTY_FIELDS);
        }
        // 单条条件 SET 更新（原子）：仅起草中(1)可提交，防并发双击/重复提交；同时写入 submitted_at/deadline
        LocalDateTime now = LocalDateTime.now();
        int updated = conceptSessionMapper.update(null, new LambdaUpdateWrapper<ConceptSession>()
                .eq(ConceptSession::getId, id)
                .eq(ConceptSession::getStatus, ConceptSession.STATUS_DRAFTING)
                .set(ConceptSession::getStatus, ConceptSession.STATUS_SUBMITTED)
                .set(ConceptSession::getSubmittedAt, now)
                .set(ConceptSession::getDeadline, now.plus(STAGE_TIMEOUT)));
        if (updated == 0) {
            throw new BizException(ResultCode.BIZ_CONCEPT_STATE_FORBIDDEN);
        }
        trace(id, userId, ConceptTrace.ACTION_SUBMIT, "等待其他管理层审阅，时限 36 小时");
        return detail(clubId, id);
    }

    @Override
    @Transactional
    public ConceptVO withdraw(Long clubId, Long id, Long userId) {
        ConceptSession session = getOwned(clubId, id);
        if (!session.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        int from = session.getStatus();
        if (from == ConceptSession.STATUS_DRAFTING || from == ConceptSession.STATUS_APPROVED
                || from == ConceptSession.STATUS_VOIDED) {
            throw new BizException(ResultCode.BIZ_CONCEPT_STATE_FORBIDDEN);
        }
        // 单条条件 SET 更新（原子）：审批链节点(2/3/4) → 起草中，deadline 显式清空（updateById 会跳过 null 字段）；已投的票物理删除（审计在 trace）
        int updated = conceptSessionMapper.update(null, new LambdaUpdateWrapper<ConceptSession>()
                .eq(ConceptSession::getId, id)
                .in(ConceptSession::getStatus, ConceptSession.STATUS_SUBMITTED,
                        ConceptSession.STATUS_REVOTING, ConceptSession.STATUS_TEACHER_REVIEW)
                .set(ConceptSession::getStatus, ConceptSession.STATUS_DRAFTING)
                .set(ConceptSession::getDeadline, null));
        if (updated == 0) {
            throw new BizException(ResultCode.BIZ_CONCEPT_STATE_FORBIDDEN);
        }
        conceptVoteMapper.delete(new LambdaQueryWrapper<ConceptVote>()
                .eq(ConceptVote::getConceptId, id));
        trace(id, userId, ConceptTrace.ACTION_WITHDRAW, "撤回至起草中，已投的票作废");
        return detail(clubId, id);
    }

    @Override
    @Transactional
    public ConceptVO abandon(Long clubId, Long id, Long userId) {
        ConceptSession session = getOwned(clubId, id);
        if (!session.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        int from = session.getStatus();
        if (from == ConceptSession.STATUS_APPROVED || from == ConceptSession.STATUS_VOIDED) {
            throw new BizException(ResultCode.BIZ_CONCEPT_STATE_FORBIDDEN);
        }
        // 单条条件 SET 更新（原子）：任意非终局 → 作废，deadline 清理
        int updated = conceptSessionMapper.update(null, new LambdaUpdateWrapper<ConceptSession>()
                .eq(ConceptSession::getId, id)
                .notIn(ConceptSession::getStatus, ConceptSession.STATUS_APPROVED, ConceptSession.STATUS_VOIDED)
                .set(ConceptSession::getStatus, ConceptSession.STATUS_VOIDED)
                .set(ConceptSession::getDeadline, null));
        if (updated == 0) {
            throw new BizException(ResultCode.BIZ_CONCEPT_STATE_FORBIDDEN);
        }
        trace(id, userId, ConceptTrace.ACTION_ABANDON, null);
        return detail(clubId, id);
    }

    @Override
    @Transactional
    public ConceptVO vote(Long clubId, Long id, Long userId, ConceptVoteDTO dto) {
        // 行级锁（FOR UPDATE）：串行化"读状态→插票→计数→推进"，防两个收官票并发时都判定"还差一票"卡死
        // （READ COMMITTED 下互不可见对方未提交的行）；同时覆盖撤回与投票并发的孤儿票场景
        ConceptSession session = conceptSessionMapper.selectOne(new LambdaQueryWrapper<ConceptSession>()
                .eq(ConceptSession::getId, id)
                .last("FOR UPDATE"));
        if (session == null || !session.getClubId().equals(clubId)) {
            throw new BizException(ResultCode.BIZ_CONCEPT_NOT_FOUND);
        }
        // 1) 必须是该社团已加入成员（老师有 activity:manage 但不参与投票）
        if (membershipOf(clubId, userId) == null) {
            throw new BizException(ResultCode.BIZ_NOT_MANAGEMENT);
        }
        // 2) 发起人的意图已通过发起动作表达，不参与投票
        if (session.getUserId().equals(userId)) {
            throw new BizException(ResultCode.BIZ_CONCEPT_REQUESTER_NO_VOTE);
        }
        int from = session.getStatus();
        int round;
        if (from == ConceptSession.STATUS_SUBMITTED) {
            round = 1;
        } else if (from == ConceptSession.STATUS_REVOTING) {
            round = 2;
        } else {
            throw new BizException(ResultCode.BIZ_CONCEPT_STATE_FORBIDDEN);
        }
        // 3) 同轮已投拦截（行锁内串行，重复投票在锁释放后被状态拦截或此处拦截）
        Long voted = conceptVoteMapper.selectCount(new LambdaQueryWrapper<ConceptVote>()
                .eq(ConceptVote::getConceptId, id)
                .eq(ConceptVote::getRound, round)
                .eq(ConceptVote::getVoterId, userId));
        if (voted > 0) {
            throw new BizException(ResultCode.BIZ_CONCEPT_ALREADY_VOTED);
        }
        // 4) 落票（首轮/复议共用一张表，round 区分；数据库 (concept_id, round, voter_id) 唯一索引兜底并发穿透）
        ConceptVote vote = new ConceptVote();
        vote.setConceptId(id);
        vote.setRound(round);
        vote.setVoterId(userId);
        vote.setResult(dto.getResult());
        vote.setComment(dto.getComment().trim());
        conceptVoteMapper.insert(vote);
        String voteLabel = round == 1 ? "赞成" : "改同意";
        if (vote.getResult() != ConceptVote.RESULT_APPROVE) {
            voteLabel = round == 1 ? "拒绝" : "再次拒绝";
        }
        trace(id, userId, round == 1 ? ConceptTrace.ACTION_VOTE : ConceptTrace.ACTION_REVOTE,
                voteLabel + "：" + vote.getComment());
        // 5) 集齐本轮全部票后按结果推进（行锁内串行，条件 SET 原子流转）
        List<Membership> management = managementMembers(clubId);
        boolean requesterInManagement = management.stream()
                .anyMatch(m -> m.getUserId().equals(session.getUserId()));
        int expected = management.size() - (requesterInManagement ? 1 : 0);
        Long cast = conceptVoteMapper.selectCount(new LambdaQueryWrapper<ConceptVote>()
                .eq(ConceptVote::getConceptId, id)
                .eq(ConceptVote::getRound, round));
        if (cast < expected) {
            return detail(clubId, id);
        }
        boolean anyReject = conceptVoteMapper.selectCount(new LambdaQueryWrapper<ConceptVote>()
                .eq(ConceptVote::getConceptId, id)
                .eq(ConceptVote::getRound, round)
                .eq(ConceptVote::getResult, ConceptVote.RESULT_REJECT)) > 0;
        if (!anyReject) {
            // 两票全赞成 → 待老师批复（deadline 随阶段重算 36h）
            if (advance(id, from, ConceptSession.STATUS_TEACHER_REVIEW, LocalDateTime.now().plus(STAGE_TIMEOUT)) == 0) {
                throw new BizException(ResultCode.BIZ_CONCEPT_STATE_FORBIDDEN);
            }
            trace(id, userId, ConceptTrace.ACTION_TO_TEACHER, "两票赞成，进入待老师批复（36 小时）");
        } else if (round == 1) {
            // 首轮出现拒绝票 → 强制复议（仅一轮，deadline 重算）
            if (advance(id, from, ConceptSession.STATUS_REVOTING, LocalDateTime.now().plus(STAGE_TIMEOUT)) == 0) {
                throw new BizException(ResultCode.BIZ_CONCEPT_STATE_FORBIDDEN);
            }
            trace(id, userId, ConceptTrace.ACTION_REVOTE_NEEDED, "出现拒绝票，进入复议（仅一轮，36 小时）");
        } else {
            // 复议再次拒绝 → 立即作废 + 通知三位管理层
            if (advance(id, from, ConceptSession.STATUS_VOIDED, null) == 0) {
                throw new BizException(ResultCode.BIZ_CONCEPT_STATE_FORBIDDEN);
            }
            trace(id, userId, ConceptTrace.ACTION_REVOTE_FAILED, "复议再次出现拒绝票，概念作废");
            notifyManagement(clubId, Message.TYPE_CONCEPT_VOID, "概念已作废",
                    "「" + brief(session) + "」复议再次出现拒绝票，已作废，可重新发起新概念", id);
        }
        return detail(clubId, id);
    }

    @Override
    @Transactional
    public ConceptVO teacherReview(Long clubId, Long id, Long userId, ConceptReviewDTO dto) {
        ConceptSession session = getOwned(clubId, id);
        // 仅该社团指导老师可批复（老师对自家社团有 activity:manage，此处收紧到本人）
        Club club = clubMapper.selectById(clubId);
        if (club == null || !club.getTeacherId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        if (session.getStatus() != ConceptSession.STATUS_TEACHER_REVIEW) {
            throw new BizException(ResultCode.BIZ_CONCEPT_STATE_FORBIDDEN);
        }
        boolean approve = Boolean.TRUE.equals(dto.getApprove());
        if (!approve && isBlank(dto.getComment())) {
            throw new BizException(ResultCode.BIZ_CONCEPT_REVIEW_COMMENT_REQUIRED);
        }
        if (approve) {
            if (advance(id, ConceptSession.STATUS_TEACHER_REVIEW, ConceptSession.STATUS_APPROVED, null) == 0) {
                throw new BizException(ResultCode.BIZ_CONCEPT_STATE_FORBIDDEN);
            }
            trace(id, userId, ConceptTrace.ACTION_TEACHER_APPROVE, dto.getComment());
            // 块 A：概念通过 → 自动创建活动（公示中）+ 全员公示通知（原管理层通过通知并入公示通知）
            activityService.createFromConcept(session);
        } else {
            if (advance(id, ConceptSession.STATUS_TEACHER_REVIEW, ConceptSession.STATUS_VOIDED, null) == 0) {
                throw new BizException(ResultCode.BIZ_CONCEPT_STATE_FORBIDDEN);
            }
            trace(id, userId, ConceptTrace.ACTION_TEACHER_REJECT, dto.getComment());
            notifyManagement(clubId, Message.TYPE_CONCEPT_VOID, "概念被指导老师否决",
                    "「" + brief(session) + "」被指导老师否决：" + dto.getComment(), id);
        }
        return detail(clubId, id);
    }

    /**
     * 36h 超时扫描：每分钟检查审批链节点（已提交/复议中/待老师批复），
     * 超时自动作废并通知三位管理层；老师批复超时额外通知老师本人。
     */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void scanTimeout() {
        List<ConceptSession> expired = conceptSessionMapper.selectList(new LambdaQueryWrapper<ConceptSession>()
                .in(ConceptSession::getStatus, ConceptSession.STATUS_SUBMITTED,
                        ConceptSession.STATUS_REVOTING, ConceptSession.STATUS_TEACHER_REVIEW)
                .lt(ConceptSession::getDeadline, LocalDateTime.now()));
        for (ConceptSession session : expired) {
            int from = session.getStatus();
            // 条件 SET 更新（原子）：状态→作废 + deadline 清理；并发下其他路径已处理则跳过
            if (advance(session.getId(), from, ConceptSession.STATUS_VOIDED, null) == 0) {
                continue;
            }
            String stage = from == ConceptSession.STATUS_TEACHER_REVIEW ? "指导老师批复" : "管理层审阅";
            trace(session.getId(), null, ConceptTrace.ACTION_TIMEOUT_VOID,
                    "等待" + stage + "超过 36 小时，自动作废");
            notifyManagement(session.getClubId(), Message.TYPE_CONCEPT_VOID, "概念超时自动作废",
                    "「" + brief(session) + "」等待" + stage + "超过 36 小时，已自动作废，可重新发起新概念",
                    session.getId());
            if (from == ConceptSession.STATUS_TEACHER_REVIEW) {
                // 老师超时导致作废：老师必须知道自己漏了（打破"老师不接收作废通知"，仅此场景）
                Club club = clubMapper.selectById(session.getClubId());
                if (club != null && club.getTeacherId() != null) {
                    notifyUser(club.getTeacherId(), Message.TYPE_CONCEPT_VOID, "概念因等待您的批复超时作废",
                            "「" + brief(session) + "」等待您的批复超过 36 小时，已自动作废", session.getId());
                }
            }
        }
    }

    /** 状态推进（条件 SET 原子流转，from → to；deadline 传入新值或 null 清理；0 行=状态已被并发修改） */
    private int advance(Long id, int from, int to, LocalDateTime newDeadline) {
        return conceptSessionMapper.update(null, new LambdaUpdateWrapper<ConceptSession>()
                .eq(ConceptSession::getId, id)
                .eq(ConceptSession::getStatus, from)
                .set(ConceptSession::getStatus, to)
                .set(ConceptSession::getDeadline, newDeadline));
    }

    /** 发起人离职：作废其在该社团的全部活跃概念（resign_void 留痕 + 通知现任管理层） */
    @Override
    @Transactional
    public void voidActiveOnResign(Long clubId, Long userId) {
        List<ConceptSession> active = conceptSessionMapper.selectList(new LambdaQueryWrapper<ConceptSession>()
                .eq(ConceptSession::getClubId, clubId)
                .eq(ConceptSession::getUserId, userId)
                .in(ConceptSession::getStatus, ACTIVE_STATUSES));
        for (ConceptSession session : active) {
            if (advance(session.getId(), session.getStatus(), ConceptSession.STATUS_VOIDED, null) == 0) {
                continue;
            }
            trace(session.getId(), userId, ConceptTrace.ACTION_RESIGN_VOID, "发起人离职，概念作废");
            notifyManagement(clubId, Message.TYPE_CONCEPT_VOID, "概念因发起人离职作废",
                    "「" + brief(session) + "」发起人已离职，概念作废，可重新发起新概念", session.getId());
        }
    }

    /** 取概念并校验社团归属 */
    private ConceptSession getOwned(Long clubId, Long id) {
        ConceptSession session = conceptSessionMapper.selectById(id);
        if (session == null || !session.getClubId().equals(clubId)) {
            throw new BizException(ResultCode.BIZ_CONCEPT_NOT_FOUND);
        }
        return session;
    }

    /** 流水留痕（全量时间线；operatorId=null 表示系统动作，如超时扫描） */
    private void trace(Long conceptId, Long operatorId, String action, String detail) {
        ConceptTrace t = new ConceptTrace();
        t.setConceptId(conceptId);
        t.setOperatorId(operatorId);
        t.setOperatorName(operatorId == null ? "系统" : ownership.nicknameOf(operatorId));
        t.setAction(action);
        t.setDetail(detail);
        conceptTraceMapper.insert(t);
    }

    private void applyDraft(ConceptSession session, ConceptDraftDTO dto) {
        if (dto == null) {
            return;
        }
        session.setReason(trimToNull(dto.getReason()));
        session.setPlannedTime(trimToNull(dto.getPlannedTime()));
        session.setPlannedLocation(trimToNull(dto.getPlannedLocation()));
        session.setContent(trimToNull(dto.getContent()));
    }

    private ConceptVO toVO(ConceptSession s, String requesterNickname) {
        ConceptVO vo = new ConceptVO();
        vo.setId(s.getId());
        vo.setClubId(s.getClubId());
        vo.setUserId(s.getUserId());
        vo.setRequesterNickname(requesterNickname);
        vo.setStatus(s.getStatus());
        vo.setReason(s.getReason());
        vo.setPlannedTime(s.getPlannedTime());
        vo.setPlannedLocation(s.getPlannedLocation());
        vo.setContent(s.getContent());
        vo.setSubmittedAt(s.getSubmittedAt());
        vo.setAiBrief(s.getAiBrief());
        vo.setDeadline(s.getDeadline());
        vo.setCreatedAt(s.getCreatedAt());
        vo.setUpdatedAt(s.getUpdatedAt());
        return vo;
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** 用户在该社团的已通过成员身份（投票资格校验：老师无 membership，天然被拦） */
    private Membership membershipOf(Long clubId, Long userId) {
        return membershipMapper.selectOne(new LambdaQueryWrapper<Membership>()
                .eq(Membership::getUserId, userId)
                .eq(Membership::getClubId, clubId)
                .eq(Membership::getStatus, Membership.STATUS_APPROVED));
    }

    /** 该社团全部管理层成员（president/vice_president，已通过身份） */
    private List<Membership> managementMembers(Long clubId) {
        List<RbacRole> roles = rbacRoleMapper.selectList(new LambdaQueryWrapper<RbacRole>()
                .in(RbacRole::getCode, RoleConstants.PRESIDENT, RoleConstants.VICE_PRESIDENT));
        if (roles.isEmpty()) {
            return List.of();
        }
        return membershipMapper.selectList(new LambdaQueryWrapper<Membership>()
                .eq(Membership::getClubId, clubId)
                .eq(Membership::getStatus, Membership.STATUS_APPROVED)
                .in(Membership::getRoleId, roles.stream().map(RbacRole::getId).toList()));
    }

    /** 站内消息：通知该社团全部管理层 */
    private void notifyManagement(Long clubId, String type, String title, String content, Long refConceptId) {
        for (Membership m : managementMembers(clubId)) {
            notifyUser(m.getUserId(), type, title, content, refConceptId);
        }
    }

    /** 站内消息：通知单用户（老师超时场景额外通知老师本人） */
    private void notifyUser(Long userId, String type, String title, String content, Long refConceptId) {
        Message msg = new Message();
        msg.setRecipientId(userId);
        msg.setType(type);
        msg.setTitle(title);
        msg.setContent(content);
        msg.setRefConceptId(refConceptId);
        msg.setReadFlag(0);
        messageMapper.insert(msg);
    }

    /** 概念简述（通知标题用，取发起理由前 20 字） */
    private static String brief(ConceptSession s) {
        String r = s.getReason();
        if (isBlank(r)) {
            return "概念";
        }
        return r.length() > 20 ? r.substring(0, 20) + "…" : r;
    }
}
