-- =============================================================
-- 基础域补强：第X任换届模型 + 存量数据清洗（幂等）
-- 1. club 增加管理层届数计数器；membership 增加任期届数
-- 2. 存量管理层补第1任标记；存量不合规昵称清洗
-- =============================================================

-- ---------- DDL：届数字段 ----------
ALTER TABLE club ADD COLUMN IF NOT EXISTS president_term_no    BIGINT NOT NULL DEFAULT 0;
ALTER TABLE club ADD COLUMN IF NOT EXISTS vice_president_term_no BIGINT NOT NULL DEFAULT 0;
ALTER TABLE membership ADD COLUMN IF NOT EXISTS term_no BIGINT;
ALTER TABLE membership ADD COLUMN IF NOT EXISTS former_role_code VARCHAR(20);

COMMENT ON COLUMN club.president_term_no     IS '社长届数计数器（任命时+1）';
COMMENT ON COLUMN club.vice_president_term_no IS '副社长届数计数器（任命时+1）';
COMMENT ON COLUMN membership.term_no         IS '任期届数（管理层任命时写入；离职保留作为第X任标记）';
COMMENT ON COLUMN membership.former_role_code IS '前任管理层职务（离职时写入，用于第X任展示）';

-- ---------- 存量管理层补届数：现有在职管理层一律视为第 1 任 ----------
UPDATE club c
SET president_term_no = 1
WHERE c.president_term_no = 0
  AND EXISTS (SELECT 1 FROM membership m
              JOIN rbac_role r ON r.id = m.role_id
              WHERE m.club_id = c.id AND m.status = 1 AND r.code = 'president');

UPDATE club c
SET vice_president_term_no = 1
WHERE c.vice_president_term_no = 0
  AND EXISTS (SELECT 1 FROM membership m
              JOIN rbac_role r ON r.id = m.role_id
              WHERE m.club_id = c.id AND m.status = 1 AND r.code = 'vice_president');

UPDATE membership m
SET term_no = 1
WHERE m.status = 1 AND m.term_no IS NULL
  AND m.role_id IN (SELECT r.id FROM rbac_role r WHERE r.code IN ('president', 'vice_president'));

-- ---------- 存量昵称清洗（对齐 @Nickname：中文/英文/数字，无符号） ----------
UPDATE sys_user SET nickname = '指导老师' || username
WHERE deleted = 0 AND username IN ('teacher1', 'teacher2') AND nickname !~ '^[\u4e00-\u9fa5A-Za-z0-9]+$';

UPDATE sys_user SET nickname = '学生' || substr(username, 5)
WHERE deleted = 0 AND username LIKE 'stu\_%' AND nickname !~ '^[\u4e00-\u9fa5A-Za-z0-9]+$';

-- ---------- 校验：应无不合规昵称 ----------
SELECT username, nickname FROM sys_user
WHERE deleted = 0 AND (nickname !~ '^[\u4e00-\u9fa5A-Za-z0-9]+$'
    OR (SELECT sum(CASE WHEN ch ~ '[\u4e00-\u9fa5]' THEN 2 ELSE 1 END) FROM regexp_split_to_table(nickname, '') AS ch) < 4
    OR (SELECT sum(CASE WHEN ch ~ '[\u4e00-\u9fa5]' THEN 2 ELSE 1 END) FROM regexp_split_to_table(nickname, '') AS ch) > 24);
