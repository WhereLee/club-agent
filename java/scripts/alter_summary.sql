-- 活动后阶段（I1）：活动总结表 + 经验条目表扩展
-- 经验条目复用 experience_entry（统一经验库），仅扩展活动来源与结构化指标列

CREATE TABLE IF NOT EXISTS activity_summary (
    id             BIGINT PRIMARY KEY,
    activity_id    BIGINT       NOT NULL,
    status         VARCHAR(20)  NOT NULL,            -- pending / awaiting / success / failed
    report         JSONB,                            -- {metrics:{...}, report_text:"..."} 结构化指标 + AI 总结
    questions      JSONB,                            -- 待确认问题清单 [{id, question}]
    answers        JSONB,                            -- 发起人回答 {questionId: answer}
    retry_count    INT          DEFAULT 0,
    generated_by   BIGINT,
    generated_at   TIMESTAMP,
    updated_at     TIMESTAMP,
    CONSTRAINT uk_activity_summary UNIQUE (activity_id)
);

-- 经验条目关联活动（活动后自动沉淀来源，可追溯）；metrics 为结构化指标快照
ALTER TABLE experience_entry ADD COLUMN IF NOT EXISTS activity_id BIGINT;
ALTER TABLE experience_entry ADD COLUMN IF NOT EXISTS metrics JSONB;
CREATE INDEX IF NOT EXISTS idx_experience_activity ON experience_entry (activity_id);
