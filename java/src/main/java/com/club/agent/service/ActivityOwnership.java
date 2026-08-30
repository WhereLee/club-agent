package com.club.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.club.agent.common.ResultCode;
import com.club.agent.entity.Activity;
import com.club.agent.entity.Membership;
import com.club.agent.entity.SysUser;
import com.club.agent.exception.BizException;
import com.club.agent.mapper.ActivityMapper;
import com.club.agent.mapper.MembershipMapper;
import com.club.agent.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 活动归属校验公共组件（Q1 收口，反屎山）：
 * 原先 getOwned/requireOwner 在 4 个 Service 各写一份，规则变更需改多处；
 * 归属规则统一在此：跨社团/不存在 → 1035，非发起人 → 403。
 * briefOf/isBlank 为通知文案工具（多 Service 复用，静态就近）。
 */
@Component
@RequiredArgsConstructor
public class ActivityOwnership {

    private final ActivityMapper activityMapper;
    private final MembershipMapper membershipMapper;
    private final SysUserMapper sysUserMapper;

    /** 校验活动存在且属于该社团，返回活动（不存在/跨社团 → 1035） */
    public Activity getOwned(Long clubId, Long activityId) {
        Activity a = activityMapper.selectById(activityId);
        if (a == null || !a.getClubId().equals(clubId)) {
            throw new BizException(ResultCode.BIZ_ACTIVITY_NOT_FOUND);
        }
        return a;
    }

    /** 校验发起人本人（非发起人 → 403） */
    public Activity requireOwner(Long clubId, Long activityId, Long userId) {
        Activity a = getOwned(clubId, activityId);
        if (!a.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        return a;
    }

    /** 对象版（已持有 Activity 时避免二次查询） */
    public void requireOwner(Activity activity, Long userId) {
        if (!activity.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
    }

    /** 通知文案简题：时间+地点 优先，退化到内容截断 */
    public static String briefOf(Activity a) {
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

    public static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** 成员昵称（用户不存在兜底空串；trace 操作人展示用） */
    public String nicknameOf(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        return user == null ? "" : user.getNickname();
    }

    /** 已批准成员列表（通知全员/职责展示共用） */
    public List<Membership> approvedMembers(Long clubId) {
        return membershipMapper.selectList(new LambdaQueryWrapper<Membership>()
                .eq(Membership::getClubId, clubId)
                .eq(Membership::getStatus, Membership.STATUS_APPROVED));
    }
}
