-- =============================================================
-- 分工（块 D）：正式文件发布时全量写入
--   activity_duty: 职责项列表（description + 指派成员 JSONB）
--   列表型结构不适合动态表单引擎字段模型，专用表承载
-- =============================================================

CREATE TABLE IF NOT EXISTS activity_duty (
    id               BIGINT      PRIMARY KEY,
    activity_id      BIGINT     NOT NULL,
    description      VARCHAR(500) NOT NULL,           -- 职责描述（如"负责路线与安全"）
    assigned_members JSONB,                           -- 指派成员 id 数组，如 [1,2]
    sort_order       INT         NOT NULL DEFAULT 0,
    created_at       TIMESTAMP NOT NULL DEFAULT now()
);

COMMENT ON TABLE activity_duty IS '活动分工项（正式文件发布时写入；列表型结构，不走表单引擎）';

CREATE INDEX IF NOT EXISTS ix_activity_duty_activity ON activity_duty (activity_id, sort_order);
