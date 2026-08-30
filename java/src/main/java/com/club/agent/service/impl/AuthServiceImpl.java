package com.club.agent.service.impl;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.ShearCaptcha;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.club.agent.common.ResultCode;
import com.club.agent.config.CaptchaProperties;
import com.club.agent.config.LoginProperties;
import com.club.agent.dto.LoginDTO;
import com.club.agent.dto.RegisterDTO;
import com.club.agent.entity.LoginLog;
import com.club.agent.entity.SysUser;
import com.club.agent.exception.BizException;
import com.club.agent.mapper.LoginLogMapper;
import com.club.agent.mapper.SysUserMapper;
import com.club.agent.service.AuthService;
import com.club.agent.util.JwtUtils;
import com.club.agent.util.RedisKeys;
import com.club.agent.vo.CaptchaVO;
import com.club.agent.vo.LoginVO;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 认证实现：
 * - 验证码：hutool 图形验证码，答案存 Redis（一次性消费）
 * - 登录：验证码校验 → 失败锁定检查 → 密码校验 → 计数管理 → 签发 JWT
 * - 防爆破：连续失败 N 次锁定（Redis 计数 + TTL），登录接口另有限流注解
 * - 登出：JWT 无状态，token 入黑名单直到自然过期
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String LUA_INCR_EXPIRE =
            "local c = redis.call('INCR', KEYS[1]) " +
            "if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end " +
            "return c";

    private final SysUserMapper userMapper;
    private final LoginLogMapper loginLogMapper;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;
    private final CaptchaProperties captchaProperties;
    private final LoginProperties loginProperties;
    private final JwtUtils jwtUtils;

    @Override
    public CaptchaVO getCaptcha() {
        ShearCaptcha captcha = CaptchaUtil.createShearCaptcha(120, 40, 4, 2);
        String key = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(
                RedisKeys.CAPTCHA + key,
                captcha.getCode(),
                Duration.ofMinutes(captchaProperties.getExpireMinutes()));
        return CaptchaVO.builder()
                .captchaKey(key)
                .imgBase64(captcha.getImageBase64Data())
                .build();
    }

    @Override
    public void register(RegisterDTO dto) {
        // 唯一性预检（友好提示；并发兜底走 DuplicateKeyException）
        Long usernameCount = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, dto.getUsername()));
        if (usernameCount != null && usernameCount > 0) {
            throw new BizException(ResultCode.BIZ_USERNAME_EXISTS);
        }
        Long emailCount = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getEmail, dto.getEmail()));
        if (emailCount != null && emailCount > 0) {
            throw new BizException(ResultCode.BIZ_EMAIL_EXISTS);
        }

        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        user.setEmail(dto.getEmail());
        user.setNickname(dto.getNickname());
        user.setStatus(1);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            log.warn("并发注册唯一性冲突: {}", dto.getUsername());
            throw new BizException(ResultCode.PARAM_ERROR.getCode(), "用户名或邮箱已被注册");
        }
    }

    @Override
    public LoginVO login(LoginDTO dto, String ip) {
        // 1. 验证码（一次性：校验后即删除）；captcha.enabled=false 时跳过（开发/自动化测试）
        if (captchaProperties.isEnabled()) {
            String captchaKey = RedisKeys.CAPTCHA + dto.getCaptchaKey();
            String expect = redisTemplate.opsForValue().get(captchaKey);
            redisTemplate.delete(captchaKey);
            if (expect == null || !expect.equalsIgnoreCase(dto.getCaptchaCode())) {
                saveLoginLog(dto.getUsername(), ip, 0, "验证码错误或已过期");
                throw new BizException(ResultCode.BIZ_CAPTCHA_ERROR);
            }
        }

        // 2. 失败锁定检查（Redis 计数 TTL 即锁定时长）
        String failKey = RedisKeys.LOGIN_FAIL + dto.getUsername();
        String failCount = redisTemplate.opsForValue().get(failKey);
        if (failCount != null && Integer.parseInt(failCount) >= loginProperties.getFailMaxCount()) {
            saveLoginLog(dto.getUsername(), ip, 0, "账号已锁定");
            throw new BizException(ResultCode.BIZ_ACCOUNT_LOCKED);
        }

        // 3. 账号密码校验（统一错误提示，防账号枚举）
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, dto.getUsername()));
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPasswordHash())) {
            incrFailCount(failKey);
            saveLoginLog(dto.getUsername(), ip, 0, "用户名或密码错误");
            throw new BizException(ResultCode.BIZ_USERNAME_OR_PASSWORD_ERROR);
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            saveLoginLog(dto.getUsername(), ip, 0, "账号被禁用");
            throw new BizException(ResultCode.BIZ_USER_DISABLED);
        }

        // 4. 成功：清计数、更新登录时间、写日志、签发 token
        redisTemplate.delete(failKey);
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(update);
        saveLoginLog(user.getUsername(), ip, 1, "登录成功");

        String token = jwtUtils.createToken(user.getId(), user.getUsername());
        return LoginVO.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatarUrl(user.getAvatarUrl())
                .isTeacher(user.getIsTeacher())
                .build();
    }

    @Override
    public void logout(String token) {
        Claims claims = jwtUtils.parseToken(token);
        if (claims == null || claims.getId() == null) {
            return;
        }
        long ttlMillis = claims.getExpiration().getTime() - System.currentTimeMillis();
        if (ttlMillis > 0) {
            redisTemplate.opsForValue().set(
                    RedisKeys.TOKEN_BLACKLIST + claims.getId(),
                    "1",
                    Duration.ofMillis(ttlMillis));
        }
    }

    /** 失败计数 INCR + 首失败设置 TTL（Lua 原子），超限即锁定 */
    private void incrFailCount(String failKey) {
        redisTemplate.execute(
                new DefaultRedisScript<>(LUA_INCR_EXPIRE, Long.class),
                List.of(failKey),
                String.valueOf(loginProperties.getLockMinutes() * 60));
    }

    /** 登录日志异步落库（审计不阻塞登录响应） */
    @Async("logExecutor")
    public void saveLoginLog(String username, String ip, int status, String message) {
        try {
            LoginLog record = new LoginLog();
            record.setUsername(username);
            record.setIp(ip);
            record.setStatus(status);
            record.setMessage(message);
            loginLogMapper.insert(record);
        } catch (Exception e) {
            log.error("登录日志落库失败: {}", e.getMessage());
        }
    }
}
