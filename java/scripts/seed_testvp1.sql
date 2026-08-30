-- 测试数据：为骑行社补第二位副社长（testvp1，投票测试用）
-- 清理悬空成员关系（上次编码异常导致 user 未落库）
DELETE FROM membership WHERE user_id = 2099990000000009001;

-- 用户（昵称英文，避免命令行中文编码问题）
INSERT INTO sys_user (id, username, password_hash, email, nickname, is_teacher, status, created_at, updated_at)
SELECT 2099990000000009001, 'testvp1', password_hash, 'testvp1@test.com', 'testvp1', false, 1, now(), now()
FROM sys_user WHERE username = 'wangzhe';

-- 成员关系：骑行社 vice_president
INSERT INTO membership (id, user_id, club_id, role_id, status, applied_at, created_at, updated_at)
VALUES (2099990000000009002, 2099990000000009001, 2099990000000001001,
        (SELECT id FROM rbac_role WHERE code = 'vice_president'), 1, now(), now(), now());
