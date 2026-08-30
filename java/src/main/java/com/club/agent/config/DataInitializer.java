package com.club.agent.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.club.agent.entity.RbacPermission;
import com.club.agent.entity.RbacRole;
import com.club.agent.entity.RbacRolePermission;
import com.club.agent.entity.SysUser;
import com.club.agent.mapper.RbacPermissionMapper;
import com.club.agent.mapper.RbacRoleMapper;
import com.club.agent.mapper.RbacRolePermissionMapper;
import com.club.agent.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 启动数据初始化（幂等）：
 * 1. RBAC 基础数据：4 角色 + 权限点 + 角色-权限映射（动态表首次灌数据）
 * 2. 预设老师账号：模拟旧系统存量（项目定位：改造项目，老师账号不设计注册）
 * 注：预设在系统角色之上创建——老师账号仅用于登录/授权，注册通道对学生开放。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final RbacRoleMapper roleMapper;
    private final RbacPermissionMapper permissionMapper;
    private final RbacRolePermissionMapper rolePermissionMapper;
    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Value("${preset.teacher.usernames}")
    private String teacherUsernames;

    @Value("${preset.teacher.password}")
    private String teacherPassword;

    @Value("${preset.teacher.email-domain}")
    private String emailDomain;

    /** 角色定义：code -> (name, isManagement, sort) */
    private static final Map<String, Object[]> ROLES = new LinkedHashMap<>() {{
        put("teacher", new Object[]{"指导老师", true, 1});
        put("president", new Object[]{"社长", true, 2});
        put("vice_president", new Object[]{"副社长", true, 3});
        put("member", new Object[]{"社员", false, 4});
    }};

    /** 权限点定义：code -> (name, type, sort) */
    private static final Map<String, Object[]> PERMISSIONS = new LinkedHashMap<>() {{
        put("club:create", new Object[]{"创建社团", "ACTION", 1});
        put("club:update", new Object[]{"修改社团信息", "ACTION", 2});
        put("club:member:approve", new Object[]{"审批成员加入", "ACTION", 3});
        put("club:member:appoint", new Object[]{"任命管理层", "ACTION", 4});
        put("activity:manage", new Object[]{"活动管理", "ACTION", 5});
        put("log:view", new Object[]{"查看操作日志", "ACTION", 6});
        put("club:member", new Object[]{"查看社团活动", "ACTION", 7});
    }};

    /** 角色-权限映射：roleCode -> permissionCode 列表 */
    private static final Map<String, List<String>> ROLE_PERMISSIONS = new LinkedHashMap<>() {{
        put("teacher", List.of(
                "club:create", "club:update", "club:member:approve",
                "club:member:appoint", "activity:manage", "log:view", "club:member"));
        put("president", List.of(
                "club:update", "club:member:approve", "activity:manage", "log:view", "club:member"));
        put("vice_president", List.of("club:member:approve", "activity:manage", "club:member"));
        put("member", List.of("club:member"));
    }};

    @Override
    public void run(ApplicationArguments args) {
        initRbac();
        initTeachers();
    }

    /** RBAC 基础数据：按 code 查重，缺失才插入（幂等） */
    private void initRbac() {
        for (Map.Entry<String, Object[]> e : ROLES.entrySet()) {
            RbacRole exist = roleMapper.selectOne(
                    new LambdaQueryWrapper<RbacRole>().eq(RbacRole::getCode, e.getKey()));
            if (exist == null) {
                RbacRole role = new RbacRole();
                role.setCode(e.getKey());
                role.setName((String) e.getValue()[0]);
                role.setIsManagement((Boolean) e.getValue()[1]);
                role.setSort((Integer) e.getValue()[2]);
                roleMapper.insert(role);
                log.info("初始化角色: {} ({})", role.getCode(), role.getName());
            }
        }
        for (Map.Entry<String, Object[]> e : PERMISSIONS.entrySet()) {
            RbacPermission exist = permissionMapper.selectOne(
                    new LambdaQueryWrapper<RbacPermission>().eq(RbacPermission::getCode, e.getKey()));
            if (exist == null) {
                RbacPermission perm = new RbacPermission();
                perm.setCode(e.getKey());
                perm.setName((String) e.getValue()[0]);
                perm.setType((String) e.getValue()[1]);
                perm.setParentId(0L);
                perm.setSort((Integer) e.getValue()[2]);
                permissionMapper.insert(perm);
                log.info("初始化权限点: {} ({})", perm.getCode(), perm.getName());
            }
        }
        bindRolePermissions();
    }

    /** 角色-权限映射：按 role 幂等重建（清空重插，保证与定义一致） */
    private void bindRolePermissions() {
        for (Map.Entry<String, List<String>> e : ROLE_PERMISSIONS.entrySet()) {
            RbacRole role = roleMapper.selectOne(
                    new LambdaQueryWrapper<RbacRole>().eq(RbacRole::getCode, e.getKey()));
            if (role == null) {
                continue;
            }
            rolePermissionMapper.delete(new LambdaQueryWrapper<RbacRolePermission>()
                    .eq(RbacRolePermission::getRoleId, role.getId()));
            for (String permCode : e.getValue()) {
                RbacPermission perm = permissionMapper.selectOne(
                        new LambdaQueryWrapper<RbacPermission>().eq(RbacPermission::getCode, permCode));
                if (perm != null) {
                    RbacRolePermission rp = new RbacRolePermission();
                    rp.setRoleId(role.getId());
                    rp.setPermissionId(perm.getId());
                    rolePermissionMapper.insert(rp);
                }
            }
        }
        log.info("RBAC 角色-权限映射就绪");
    }

    /** 预设老师账号：username 不存在才创建（幂等），密码运行时 BCrypt 加密 */
    private void initTeachers() {
        for (String username : teacherUsernames.split(",")) {
            String u = username.trim();
            if (u.isEmpty()) {
                continue;
            }
            Long count = userMapper.selectCount(
                    new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, u));
            if (count != null && count > 0) {
                continue;
            }
            SysUser teacher = new SysUser();
            teacher.setUsername(u);
            teacher.setPasswordHash(passwordEncoder.encode(teacherPassword));
            teacher.setEmail(u + "@" + emailDomain);
            // 昵称必须符合 @Nickname 规则（中文/英文/数字，无符号）——username 本身是字母数字，直接拼接
            teacher.setNickname("指导老师" + u);
            teacher.setIsTeacher(true);
            teacher.setStatus(1);
            userMapper.insert(teacher);
            log.info("预设老师账号: {}（密码见配置，生产环境需覆盖）", u);
        }
    }
}
