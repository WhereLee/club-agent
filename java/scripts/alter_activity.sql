-- =============================================================
-- 活动前阶段（块 A）：活动主表 + 活动留痕时间线（幂等）
-- 概念通过（concept.status=5）自动创建 activity（status=1 公示中）；
-- 状态：1=公示中 2=问卷中 3=讨论中 4=已发布 5=已取消
-- =============================================================

CREATE TABLE IF NOT EXISTS activity (
    id             BIGINT       PRIMARY KEY,
    club_id        BIGINT      NOT NULL,
    concept_id     BIGINT      NOT NULL UNIQUE,      -- 来源概念（一个概念只转一个活动，可追溯）
    user_id        BIGINT      NOT NULL,             -- 发起人（复制自概念；取消权限校验用）
    status         SMALLINT    NOT NULL DEFAULT 1,   -- 1=公示中 2=问卷中 3=讨论中 4=已发布 5=已取消
    planned_time   VARCHAR(100),                     -- 对齐 concept_session 列宽
    planned_location VARCHAR(200),                     -- 对齐 concept_session 列宽
    content        TEXT,                              -- 初稿简述（复制自概念，公示用）
    cancel_reason  VARCHAR(500),                     -- 取消理由（必填）
    created_at     TIMESTAMP NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP NOT NULL DEFAULT now()
);

COMMENT ON TABLE  activity IS '活动主表（概念通过后自动创建，活动前/中/后三阶段状态机）';
COMMENT ON COLUMN activity.status IS '状态：1=公示中 2=问卷中 3=讨论中 4=已发布 5=已取消';

CREATE INDEX IF NOT EXISTS ix_activity_club_status ON activity (club_id, status, created_at DESC);

CREATE TABLE IF NOT EXISTS activity_trace (
    id             BIGINT       PRIMARY KEY,
    activity_id    BIGINT      NOT NULL,
    operator_id    BIGINT,                            -- NULL=系统动作（如概念转活动）
    operator_name  VARCHAR(50) NOT NULL,
    action         VARCHAR(30) NOT NULL,              -- create/cancel（块 B/C/D 补 survey_publish/discuss_start/file_publish）
    detail         VARCHAR(500),
    created_at     TIMESTAMP NOT NULL DEFAULT now()
);

COMMENT ON TABLE activity_trace IS '活动全量流水：谁在什么时候做了什么（时间线展示与审计）';

CREATE INDEX IF NOT EXISTS ix_activity_trace_activity ON activity_trace (activity_id, created_at);

-- 活动通知关联（公示/取消等；存量列不受影响，幂等）
ALTER TABLE message ADD COLUMN IF NOT EXISTS ref_activity_id BIGINT;
COMMENT ON COLUMN message.ref_activity_id IS '关联活动（雪花 id）；与 ref_concept_id 二选一';
