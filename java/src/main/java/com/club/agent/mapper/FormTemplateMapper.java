package com.club.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.club.agent.entity.FormTemplate;
import org.apache.ibatis.annotations.Mapper;

/** 动态表单模板 Mapper（块 B 问卷；复用引擎表） */
@Mapper
public interface FormTemplateMapper extends BaseMapper<FormTemplate> {
}