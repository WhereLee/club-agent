package com.club.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.club.agent.common.ResultCode;
import com.club.agent.dto.UpdatePasswordDTO;
import com.club.agent.dto.UpdateProfileDTO;
import com.club.agent.entity.SysUser;
import com.club.agent.exception.BizException;
import com.club.agent.mapper.SysUserMapper;
import com.club.agent.service.UserService;
import com.club.agent.storage.StorageService;
import com.club.agent.util.SecurityUtils;
import com.club.agent.vo.UserInfoVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户信息实现：基于 SecurityContext 中的登录用户 ID 操作自身数据。
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final StorageService storageService;

    @Override
    public UserInfoVO getCurrentUser() {
        SysUser user = requireCurrentUser();
        return toVO(user);
    }

    @Override
    public UserInfoVO updateProfile(UpdateProfileDTO dto) {
        SysUser user = requireCurrentUser();
        // 邮箱唯一性（排除自己）
        Long emailCount = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getEmail, dto.getEmail())
                        .ne(SysUser::getId, user.getId()));
        if (emailCount != null && emailCount > 0) {
            throw new BizException(ResultCode.BIZ_EMAIL_EXISTS);
        }
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setNickname(dto.getNickname());
        update.setEmail(dto.getEmail());
        userMapper.updateById(update);
        return toVO(userMapper.selectById(user.getId()));
    }

    @Override
    public void updatePassword(UpdatePasswordDTO dto) {
        SysUser user = requireCurrentUser();
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPasswordHash())) {
            throw new BizException(ResultCode.BIZ_OLD_PASSWORD_ERROR);
        }
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setPasswordHash(passwordEncoder.encode(dto.getNewPassword()));
        userMapper.updateById(update);
    }

    @Override
    public String updateAvatar(MultipartFile file) {
        SysUser user = requireCurrentUser();
        String url = storageService.upload(file, "avatar");
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setAvatarUrl(url);
        userMapper.updateById(update);
        return url;
    }

    /** 从安全上下文取当前用户；不存在抛 401（理论不可达，防御性编码） */
    private SysUser requireCurrentUser() {
        SysUser user = SecurityUtils.getUser();
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return user;
    }

    private UserInfoVO toVO(SysUser user) {
        return UserInfoVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
