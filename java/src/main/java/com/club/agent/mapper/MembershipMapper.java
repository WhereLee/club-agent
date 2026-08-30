package com.club.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.club.agent.entity.Membership;
import com.club.agent.vo.ClubContextVO;
import com.club.agent.vo.MemberVO;
import com.club.agent.vo.MyClubVO;
import com.club.agent.vo.TodoVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 成员关系 Mapper（复杂查询走 XML：resources/mapper/MembershipMapper.xml）。
 */
@Mapper
public interface MembershipMapper extends BaseMapper<Membership> {

    /** 社团成员列表（含待审批，JOIN 用户/角色信息） */
    List<MemberVO> selectMemberList(@Param("clubId") Long clubId);

    /** 我的社团（当前用户全部 membership，JOIN 社团/角色信息） */
    List<MyClubVO> selectMyClubs(@Param("userId") Long userId);

    /** 我管理的社团（老师视角：按 club.teacher_id 归属） */
    List<MyClubVO> selectManagedClubs(@Param("teacherId") Long teacherId);

    /** 待办：老师管理的所有社团的待审批申请 */
    List<TodoVO> selectPendingTodosByTeacher(@Param("teacherId") Long teacherId);

    /** 待办：管理层所在社团的待审批申请 */
    List<TodoVO> selectPendingTodosByManagement(@Param("userId") Long userId);

    /** AI 起草工具：当前社团管理层名单（join 角色/用户昵称） */
    List<ClubContextVO.ManagerVO> selectManagersByClub(@Param("clubId") Long clubId);
}
