package com.club.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.club.agent.entity.FileDraftSession;
import org.apache.ibatis.annotations.Mapper;

/** 正式文件撰写会话 Mapper（tool_args JSONB 列：insert 走 XML CAST，K23 先例） */
@Mapper
public interface FileDraftSessionMapper extends BaseMapper<FileDraftSession> {

    int insertToolMessage(FileDraftSession msg);
}