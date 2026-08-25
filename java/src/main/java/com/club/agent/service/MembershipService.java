package com.club.agent.service;

import com.club.agent.vo.MemberVO;
import com.club.agent.vo.MyClubVO;

import java.util.List;

/**
 * 成员关系服务：申请 / 审批 / 任命 / 离职。
 */
public interface MembershipService {

    /** 申请加入（学生；已加入/申请中拦截，被拒后重新申请） */
    void apply(Long clubId, Long userId);

    /** 审批通过（老师/管理层） */
    void approve(Long clubId, Long membershipId, Long operatorId);

    /** 拒绝（老师/管理层）；被拒者可重新申请 */
    void reject(Long clubId, Long membershipId, Long operatorId);

    /** 任命管理层（仅老师；只进空位，槽位校验 + 触发器兜底） */
    void appoint(Long clubId, Long membershipId, String role, Long operatorId);

    /** 管理层离职（本人；角色降为社员，位置空出） */
    void resign(Long clubId, Long userId);

    /** 社团成员列表（含待审批，老师/管理层可见） */
    List<MemberVO> listMembers(Long clubId);

    /** 我的社团 */
    List<MyClubVO> myClubs(Long userId);
}
