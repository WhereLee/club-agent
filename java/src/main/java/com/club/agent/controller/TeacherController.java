package com.club.agent.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.club.agent.common.R;
import com.club.agent.common.ResultCode;
import com.club.agent.entity.LoginLog;
import com.club.agent.entity.OperLog;
import com.club.agent.entity.SysUser;
import com.club.agent.exception.BizException;
import com.club.agent.service.LogQueryService;
import com.club.agent.service.MembershipService;
import com.club.agent.util.SecurityUtils;
import com.club.agent.vo.TodoVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 老师管理台接口：待办审批（老师/管理层）、操作日志/登录日志（仅老师）。
 * 待办接口对所有登录用户开放（普通成员返回空列表）；
 * 日志接口在方法内校验老师身份（log:view 为全局权限点，无社团上下文，不走 @ClubPermission）。
 */
@Tag(name = "老师管理台")
@Validated
@RestController
@RequestMapping
@RequiredArgsConstructor
public class TeacherController {

    private final MembershipService membershipService;
    private final LogQueryService logQueryService;

    @GetMapping("/todos")
    @Operation(summary = "待办审批：老师=其管理的社团，管理层=所在社团，普通成员=空")
    public R<List<TodoVO>> todos() {
        SysUser user = SecurityUtils.getUser();
        if (Boolean.TRUE.equals(user.getIsTeacher())) {
            return R.ok(membershipService.pendingTodosByTeacher(user.getId()));
        }
        return R.ok(membershipService.pendingTodosByManagement(user.getId()));
    }

    @GetMapping("/logs/oper")
    @Operation(summary = "操作日志分页（仅老师）")
    public R<IPage<OperLog>> operLogs(@RequestParam(defaultValue = "1") @Min(value = 1, message = "页码不能小于 1") long page,
                                      @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页数量不能小于 1") @Max(value = 100, message = "每页最多 100 条") long size) {
        requireTeacher();
        return R.ok(logQueryService.pageOperLogs(page, size));
    }

    @GetMapping("/logs/login")
    @Operation(summary = "登录日志分页（仅老师）")
    public R<IPage<LoginLog>> loginLogs(@RequestParam(defaultValue = "1") @Min(value = 1, message = "页码不能小于 1") long page,
                                        @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页数量不能小于 1") @Max(value = 100, message = "每页最多 100 条") long size) {
        requireTeacher();
        return R.ok(logQueryService.pageLoginLogs(page, size));
    }

    private void requireTeacher() {
        SysUser user = SecurityUtils.getUser();
        if (user == null || !Boolean.TRUE.equals(user.getIsTeacher())) {
            throw new BizException(ResultCode.BIZ_TEACHER_ONLY);
        }
    }
}
