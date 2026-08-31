package com.club.agent.config;

import com.club.agent.mapper.QaSessionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 问答 checkpoint TTL 清理调度单测：软删会话过期后清三表；删除 0 行静默不报错。
 */
@ExtendWith(MockitoExtension.class)
class QaCheckpointSchedulerTest {

    @Mock QaSessionMapper qaSessionMapper;

    @InjectMocks QaCheckpointScheduler scheduler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "checkpointTtlDays", 30);
    }

    @Test
    @DisplayName("按 TTL 天数清理 checkpoint 三表（writes→blobs→checkpoints）")
    void cleanup_deletesThreeTables() {
        when(qaSessionMapper.cleanupCheckpointWrites(30)).thenReturn(2);
        when(qaSessionMapper.cleanupCheckpointBlobs(30)).thenReturn(2);
        when(qaSessionMapper.cleanupCheckpoints(30)).thenReturn(1);

        scheduler.cleanupExpiredCheckpoints();

        verify(qaSessionMapper).cleanupCheckpointWrites(30);
        verify(qaSessionMapper).cleanupCheckpointBlobs(30);
        verify(qaSessionMapper).cleanupCheckpoints(30);
    }

    @Test
    @DisplayName("无过期会话时删除 0 行，不抛异常（静默跳过）")
    void cleanup_zeroRows_silent() {
        when(qaSessionMapper.cleanupCheckpointWrites(30)).thenReturn(0);
        when(qaSessionMapper.cleanupCheckpointBlobs(30)).thenReturn(0);
        when(qaSessionMapper.cleanupCheckpoints(30)).thenReturn(0);

        scheduler.cleanupExpiredCheckpoints();

        verify(qaSessionMapper).cleanupCheckpointWrites(30);
        verify(qaSessionMapper).cleanupCheckpointBlobs(30);
        verify(qaSessionMapper).cleanupCheckpoints(30);
    }
}
