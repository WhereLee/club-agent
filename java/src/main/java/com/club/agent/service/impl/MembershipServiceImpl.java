package com.club.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import com.club.agent.service.ConceptService;
import com.club.agent.service.MembershipService;
import com.club.agent.util.RoleConstants;
import com.club.agent.vo.MemberVO;
import com.club.agent.vo.MyClubVO;
import com.club.agent.vo.TodoVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
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
    private final ConceptService conceptService;

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
            // 被拒后重新申请：仅当仍是已拒绝状态才重置（CAS，防与审批并发时覆盖新状态）
            int rows = membershipMapper.update(null, new LambdaUpdateWrapper<Membership>()
                    .eq(Membership::getId, existing.getId())
                    .eq(Membership::getStatus, Membership.STATUS_REJECTED)
                    .set(Membership::getStatus, Membership.STATUS_PENDING)
                    .set(Membership::getRoleId, memberRole.getId())
                    .set(Membership::getAppliedAt, LocalDateTime.now()));
            if (rows == 0) {
                throwReapplyConflict(existing.getId());
            }
            return;
        }
        Membership membership = new Membership();
        membership.setUserId(userId);
        membership.setClubId(clubId);
        membership.setRoleId(memberRole.getId());
        membership.setStatus(Membership.STATUS_PENDING);
        membership.setAppliedAt(LocalDateTime.now());
        try {
            membershipMapper.insert(membership);
        } catch (DuplicateKeyException e) {
            // 并发双击申请：唯一索引兜底，转友好提示
            throw new BizException(ResultCode.BIZ_ALREADY_APPLIED);
        }
    }

    @Override
    public void approve(Long clubId, Long membershipId, Long operatorId) {
        requireMembershipOf(clubId, membershipId);
        // CAS 条件更新：仅待审批状态可流转，防并发双击后写覆盖
        int rows = membershipMapper.update(null, new LambdaUpdateWrapper<Membership>()
                .eq(Membership::getId, membershipId)
                .eq(Membership::getStatus, Membership.STATUS_PENDING)
                .set(Membership::getStatus, Membership.STATUS_APPROVED)
                .set(Membership::getApprovedBy, operatorId)
                .set(Membership::getApprovedAt, LocalDateTime.now()));
        if (rows == 0) {
            throw new BizException(ResultCode.BIZ_APPLY_HANDLED);
        }
    }

    @Override
    public void reject(Long clubId, Long membershipId, Long operatorId) {
        requireMembershipOf(clubId, membershipId);
        int rows = membershipMapper.update(null, new LambdaUpdateWrapper<Membership>()
                .eq(Membership::getId, membershipId)
                .eq(Membership::getStatus, Membership.STATUS_PENDING)
                .set(Membership::getStatus, Membership.STATUS_REJECTED)
                .set(Membership::getApprovedBy, operatorId)
                .set(Membership::getApprovedAt, LocalDateTime.now()));
        if (rows == 0) {
            throw new BizException(ResultCode.BIZ_APPLY_HANDLED);
        }
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
        // 任职届数：目标职务计数器 +1（副社长升社长 → 社长届数+1，届数标记随新职务）
        Club club = clubMapper.selectById(clubId);
        if (club == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "社团不存在");
        }
        Long termNo;
        if (RoleConstants.PRESIDENT.equals(role)) {
            club.setPresidentTermNo(safeTermNo(club.getPresidentTermNo()) + 1);
            termNo = club.getPresidentTermNo();
        } else {
            club.setVicePresidentTermNo(safeTermNo(club.getVicePresidentTermNo()) + 1);
            termNo = club.getVicePresidentTermNo();
        }
        clubMapper.updateById(club);

        membership.setRoleId(targetRole.getId());
        membership.setTermNo(termNo);
        membership.setFormerRoleCode(null); // 当前在职，清空前任标记
        membershipMapper.updateById(membership);
    }

    /** 届数计数器容错：存量数据可能为 null，按 0 处理 */
    private long safeTermNo(Long termNo) {
        return termNo == null ? 0L : termNo;
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
            // 普通成员退出：直接删除成员关系
            membershipMapper.deleteById(membership.getId());
            return;
        }
        // 管理层离职：角色降为社员，term_no/former_role_code 保留（第X任标记），位置空出；人留在社团
        // 联动：发起人离职 → 其在该社团的活跃概念自动作废（resign_void 留痕 + 通知现任管理层），释放唯一名额
        conceptService.voidActiveOnResign(clubId, userId);
        membership.setRoleId(requireRole(RoleConstants.MEMBER).getId());
        membership.setFormerRoleCode(currentRole.getCode());
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

    @Override
    public List<MyClubVO> managedClubs(Long teacherId) {
        return membershipMapper.selectManagedClubs(teacherId);
    }

    @Override
    public List<TodoVO> pendingTodosByTeacher(Long teacherId) {
        return membershipMapper.selectPendingTodosByTeacher(teacherId);
    }

    @Override
    public List<TodoVO> pendingTodosByManagement(Long userId) {
        return membershipMapper.selectPendingTodosByManagement(userId);
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

    /** 重新申请 CAS 失败：并发下状态已变化，按最新状态给出准确提示 */
    private void throwReapplyConflict(Long membershipId) {
        Membership latest = membershipMapper.selectById(membershipId);
        if (latest == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "成员记录不存在");
        }
        if (latest.getStatus() == Membership.STATUS_APPROVED) {
            throw new BizException(ResultCode.BIZ_ALREADY_MEMBER);
        }
        if (latest.getStatus() == Membership.STATUS_PENDING) {
            throw new BizException(ResultCode.BIZ_ALREADY_APPLIED);
        }
        throw new BizException(ResultCode.BIZ_APPLY_HANDLED);
    }
}
