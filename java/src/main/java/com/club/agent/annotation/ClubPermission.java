package com.club.agent.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 社团上下文权限校验：标注在 Controller 方法上，由 ClubPermissionAspect 校验
 * "当前用户能否在指定社团内行使某权限"。
 * <p>
 * 校验规则：① 用户是该社团的指导老师（club.teacher_id）→ 按 teacher 角色权限集判断；
 * ② 否则查该用户在社团内的已通过身份（membership.status=1）→ 按角色权限集判断。
 * 两个来源都无对应权限点 → 403。
 *
 * <pre>
 * {@code @ClubPermission(clubId = "#clubId", permission = "club:member:approve")}
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ClubPermission {

    /** 社团 ID 的 SpEL 表达式（引用方法参数，如 #clubId） */
    String clubId();

    /** 权限点编码（如 club:member:approve） */
    String permission();
}
