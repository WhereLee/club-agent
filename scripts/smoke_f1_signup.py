# -*- coding: utf-8 -*-
"""块 F 冒烟：报名（signup/list + 不感兴趣拦截 + 在线协助 + 截止 + 名单）"""
import json
import os
import subprocess
import sys
import urllib.request

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

BASE = "http://127.0.0.1:8093"
CLUB = "2092285569724362753"
ACT_PUBLISHED = "2093806568215207938"  # 状态 5 报名中（脚本重置）
ACT_DISCUSS = "2093889438208274435"    # 状态 3 非报名中
SURVEY_FIELD = "2093808388513783811"   # 问卷"是否感兴趣" system_flag=1

PASS = 0
FAIL = 0


def check(name, ok, extra=""):
    global PASS, FAIL
    if ok:
        PASS += 1
        print(f"PASS {name}")
    else:
        FAIL += 1
        print(f"FAIL {name} {extra}")


def api(method, path, headers=None, body=None, timeout=30):
    req = urllib.request.Request(BASE + path, method=method)
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    d = None
    if body is not None:
        d = json.dumps(body, ensure_ascii=False).encode("utf-8")
        req.add_header("Content-Type", "application/json; charset=utf-8")
    try:
        with urllib.request.urlopen(req, data=d, timeout=timeout) as r:
            return json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return {"http": e.code, "body": e.read().decode("utf-8", "replace")}


def sql(q):
    return subprocess.run(
        ["psql", "-U", "postgres", "-h", "127.0.0.1", "-d", "club_agent", "-t", "-A", "-c", q],
        env={**os.environ, "PGPASSWORD": "root"},
        capture_output=True, text=True, encoding="utf-8", errors="replace",
    ).stdout.strip()


def login(username, password="Test123456"):
    cap = api("GET", "/auth/captcha")
    key = cap["data"]["captchaKey"]
    code = subprocess.run(["F:/Redis/redis-cli.exe", "GET", f"club:captcha:{key}"], capture_output=True, text=True).stdout.strip()
    return api("POST", "/auth/login", body={"username": username, "password": password, "captchaKey": key, "captchaCode": code})["data"]["token"]


print("=== 块 F 冒烟：报名 ===")
ptok = login("stu_7f36dc")
vtok = login("stu_4512db")
PH = {"Authorization": f"Bearer {ptok}"}
VH = {"Authorization": f"Bearer {vtok}"}
check("登录", True)

uid_p = sql(f"SELECT user_id FROM activity WHERE id={ACT_PUBLISHED}")          # 发起人
uid_v = sql(f"SELECT id FROM sys_user WHERE username='stu_4512db'")

# ---- 0) 数据准备：活动 B 重置到报名中 + 清报名/问卷答案 ----
sql(f"UPDATE activity SET status=5, signup_deadline='2026-09-10 20:00:00' WHERE id={ACT_PUBLISHED}")
sql(f"DELETE FROM activity_signup WHERE activity_id={ACT_PUBLISHED}")
sql(f"DELETE FROM activity_discussion_summary WHERE activity_id={ACT_PUBLISHED}")
# 清副社长问卷答案（form_answer 级联前先删）
sql(f"DELETE FROM form_answer WHERE submission_id IN (SELECT id FROM form_submission WHERE template_id=2093808388513783810 AND user_id={uid_v})")
sql(f"DELETE FROM form_submission WHERE template_id=2093808388513783810 AND user_id={uid_v}")
check("数据准备完成", int(sql(f"SELECT count(*) FROM activity_signup WHERE activity_id={ACT_PUBLISHED}") or 0) == 0)

# ---- 1) 非报名中活动报名 → 1037 ----
r = api("POST", f"/clubs/{CLUB}/activities/{ACT_DISCUSS}/signup", headers=VH,
        body={"choice": "participate", "onlineAssist": False})
check("非报名中报名 1037", r.get("code") == 1037, str(r)[:80])

# ---- 2) 正常报名 participate ----
r = api("POST", f"/clubs/{CLUB}/activities/{ACT_PUBLISHED}/signup", headers=VH,
        body={"choice": "participate", "onlineAssist": False})
check("报名 participate 200", r.get("code") == 200, str(r)[:80])
row = sql(f"SELECT choice FROM activity_signup WHERE activity_id={ACT_PUBLISHED} AND user_id={uid_v}")
check("库中 choice=participate", row == "participate", row)

# ---- 3) 修改报名：not_participate + onlineAssist ----
r = api("POST", f"/clubs/{CLUB}/activities/{ACT_PUBLISHED}/signup", headers=VH,
        body={"choice": "not_participate", "onlineAssist": True})
