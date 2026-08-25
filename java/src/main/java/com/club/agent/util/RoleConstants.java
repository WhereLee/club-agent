package com.club.agent.util;

/**
 * 角色编码常量（与 DataInitializer 初始化数据对应）。
 * 角色表虽是动态表，但业务代码需要引用这四个基础角色的 code。
 */
public final class RoleConstants {

    public static final String TEACHER = "teacher";
    public static final String PRESIDENT = "president";
    public static final String VICE_PRESIDENT = "vice_president";
    public static final String MEMBER = "member";

    private RoleConstants() {
    }
}
