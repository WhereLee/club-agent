-- =============================================================
-- 活动中阶段（状态机扩展 + 讨论质量预处理，2026-08-30）
-- 1) activity：报名截止 / 留痕截止 / 讨论关闭时间
-- 2) chat_message：字数与低质量标记（<10 字即低质量，插入时计算）
-- 3) activity_discussion_summary：讨论结束快照（频率统计，奖励"高频参与分"数据源）
-- =============================================================

ALTER TABLE activity
    ADD COLUMN IF NOT EXISTS signup_deadline    TIMESTAMP,
    ADD COLUMN IF NOT EXISTS record_deadline    TIMESTAMP,
    ADD COLUMN IF NOT EXISTS discussion_closed_at TIMESTAMP;

COMMENT ON COLUMN activity.signup_deadline IS '报名截止时间（发起人开始报名时设置，超时锁报名）';
COMMENT ON COLUMN activity.record_deadline IS '执行留痕提交截止时间（管理层设置，超时自动进复盘）';
COMMENT ON COLUMN activity.discussion_closed_at IS '讨论关闭时间（发起人结束讨论；关闭后才可撰写正式文件）';

ALTER TABLE chat_message
    ADD COLUMN IF NOT EXISTS word_count INT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS is_low_quality BOOLEAN DEFAULT FALSE;

COMMENT ON COLUMN chat_message.word_count IS '消息去空白后字数（插入时计算）';
COMMENT ON COLUMN chat_message.is_low_quality IS '低质量标记：字数 < 10 的短回复（ok/好的等），不进入文件 Agent 参考集';

-- 历史数据回填（讨论群已有消息）
UPDATE chat_message
SET word_count = length(regexp_replace(content, '\s+', '', 'g')),
    is_low_quality = length(regexp_replace(content, '\s+', '', 'g')) < 10;

CREATE TABLE IF NOT EXISTS activity_discussion_summary (
    id            BIGINT PRIMARY KEY,
    activity_id   BIGINT NOT NULL,
    user_id       BIGINT NOT NULL,
    msg_count     INT NOT NULL DEFAULT 0,
    quality_count INT NOT NULL DEFAULT 0,
    is_high_freq  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uk_discussion_summary UNIQUE (activity_id, user_id)
);

COMMENT ON TABLE activity_discussion_summary IS '讨论结束快照：每成员消息数与高质量消息数（频率标准数据源，奖励"高频参与分"）';
CREATE INDEX IF NOT EXISTS ix_discussion_summary_activity ON activity_discussion_summary (activity_id);
