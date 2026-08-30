-- =============================================================
-- 正式文件撰写会话（活动前 Agent，E1）：
--   file_draft_session: 发起人与 AI 讨论正式文件的业务会话表（事实源）
--   （LangGraph checkpoint 存图状态，本表存业务消息，双真相源同概念阶段）
-- =============================================================

CREATE TABLE IF NOT EXISTS file_draft_session (
    id          BIGINT      PRIMARY KEY,
    activity_id BIGINT     NOT NULL,
    user_id     BIGINT     NOT NULL,
    role        VARCHAR(20) NOT NULL,               -- user / assistant / tool
    content     TEXT,
    tool_name   VARCHAR(50),
    tool_args   JSONB,                               -- 工具参数（generate_file_draft 的章节草稿 JSON 在此）
    tokens_in   INT,
    tokens_out  INT,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

COMMENT ON TABLE file_draft_session IS '正式文件撰写会话（AI 对话留痕；章节草稿经人采纳后写入 form 引擎）';

CREATE INDEX ix_file_draft_session_activity ON file_draft_session (activity_id, created_at);
