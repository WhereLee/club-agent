package com.club.agent.service;

import com.club.agent.dto.UpdatePasswordDTO;
import com.club.agent.dto.UpdateProfileDTO;
import com.club.agent.vo.UserInfoVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户信息服务：个人信息查询 / 修改 / 改密 / 头像。
 */
public interface UserService {

    /** 当前登录用户信息（实时查库） */
    UserInfoVO getCurrentUser();

    /** 修改昵称/邮箱 */
    UserInfoVO updateProfile(UpdateProfileDTO dto);

    /** 修改密码（校验原密码） */
    void updatePassword(UpdatePasswordDTO dto);

    /** 上传头像，返回新头像 URL */
    String updateAvatar(MultipartFile file);
}
