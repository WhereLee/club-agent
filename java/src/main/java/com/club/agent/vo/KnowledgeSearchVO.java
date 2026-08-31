package com.club.agent.vo;

import lombok.Data;

import java.util.List;

/**
 * 双源知识检索结果（双项目集成任务6）：
 * - sqlItems：结构化经验条目（experience_entry，SQL 检索，D3 链路复用）
 * - fileItems：活动资料文件命中块（rag org 空间，混合检索 + rerank，含来源溯源）
 * - similarActivityCount：数据水位（B1 语义，本社团非思考角度经验总数）
 */
@Data
public class KnowledgeSearchVO {

    private List<ExperienceSearchVO.Item> sqlItems;

    private List<FileItem> fileItems;

    private Integer similarActivityCount;

    /** rag 命中块（检索器模式：概念 Agent 整合进草案并标注引用来源） */
    @Data
    public static class FileItem {
        private String filename;
        private Integer pageNo;
        private String headingPath;
        private String content;
        private Double score;
    }
}
