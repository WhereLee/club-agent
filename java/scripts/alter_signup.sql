-- =============================================================
-- 块 F：报名（2026-08-30）
--   activity_signup: 成员报名（参加/不参加+在线协助），uk 一人一条（可改，截止前）
--   拦截：问卷"不感兴趣"者限制参加（participate 拒绝，在线协助放行）
-- =============================================================

CREATE TABLE IF NOT EXISTS activity_signup (
    id            BIGINT PRIMARY KEY,
    activity_id   BIGINT NOT NULL,
    user_id       BIGINT NOT NULL,
    choice        VARCHAR(20) NOT NULL,          -- participate / not_participate
    online_assist BOOLEAN NOT NULL DEFAULT FALSE, -- 不参加时勾选在线协助 → 提示发起人
    created_at    TIMESTAMP NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uk_activity_signup UNIQUE (activity_id, user_id)
);

COMMENT ON TABLE activity_signup IS '活动报名（执行阶段起点；问卷不感兴趣者限制参加）';
CREATE INDEX ix_activity_signup_activity ON activity_signup (activity_id);
