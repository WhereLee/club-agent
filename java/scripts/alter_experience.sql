-- D3 经验沉淀 + 想法简析：experience_entry 建表 + concept_session.ai_brief 列（幂等，可重复执行）
-- 执行：psql -U postgres -d club_agent -f alter_experience.sql

-- 1) 经验库：业务知识（筹备/强度/住宿/风险）+ 思考角度（thinking_pattern）
CREATE TABLE IF NOT EXISTS experience_entry (
    id                BIGINT       PRIMARY KEY,
    club_id           BIGINT,                -- 可空 = 全社团通用
    category          VARCHAR(30)  NOT NULL, -- thinking_pattern / 筹备知识 / 复盘教训 / context
    title             VARCHAR(100) NOT NULL,
    content           TEXT         NOT NULL,
    owner_id          BIGINT,                -- 思考角度归属的发起人（可空 = 通用）
    source_concept_id BIGINT,                -- 来源概念（可追溯）
    source_user_id    BIGINT,                -- 沉淀人
    status            SMALLINT     NOT NULL DEFAULT 1, -- 1=有效 0=作废
    created_at        TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_experience_club ON experience_entry (club_id, category, status);
CREATE INDEX IF NOT EXISTS ix_experience_owner ON experience_entry (owner_id, status);

-- 2) 概念会话表加 ai_brief（提交时异步生成，冻结"发起人思路"）
ALTER TABLE concept_session ADD COLUMN IF NOT EXISTS ai_brief TEXT;
