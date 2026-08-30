package com.club.agent.controller;

import com.club.agent.annotation.Log;
import com.club.agent.annotation.RateLimiter;
import com.club.agent.annotation.RepeatSubmit;
import com.club.agent.common.R;
import com.club.agent.dto.UpdatePasswordDTO;
import com.club.agent.dto.UpdateProfileDTO;
import com.club.agent.service.MembershipService;
import com.club.agent.service.UserService;
import com.club.agent.util.SecurityUtils;
import com.club.agent.vo.MyClubVO;
import com.club.agent.vo.UserInfoVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 个人信息接口（需登录）。
 */
@Tag(name = "个人信息")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final MembershipService membershipService;

    @GetMapping("/me")
    @Operation(summary = "当前用户信息")
    public R<UserInfoVO> me() {
        return R.ok(userService.getCurrentUser());
    }

    @PutMapping("/profile")
    @Log(module = "个人信息", operation = "修改资料")
    @Operation(summary = "修改昵称/邮箱")
    public R<UserInfoVO> updateProfile(@Valid @RequestBody UpdateProfileDTO dto) {
        return R.ok(userService.updateProfile(dto));
    }

    @PutMapping("/password")
    @Log(module = "个人信息", operation = "修改密码")
    @Operation(summary = "修改密码")
    public R<Void> updatePassword(@Valid @RequestBody UpdatePasswordDTO dto) {
        userService.updatePassword(dto);
        return R.ok();
    }

    @PostMapping("/avatar")
    @Log(module = "个人信息", operation = "上传头像")
    @RateLimiter(limit = 10, windowSeconds = 60)
    @RepeatSubmit(intervalSeconds = 3)
    @Operation(summary = "上传头像（multipart/form-data，file 字段）")
    public R<Map<String, String>> updateAvatar(@RequestParam("file") MultipartFile file) {
        String url = userService.updateAvatar(file);
        return R.ok(Map.of("avatarUrl", url));
    }

    @GetMapping("/clubs")
    @Operation(summary = "我的社团（状态/角色）；老师返回其管理的社团")
    public R<List<MyClubVO>> myClubs() {
        Long userId = SecurityUtils.getUserId();
        if (Boolean.TRUE.equals(userService.getCurrentUser().getIsTeacher())) {
            return R.ok(membershipService.managedClubs(userId));
        }
        return R.ok(membershipService.myClubs(userId));
    }
}
