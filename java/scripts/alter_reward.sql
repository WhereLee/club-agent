-- =============================================================
-- 块 H：奖励（2026-08-30）
--   activity_suggestion:  讨论建议（Java AI 提炼候选，发起人采纳 → 质量分）
--   activity_record_score: 留痕打分（管理员手动 + Java AI 预评并列）
--   score_config: 奖励分值配置（建议采纳分/留痕分/高频参与分）
--   score_level:  等级区间（优秀/良好/合格，表驱动）
-- =============================================================

CREATE TABLE IF NOT EXISTS activity_suggestion (
    id          BIGINT PRIMARY KEY,
    activity_id BIGINT NOT NULL,
    message_id  BIGINT NOT NULL,          -- 来源消息（计分链：建议人）
    sender_id   BIGINT NOT NULL,          -- 建议人
    summary     TEXT NOT NULL,            -- AI 提炼要点
    content     TEXT NOT NULL,            -- 消息原文（参考）
    adopted     BOOLEAN NOT NULL DEFAULT FALSE,
    adopted_at  TIMESTAMP,
    adopted_by  BIGINT,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uk_activity_suggestion UNIQUE (activity_id, message_id)
);

CREATE TABLE IF NOT EXISTS activity_record_score (
    id          BIGINT PRIMARY KEY,
    activity_id BIGINT NOT NULL,
    user_id     BIGINT NOT NULL,          -- 被评人（留痕提交人）
    score       INTEGER NOT NULL,         -- 最终分 0-100
    ai_score    INTEGER,                  -- AI 预评分（并列参考）
    ai_reason   TEXT,                     -- AI 预评理由
    score_by    BIGINT,                   -- 打分管理员
    score_at    TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uk_activity_record_score UNIQUE (activity_id, user_id)
);

CREATE TABLE IF NOT EXISTS score_config (
    id          BIGINT PRIMARY KEY,
    club_id     BIGINT NOT NULL,
    cfg_key     VARCHAR(50) NOT NULL,     -- suggestion_score / record_score / freq_score
    cfg_value   INTEGER NOT NULL,
    remark      VARCHAR(200),
    updated_at  TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uk_score_config UNIQUE (club_id, cfg_key)
);

CREATE TABLE IF NOT EXISTS score_level (
    id          BIGINT PRIMARY KEY,
    club_id     BIGINT NOT NULL,
    level_name  VARCHAR(50) NOT NULL,
    min_score   INTEGER NOT NULL,
    max_score   INTEGER NOT NULL,
    updated_at  TIMESTAMP NOT NULL DEFAULT now()
);

COMMENT ON TABLE activity_suggestion IS '讨论建议候选（Java AI 提炼；采纳计质量分）';
COMMENT ON TABLE activity_record_score IS '执行留痕打分（管理员 + AI 预评并列）';
COMMENT ON TABLE score_config IS '奖励分值配置（建议采纳分/留痕分/高频参与分）';
COMMENT ON TABLE score_level IS '奖励等级区间（表驱动）';
