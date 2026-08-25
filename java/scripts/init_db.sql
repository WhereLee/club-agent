-- =============================================================
-- 社团管理 Agent 数据库初始化脚本
-- 目标库：club_agent
-- 说明：仅建表（含注释/索引/约束）。
--       角色/权限/预设老师账号等基础数据由应用启动时
--       DataInitializer 幂等写入（运行时 BCrypt 加密，不落明文）。
-- 主键策略：雪花算法（MyBatis-Plus ASSIGN_ID 赋值），故主键为普通 BIGINT，不用自增。
-- 命名约定：sys_ 前缀 = 系统基础表；rbac_ 前缀 = 权限体系；
--           业务表保持原名（user/role 为 PG 保留字，必须规避）。
-- =============================================================

-- ---------- 用户表（全局身份：一个人一个账号） ----------
CREATE TABLE IF NOT EXISTS sys_user (
    id            BIGINT       PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,                -- BCrypt 哈希（60 字符，留余量）
    email         VARCHAR(100) NOT NULL,                -- 全局唯一
    nickname      VARCHAR(50)  NOT NULL,
    avatar_url    VARCHAR(500),                         -- 对象存储 URL（不存文件本体）
    is_teacher    BOOLEAN      NOT NULL DEFAULT FALSE, -- 老师身份（全局标记；老师不进 membership，以 club.teacher_id 表达归属）
    status        SMALLINT     NOT NULL DEFAULT 1,      -- 1=正常 0=禁用（老师离职处理）
    last_login_at TIMESTAMP,
    created_at    TIMESTAMP  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP  NOT NULL DEFAULT now(),
    deleted       SMALLINT     NOT NULL DEFAULT 0       -- 0=正常 1=逻辑删除
);

COMMENT ON TABLE  sys_user            IS '用户表（全局身份）';
COMMENT ON COLUMN sys_user.username   IS '登录名';
COMMENT ON COLUMN sys_user.password_hash IS 'BCrypt 密码哈希';
COMMENT ON COLUMN sys_user.email      IS '邮箱（全局唯一）';
COMMENT ON COLUMN sys_user.status     IS '状态：1=正常 0=禁用';
COMMENT ON COLUMN sys_user.deleted    IS '逻辑删除：0=正常 1=已删';

CREATE UNIQUE INDEX uk_sys_user_username ON sys_user (username) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_sys_user_email    ON sys_user (email)    WHERE deleted = 0;

-- ---------- 社团表 ----------
CREATE TABLE IF NOT EXISTS club (
    id          BIGINT       PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    teacher_id  BIGINT       NOT NULL,                  -- 指导老师（创建者）
    created_at  TIMESTAMP  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP  NOT NULL DEFAULT now(),
    deleted     SMALLINT     NOT NULL DEFAULT 0
);

COMMENT ON TABLE  club            IS '社团表';
COMMENT ON COLUMN club.teacher_id IS '指导老师（创建社团的老师，可负责多个社团）';

CREATE UNIQUE INDEX uk_club_name ON club (name) WHERE deleted = 0;
CREATE INDEX ix_club_teacher ON club (teacher_id);

