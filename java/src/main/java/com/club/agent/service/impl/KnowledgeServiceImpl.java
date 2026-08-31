package com.club.agent.service.impl;

import com.club.agent.config.RagClientFactory;
import com.club.agent.dto.RagRetrieveResult;
import com.club.agent.service.ExperienceService;
import com.club.agent.service.KnowledgeService;
import com.club.agent.vo.ExperienceSearchVO;
import com.club.agent.vo.KnowledgeSearchVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 双源知识检索实现（双项目集成任务6）。
 *
 * 源 A（SQL）：复用 ExperienceService.experience（D3 检索 + B1 数据水位 + thinking_pattern 注入）。
 * 源 B（rag）：RagClientFactory.retrieve（org 空间 = clubId；混合检索 + rerank）。
 * 降级策略：rag 未启用 / 调用失败 → 仅返回源 A（文件块空列表），概念 Agent 照常起草（K20 铁律：不因知识源故障阻断）。
 * 缓存（J4）：Redis 短 TTL——问答/起草高频重复提问场景减少重复检索；只缓存成功结果（降级态不缓存），
 * TTL 短（默认 5 分钟）容忍新沉淀经验的短暂不可见；Redis 故障直接穿透不阻断。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeServiceImpl implements KnowledgeService {

    private final ExperienceService experienceService;
    private final RagClientFactory ragClientFactory;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${rag.enabled:true}")
    private boolean ragEnabled;

    /** 检索结果缓存 TTL（秒）；<=0 关闭缓存 */
    @Value("${rag.knowledge-cache-ttl-seconds:300}")
    private long cacheTtlSeconds;

    @Override
    public KnowledgeSearchVO knowledge(Long clubId, Long userId, String q, int topK) {
        String cacheKey = cacheKey(clubId, q, topK);
        KnowledgeSearchVO cached = readCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        KnowledgeSearchVO vo = new KnowledgeSearchVO();

        // 源 A：结构化经验（SQL，含数据水位与 thinking_pattern）
        ExperienceSearchVO sql = experienceService.experience(clubId, userId, q);
        vo.setSqlItems(sql.getItems());
        vo.setSimilarActivityCount(sql.getSimilarActivityCount());

        // 源 B：活动资料文件（rag org 空间；尽力而为）
        List<KnowledgeSearchVO.FileItem> fileItems = new ArrayList<>();
        boolean ragOk = !ragEnabled;  // rag 关闭是稳定态（可缓存）；启用但失败是降级态（不缓存）
        if (ragEnabled) {
            try {
                RagRetrieveResult result = ragClientFactory.retrieve(q, clubId, topK);
                if (result != null && result.items() != null) {
                    for (RagRetrieveResult.Item it : result.items()) {
                        KnowledgeSearchVO.FileItem fi = new KnowledgeSearchVO.FileItem();
                        fi.setFilename(it.filename());
                        fi.setPageNo(it.pageNo());
                        fi.setHeadingPath(it.headingPath());
                        fi.setContent(it.content());
                        fi.setScore(it.score());
                        fileItems.add(fi);
                    }
                }
                ragOk = true;
            } catch (Exception e) {
                log.warn("rag 检索失败，降级为 SQL 经验单源: club={} err={}", clubId, e.getMessage());
            }
        }
        vo.setFileItems(fileItems);
        // 只缓存非降级态结果（降级态不进缓存，避免故障窗口被固化）
        if (ragOk) {
            writeCache(cacheKey, vo);
        }
        return vo;
    }

    private String cacheKey(Long clubId, String q, int topK) {
        return "knowledge:" + clubId + ":" + topK + ":" + md5(q);
    }

    private static String md5(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(s.hashCode());
        }
    }

    private KnowledgeSearchVO readCache(String key) {
        if (cacheTtlSeconds <= 0) {
            return null;
        }
        try {
            String json = redisTemplate.opsForValue().get(key);
            return StringUtils.hasText(json) ? objectMapper.readValue(json, KnowledgeSearchVO.class) : null;
        } catch (Exception e) {
            log.warn("知识检索缓存读取失败（穿透重查）: {}", e.getMessage());
            return null;
        }
    }

    private void writeCache(String key, KnowledgeSearchVO vo) {
        if (cacheTtlSeconds <= 0) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(vo),
                    Duration.ofSeconds(cacheTtlSeconds));
        } catch (Exception e) {
            log.warn("知识检索缓存写入失败（不影响本次结果）: {}", e.getMessage());
        }
    }
}
