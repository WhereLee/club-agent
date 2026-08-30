# -*- coding: utf-8 -*-
"""块 A 冒烟：概念通过→活动创建（公示）→ 列表/详情 → 取消链路（403/400/200/1037）→ 全员通知"""
import json
import subprocess
import time
import urllib.request

BASE = "http://127.0.0.1:8093"
CLUB = "2092285569724362753"
CONCEPT = "2093694285317214210"

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
                       capture_output=True, text=True)
    return r.stdout.strip()


print("=== 块 A 冒烟 ===")
# 1) 登录
ptok = login("stu_7f36dc")  # 社长（发起人）
vtok = login("stu_4512db")  # 副社长（非发起人）
ttok = login("teacher1", "teacher123456")  # 老师
PH = {"Authorization": f"Bearer {ptok}"}
VH = {"Authorization": f"Bearer {vtok}"}
TH = {"Authorization": f"Bearer {ttok}"}
check("社长登录", True)

# 2) 概念推进到待老师批复（SQL 直改：跳过投票环节）
st = sql(f"SELECT status FROM concept_session WHERE id='{CONCEPT}'")
print("概念当前状态:", st)
check("概念存在且可推进", st in ("2", "3", "4", "6"), f"st={st}")
if st not in ("4",):
    sql(f"UPDATE concept_session SET status=4, deadline=now()+interval '36 hours' WHERE id='{CONCEPT}'")
    check("概念置为待老师批复", sql(f"SELECT status FROM concept_session WHERE id='{CONCEPT}'") == "4")

# 3) 老师批复通过 → 自动创建活动
r = api("POST", f"/clubs/{CLUB}/concepts/{CONCEPT}/review", headers=TH, body={"approve": True, "comment": "同意，组织好细节"})
check("老师批复通过", r.get("code") == 200, str(r)[:120])

# 4) 活动列表出现 status=1（公示中），conceptId 对应
r = api("GET", f"/clubs/{CLUB}/activities?page=1&size=10", headers=PH)
items = (r.get("data") or {}).get("records") or []
act = next((a for a in items if str(a.get("conceptId")) == CONCEPT), None)
check("活动已创建（公示中）", act is not None and act.get("status") == 1, f"items={len(items)}")
check("活动带发起人昵称", act is not None and bool(act.get("requesterNickname")), str(act)[:150])
check("活动带初稿字段", act is not None and act.get("plannedTime"), str(act)[:150])
AID = act["id"]

# 5) 全员公示通知（社长收到 activity_announce）
r = api("GET", "/messages?page=1&size=10", headers=PH)
msgs = (r.get("data") or {}).get("records") or []
ann = next((m for m in msgs if m.get("type") == "activity_announce"), None)
check("公示通知全员", ann is not None and str(ann.get("refActivityId")) == str(AID), f"msgs={len(msgs)}")

# 6) 详情含 trace(create)
r = api("GET", f"/clubs/{CLUB}/activities/{AID}", headers=PH)
d = r.get("data") or {}
tr = d.get("traces") or []
check("详情时间线含 create", any(t.get("action") == "create" for t in tr), str(tr)[:150])

# 7) 老师可见列表（club:member 角色权限）
r = api("GET", f"/clubs/{CLUB}/activities?page=1&size=10", headers=TH)
check("老师可看活动列表", r.get("code") == 200)

# 8) 取消链路
r = api("POST", f"/clubs/{CLUB}/activities/{AID}/cancel", headers=VH, body={"reason": "x"})
check("非发起人取消 403", r.get("code") == 403, str(r)[:80])
r = api("POST", f"/clubs/{CLUB}/activities/{AID}/cancel", headers=PH, body={"reason": ""})
check("空理由取消 400", r.get("code") == 400, str(r)[:80])
r = api("POST", f"/clubs/{CLUB}/activities/{AID}/cancel", headers=PH, body={"reason": "问卷反馈参与人数不足，取消活动"})
check("发起人取消成功", r.get("code") == 200, str(r)[:120])

# 9) 取消后状态 + trace + 通知
r = api("GET", f"/clubs/{CLUB}/activities/{AID}", headers=PH)
d = r.get("data") or {}
check("活动已取消 status=10", d.get("status") == 10)
check("取消理由落库", "参与人数不足" in (d.get("cancelReason") or ""))
tr = d.get("traces") or []
check("时间线含 cancel", any(t.get("action") == "cancel" for t in tr))
r = api("GET", "/messages?page=1&size=10", headers=PH)
msgs = (r.get("data") or {}).get("records") or []
cancel_msg = next((m for m in msgs if m.get("type") == "activity_cancel"), None)
check("取消通知全员附理由", cancel_msg is not None and "参与人数不足" in (cancel_msg.get("content") or ""))
# 副社长也收到
r = api("GET", "/messages?page=1&size=10", headers=VH)
msgs = (r.get("data") or {}).get("records") or []
check("副社长收到取消通知", any(m.get("type") == "activity_cancel" for m in msgs))

# 10) 已取消再取消 → 1037
r = api("POST", f"/clubs/{CLUB}/activities/{AID}/cancel", headers=PH, body={"reason": "再来一次"})
check("已取消再取消 1037", r.get("code") == 1037, str(r)[:80])

# 11) 不存在活动 1036
r = api("GET", f"/clubs/{CLUB}/activities/9223372036854775807", headers=PH)
check("不存在活动 1036", r.get("code") == 1036, str(r)[:80])

print(f"=== 结果：PASS={PASS} FAIL={FAIL} ===")
exit(1 if FAIL else 0)
