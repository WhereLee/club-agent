CREATE TABLE IF NOT EXISTS concept_draft_session (
    id            BIGINT       PRIMARY KEY,
    concept_id    BIGINT       NOT NULL,
    user_id       BIGINT       NOT NULL,
    role          VARCHAR(10)  NOT NULL,
    content       TEXT,
    tool_name     VARCHAR(50),
    tool_args     JSONB,
    form_snapshot JSONB,
    tokens_in     INT,
    tokens_out    INT,
    created_at    TIMESTAMP    NOT NULL DEFAULT now()
);
COMMENT ON TABLE  concept_draft_session IS '概念起草会话：AI 对话消息留痕（人/AI/工具三方），审计与续聊的事实源';
CREATE INDEX IF NOT EXISTS ix_draft_session_concept ON concept_draft_session (concept_id, created_at);