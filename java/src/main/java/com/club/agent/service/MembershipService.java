package com.club.agent.service;

import com.club.agent.vo.MemberVO;
import com.club.agent.vo.MyClubVO;
import com.club.agent.vo.TodoVO;

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

    /** 离职/退出（管理层：角色降为社员并保留第X任标记；普通成员：删除成员关系） */
    void resign(Long clubId, Long userId);

    /** 社团成员列表（含待审批，老师/管理层可见） */
    List<MemberVO> listMembers(Long clubId);

    /** 我的社团 */
    List<MyClubVO> myClubs(Long userId);

    /** 我管理的社团（老师视角：按 club.teacher_id 归属） */
    List<MyClubVO> managedClubs(Long teacherId);

    /** 待办：老师管理的所有社团的待审批申请 */
    List<TodoVO> pendingTodosByTeacher(Long teacherId);

    /** 待办：管理层所在社团的待审批申请（普通成员返回空列表） */
    List<TodoVO> pendingTodosByManagement(Long userId);
}
