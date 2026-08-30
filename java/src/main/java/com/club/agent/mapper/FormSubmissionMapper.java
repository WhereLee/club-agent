package com.club.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.club.agent.entity.FormSubmission;
import org.apache.ibatis.annotations.Mapper;

/** 表单填报记录 Mapper */
@Mapper
public interface FormSubmissionMapper extends BaseMapper<FormSubmission> {
}