-- ---------- 成员关系表（用户在某个社团里的身份） ----------
CREATE TABLE IF NOT EXISTS membership (
    id         BIGINT       PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    club_id    BIGINT      NOT NULL,
    role_id    BIGINT      NOT NULL,                    -- 引用 rbac_role（社团内角色）
    status     SMALLINT    NOT NULL DEFAULT 0,          -- 0=申请中 1=已通过 2=已拒绝
    applied_at TIMESTAMP NOT NULL DEFAULT now(),
    approved_at TIMESTAMP,
    approved_by BIGINT,                                 -- 审批人（老师/管理层）
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

COMMENT ON TABLE  membership              IS '成员关系表（一人可加入多个社团）';
COMMENT ON COLUMN membership.status       IS '状态：0=申请中 1=已通过 2=已拒绝';
COMMENT ON COLUMN membership.approved_by  IS '审批人（指导老师/社长/副社长）';

-- 一人一社团仅一条关系
CREATE UNIQUE INDEX uk_membership_user_club ON membership (user_id, club_id);
CREATE INDEX ix_membership_club ON membership (club_id);
CREATE INDEX ix_membership_role ON membership (role_id);

-- 约束：管理层职务规则（三个硬约束，数据库层兑底，应用层给友好提示）
-- 1. 跨社团唯一：同一用户不能同时担任多个社团的管理层
-- 2. 一社团一社长：president 槽位唯一
-- 3. 副社长上限：vice_president 在职最多 2 人
-- 跨表约束无法用部分唯一索引表达（索引谓词禁止子查询），采用触发器。
-- 换届模型：任命只进空位（在职者先离职，role 改回 member），故此处只校验槽位。
CREATE OR REPLACE FUNCTION fn_check_management_unique() RETURNS TRIGGER AS $$
DECLARE
    v_code VARCHAR(50);
    v_is_management BOOLEAN;
    v_other_management BOOLEAN;
    v_president_count INT;
    v_vp_count INT;
BEGIN
    SELECT code, is_management INTO v_code, v_is_management
    FROM rbac_role WHERE id = NEW.role_id;

    IF v_is_management THEN
        -- 1. 跨社团唯一（排除自身）
        SELECT EXISTS (
            SELECT 1 FROM membership m
            JOIN rbac_role r ON r.id = m.role_id
            WHERE m.user_id = NEW.user_id
              AND m.status = 1
              AND r.is_management
              AND m.id <> NEW.id
        ) INTO v_other_management;
        IF v_other_management THEN
            RAISE EXCEPTION '该用户已是其他社团的管理层，不能重复任命';
        END IF;

        -- 2. 一社团一社长（排除自身，即角色变更场景）
        IF v_code = 'president' THEN
            SELECT count(*) INTO v_president_count FROM membership m
            JOIN rbac_role r ON r.id = m.role_id
            WHERE m.club_id = NEW.club_id AND m.status = 1
              AND r.code = 'president' AND m.id <> NEW.id;
            IF v_president_count > 0 THEN
                RAISE EXCEPTION '该社团已有在职社长，需现任社长先离职';
            END IF;
        END IF;

        -- 3. 副社长上限 2 人（排除自身）
        IF v_code = 'vice_president' THEN
            SELECT count(*) INTO v_vp_count FROM membership m
            JOIN rbac_role r ON r.id = m.role_id
            WHERE m.club_id = NEW.club_id AND m.status = 1
              AND r.code = 'vice_president' AND m.id <> NEW.id;
            IF v_vp_count >= 2 THEN
                RAISE EXCEPTION '该社团副社长已满（最多 2 人），需现任副社长先离职';
            END IF;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_management_unique ON membership;
CREATE TRIGGER trg_management_unique
    BEFORE INSERT OR UPDATE OF user_id, role_id, status ON membership
    FOR EACH ROW EXECUTE FUNCTION fn_check_management_unique();

-- ---------- 角色表（动态表，支持运行时增删改） ----------
CREATE TABLE IF NOT EXISTS rbac_role (
    id           BIGINT       PRIMARY KEY,
    code         VARCHAR(50) NOT NULL,
    name         VARCHAR(50) NOT NULL,
    is_management BOOLEAN    NOT NULL DEFAULT FALSE,    -- 管理层标记（支撑唯一约束）
    sort         INT         NOT NULL DEFAULT 0,
    remark       VARCHAR(200),
    created_at   TIMESTAMP NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP NOT NULL DEFAULT now(),
    deleted      SMALLINT    NOT NULL DEFAULT 0
);

COMMENT ON TABLE  rbac_role            IS '角色表（动态，非枚举）';
COMMENT ON COLUMN rbac_role.is_management IS '是否管理层（社长/副社长/老师），用于管理层唯一约束';

CREATE UNIQUE INDEX uk_rbac_role_code ON rbac_role (code) WHERE deleted = 0;

-- ---------- 权限点表 ----------
CREATE TABLE IF NOT EXISTS rbac_permission (
    id         BIGINT       PRIMARY KEY,
    code       VARCHAR(100) NOT NULL,                   -- 如 club:member:approve
    name       VARCHAR(50)  NOT NULL,
    type       VARCHAR(20)  NOT NULL DEFAULT 'ACTION',  -- MENU / BUTTON / ACTION
    parent_id  BIGINT       NOT NULL DEFAULT 0,
    sort       INT          NOT NULL DEFAULT 0,
    created_at TIMESTAMP  NOT NULL DEFAULT now(),
    updated_at TIMESTAMP  NOT NULL DEFAULT now(),
    deleted    SMALLINT     NOT NULL DEFAULT 0
);

COMMENT ON TABLE  rbac_permission            IS '权限点表（业务动作/菜单/按钮级）';
COMMENT ON COLUMN rbac_permission.type       IS '类型：MENU=菜单 BUTTON=按钮 ACTION=业务动作';

CREATE UNIQUE INDEX uk_rbac_permission_code ON rbac_permission (code) WHERE deleted = 0;

-- ---------- 角色-权限关联表（RBAC 多对多） ----------
CREATE TABLE IF NOT EXISTS rbac_role_permission (
    id            BIGINT       PRIMARY KEY,
    role_id       BIGINT      NOT NULL,
    permission_id BIGINT      NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT now()
);

COMMENT ON TABLE rbac_role_permission IS '角色-权限关联表（RBAC）';

CREATE UNIQUE INDEX uk_role_permission ON rbac_role_permission (role_id, permission_id);

-- ---------- 操作日志表（@Log 注解落库） ----------
CREATE TABLE IF NOT EXISTS oper_log (
    id            BIGINT       PRIMARY KEY,
    module        VARCHAR(50),                          -- 模块名（如 认证/个人信息）
    operation     VARCHAR(100),                         -- 操作描述
    request_method VARCHAR(10),
    request_uri   VARCHAR(200),
    java_method   VARCHAR(200),                         -- 全限定方法名
    params        TEXT,                                 -- 请求参数（JSON）
    result        SMALLINT,                             -- 1=成功 0=失败
    error_msg     VARCHAR(1000),
    operator_id   BIGINT,
    operator_name VARCHAR(50),
    cost_time     BIGINT,                               -- 耗时（毫秒）
    ip            VARCHAR(64),
    created_at    TIMESTAMP NOT NULL DEFAULT now()
);

COMMENT ON TABLE oper_log IS '操作日志表（审计留痕）';

CREATE INDEX ix_oper_log_operator ON oper_log (operator_id, created_at DESC);
CREATE INDEX ix_oper_log_created  ON oper_log (created_at DESC);

-- ---------- 登录日志表（成功/失败留痕，安全审计） ----------
CREATE TABLE IF NOT EXISTS login_log (
    id         BIGINT       PRIMARY KEY,
    username   VARCHAR(50)  NOT NULL,
    ip         VARCHAR(64),
    status     SMALLINT     NOT NULL,                   -- 1=成功 0=失败
    message    VARCHAR(200),                            -- 失败原因
    created_at TIMESTAMP  NOT NULL DEFAULT now()
);

COMMENT ON TABLE login_log IS '登录日志表（防爆破审计）';

CREATE INDEX ix_login_log_username ON login_log (username, created_at DESC);
CREATE INDEX ix_login_log_created  ON login_log (created_at DESC);
