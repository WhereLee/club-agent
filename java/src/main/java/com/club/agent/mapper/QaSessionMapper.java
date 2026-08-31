package com.club.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.club.agent.entity.QaSession;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface QaSessionMapper extends BaseMapper<QaSession> {

    // ---------- A1 对齐：问答 checkpoint TTL 清理（软删会话过期后删除三表） ----------
    // thread_id 为 qa 会话 id 的字符串形态；终态 = 软删(0)；updated_at 距今超过 ttlDays 天

    @Delete("""
            DELETE FROM checkpoint_writes WHERE thread_id IN (
                SELECT CAST(id AS TEXT) FROM qa_session
                WHERE status = 0 AND updated_at < NOW() - (#{ttlDays} || ' days')::INTERVAL
            )
            """)
    int cleanupCheckpointWrites(@Param("ttlDays") int ttlDays);

    @Delete("""
            DELETE FROM checkpoint_blobs WHERE thread_id IN (
                SELECT CAST(id AS TEXT) FROM qa_session
                WHERE status = 0 AND updated_at < NOW() - (#{ttlDays} || ' days')::INTERVAL
            )
            """)
    int cleanupCheckpointBlobs(@Param("ttlDays") int ttlDays);

    @Delete("""
            DELETE FROM checkpoints WHERE thread_id IN (
                SELECT CAST(id AS TEXT) FROM qa_session
                WHERE status = 0 AND updated_at < NOW() - (#{ttlDays} || ' days')::INTERVAL
            )
            """)
    int cleanupCheckpoints(@Param("ttlDays") int ttlDays);
}
