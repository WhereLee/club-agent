package com.club.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.club.agent.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper（复杂查询走 XML：resources/mapper/SysUserMapper.xml）。
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
