-- =============================================================
-- 块 G：签到 + 执行留痕（2026-08-30）
--   activity_attendance: 签到（仅报名参加者可签，uk 一人一条，幂等）
--   留痕复用 form 引擎（type=record，completeExecution 时自动建模板）
-- =============================================================

CREATE TABLE IF NOT EXISTS activity_attendance (
    id          BIGINT PRIMARY KEY,
    activity_id BIGINT NOT NULL,
    user_id     BIGINT NOT NULL,
    checked_at  TIMESTAMP NOT NULL DEFAULT now(),
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uk_activity_attendance UNIQUE (activity_id, user_id)
);

COMMENT ON TABLE activity_attendance IS '活动签到（执行中开放；未报名参加者不可签）';
CREATE INDEX IF NOT EXISTS ix_activity_attendance_activity ON activity_attendance (activity_id);
