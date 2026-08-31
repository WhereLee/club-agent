package com.club.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活动资料库（双项目集成）：管理层上传的活动资料，经 club 中转推入 rag 知识库（org 空间）。
 * 闭环：上传 → StorageService 落盘 + 落表 → rag 异步入库（解析/切块/向量化）→ 概念 Agent 检索引用。
 */
@Data
@TableName("activity_file_lib")
public class ActivityFileLib {

    /** rag 入库状态：本地落库成功但尚未推 rag */
    public static final String RAG_PENDING = "pending";
    /** 已推 rag，解析中 */
    public static final String RAG_PARSING = "parsing";
    /** rag 解析成功 */
    public static final String RAG_SUCCESS = "success";
    /** rag 解析部分降级（占位块等） */
    public static final String RAG_PARTIAL = "partial";
    /** 推 rag 失败（可重试） */
    public static final String RAG_FAILED = "failed";
    /** 已软删（rag 侧同步失效） */
    public static final String RAG_VOIDED = "voided";

    public static final int STATUS_VALID = 1;
    public static final int STATUS_DELETED = 0;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long clubId;

    /** 可空 = 社团级通用资料 */
    private Long activityId;

    private Long uploaderId;

    private String filename;

    private Long fileSize;

    /** StorageService 返回的 URL（本地 /uploads/... 或 COS 域名 URL） */
    private String storageUrl;

    /** rag 侧 user_file.id（入库成功后回填，删除时同步软删） */
    private Long ragFileId;

    private String ragStatus;

    private Integer status;

    private LocalDateTime createdAt;
}
