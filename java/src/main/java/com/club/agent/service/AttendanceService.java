package com.club.agent.service;

import com.club.agent.vo.AttendanceVO;

import java.util.List;

/**
 * 签到服务（块 G）：
 * 签到（执行中开放，仅报名参加者可签，幂等）；名单（管理层：参加者 + 签到状态）。
 */
public interface AttendanceService {

    /** 签到（未报名参加 1046；重复签到幂等） */
    void checkin(Long clubId, Long activityId, Long userId);

    /** 签到名单（管理层视图：报名参加者 + 签到状态） */
    List<AttendanceVO> list(Long clubId, Long activityId);
}
