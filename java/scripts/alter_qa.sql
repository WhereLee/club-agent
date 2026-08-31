-- 双项目集成阶段2（J3）：管理层经验问答（独立 Agent 服务的业务事实源）
-- qa_session：问答会话（管理层本人创建，跨届经验传承的入口）
-- qa_message：会话消息留痕（人/AI/工具三方，审计与续聊的事实源；checkpoint 只是运行态缓存）
-- 执行：psql -U postgres -d club_agent -f alter_qa.sql

CREATE TABLE IF NOT EXISTS qa_session (
    id         BIGINT       PRIMARY KEY,
    club_id    BIGINT       NOT NULL,
    user_id    BIGINT       NOT NULL,              -- 创建人（管理层本人；会话私有）
    title      VARCHAR(100) NOT NULL,
    status     SMALLINT     NOT NULL DEFAULT 1,    -- 1=有效 0=已删除（软删）
    created_at TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at TIMESTAMP    NOT NULL DEFAULT now()
);
COMMENT ON TABLE qa_session IS '管理层经验问答会话（独立 Agent 服务；跨届经验传承出口）';
CREATE INDEX IF NOT EXISTS ix_qa_session_club ON qa_session (club_id, user_id, status);

CREATE TABLE IF NOT EXISTS qa_message (
    id         BIGINT      PRIMARY KEY,
    session_id BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,               -- 提问人
    role       VARCHAR(10) NOT NULL,               -- user / assistant / tool
    content    TEXT,
    tool_name  VARCHAR(50),
    tool_args  TEXT,                               -- K31 教训：TEXT 而非 JSONB（MP 直传 String）
    created_at TIMESTAMP   NOT NULL DEFAULT now()
);
COMMENT ON TABLE qa_message IS '问答会话消息留痕（人/AI/工具三方），审计与重放的事实源';
CREATE INDEX IF NOT EXISTS ix_qa_message_session ON qa_message (session_id, created_at);
