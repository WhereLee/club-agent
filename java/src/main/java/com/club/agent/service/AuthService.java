package com.club.agent.service;

import com.club.agent.dto.LoginDTO;
import com.club.agent.dto.RegisterDTO;
import com.club.agent.vo.CaptchaVO;
import com.club.agent.vo.LoginVO;

/**
 * 认证服务：验证码 / 注册 / 登录 / 登出。
 */
public interface AuthService {

    /** 生成图形验证码（答案存 Redis，一次性） */
    CaptchaVO getCaptcha();

    /** 注册（学生入口，公开接口） */
    void register(RegisterDTO dto);

    /** 登录：验证码 + 失败锁定 + 签发 JWT */
    LoginVO login(LoginDTO dto, String ip);

    /** 登出：token 加入 Redis 黑名单（TTL = 剩余有效期） */
    void logout(String token);
}
