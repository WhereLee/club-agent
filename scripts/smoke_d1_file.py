# -*- coding: utf-8 -*-
"""块 D 冒烟：正式文件 + 分工（草稿/权限/发布/通知/查看）"""
import json
import subprocess
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


print("=== 块 D 正式文件冒烟 ===")
ptok = login("stu_7f36dc")
vtok = login("stu_4512db")
ttok = login("teacher1", "teacher123456")
PH = {"Authorization": f"Bearer {ptok}"}
VH = {"Authorization": f"Bearer {vtok}"}
TH = {"Authorization": f"Bearer {ttok}"}

r = api("GET", f"/clubs/{CLUB}/activities?page=1&size=10", headers=PH)
act = next((a for a in (r.get("data") or {}).get("records") or [] if str(a.get("conceptId")) == CONCEPT), None)
check("活动存在且讨论中", act is not None and act.get("status") == 3, str(act))
AID = act["id"]

# 1) 非发起人保存草稿 403
dto = {"sections": [{"title": "活动安排", "content": "10 月 15 日 9 点东湖绿道集合"}, {"title": "预算", "content": "人均 50 元"}]}
r = api("POST", f"/clubs/{CLUB}/activities/{AID}/file/save", headers=VH, body=dto)
check("非发起人保存草稿 403", r.get("code") == 403, str(r)[:80])

# 2) 发起人保存草稿
r = api("POST", f"/clubs/{CLUB}/activities/{AID}/file/save", headers=PH, body=dto)
check("保存草稿", r.get("code") == 200, str(r)[:80])

# 3) 草稿可见性：老师 403（非管理层）；发起人 200
r = api("GET", f"/clubs/{CLUB}/activities/{AID}/file", headers=TH)
check("老师看草稿 403", r.get("code") == 403, str(r)[:80])
r = api("GET", f"/clubs/{CLUB}/activities/{AID}/file", headers=PH)
d = r.get("data") or {}
check("发起人看草稿", r.get("code") == 200 and len(d.get("sections") or []) == 2, str(r)[:100])

# 4) 发布校验：sections 空 → 400
r = api("POST", f"/clubs/{CLUB}/activities/{AID}/file/publish", headers=PH,
        body={"sections": [], "duties": [{"description": "路线安全", "memberIds": [2092285567115505666]}]})
check("空章节发布 400", r.get("code") == 400, str(r)[:80])
# duties 空 → 400
r = api("POST", f"/clubs/{CLUB}/activities/{AID}/file/publish", headers=PH, body={"sections": dto["sections"], "duties": []})
check("空分工发布 400", r.get("code") == 400, str(r)[:80])

# 5) 发布（章节 2 + 分工 2 项）
publish = {"sections": dto["sections"], "duties": [
    {"description": "负责路线与骑行安全", "memberIds": [2092285567115505666]},
    {"description": "突发情况联络人", "memberIds": [2092285567522353154]},
]}
r = api("POST", f"/clubs/{CLUB}/activities/{AID}/file/publish", headers=PH, body=publish)
check("发布成功", r.get("code") == 200, str(r)[:100])

# 6) 状态 4 + trace
r = api("GET", f"/clubs/{CLUB}/activities/{AID}", headers=PH)
d = r.get("data") or {}
check("活动已发布", d.get("status") == 4, str(d.get("status")))
check("trace file_publish", any(t.get("action") == "file_publish" for t in (d.get("traces") or [])))

# 7) 通知：全员 activity_file + 指派 activity_duty
r = api("GET", "/messages?page=1&size=20", headers=VH)
msgs = (r.get("data") or {}).get("records") or []
check("全员收正式文件通知", any(m.get("type") == "activity_file" for m in msgs))
check("被指派收分工通知", any(m.get("type") == "activity_duty" and "突发情况联络人" in (m.get("content") or "") for m in msgs))

# 8) 发布后全员可见（副社长 + 老师）+ memberNames 展示
r = api("GET", f"/clubs/{CLUB}/activities/{AID}/file", headers=VH)
d = r.get("data") or {}
check("副社长看发布文件", r.get("code") == 200 and len(d.get("sections") or []) == 2, str(r)[:100])
duties = d.get("duties") or []
check("分工 2 项", len(duties) == 2, str(len(duties)))
check("memberNames 展示", any("学生4512db" in (dv.get("memberNames") or "") for dv in duties), str([dv.get("memberNames") for dv in duties]))
r = api("GET", f"/clubs/{CLUB}/activities/{AID}/file", headers=TH)
check("老师看发布文件", r.get("code") == 200, str(r)[:80])

# 9) 发布后：保存 1037 + 重复发布 1037
r = api("POST", f"/clubs/{CLUB}/activities/{AID}/file/save", headers=PH, body=dto)
check("发布后保存 1037", r.get("code") == 1037, str(r)[:80])
r = api("POST", f"/clubs/{CLUB}/activities/{AID}/file/publish", headers=PH, body=publish)
check("重复发布 1037", r.get("code") == 1037, str(r)[:80])

print(f"=== 结果：PASS={PASS} FAIL={FAIL} ===")
exit(1 if FAIL else 0)
