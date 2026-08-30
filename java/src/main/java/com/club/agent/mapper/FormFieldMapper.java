package com.club.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.club.agent.entity.FormField;
import org.apache.ibatis.annotations.Mapper;

/** 表单字段 Mapper（options JSONB 列：insert 走 XML CAST，K23 先例） */
@Mapper
public interface FormFieldMapper extends BaseMapper<FormField> {

    /** 自定义 insert：options 显式 CAST 为 jsonb（MP 内置 insert 不带 CAST 会被 PG 拒绝） */
    int insertWithOptions(FormField field);
}