package com.club.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.club.agent.entity.Membership;
import com.club.agent.vo.MemberVO;
import com.club.agent.vo.MyClubVO;
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
}
