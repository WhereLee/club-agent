package com.club.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.club.agent.common.ResultCode;
import com.club.agent.dto.ClubCreateDTO;
import com.club.agent.entity.Club;
import com.club.agent.entity.Membership;
import com.club.agent.entity.RbacRole;
import com.club.agent.entity.SysUser;
import com.club.agent.exception.BizException;
import com.club.agent.mapper.ClubMapper;
import com.club.agent.mapper.MembershipMapper;
import com.club.agent.mapper.RbacRoleMapper;
import com.club.agent.mapper.SysUserMapper;
import com.club.agent.service.ClubService;
import com.club.agent.vo.ClubDetailVO;
import com.club.agent.vo.ClubVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 社团实现。
 */
@Service
@RequiredArgsConstructor
public class ClubServiceImpl implements ClubService {

    private final ClubMapper clubMapper;
    private final MembershipMapper membershipMapper;
    private final SysUserMapper userMapper;
    private final RbacRoleMapper roleMapper;

    @Override
    public ClubVO create(ClubCreateDTO dto, Long operatorId) {
        SysUser operator = userMapper.selectById(operatorId);
        if (operator == null || !Boolean.TRUE.equals(operator.getIsTeacher())) {
            throw new BizException(ResultCode.BIZ_TEACHER_ONLY);
        }
        Long nameCount = clubMapper.selectCount(
                new LambdaQueryWrapper<Club>().eq(Club::getName, dto.getName()));
        if (nameCount != null && nameCount > 0) {
            throw new BizException(ResultCode.BIZ_CLUB_NAME_EXISTS);
        }
        Club club = new Club();
        club.setName(dto.getName());
        club.setDescription(dto.getDescription());
        club.setTeacherId(operatorId);
        try {
            clubMapper.insert(club);
        } catch (DuplicateKeyException e) {
            throw new BizException(ResultCode.BIZ_CLUB_NAME_EXISTS);
        }
        ClubVO vo = new ClubVO();
        vo.setId(club.getId());
        vo.setName(club.getName());
        vo.setDescription(club.getDescription());
        vo.setTeacherName(operator.getNickname());
        vo.setMemberCount(0L);
        vo.setCreatedAt(club.getCreatedAt());
        return vo;
    }

    @Override
    public IPage<ClubVO> list(long page, long size) {
        return clubMapper.selectClubPage(new Page<>(page, size));
    }

    @Override
    public ClubDetailVO detail(Long clubId, Long userId) {
        Club club = clubMapper.selectById(clubId);
        if (club == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "社团不存在");
        }
        SysUser teacher = userMapper.selectById(club.getTeacherId());
        Long memberCount = membershipMapper.selectCount(
                new LambdaQueryWrapper<Membership>()
                        .eq(Membership::getClubId, clubId)
                        .eq(Membership::getStatus, Membership.STATUS_APPROVED));

        ClubDetailVO vo = new ClubDetailVO();
        vo.setId(club.getId());
        vo.setName(club.getName());
        vo.setDescription(club.getDescription());
        vo.setTeacherName(teacher == null ? "" : teacher.getNickname());
        vo.setMemberCount(memberCount);
        vo.setMyStatus(-1);

        // 当前用户身份（老师看自己创建的社团：直接标记为已加入，角色 teacher）
        if (club.getTeacherId().equals(userId)) {
            vo.setMyStatus(Membership.STATUS_APPROVED);
            vo.setMyRoleCode("teacher");
            vo.setMyRoleName("指导老师");
            return vo;
        }
        Membership membership = membershipMapper.selectOne(
                new LambdaQueryWrapper<Membership>()
                        .eq(Membership::getUserId, userId)
                        .eq(Membership::getClubId, clubId));
        if (membership != null) {
            vo.setMyStatus(membership.getStatus());
            vo.setMyMembershipId(membership.getId());
            RbacRole role = roleMapper.selectById(membership.getRoleId());
            if (role != null) {
                vo.setMyRoleCode(role.getCode());
                vo.setMyRoleName(role.getName());
            }
        }
        return vo;
    }
}
