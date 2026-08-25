package com.club.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.club.agent.common.ResultCode;
import com.club.agent.entity.Club;
import com.club.agent.entity.Membership;
import com.club.agent.entity.RbacRole;
import com.club.agent.entity.SysUser;
import com.club.agent.exception.BizException;
import com.club.agent.mapper.ClubMapper;
import com.club.agent.mapper.MembershipMapper;
import com.club.agent.mapper.RbacRoleMapper;
import com.club.agent.mapper.SysUserMapper;
import com.club.agent.service.MembershipService;
import com.club.agent.util.RoleConstants;
import com.club.agent.vo.MemberVO;
import com.club.agent.vo.MyClubVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 成员关系实现：申请 / 审批 / 任命 / 离职。
 * 换届模型：任命只进空位——槽位由在职者离职空出（应用层预检给友好提示，触发器兜底并发）。
 */
@Service
@RequiredArgsConstructor
public class MembershipServiceImpl implements MembershipService {

    private final ClubMapper clubMapper;
    private final MembershipMapper membershipMapper;
    private final RbacRoleMapper roleMapper;
    private final SysUserMapper userMapper;

    @Override
    public void apply(Long clubId, Long userId) {
        requireClub(clubId);
        SysUser user = userMapper.selectById(userId);
        if (user == null || Boolean.TRUE.equals(user.getIsTeacher())) {
            throw new BizException(ResultCode.BIZ_APPLY_STUDENT_ONLY);
        }
        RbacRole memberRole = requireRole(RoleConstants.MEMBER);

        Membership existing = membershipMapper.selectOne(
                new LambdaQueryWrapper<Membership>()
                        .eq(Membership::getUserId, userId)
                        .eq(Membership::getClubId, clubId));
        if (existing != null) {
            if (existing.getStatus() == Membership.STATUS_APPROVED) {
                throw new BizException(ResultCode.BIZ_ALREADY_MEMBER);
            }
            if (existing.getStatus() == Membership.STATUS_PENDING) {
                throw new BizException(ResultCode.BIZ_ALREADY_APPLIED);
            }
            // 被拒后重新申请：复用记录，状态回申请中
            existing.setStatus(Membership.STATUS_PENDING);
            existing.setRoleId(memberRole.getId());
            existing.setAppliedAt(LocalDateTime.now());
            membershipMapper.updateById(existing);
            return;
        }
        Membership membership = new Membership();
        membership.setUserId(userId);
        membership.setClubId(clubId);
        membership.setRoleId(memberRole.getId());
        membership.setStatus(Membership.STATUS_PENDING);
        membership.setAppliedAt(LocalDateTime.now());
        membershipMapper.insert(membership);
    }

    @Override
    public void approve(Long clubId, Long membershipId, Long operatorId) {
        Membership membership = requireMembershipOf(clubId, membershipId);
        if (membership.getStatus() != Membership.STATUS_PENDING) {
            throw new BizException(ResultCode.BIZ_APPLY_HANDLED);
        }
        membership.setStatus(Membership.STATUS_APPROVED);
        membership.setApprovedBy(operatorId);
        membership.setApprovedAt(LocalDateTime.now());
        membershipMapper.updateById(membership);
    }

    @Override
    public void reject(Long clubId, Long membershipId, Long operatorId) {
        Membership membership = requireMembershipOf(clubId, membershipId);
        if (membership.getStatus() != Membership.STATUS_PENDING) {
            throw new BizException(ResultCode.BIZ_APPLY_HANDLED);
        }
        membership.setStatus(Membership.STATUS_REJECTED);
        membership.setApprovedBy(operatorId);
        membership.setApprovedAt(LocalDateTime.now());
        membershipMapper.updateById(membership);
    }

