package com.club.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.club.agent.common.ResultCode;
import com.club.agent.entity.Activity;
import com.club.agent.entity.ActivityAttendance;
import com.club.agent.entity.ActivitySignup;
import com.club.agent.entity.SysUser;
import com.club.agent.exception.BizException;
import com.club.agent.mapper.ActivityAttendanceMapper;
import com.club.agent.mapper.ActivityMapper;
import com.club.agent.mapper.ActivitySignupMapper;
import com.club.agent.mapper.SysUserMapper;
import com.club.agent.service.AttendanceService;
import com.club.agent.vo.AttendanceVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 签到服务实现（块 G）：
 * - 窗口：状态=执行中(6)；未报名/不参加者不可签（1046）；uk 幂等
 * - 名单：报名参加者 + 签到状态（管理层视图）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceServiceImpl implements AttendanceService {

    private final ActivityMapper activityMapper;
    private final ActivitySignupMapper signupMapper;
    private final ActivityAttendanceMapper attendanceMapper;
    private final SysUserMapper sysUserMapper;

    @Override
    @Transactional
    public void checkin(Long clubId, Long activityId, Long userId) {
        Activity a = activityMapper.selectById(activityId);
        if (a == null || !a.getClubId().equals(clubId)) {
            throw new BizException(ResultCode.BIZ_ACTIVITY_NOT_FOUND);
        }
        if (a.getStatus() != Activity.STATUS_EXECUTING) {
            throw new BizException(ResultCode.BIZ_ACTIVITY_STATE_FORBIDDEN);
        }
        // 仅报名参加者可签（1046）
        ActivitySignup s = signupMapper.selectOne(new LambdaQueryWrapper<ActivitySignup>()
                .eq(ActivitySignup::getActivityId, activityId)
                .eq(ActivitySignup::getUserId, userId));
        if (s == null || !ActivitySignup.CHOICE_PARTICIPATE.equals(s.getChoice())) {
            throw new BizException(ResultCode.BIZ_ATTENDANCE_FORBIDDEN);
        }
        // 幂等：已签到直接返回
        Long cnt = attendanceMapper.selectCount(new LambdaQueryWrapper<ActivityAttendance>()
                .eq(ActivityAttendance::getActivityId, activityId)
                .eq(ActivityAttendance::getUserId, userId));
        if (cnt != null && cnt > 0) {
            return;
        }
        ActivityAttendance at = new ActivityAttendance();
        at.setId(IdWorker.getId());
        at.setActivityId(activityId);
        at.setUserId(userId);
        at.setCheckedAt(LocalDateTime.now());
        at.setCreatedAt(LocalDateTime.now());
        attendanceMapper.insert(at);
    }

    @Override
    public List<AttendanceVO> list(Long clubId, Long activityId) {
        Activity a = activityMapper.selectById(activityId);
        if (a == null || !a.getClubId().equals(clubId)) {
            throw new BizException(ResultCode.BIZ_ACTIVITY_NOT_FOUND);
        }
        Map<Long, ActivityAttendance> attMap = attendanceMapper.selectList(
                        new LambdaQueryWrapper<ActivityAttendance>().eq(ActivityAttendance::getActivityId, activityId))
                .stream().collect(Collectors.toMap(ActivityAttendance::getUserId, Function.identity()));
        List<ActivitySignup> participants = signupMapper.selectList(new LambdaQueryWrapper<ActivitySignup>()
                .eq(ActivitySignup::getActivityId, activityId)
                .eq(ActivitySignup::getChoice, ActivitySignup.CHOICE_PARTICIPATE));
        List<AttendanceVO> list = new ArrayList<>();
        for (ActivitySignup s : participants) {
            AttendanceVO vo = new AttendanceVO();
            vo.setUserId(s.getUserId());
            SysUser u = sysUserMapper.selectById(s.getUserId());
            vo.setNickname(u == null ? "未知" : u.getNickname());
            ActivityAttendance at = attMap.get(s.getUserId());
            vo.setSigned(at != null);
            vo.setCheckedAt(at == null ? null : at.getCheckedAt());
            list.add(vo);
        }
        return list;
    }
}
