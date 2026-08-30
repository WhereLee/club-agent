-- =============================================================
-- 概念审批：站内消息表（作废/通过通知，幂等）
-- 通知范围：作废（复议再拒/投票超时/老师否决/老师超时）→ 三位管理层；
--           老师超时场景额外通知老师本人；概念通过 → 三位管理层。
-- =============================================================

CREATE TABLE IF NOT EXISTS message (
    id             BIGINT       PRIMARY KEY,
    recipient_id   BIGINT      NOT NULL,
    type           VARCHAR(30) NOT NULL,             -- concept_void / concept_approved
    title          VARCHAR(100) NOT NULL,
    content        VARCHAR(500),
    ref_concept_id BIGINT,                           -- 关联概念（雪花 id）
    read_flag      SMALLINT NOT NULL DEFAULT 0,      -- 0=未读 1=已读
    created_at     TIMESTAMP NOT NULL DEFAULT now()
);

COMMENT ON TABLE  message IS '站内消息（概念作废/通过通知；前端以待办聚合展示）';
COMMENT ON COLUMN message.type IS '类型：concept_void=概念作废 concept_approved=概念通过';

CREATE INDEX ix_message_recipient ON message (recipient_id, read_flag, created_at DESC);
