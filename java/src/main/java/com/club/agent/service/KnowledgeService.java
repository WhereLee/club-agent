package com.club.agent.service;

import com.club.agent.vo.KnowledgeSearchVO;

/**
 * 双源知识检索（双项目集成任务6）：概念 Agent 起草时的统一知识入口。
 * 源 A：结构化经验条目（experience_entry，SQL 检索，短精）；
 * 源 B：活动资料文件（rag org 空间，向量+BM25+rerank，长散）。
 * 双源分离不混库（方案 D5）；rag 侧故障降级为源 A 单源，不阻断起草。
 */
public interface KnowledgeService {

    /**
     * @param clubId 社团（org 空间 = clubId）
     * @param userId 当前用户（源 A 需注入该发起人 thinking_pattern）
     * @param q      查询
     * @param topK   rag 侧返回块数（1-20）
     */
    KnowledgeSearchVO knowledge(Long clubId, Long userId, String q, int topK);
}
