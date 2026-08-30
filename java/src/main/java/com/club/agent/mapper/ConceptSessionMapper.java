package com.club.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.club.agent.entity.ConceptSession;
import com.club.agent.vo.ConceptVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 概念会话 Mapper（复杂查询走 XML：resources/mapper/ConceptSessionMapper.xml）。
 */
@Mapper
public interface ConceptSessionMapper extends BaseMapper<ConceptSession> {

    /** 概念分页列表（带发起人昵称 + 当前用户是否已投） */
    IPage<ConceptVO> selectConceptPage(IPage<ConceptVO> page,
                                       @Param("clubId") Long clubId,
                                       @Param("userId") Long userId,
                                       @Param("status") Integer status);

    // ---------- A1：LangGraph checkpoint TTL 清理（终态概念过期后删除三表） ----------
    // thread_id 为概念 id 的字符串形态；终态 = 已通过(5)/作废(6)；updated_at 距今超过 ttlDays 天

    @Delete("""
            DELETE FROM checkpoint_writes WHERE thread_id IN (
                SELECT CAST(id AS TEXT) FROM concept_session
                WHERE status IN (5, 6) AND updated_at < NOW() - (#{ttlDays} || ' days')::INTERVAL
            )
            """)
    int cleanupCheckpointWrites(@Param("ttlDays") int ttlDays);

    @Delete("""
            DELETE FROM checkpoint_blobs WHERE thread_id IN (
                SELECT CAST(id AS TEXT) FROM concept_session
                WHERE status IN (5, 6) AND updated_at < NOW() - (#{ttlDays} || ' days')::INTERVAL
            )
            """)
    int cleanupCheckpointBlobs(@Param("ttlDays") int ttlDays);

    @Delete("""
            DELETE FROM checkpoints WHERE thread_id IN (
                SELECT CAST(id AS TEXT) FROM concept_session
                WHERE status IN (5, 6) AND updated_at < NOW() - (#{ttlDays} || ' days')::INTERVAL
            )
            """)
    int cleanupCheckpoints(@Param("ttlDays") int ttlDays);
}
