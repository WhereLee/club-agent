-- S1 存量清洗：oper_log.params 中的明文密码掩码（幂等，可重复执行）
-- 覆盖 register/login/改密三类字段名；重复执行时已掩码的 ****** 不再变化
UPDATE oper_log
SET params = regexp_replace(
        regexp_replace(
            regexp_replace(
                params,
                '"password"\s*:\s*"[^"]*"', '"password":"******"', 'g'
            ),
            '"oldPassword"\s*:\s*"[^"]*"', '"oldPassword":"******"', 'g'
        ),
        '"newPassword"\s*:\s*"[^"]*"', '"newPassword":"******"', 'g'
    )
WHERE params LIKE '%"password"%'
   OR params LIKE '%"oldPassword"%'
   OR params LIKE '%"newPassword"%';
