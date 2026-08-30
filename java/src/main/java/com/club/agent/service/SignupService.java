package com.club.agent.service;

import com.club.agent.vo.SignupMemberVO;

import java.util.List;

/**
 * 报名服务（块 F）：
 * 成员报名（状态=报名中 + 截止前；问卷不感兴趣者限制参加，在线协助放行并提示发起人）；
 * 名单（管理层视图：全员报名状态 + 拦截标记）。
 */
public interface SignupService {

    /** 报名/修改报名（uk 一人一条，截止前可改） */
    void signup(Long clubId, Long activityId, Long userId, String choice, Boolean onlineAssist);

    /** 报名名单（管理层） */
    List<SignupMemberVO> list(Long clubId, Long activityId);
}
