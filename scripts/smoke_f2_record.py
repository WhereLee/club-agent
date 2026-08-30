# -*- coding: utf-8 -*-
"""块 G 冒烟：签到（checkin/list）+ 执行留痕（submit/mine/list + 截止）"""
import json
import os
import subprocess
import sys
import urllib.request

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

BASE = "http://127.0.0.1:8093"
CLUB = "2092285569724362753"
ACT = "2093806568215207938"

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


print("=== 块 G 冒烟：签到 + 执行留痕 ===")
ptok = login("stu_7f36dc")
vtok = login("stu_4512db")
wtok = login("stu_7ff1f7")
PH = {"Authorization": f"Bearer {ptok}"}
VH = {"Authorization": f"Bearer {vtok}"}
WH = {"Authorization": f"Bearer {wtok}"}
check("登录", True)

uid_p = sql(f"SELECT user_id FROM activity WHERE id={ACT}")
uid_v = sql(f"SELECT id FROM sys_user WHERE username='stu_4512db'")
uid_w = sql(f"SELECT id FROM sys_user WHERE username='stu_7ff1f7'")

# ---- 0) 数据准备：重置活动 B 到报名中 + 清签到/留痕 ----
sql(f"UPDATE activity SET status=5, signup_deadline='2026-09-10 20:00:00', record_deadline=NULL WHERE id={ACT}")
sql(f"DELETE FROM activity_attendance WHERE activity_id={ACT}")
sql(f"DELETE FROM activity_signup WHERE activity_id={ACT}")
sql(f"""DELETE FROM form_answer WHERE submission_id IN
        (SELECT s.id FROM form_submission s JOIN form_template t ON s.template_id=t.id
         WHERE t.activity_id={ACT} AND t.type='record')""")
sql(f"""DELETE FROM form_submission WHERE template_id IN
        (SELECT id FROM form_template WHERE activity_id={ACT} AND type='record')""")
sql(f"""DELETE FROM form_field WHERE template_id IN
        (SELECT id FROM form_template WHERE activity_id={ACT} AND type='record')""")
sql(f"DELETE FROM form_template WHERE activity_id={ACT} AND type='record'")
# 清块 F 冒烟残留的"不感兴趣"问卷答案（活动 B survey 模板 2093808388513783810）
sql(f"DELETE FROM form_answer WHERE submission_id IN (SELECT id FROM form_submission WHERE template_id=2093808388513783810)")
sql(f"DELETE FROM form_submission WHERE template_id=2093808388513783810")
# 报名：发起人 + 副社长参加
r = api("POST", f"/clubs/{CLUB}/activities/{ACT}/signup", headers=PH, body={"choice": "participate"})
check("发起人报名 200", r.get("code") == 200, str(r)[:60])
r = api("POST", f"/clubs/{CLUB}/activities/{ACT}/signup", headers=VH, body={"choice": "participate"})
check("副社长报名 200", r.get("code") == 200, str(r)[:60])

# ---- 1) 签到前置校验 ----
r = api("POST", f"/clubs/{CLUB}/activities/{ACT}/attendance", headers=VH)
check("非执行中签到 1037", r.get("code") == 1037, str(r)[:80])

# ---- 2) 开始执行（带留痕截止）+ 签到 ----
r = api("POST", f"/clubs/{CLUB}/activities/{ACT}/execution/start", headers=PH,
        body={"deadline": "2026-09-12T20:00:00"})
check("开始执行 200", r.get("code") == 200, str(r)[:80])
r = api("GET", f"/clubs/{CLUB}/activities/{ACT}", headers=PH)
check("状态=6 执行中", (r.get("data") or {}).get("status") == 6)
check("recordDeadline 已存", (r.get("data") or {}).get("recordDeadline") is not None, str((r.get("data") or {}).get("recordDeadline")))
r = api("POST", f"/clubs/{CLUB}/activities/{ACT}/attendance", headers=WH)
check("未报名签到 1046", r.get("code") == 1046, str(r)[:80])
r = api("POST", f"/clubs/{CLUB}/activities/{ACT}/attendance", headers=VH)
check("副社长签到 200", r.get("code") == 200, str(r)[:80])
r = api("POST", f"/clubs/{CLUB}/activities/{ACT}/attendance", headers=VH)
check("重复签到幂等 200", r.get("code") == 200, str(r)[:80])
r = api("POST", f"/clubs/{CLUB}/activities/{ACT}/attendance", headers=PH)
check("发起人签到 200", r.get("code") == 200, str(r)[:80])

# ---- 3) 签到名单 ----
r = api("GET", f"/clubs/{CLUB}/activities/{ACT}/attendances", headers=PH)
d = r.get("data") or []
check("名单 2 人（参加者）", len(d) == 2, f"n={len(d)}")
m_v = next((x for x in d if x["userId"] == uid_v), None)
check("副社长 signed=true", m_v is not None and m_v.get("signed") is True, str(m_v)[:120])
check("副社长 checkedAt 非空", m_v is not None and m_v.get("checkedAt") is not None, str(m_v)[:120])
m_p = next((x for x in d if x["userId"] == uid_p), None)
check("发起人 signed=true", m_p is not None and m_p.get("signed") is True, str(m_p)[:120])

