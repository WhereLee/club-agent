package com.club.agent.controller;

import com.club.agent.annotation.Log;
import com.club.agent.annotation.RateLimiter;
import com.club.agent.annotation.RepeatSubmit;
import com.club.agent.common.R;
import com.club.agent.dto.LoginDTO;
import com.club.agent.dto.RegisterDTO;
import com.club.agent.service.AuthService;
import com.club.agent.util.IpUtils;
import com.club.agent.vo.CaptchaVO;
import com.club.agent.vo.LoginVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：验证码 / 注册 / 登录 / 登出（登录路径均带防爆破措施）。
 */
@Tag(name = "认证")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @GetMapping("/captcha")
    @Operation(summary = "获取图形验证码")
    public R<CaptchaVO> captcha() {
        return R.ok(authService.getCaptcha());
    }

    @PostMapping("/register")
    @Log(module = "认证", operation = "用户注册")
    @RepeatSubmit(intervalSeconds = 3)
    @Operation(summary = "注册（学生入口）")
    public R<Void> register(@Valid @RequestBody RegisterDTO dto) {
        authService.register(dto);
        return R.ok();
    }

    @PostMapping("/login")
    @Log(module = "认证", operation = "用户登录")
    @RateLimiter(limit = 10, windowSeconds = 60)
    @Operation(summary = "登录")
    public R<LoginVO> login(@Valid @RequestBody LoginDTO dto, HttpServletRequest request) {
        return R.ok(authService.login(dto, IpUtils.getIp(request)));
    }

    @PostMapping("/logout")
    @Log(module = "认证", operation = "用户登出")
    @Operation(summary = "登出（token 入黑名单）")
    public R<Void> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            authService.logout(authHeader.substring("Bearer ".length()));
        }
        return R.ok();
    }
}
