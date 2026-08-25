package com.club.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.club.agent.common.ResultCode;
import com.club.agent.entity.Club;
import com.club.agent.entity.Membership;
import com.club.agent.entity.RbacPermission;
import com.club.agent.entity.RbacRole;
import com.club.agent.entity.SysUser;
import com.club.agent.exception.BizException;
import com.club.agent.mapper.ClubMapper;
import com.club.agent.mapper.MembershipMapper;
import com.club.agent.mapper.RbacPermissionMapper;
import com.club.agent.mapper.RbacRoleMapper;
import com.club.agent.mapper.RbacRolePermissionMapper;
import com.club.agent.mapper.SysUserMapper;
import com.club.agent.service.ClubSecurityService;
import com.club.agent.util.RoleConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 社团上下文权限实现：
 * - 老师（is_teacher 且为该社团指导老师）→ 按 teacher 角色权限集
 * - 学生 → 按 membership 已通过身份的角色权限集
 * - 老师非本社团指导老师 / 无身份 → 无权限
 */
@Service
@RequiredArgsConstructor
public class ClubSecurityServiceImpl implements ClubSecurityService {

    private final SysUserMapper userMapper;
    private final ClubMapper clubMapper;
    private final MembershipMapper membershipMapper;
    private final RbacRoleMapper roleMapper;
    private final RbacPermissionMapper permissionMapper;
    private final RbacRolePermissionMapper rolePermissionMapper;

    @Override
    public void checkPermission(Long userId, Long clubId, String permissionCode) {
        Set<Long> roleIds = resolveRoleIds(userId, clubId);
        if (roleIds.isEmpty() || !hasPermission(roleIds, permissionCode)) {
            Club club = clubMapper.selectById(clubId);
            String name = club == null ? "" : club.getName();
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "无权限在社团「" + name + "」执行该操作");
        }
    }

    @Override
    public Set<String> listPermissions(Long userId, Long clubId) {
        Set<Long> roleIds = resolveRoleIds(userId, clubId);
        if (roleIds.isEmpty()) {
            return Set.of();
        }
        return permissionMapper.selectList(
                        new LambdaQueryWrapper<RbacPermission>()
                                .in(RbacPermission::getId,
                                        rolePermissionMapper.selectList(
                                                        new LambdaQueryWrapper<com.club.agent.entity.RbacRolePermission>()
                                                                .in(com.club.agent.entity.RbacRolePermission::getRoleId, roleIds))
                                                .stream()
                                                .map(com.club.agent.entity.RbacRolePermission::getPermissionId)
                                                .collect(Collectors.toSet())))
                .stream()
                .map(RbacPermission::getCode)
                .collect(Collectors.toSet());
    }

    /** 用户在该社团的角色 id 集合 */
    private Set<Long> resolveRoleIds(Long userId, Long clubId) {
        Set<Long> roleIds = new HashSet<>();
        Club club = clubMapper.selectById(clubId);
        SysUser user = userMapper.selectById(userId);
        if (club == null || user == null) {
            return roleIds;
        }
        if (Boolean.TRUE.equals(user.getIsTeacher())) {
            // 老师只对自家社团有 teacher 权限集（不越权管其他社团）
            if (club.getTeacherId() != null && club.getTeacherId().equals(userId)) {
                RbacRole teacherRole = roleMapper.selectOne(
                        new LambdaQueryWrapper<RbacRole>().eq(RbacRole::getCode, RoleConstants.TEACHER));
                if (teacherRole != null) {
                    roleIds.add(teacherRole.getId());
                }
            }
            return roleIds;
        }
        // 学生：查已通过身份
        Membership membership = membershipMapper.selectOne(
                new LambdaQueryWrapper<Membership>()
                        .eq(Membership::getUserId, userId)
                        .eq(Membership::getClubId, clubId)
                        .eq(Membership::getStatus, Membership.STATUS_APPROVED));
        if (membership != null) {
            roleIds.add(membership.getRoleId());
        }
        return roleIds;
    }

    private boolean hasPermission(Set<Long> roleIds, String permissionCode) {
        RbacPermission permission = permissionMapper.selectOne(
                new LambdaQueryWrapper<RbacPermission>().eq(RbacPermission::getCode, permissionCode));
        if (permission == null) {
            return false;
        }
        Long count = rolePermissionMapper.selectCount(
                new LambdaQueryWrapper<com.club.agent.entity.RbacRolePermission>()
                        .in(com.club.agent.entity.RbacRolePermission::getRoleId, roleIds)
                        .eq(com.club.agent.entity.RbacRolePermission::getPermissionId, permission.getId()));
        return count != null && count > 0;
    }
}
