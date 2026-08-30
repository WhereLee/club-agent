-- =============================================================
-- 社团管理 Agent 测试数据（幂等：以 username / club name / user+club 为键）
-- 学生密码统一：teacher123456（复用 teacher1 的 BCrypt hash）
-- 角色 id：president=2091961234107154433 / vice_president=2091961234107154434 / member=2091961234107154435
-- 老师 id：teacher1=2091961234828574722 / teacher2=2091961235155730433
-- =============================================================

-- ---------- 用户（6 名学生，昵称符合 2-12 汉字规则） ----------
INSERT INTO sys_user (id, username, password_hash, email, nickname, is_teacher, status, created_at, updated_at, deleted)
SELECT v.id, v.username, v.hash, v.email, v.nickname, FALSE, 1, now(), now(), 0
FROM (VALUES
  (2099990000000000001, 'wangzhe', '$2a$10$qXBT0RlLN1Uta7/AK9mI5uui5OcVwkzUuVMTJZaglCZmfchhPzOWq', 'wangzhe@test.club',  '王者'),
  (2099990000000000002, 'liuyang', '$2a$10$qXBT0RlLN1Uta7/AK9mI5uui5OcVwkzUuVMTJZaglCZmfchhPzOWq', 'liuyang@test.club',  '刘洋'),
  (2099990000000000003, 'chenxiao', '$2a$10$qXBT0RlLN1Uta7/AK9mI5uui5OcVwkzUuVMTJZaglCZmfchhPzOWq', 'chenxiao@test.club', '陈晓'),
  (2099990000000000004, 'zhaomin', '$2a$10$qXBT0RlLN1Uta7/AK9mI5uui5OcVwkzUuVMTJZaglCZmfchhPzOWq', 'zhaomin@test.club',  '赵敏'),
  (2099990000000000005, 'sunli',   '$2a$10$qXBT0RlLN1Uta7/AK9mI5uui5OcVwkzUuVMTJZaglCZmfchhPzOWq', 'sunli@test.club',    '孙丽'),
  (2099990000000000006, 'zhouqiang', '$2a$10$qXBT0RlLN1Uta7/AK9mI5uui5OcVwkzUuVMTJZaglCZmfchhPzOWq', 'zhouqiang@test.club', '周强')
) AS v(id, username, hash, email, nickname)
WHERE NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.username = v.username);

-- ---------- 社团（3 个可读社团） ----------
INSERT INTO club (id, name, description, teacher_id, created_at, updated_at, deleted)
SELECT v.id, v.name, v.description, v.teacher_id, now(), now(), 0
FROM (VALUES
  (2099990000000001001, '骑行社',   '周末骑行、远途拉练，欢迎热爱骑行的同学', 2091961234828574722),
  (2099990000000001002, '摄影社',   '校园摄影与后期分享',                    2091961234828574722),
  (2099990000000001003, '读书会',   '每月共读一本书，线下交流',              2091961235155730433)
) AS v(id, name, description, teacher_id)
WHERE NOT EXISTS (SELECT 1 FROM club c WHERE c.name = v.name AND c.deleted = 0);

-- ---------- 成员关系（已通过 status=1） ----------
-- 约束校验：每人至多在一个社团任管理层；每社一社长一副社长
-- 骑行社：王者=社长 刘洋=副社长 陈晓/赵敏=社员
-- 摄影社：陈晓=社长 孙丽=副社长 王者=社员
-- 读书会：赵敏=社长 周强=副社长 刘洋=社员
INSERT INTO membership (id, user_id, club_id, role_id, status, applied_at, approved_at, approved_by, created_at, updated_at)
SELECT v.id, v.user_id, v.club_id, v.role_id, 1, now(), now(), v.approved_by, now(), now()
FROM (VALUES
  (2099990000000002001, 2099990000000000001, 2099990000000001001, 2091961234107154433, 2091961234828574722),
  (2099990000000002002, 2099990000000000002, 2099990000000001001, 2091961234107154434, 2091961234828574722),
  (2099990000000002003, 2099990000000000003, 2099990000000001001, 2091961234107154435, 2091961234828574722),
  (2099990000000002004, 2099990000000000004, 2099990000000001001, 2091961234107154435, 2091961234828574722),
  (2099990000000002005, 2099990000000000003, 2099990000000001002, 2091961234107154433, 2091961234828574722),
  (2099990000000002006, 2099990000000000005, 2099990000000001002, 2091961234107154434, 2091961234828574722),
  (2099990000000002007, 2099990000000000001, 2099990000000001002, 2091961234107154435, 2091961234828574722),
  (2099990000000002008, 2099990000000000004, 2099990000000001003, 2091961234107154433, 2091961235155730433),
  (2099990000000002009, 2099990000000000006, 2099990000000001003, 2091961234107154434, 2091961235155730433),
  (2099990000000002010, 2099990000000000002, 2099990000000001003, 2091961234107154435, 2091961235155730433)
) AS v(id, user_id, club_id, role_id, approved_by)
WHERE NOT EXISTS (SELECT 1 FROM membership m WHERE m.user_id = v.user_id AND m.club_id = v.club_id);

-- ---------- 汇总 ----------
SELECT 'users' AS item, count(*) FROM sys_user WHERE username LIKE 'wangzhe%' OR username LIKE 'liuyang%' OR username LIKE 'chenxiao%' OR username LIKE 'zhaomin%' OR username LIKE 'sunli%' OR username LIKE 'zhouqiang%'
UNION ALL
SELECT 'clubs', count(*) FROM club WHERE name IN ('骑行社','摄影社','读书会') AND deleted = 0
UNION ALL
SELECT 'memberships', count(*) FROM membership WHERE id BETWEEN 2099990000000002001 AND 2099990000000002010;
