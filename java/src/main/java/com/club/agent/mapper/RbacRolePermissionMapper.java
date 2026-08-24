package com.club.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.club.agent.entity.RbacRolePermission;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色-权限关联 Mapper。
 */
@Mapper
public interface RbacRolePermissionMapper extends BaseMapper<RbacRolePermission> {
}
