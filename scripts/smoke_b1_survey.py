# -*- coding: utf-8 -*-
"""块 B 冒烟：问卷全链路（发布/详情/提交/重复/必填/截止/结果/关闭进讨论）"""
import json
import subprocess
import time
import urllib.request

BASE = "http://127.0.0.1:8093"
CLUB = "2092285569724362753"
CONCEPT = "2093792187737792513"

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


def api(method, path, headers=None, body=None):
    req = urllib.request.Request(BASE + path, method=method)
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    d = None
    if body is not None:
        d = json.dumps(body, ensure_ascii=False).encode("utf-8")
        req.add_header("Content-Type", "application/json; charset=utf-8")
    try:
        with urllib.request.urlopen(req, data=d, timeout=30) as r:
            return json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return {"http": e.code, "body": e.read().decode("utf-8", "replace")}


def login(username, password="Test123456"):
    cap = api("GET", "/auth/captcha")
    key = cap["data"]["captchaKey"]
    code = subprocess.run(["F:/Redis/redis-cli.exe", "GET", f"club:captcha:{key}"], capture_output=True, text=True).stdout.strip()
    return api("POST", "/auth/login", body={"username": username, "password": password, "captchaKey": key, "captchaCode": code})["data"]["token"]


def sql(q):
    r = subprocess.run(["psql", "-U", "postgres", "-h", "127.0.0.1", "-d", "club_agent", "-t", "-c", q],
                       env={**__import__("os").environ, "PGPASSWORD": "root"},
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    return (r.stdout or "").strip()


print("=== 块 B 问卷冒烟 ===")
ptok = login("stu_7f36dc")   # 社长（发起人）
vtok = login("stu_4512db")   # 副社长
ttok = login("teacher1", "teacher123456")
PH = {"Authorization": f"Bearer {ptok}"}
VH = {"Authorization": f"Bearer {vtok}"}
TH = {"Authorization": f"Bearer {ttok}"}
check("三方登录", True)

# 1) 概念推进 → 老师批复 → 活动公示中
st = sql(f"SELECT status FROM concept_session WHERE id='{CONCEPT}'")
if st not in ("4", "5"):
    sql(f"UPDATE concept_session SET status=4, deadline=now()+interval '36 hours' WHERE id='{CONCEPT}'")
check("概念可推进", sql(f"SELECT status FROM concept_session WHERE id='{CONCEPT}'") in ("4", "5"))
if st != "5":
    r = api("POST", f"/clubs/{CLUB}/concepts/{CONCEPT}/review", headers=TH, body={"approve": True, "comment": "同意"})
    check("老师批复", r.get("code") == 200, str(r)[:100])
r = api("GET", f"/clubs/{CLUB}/activities?page=1&size=10&status=1", headers=PH)
items = (r.get("data") or {}).get("records") or []
act = next((a for a in items if str(a.get("conceptId")) == CONCEPT), None)
check("活动公示中", act is not None and act.get("status") == 1, f"items={len(items)}")
AID = act["id"]

# 2) 非发起人发布问卷 403
deadline = "2026-09-02T20:00:00"
body = {"deadline": deadline, "fields": [
    {"label": "你会修车吗？", "fieldType": "radio", "required": 0, "options": ["会", "不会"]},
    {"label": "1 天还是 2 天？", "fieldType": "radio", "required": 1, "options": ["1 天", "2 天"]},
]}
r = api("POST", f"/clubs/{CLUB}/activities/{AID}/survey", headers=VH, body=body)
check("非发起人发布 403", r.get("code") == 403, str(r)[:80])

# 3) 发起人发布
r = api("POST", f"/clubs/{CLUB}/activities/{AID}/survey", headers=PH, body=body)
check("发布问卷", r.get("code") == 200, str(r)[:120])
d = r.get("data") or {}
fields = d.get("fields") or []
check("含系统内置是否感兴趣", any(f.get("systemFlag") == 1 and f.get("label") == "是否感兴趣" for f in fields), str([f.get("label") for f in fields]))
check("含自定义题 2 道", len(fields) == 3, str(len(fields)))
check("状态问卷中", d.get("status") == 1)

# 4) 详情 trace + 通知
r = api("GET", f"/clubs/{CLUB}/activities/{AID}", headers=PH)
tr = (r.get("data") or {}).get("traces") or []
check("trace survey_publish", any(t.get("action") == "survey_publish" for t in tr))
r = api("GET", "/messages?page=1&size=10", headers=VH)
msgs = (r.get("data") or {}).get("records") or []
check("问卷发布通知全员", any(m.get("type") == "activity_survey" for m in msgs))

# 5) 提交链路
FIELDS = {f["label"]: f for f in fields}
f_interest = FIELDS["是否感兴趣"]["id"]
f_repair = FIELDS["你会修车吗？"]["id"]
f_days = FIELDS["1 天还是 2 天？"]["id"]
# 缺必答（是否感兴趣 + 天数）→ 1041
r = api("POST", f"/clubs/{CLUB}/activities/{AID}/survey/submit", headers=PH,
        body={"answers": [{"fieldId": f_repair, "value": "会"}]})
check("缺必答题 1041", r.get("code") == 1041, str(r)[:80])
# 合法提交（社长：感兴趣 + 会修车 + 2 天）
r = api("POST", f"/clubs/{CLUB}/activities/{AID}/survey/submit", headers=PH,
        body={"answers": [{"fieldId": f_interest, "value": "感兴趣"}, {"fieldId": f_repair, "value": "会"}, {"fieldId": f_days, "value": "2 天"}]})
check("社长提交成功", r.get("code") == 200, str(r)[:80])
# 重复提交 1040
r = api("POST", f"/clubs/{CLUB}/activities/{AID}/survey/submit", headers=PH,
        body={"answers": [{"fieldId": f_interest, "value": "感兴趣"}]})
check("重复提交 1040", r.get("code") == 1040, str(r)[:80])
# 副社长提交（不感兴趣 + 不会修车 + 1 天）
r = api("POST", f"/clubs/{CLUB}/activities/{AID}/survey/submit", headers=VH,
        body={"answers": [{"fieldId": f_interest, "value": "不感兴趣"}, {"fieldId": f_repair, "value": "不会"}, {"fieldId": f_days, "value": "1 天"}]})
check("副社长提交成功", r.get("code") == 200, str(r)[:80])
# 详情 submitted=true
r = api("GET", f"/clubs/{CLUB}/activities/{AID}/survey", headers=PH)
check("详情 submitted=true", (r.get("data") or {}).get("submitted") is True)

# 6) 结果统计
r = api("GET", f"/clubs/{CLUB}/activities/{AID}/survey/results", headers=PH)
res = r.get("data") or {}
check("结果提交数=2", res.get("totalSubmissions") == 2, str(res.get("totalSubmissions")))
stat_interest = next((f for f in (res.get("fields") or []) if f.get("label") == "是否感兴趣"), None)
cnt = {c["option"]: c["count"] for c in (stat_interest or {}).get("counts") or []}
check("感兴趣 1 / 不感兴趣 1", cnt.get("感兴趣") == 1 and cnt.get("不感兴趣") == 1, str(cnt))

# 7) 截止后提交 1039
sql(f"UPDATE form_template SET deadline = now() - interval '1 minute' WHERE activity_id={AID} AND type='survey'")
r = api("POST", f"/clubs/{CLUB}/activities/{AID}/survey/submit", headers=TH,
        body={"answers": [{"fieldId": f_interest, "value": "感兴趣"}]})
check("截止后提交 1039", r.get("code") == 1039, str(r)[:80])

# 8) 关闭进讨论（非发起人 403 → 发起人 200）
r = api("POST", f"/clubs/{CLUB}/activities/{AID}/survey/close", headers=VH)
check("非发起人关闭 403", r.get("code") == 403, str(r)[:80])
r = api("POST", f"/clubs/{CLUB}/activities/{AID}/survey/close", headers=PH)
check("发起人关闭成功", r.get("code") == 200, str(r)[:80])
r = api("GET", f"/clubs/{CLUB}/activities/{AID}", headers=PH)
d = r.get("data") or {}
check("活动状态讨论中", d.get("status") == 3, str(d.get("status")))
tr = d.get("traces") or []
check("trace discuss_start", any(t.get("action") == "discuss_start" for t in tr))
# 关闭后再提交 → 1039（模板已关闭）
r = api("POST", f"/clubs/{CLUB}/activities/{AID}/survey/submit", headers=TH,
        body={"answers": [{"fieldId": f_interest, "value": "感兴趣"}]})
check("模板关闭后提交 1039", r.get("code") == 1039, str(r)[:80])
# 重复发布问卷 → 1037（状态已 3）
r = api("POST", f"/clubs/{CLUB}/activities/{AID}/survey", headers=PH, body=body)
check("重复发布问卷 1037", r.get("code") == 1037, str(r)[:80])

print(f"=== 结果：PASS={PASS} FAIL={FAIL} ===")
exit(1 if FAIL else 0)
