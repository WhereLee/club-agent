-- =============================================================
-- 概念诞生：发起者起草会话表（幂等）
-- 本表承载"概念诞生"阶段发起者部分：想法 → 起草 → 提交。
-- 审批子流程（两票/复议/老师批复）由后续块扩展 status。
-- =============================================================

CREATE TABLE IF NOT EXISTS concept_session (
    id               BIGINT       PRIMARY KEY,
    club_id          BIGINT      NOT NULL,
    user_id          BIGINT      NOT NULL,                 -- 发起者（该社团管理层）
    status           SMALLINT    NOT NULL DEFAULT 1,       -- 1=起草中 2=已提交待审批
    idea             TEXT,                                 -- 发起者的想法描述（可空=直接填表；AI 起草会话入口）
    planned_time     VARCHAR(100),                         -- 预计时间
    planned_location VARCHAR(200),                         -- 预计地点
    content          TEXT,                                 -- 活动大致内容
    submitted_at     TIMESTAMP,                            -- 提交时间（status=2 时写入）
    created_at       TIMESTAMP NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP NOT NULL DEFAULT now(),
    deleted          SMALLINT   NOT NULL DEFAULT 0
);

COMMENT ON TABLE  concept_session IS '概念诞生会话（活动起草，发起者为该社团管理层）';
COMMENT ON COLUMN concept_session.status IS '状态：1=起草中 2=已提交待审批（审批子流程后续扩展）';
-- idea 列仅老库存在（新库 init_db 已改名 reason），条件 COMMENT 兼容两种形态（干净库部署修复）
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'concept_session' AND column_name = 'idea') THEN
        COMMENT ON COLUMN concept_session.idea IS '发起者的想法描述（可空；后续 AI 起草会话的输入入口）';
    END IF;
END $$;
COMMENT ON COLUMN concept_session.submitted_at IS '提交时间（status=2 时写入）';

CREATE INDEX IF NOT EXISTS ix_concept_session_club ON concept_session (club_id, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_concept_session_user ON concept_session (user_id, created_at DESC);
