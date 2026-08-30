package com.club.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.club.agent.entity.ConceptDraftSession;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ConceptDraftSessionMapper extends BaseMapper<ConceptDraftSession> {

    /** 按概念取全部会话消息（时间升序，会话重放） */
    List<ConceptDraftSession> selectByConceptId(Long conceptId);

    /** 工具消息落库（tool_args JSONB 需显式 CAST；id 调用方用 IdWorker 生成） */
    int insertToolMessage(ConceptDraftSession msg);
}
