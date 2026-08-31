package com.club.agent.config;

import com.club.agent.mapper.QaSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 问答 checkpoint TTL 清理（A1 对齐概念侧 cleanupExpiredCheckpoints）：
 * 软删会话（终态，不再续聊）超过 checkpointTtlDays 天后，每天 03:30 清理其 checkpoint 三表，
 * 防 PostgresSaver 无限膨胀。独立类不实现接口 → CGLIB 代理，@Scheduled 可识别。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QaCheckpointScheduler {

    private final QaSessionMapper qaSessionMapper;

    @Value("${ai.qa.checkpoint-ttl-days:30}")
    private int checkpointTtlDays;

    @Scheduled(cron = "0 30 3 * * ?")
    @Transactional
    public void cleanupExpiredCheckpoints() {
        int ttl = checkpointTtlDays;
        int writes = qaSessionMapper.cleanupCheckpointWrites(ttl);
        int blobs = qaSessionMapper.cleanupCheckpointBlobs(ttl);
        int checkpoints = qaSessionMapper.cleanupCheckpoints(ttl);
        if (writes + blobs + checkpoints > 0) {
            log.info("问答 checkpoint TTL 清理：writes={} blobs={} checkpoints={}（软删超 {} 天）",
                    writes, blobs, checkpoints, ttl);
        }
    }
}