# ---- 4) 结束执行 → 留痕中 + 模板自动创建 ----
r = api("POST", f"/clubs/{CLUB}/activities/{ACT}/execution/complete", headers=PH)
check("结束执行 200", r.get("code") == 200, str(r)[:80])
r = api("GET", f"/clubs/{CLUB}/activities/{ACT}", headers=PH)
check("状态=7 留痕中", (r.get("data") or {}).get("status") == 7)
r = api("GET", f"/clubs/{CLUB}/activities/{ACT}/records/mine", headers=VH)
d = r.get("data") or {}
check("留痕模板 3 字段", len(d.get("fields") or []) == 3, str((d.get("fields") or [])[:3]))
check("模板含完成情况选项", any(f.get("label") == "完成情况" and f.get("options") == ["已完成", "进行中", "受阻"]
                          for f in (d.get("fields") or [])), str(d.get("fields"))[:200])
check("本人未提交（answers 空）", not (d.get("answers") or []), str(d)[:120])

# ---- 5) 留痕提交 ----
fid_work = next(f["fieldId"] for f in (d.get("fields") or []) if f["label"] == "工作内容")
fid_done = next(f["fieldId"] for f in (d.get("fields") or []) if f["label"] == "完成情况")
fid_note = next(f["fieldId"] for f in (d.get("fields") or []) if f["label"] == "补充说明")
r = api("POST", f"/clubs/{CLUB}/activities/{ACT}/records", headers=VH,
        body={"answers": [{"fieldId": fid_done, "value": "已完成"}]})
check("缺必填提交 1050", r.get("code") == 1050, str(r)[:80])
r = api("POST", f"/clubs/{CLUB}/activities/{ACT}/records", headers=VH,
        body={"answers": [{"fieldId": fid_work, "value": "负责现场秩序维护与签到引导"},
                          {"fieldId": fid_done, "value": "已完成"},
                          {"fieldId": fid_note, "value": "整体顺利"}]})
check("留痕提交 200", r.get("code") == 200, str(r)[:80])
cnt = int(sql(f"""SELECT count(*) FROM form_submission WHERE template_id IN
        (SELECT id FROM form_template WHERE activity_id={ACT} AND type='record') AND user_id={uid_v}""") or 0)
check("一人一份提交", cnt == 1, f"cnt={cnt}")
# 覆盖更新
r = api("POST", f"/clubs/{CLUB}/activities/{ACT}/records", headers=VH,
        body={"answers": [{"fieldId": fid_work, "value": "负责现场秩序维护与签到引导（补充：二次巡检）"},
                          {"fieldId": fid_done, "value": "已完成"}]})
check("留痕覆盖更新 200", r.get("code") == 200, str(r)[:80])
cnt = int(sql(f"""SELECT count(*) FROM form_submission WHERE template_id IN
        (SELECT id FROM form_template WHERE activity_id={ACT} AND type='record') AND user_id={uid_v}""") or 0)
check("覆盖后仍一人一份", cnt == 1, f"cnt={cnt}")
r = api("GET", f"/clubs/{CLUB}/activities/{ACT}/records/mine", headers=VH)
d = r.get("data") or {}
check("回显已提交内容", any(a.get("value") and "二次巡检" in a.get("value") for a in (d.get("answers") or [])), str(d.get("answers"))[:200])
# fieldId 归属校验
r = api("POST", f"/clubs/{CLUB}/activities/{ACT}/records", headers=VH,
        body={"answers": [{"fieldId": 999999999999, "value": "x"}]})
check("非法 fieldId 400", r.get("code") == 400, str(r)[:80])

# ---- 6) 留痕列表（管理层）----
r = api("GET", f"/clubs/{CLUB}/activities/{ACT}/records", headers=PH)
d = r.get("data") or []
check("留痕列表 1 条", len(d) == 1, f"n={len(d)}")
check("列表含提交人昵称", d and d[0].get("nickname") == "学生4512db", str(d)[:150])
check("列表 answers 非空", d and len(d[0].get("answers") or []) >= 2, str(d)[:200])

# ---- 7) 留痕截止 → 1047 ----
sql(f"UPDATE activity SET record_deadline='2026-08-01 20:00:00' WHERE id={ACT}")
r = api("POST", f"/clubs/{CLUB}/activities/{ACT}/records", headers=PH,
        body={"answers": [{"fieldId": fid_work, "value": "测试"}, {"fieldId": fid_done, "value": "已完成"}]})
check("截止后提交 1047", r.get("code") == 1047, str(r)[:80])
sql(f"UPDATE activity SET record_deadline='2026-09-12 20:00:00' WHERE id={ACT}")

print(f"\n=== 块 G 结果: PASS {PASS} / FAIL {FAIL} ===")
sys.exit(1 if FAIL else 0)
