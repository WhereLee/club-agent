-- 双项目集成阶段2（J1）：活动总结报告入 rag 知识库
-- activity_summary 记录 rag 侧 file_id（重生成时软删旧文件 + 重推新文件）
-- 执行：psql -U postgres -d club_agent -f alter_summary_rag.sql

ALTER TABLE activity_summary ADD COLUMN IF NOT EXISTS rag_file_id BIGINT;
