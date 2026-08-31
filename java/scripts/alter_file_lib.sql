-- 活动资料库（双项目集成任务5）：管理层上传的活动资料，经 club 中转推入 rag 知识库（org 空间）
-- 闭环：上传 → StorageService 落盘 + 落表 → rag 入库（异步解析）→ 概念 Agent 检索 → 草案引用

CREATE TABLE IF NOT EXISTS activity_file_lib (
    id           BIGINT PRIMARY KEY,
    club_id      BIGINT       NOT NULL,
    activity_id  BIGINT,                              -- 可空 = 社团级通用资料
    uploader_id  BIGINT       NOT NULL,
    filename     VARCHAR(255) NOT NULL,
    file_size    BIGINT       NOT NULL DEFAULT 0,
    storage_url  VARCHAR(500) NOT NULL,               -- StorageService URL（本地 /uploads/... 或 COS）
    rag_file_id  BIGINT,                              -- rag 侧 user_file.id（入库成功后回填）
    rag_status   VARCHAR(16)  NOT NULL DEFAULT 'pending',  -- pending/parsing/success/partial/failed/voided
    status       SMALLINT     NOT NULL DEFAULT 1,     -- 1正常 0已软删
    created_at   TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_file_lib_club ON activity_file_lib (club_id, status, created_at DESC);
