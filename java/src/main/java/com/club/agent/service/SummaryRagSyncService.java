package com.club.agent.service;

/**
 * 活动总结报告入 rag 知识库（双项目集成阶段2 · J1）。
 *
 * 闭环定位：总结报告是"沉淀 → 复用"的最后一环——归档（人确认后）的报告渲染为
 * Markdown 推入 rag org 空间，概念 Agent 与管理层问答即可检索到历史活动总结。
 */
public interface SummaryRagSyncService {

    /**
     * 推送总结报告入 rag（幂等替换：软删旧文件 + 重推新文件 + 回填 rag_file_id）。
     * 仅处理 success 状态的总结；失败仅告警不阻断（归档/重生成主流程不受影响）。
     */
    void syncToRag(Long clubId, Long activityId);
}
