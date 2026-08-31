package com.club.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * rag 检索器响应（/api/org/retrieve）：org 空间命中 chunks（检索器模式，无生成）。
 * score 语义：rerank 时为 logits（跨查询可比）；rag 侧关闭精排时为 RRF 原始分。
 */
public record RagRetrieveResult(
        List<Item> items,
        int total) {

    /** 命中块（含来源溯源：文件名/页码/章节路径——概念 Agent 引用时标注出处） */
    public record Item(
            @JsonProperty("file_id") long fileId,
            @JsonProperty("filename") String filename,
            @JsonProperty("chunk_type") String chunkType,
            @JsonProperty("page_no") Integer pageNo,
            @JsonProperty("heading_path") String headingPath,
            @JsonProperty("content") String content,
            @JsonProperty("score") double score) {
    }
}
