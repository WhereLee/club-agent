-- =============================================================
-- 讨论群（块 C）：
--   chat_message:        讨论消息（先落库后广播，复盘/Agent 语料留痕）
--   activity_chat_member: 入群名单快照（问卷截止后统一生成 = 感兴趣成员 ∪ 管理层；老师不在内）
-- =============================================================

CREATE TABLE IF NOT EXISTS chat_message (
    id          BIGINT      PRIMARY KEY,
    activity_id BIGINT     NOT NULL,
    sender_id   BIGINT     NOT NULL,
    sender_name VARCHAR(50) NOT NULL,               -- 冗余昵称（历史展示不回查）
    content     TEXT        NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

COMMENT ON TABLE chat_message IS '讨论群消息（先落库后广播；重连 REST 补拉）';

CREATE TABLE IF NOT EXISTS activity_chat_member (
    id          BIGINT      PRIMARY KEY,
    activity_id BIGINT     NOT NULL,
    user_id     BIGINT     NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uk_chat_member UNIQUE (activity_id, user_id)
);

COMMENT ON TABLE activity_chat_member IS '讨论群成员快照（问卷截止后统一生成；订阅/发送/拉历史鉴权）';

CREATE INDEX IF NOT EXISTS ix_chat_message_activity ON chat_message (activity_id, created_at);
CREATE INDEX IF NOT EXISTS ix_chat_member_activity ON activity_chat_member (activity_id);
