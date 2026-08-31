-- =============================================================
-- 概念生命周期：字段修正 + 唯一性 + 投票/流水表（幂等）
-- 1. idea → reason（发起理由，提交必填；LangGraph 起草会话的输入入口）
-- 2. 移除 deleted（生命周期由 status 表达，与 membership 一致）
-- 3. deadline：当前阶段截止时间（提交=提交+36h；进入老师期=进入+36h）
-- 4. 部分唯一索引：一个社团同一时间最多一个活跃概念（活跃集 1-4）
-- 5. concept_vote：投票状态机（块 B 使用）；concept_trace：全量时间线（审计/展示）
-- =============================================================

-- 仅老库（存在 idea 列）需要改名；init_db 新库已是 reason，直接跳过（干净库部署兼容）
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'concept_session' AND column_name = 'idea') THEN
        ALTER TABLE concept_session RENAME COLUMN idea TO reason;
    END IF;
END $$;
ALTER TABLE concept_session DROP COLUMN IF EXISTS deleted;
ALTER TABLE concept_session ADD COLUMN IF NOT EXISTS deadline TIMESTAMP;

COMMENT ON COLUMN concept_session.reason IS '发起理由（必填；LangGraph AI 起草会话的输入入口）';
COMMENT ON COLUMN concept_session.deadline IS '当前阶段截止时间（提交=提交+36h；进入待老师批复=进入+36h）';

CREATE UNIQUE INDEX IF NOT EXISTS uk_concept_active
    ON concept_session (club_id) WHERE status IN (1, 2, 3, 4);

-- ---------- 概念投票表（状态机数据：判断两票/复议轮） ----------
CREATE TABLE IF NOT EXISTS concept_vote (
    id         BIGINT       PRIMARY KEY,
    concept_id BIGINT      NOT NULL,
    round      SMALLINT    NOT NULL,                -- 1=首次投票 2=复议
    voter_id   BIGINT      NOT NULL,
    result     SMALLINT    NOT NULL,                -- 1=赞成 0=拒绝
    comment    VARCHAR(500) NOT NULL,               -- 必填理由（留痕主体）
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (concept_id, round, voter_id)
);

COMMENT ON TABLE  concept_vote IS '概念投票（发起人不投票；复议=两人重投）';
COMMENT ON COLUMN concept_vote.round IS '轮次：1=首次 2=复议';

CREATE INDEX IF NOT EXISTS ix_concept_vote ON concept_vote (concept_id, round);

-- ---------- 概念流水表（全量时间线：谁/何时/什么，审计与老师视图） ----------
CREATE TABLE IF NOT EXISTS concept_trace (
    id            BIGINT       PRIMARY KEY,
    concept_id    BIGINT      NOT NULL,
    operator_id   BIGINT,                              -- 可空：系统动作（超时扫描）无操作人
    operator_name VARCHAR(50) NOT NULL,
    action        VARCHAR(30) NOT NULL,             -- create/save/submit/vote/revote/withdraw/abandon/teacher_approve/teacher_reject/timeout_void/resign_void
    detail        VARCHAR(500),                     -- 理由/备注
    created_at    TIMESTAMP NOT NULL DEFAULT now()
);

-- 已建表的老库同步（K8 修复：系统动作无操作人）
ALTER TABLE concept_trace ALTER COLUMN operator_id DROP NOT NULL;

COMMENT ON TABLE  concept_trace IS '概念全量流水（谁在什么时候做了什么，时间线展示与审计）';

CREATE INDEX IF NOT EXISTS ix_concept_trace ON concept_trace (concept_id, created_at ASC);

-- 超时扫描查询索引（status IN (2,3,4) AND deadline < now()）
CREATE INDEX IF NOT EXISTS ix_concept_status_deadline
    ON concept_session (status, deadline) WHERE status IN (2, 3, 4);
