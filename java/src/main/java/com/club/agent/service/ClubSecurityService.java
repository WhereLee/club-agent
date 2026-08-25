package com.club.agent.service;

import java.util.Set;

/**
 * 社团上下文权限服务：校验"用户能否在指定社团内行使某权限点"。
 */
public interface ClubSecurityService {

    /**
     * 校验权限；无权限抛 403（带社团名）。
     *
     * @param userId         当前用户
     * @param clubId         目标社团
     * @param permissionCode 权限点编码
     */
    void checkPermission(Long userId, Long clubId, String permissionCode);

    /**
     * 用户在指定社团内的全部权限点编码（前端按权限渲染按钮）。
     * 无身份返回空集合。
     */
    Set<String> listPermissions(Long userId, Long clubId);
}