check("修改报名 200", r.get("code") == 200, str(r)[:80])
row = sql(f"SELECT choice || '|' || online_assist FROM activity_signup WHERE activity_id={ACT_PUBLISHED} AND user_id={uid_v}")
check("覆盖更新 choice+assist", row == "not_participate|true", row)
cnt = int(sql(f"SELECT count(*) FROM activity_signup WHERE activity_id={ACT_PUBLISHED} AND user_id={uid_v}") or 0)
check("uk 一人一条", cnt == 1, f"cnt={cnt}")
n = sql(f"SELECT count(*) FROM message WHERE recipient_id={uid_p} AND type='activity_online_assist'")
check("在线协助通知发起人", int(n or 0) >= 1, f"n={n}")

# ---- 4) 不感兴趣拦截（造问卷答案：system_flag=1 字段答"不感兴趣"）----
_tmp = os.path.join(os.environ.get("TEMP", "."), "_f1_interested.sql")
sid = 9100000000000000001
with open(_tmp, "w", encoding="utf-8") as f:
    f.write(
        f"INSERT INTO form_submission (id, template_id, activity_id, user_id, submitted_at) "
        f"VALUES ({sid}, 2093808388513783810, {ACT_PUBLISHED}, {uid_v}, now());\n"
        f"INSERT INTO form_answer (id, submission_id, field_id, value, created_at) "
        f"VALUES ({sid + 1}, {sid}, {SURVEY_FIELD}, '不感兴趣', now());\n"
    )
subprocess.run(["psql", "-U", "postgres", "-h", "127.0.0.1", "-d", "club_agent", "-f", _tmp],
               env={**os.environ, "PGPASSWORD": "root"}, capture_output=True, text=True,
               encoding="utf-8", errors="replace")
check("不感兴趣答案已造", int(sql(f"SELECT count(*) FROM form_answer WHERE submission_id={sid}") or 0) == 1)
r = api("POST", f"/clubs/{CLUB}/activities/{ACT_PUBLISHED}/signup", headers=VH,
        body={"choice": "participate", "onlineAssist": False})
check("不感兴趣报名参加 1045", r.get("code") == 1045, str(r)[:80])
r = api("POST", f"/clubs/{CLUB}/activities/{ACT_PUBLISHED}/signup", headers=VH,
        body={"choice": "not_participate", "onlineAssist": True})
check("不感兴趣在线协助放行 200", r.get("code") == 200, str(r)[:80])

# ---- 5) 截止后报名 → 1044 ----
sql(f"UPDATE activity SET signup_deadline='2026-08-01 20:00:00' WHERE id={ACT_PUBLISHED}")
r = api("POST", f"/clubs/{CLUB}/activities/{ACT_PUBLISHED}/signup", headers=VH,
        body={"choice": "participate", "onlineAssist": False})
check("截止后报名 1044", r.get("code") == 1044, str(r)[:80])
sql(f"UPDATE activity SET signup_deadline='2026-09-10 20:00:00' WHERE id={ACT_PUBLISHED}")

# ---- 6) 发起人报名（无拦截）----
r = api("POST", f"/clubs/{CLUB}/activities/{ACT_PUBLISHED}/signup", headers=PH,
        body={"choice": "participate", "onlineAssist": False})
check("发起人报名 200", r.get("code") == 200, str(r)[:80])

# ---- 7) 名单（管理层视图）----
r = api("GET", f"/clubs/{CLUB}/activities/{ACT_PUBLISHED}/signups", headers=PH)
d = r.get("data") or []
check("名单 3 人（全员）", len(d) == 3, f"n={len(d)}")
m_v = next((x for x in d if x["userId"] == uid_v), None)
check("副社长 blocked=true", m_v is not None and m_v.get("blocked") is True, str(m_v)[:120])
check("副社长 choice=not_participate", m_v is not None and m_v.get("choice") == "not_participate", str(m_v)[:120])
check("副社长 onlineAssist=true", m_v is not None and m_v.get("onlineAssist") is True, str(m_v)[:120])
check("副社长 signupAt 非空", m_v is not None and m_v.get("signupAt") is not None, str(m_v)[:120])
m_p = next((x for x in d if x["userId"] == uid_p), None)
check("发起人 choice=participate", m_p is not None and m_p.get("choice") == "participate", str(m_p)[:120])
check("发起人 blocked=false", m_p is not None and m_p.get("blocked") is False, str(m_p)[:120])
m_none = next((x for x in d if x.get("choice") is None), None)
check("未报名成员 choice=null", m_none is not None, str(d)[:200])

# ---- 8) choice 参数校验 ----
r = api("POST", f"/clubs/{CLUB}/activities/{ACT_PUBLISHED}/signup", headers=PH,
        body={"choice": "maybe", "onlineAssist": False})
check("非法 choice 400", r.get("code") == 400, str(r)[:80])

print(f"\n=== 块 F 结果: PASS {PASS} / FAIL {FAIL} ===")
sys.exit(1 if FAIL else 0)
