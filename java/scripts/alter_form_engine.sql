-- =============================================================
-- 动态表单引擎（块 B：问卷；块 D 正式文件 / 活动中执行记录复用同一套表）
-- 模板层（字段定义） + 数据层（填报结果）分离：
--   form_template: 一份表单（type=survey/file/record）
--   form_field:    字段定义（类型/必填/选项 JSONB/系统内置标记）
--   form_submission: 一人一份填报记录
--   form_answer:    每字段答案
-- =============================================================

CREATE TABLE IF NOT EXISTS form_template (
    id          BIGINT      PRIMARY KEY,
    activity_id BIGINT     NOT NULL,
    type        VARCHAR(20) NOT NULL,               -- survey=问卷 file=正式文件 record=执行记录
    title       VARCHAR(100),
    deadline    TIMESTAMP,                           -- 问卷截止时间（发起人定；file/record 无）
    status      SMALLINT    NOT NULL DEFAULT 1,      -- 1=进行中 2=已截止/已关闭
    created_by  BIGINT      NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uk_form_template_activity_type UNIQUE (activity_id, type)  -- 一个活动每种表单只有一份
);

COMMENT ON TABLE form_template IS '动态表单模板（问卷/正式文件/执行记录共用）';

CREATE TABLE IF NOT EXISTS form_field (
    id           BIGINT      PRIMARY KEY,
    template_id  BIGINT     NOT NULL,
    label        VARCHAR(100) NOT NULL,             -- 题目/字段标签
    field_type   VARCHAR(20) NOT NULL,              -- text/textarea/radio/select/checkbox/number
    required     SMALLINT    NOT NULL DEFAULT 0,    -- 1=必填
    options      JSONB,                              -- radio/select/checkbox 的选项数组
    sort_order   INT         NOT NULL DEFAULT 0,
    system_flag  SMALLINT    NOT NULL DEFAULT 0,    -- 1=系统内置（如"是否感兴趣"，不可删）
    created_at   TIMESTAMP NOT NULL DEFAULT now()
);

COMMENT ON TABLE form_field IS '表单字段定义（发起人/管理层自定义；system_flag=1 为系统内置）';

CREATE TABLE IF NOT EXISTS form_submission (
    id          BIGINT      PRIMARY KEY,
    template_id BIGINT     NOT NULL,
    activity_id BIGINT     NOT NULL,
    user_id     BIGINT     NOT NULL,
    submitted_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uk_form_submission_template_user UNIQUE (template_id, user_id)  -- 一人一提交
);

COMMENT ON TABLE form_submission IS '表单填报记录（一人一份）';

CREATE TABLE IF NOT EXISTS form_answer (
    id            BIGINT      PRIMARY KEY,
    submission_id BIGINT     NOT NULL,
    field_id      BIGINT     NOT NULL,
    value         TEXT,                              -- 文本直接存；多选存 JSON 数组字符串
    created_at    TIMESTAMP NOT NULL DEFAULT now()
);

COMMENT ON TABLE form_answer IS '字段答案（多选 value 存 JSON 数组字符串）';

CREATE INDEX ix_form_field_template ON form_field (template_id, sort_order);
CREATE INDEX ix_form_submission_activity ON form_submission (activity_id);
CREATE INDEX ix_form_answer_submission ON form_answer (submission_id);