    @Override
    public void appoint(Long clubId, Long membershipId, String role, Long operatorId) {
        Membership membership = requireMembershipOf(clubId, membershipId);
        if (membership.getStatus() != Membership.STATUS_APPROVED) {
            throw new BizException("只能任命已加入的成员");
        }
        if (!RoleConstants.PRESIDENT.equals(role) && !RoleConstants.VICE_PRESIDENT.equals(role)) {
            throw new BizException(ResultCode.BIZ_INVALID_ROLE);
        }
        RbacRole targetRole = requireRole(role);

        // 已是目标职务：重复任命拒绝（幂等不放行，保持语义清晰）
        if (membership.getRoleId().equals(targetRole.getId())) {
            throw new BizException(ResultCode.BIZ_ALREADY_APPOINTED);
        }

        // 槽位预检（友好提示；并发兜底由数据库触发器负责）
        Long occupying = membershipMapper.selectCount(
                new LambdaQueryWrapper<Membership>()
                        .eq(Membership::getClubId, clubId)
                        .eq(Membership::getStatus, Membership.STATUS_APPROVED)
                        .eq(Membership::getRoleId, targetRole.getId())
                        .ne(Membership::getId, membershipId));
        if (RoleConstants.PRESIDENT.equals(role)) {
            if (occupying != null && occupying > 0) {
                throw new BizException(ResultCode.BIZ_PRESIDENT_EXISTS);
            }
        } else {
            if (occupying != null && occupying >= 2) {
                throw new BizException(ResultCode.BIZ_VICE_PRESIDENT_FULL);
            }
        }
        // 跨社团管理层唯一预检
        List<Long> managementRoleIds = roleMapper.selectList(
                        new LambdaQueryWrapper<RbacRole>().eq(RbacRole::getIsManagement, true))
                .stream().map(RbacRole::getId).toList();
        if (!managementRoleIds.isEmpty()) {
            Long other = membershipMapper.selectCount(
                    new LambdaQueryWrapper<Membership>()
                            .eq(Membership::getUserId, membership.getUserId())
                            .eq(Membership::getStatus, Membership.STATUS_APPROVED)
                            .in(Membership::getRoleId, managementRoleIds)
                            .ne(Membership::getId, membershipId));
            if (other != null && other > 0) {
                throw new BizException(ResultCode.BIZ_ALREADY_MANAGEMENT);
            }
        }
        membership.setRoleId(targetRole.getId());
        membershipMapper.updateById(membership);
    }

    @Override
    public void resign(Long clubId, Long userId) {
        Membership membership = membershipMapper.selectOne(
                new LambdaQueryWrapper<Membership>()
                        .eq(Membership::getUserId, userId)
                        .eq(Membership::getClubId, clubId));
        if (membership == null || membership.getStatus() != Membership.STATUS_APPROVED) {
            throw new BizException(ResultCode.BIZ_NOT_CLUB_MEMBER);
        }
        RbacRole currentRole = roleMapper.selectById(membership.getRoleId());
        if (currentRole == null || !Boolean.TRUE.equals(currentRole.getIsManagement())) {
            throw new BizException(ResultCode.BIZ_NOT_MANAGEMENT);
        }
        // 离职：角色降为社员，位置空出；人留在社团
        membership.setRoleId(requireRole(RoleConstants.MEMBER).getId());
        membershipMapper.updateById(membership);
    }

    @Override
    public List<MemberVO> listMembers(Long clubId) {
        return membershipMapper.selectMemberList(clubId);
    }

    @Override
    public List<MyClubVO> myClubs(Long userId) {
        return membershipMapper.selectMyClubs(userId);
    }

    private Club requireClub(Long clubId) {
        Club club = clubMapper.selectById(clubId);
        if (club == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "社团不存在");
        }
        return club;
    }

    private Membership requireMembershipOf(Long clubId, Long membershipId) {
        Membership membership = membershipMapper.selectById(membershipId);
        if (membership == null || !membership.getClubId().equals(clubId)) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "成员记录不存在");
        }
        return membership;
    }

    private RbacRole requireRole(String code) {
        RbacRole role = roleMapper.selectOne(
                new LambdaQueryWrapper<RbacRole>().eq(RbacRole::getCode, code));
        if (role == null) {
            throw new BizException("角色未初始化: " + code);
        }
        return role;
    }
}
