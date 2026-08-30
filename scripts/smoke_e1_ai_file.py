# -*- coding: utf-8 -*-
"""E1 冒烟：正式文件撰写 Agent（活动前）——真实 MiMo 对话 + 工具 + 章节草稿 + 落库/权限/回放。

链路：现造讨论中活动（概念批复→问卷→讨论）→ get_activity_context → 对话（触发
generate_file_draft）→ 断言章节 JSON → 会话回放 → 负例。
"""
import json
import subprocess
import sys
import urllib.request

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

BASE = "http://127.0.0.1:8093"
CLUB = "2092285569724362753"
ACT_PUBLISHED = "2093806568215207938"  # 已发布活动（状态 4，负例用）

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
    except Exception as e:
        return {"err": str(e)}


def sql(q):
    r = subprocess.run(
        ["psql", "-U", "postgres", "-h", "127.0.0.1", "-d", "club_agent", "-t", "-A", "-c", q],
        env={**__import__("os").environ, "PGPASSWORD": "root"},
        capture_output=True, text=True, encoding="utf-8", errors="replace",
    )
    return r.stdout.strip() or r.stderr.strip()


def login(username, password="Test123456"):
    cap = api("GET", "/auth/captcha")
    key = cap["data"]["captchaKey"]
    code = subprocess.run(["F:/Redis/redis-cli.exe", "GET", f"club:captcha:{key}"], capture_output=True, text=True).stdout.strip()
    return api("POST", "/auth/login", body={"username": username, "password": password, "captchaKey": key, "captchaCode": code})["data"]["token"]


print("=== E1 正式文件 Agent 冒烟 ===")
ptok = login("stu_7f36dc")   # 社长（发起人）
vtok = login("stu_4512db")   # 副社长
ttok = login("teacher1", "teacher123456")
PH = {"Authorization": f"Bearer {ptok}"}
VH = {"Authorization": f"Bearer {vtok}"}
TH = {"Authorization": f"Bearer {ttok}"}
check("三方登录", True)

# 1) 找/造讨论中活动
acts = (api("GET", f"/clubs/{CLUB}/activities?page=1&size=50", headers=PH).get("data") or {}).get("records") or []
AID = next((a["id"] for a in acts if a.get("status") == 3), None)
if AID is None:
    # 无讨论中活动 → 先作废悬置的进行中概念（阻塞新建），再创建新概念走 批复→活动
    sql(f"UPDATE concept_session SET status=6 WHERE club_id={CLUB} AND status IN (2,3,4)")
    r = api("POST", f"/clubs/{CLUB}/concepts", headers=PH, body={
        "reason": "E1 正式文件 Agent 冒烟活动",
        "plannedTime": "2026-11-15 09:00",
        "plannedLocation": "社团活动室",
        "content": "E1 冒烟：验证正式文件撰写 Agent 的完整链路",
    })
    CID = str((r.get("data") or {}).get("id") or "")
    check("创建新概念", bool(CID), str(r)[:150])
    sql(f"UPDATE concept_session SET status=4, deadline=now()+interval '72 hours' WHERE id={CID}")
    r = api("POST", f"/clubs/{CLUB}/concepts/{CID}/review", headers=TH, body={"approve": True, "comment": "E1 冒烟批复"})
    check("老师批复建活动", r.get("code") == 200, str(r)[:150])
    acts = (api("GET", f"/clubs/{CLUB}/activities?page=1&size=50", headers=PH).get("data") or {}).get("records") or []
    AID = next((a["id"] for a in acts if str(a.get("conceptId")) == CID), None)
    check("批复后活动公示中", AID is not None, f"concept={CID}")
    body = {"deadline": "2026-09-30T20:00:00", "fields": [
        {"label": "你会修车吗？", "fieldType": "radio", "required": 0, "options": ["会", "不会"]},
        {"label": "1 天还是 2 天？", "fieldType": "radio", "required": 1, "options": ["1 天", "2 天"]},
    ]}
    r = api("POST", f"/clubs/{CLUB}/activities/{AID}/survey", headers=PH, body=body)
    check("发布问卷", r.get("code") == 200, str(r)[:100])
    r = api("POST", f"/clubs/{CLUB}/activities/{AID}/survey/close", headers=PH)
    check("关闭进讨论", r.get("code") == 200, str(r)[:100])
else:
    check("复用讨论中活动", True, f"AID={AID}")
check("讨论中活动就绪", AID is not None, "无可用活动")

# 2) 活动上下文数据接口（get_activity_context 工具数据源）
r = api("GET", f"/clubs/{CLUB}/activities/{AID}/ai/context", headers=PH)
d = r.get("data") or {}
check("context 概念批复结果", d.get("concept") is not None and (d.get("concept") or {}).get("plannedTime"), str(d)[:150])
check("context 讨论群列表", isinstance(d.get("discussions"), list), str(d.get("discussions"))[:80])
check("context 问卷统计", d.get("survey") is not None and "totalSubmissions" in (d.get("survey") or {}), str(d.get("survey"))[:120])

# 3) 真实对话（触发 get_activity_context + generate_file_draft）
before = int(sql(f"SELECT count(*) FROM file_draft_session WHERE activity_id={AID}") or 0)
r = api("POST", f"/clubs/{CLUB}/activities/{AID}/ai/chat", headers=PH,
        body={"message": "根据概念批复、讨论群和问卷结果，帮我生成一版正式文件章节草稿（活动安排、后勤保障、分工说明三个章节）"},
        timeout=300)
msgs = r.get("data") or []
check("chat 返回消息列表", r.get("code") == 200 and len(msgs) >= 3, str(r)[:200])
roles = [m.get("role") for m in msgs]
check("user/tool/assistant 全落库", roles.count("user") >= 1 and roles.count("assistant") >= 1 and roles.count("tool") >= 1, str(roles))
tools_used = [m.get("toolName") for m in msgs if m.get("toolName")]
check("调用 get_activity_context", "get_activity_context" in tools_used, str(tools_used))
check("调用 generate_file_draft", "generate_file_draft" in tools_used, str(tools_used))
draft_msg = next((m for m in msgs if m.get("toolName") == "generate_file_draft"), None)
# 章节 JSON 在工具结果的 content（tool_args 是调用参数）；且参数里应带活动上下文
draft_args = draft_msg.get("toolArgs") if draft_msg else None
draft_result = draft_msg.get("content") if draft_msg else None
sections = []
if draft_result:
    try:
        sections = (json.loads(draft_result) or {}).get("sections") or []
    except Exception as e:
        print("  draft 结果解析失败:", e)
check("章节草稿 JSON 可解析且 ≥1 章节", len(sections) >= 1, str(draft_result)[:200])
check("生成参数携带活动上下文", draft_args is not None and "activity_context" in (draft_args or ""), str(draft_args)[:120])
check("章节含 title/content", all(s.get("title") and s.get("content") for s in sections), str(sections)[:150])
check("落库增量 ≥3（user/tool/assistant）", int(sql(f"SELECT count(*) FROM file_draft_session WHERE activity_id={AID}") or 0) - before >= 3, f"before={before}")

# 4) 会话回放
r = api("GET", f"/clubs/{CLUB}/activities/{AID}/ai/session", headers=PH)
msgs = r.get("data") or []
check("会话回放", r.get("code") == 200 and len(msgs) >= 4 and msgs[-1].get("role") == "assistant", str(len(msgs)))

# 5) 负例
r = api("POST", f"/clubs/{CLUB}/activities/{AID}/ai/chat", headers=VH,
        body={"message": "越权尝试"}, timeout=30)
check("非发起人 403", r.get("code") == 403, str(r)[:80])
r = api("POST", f"/clubs/{CLUB}/activities/{ACT_PUBLISHED}/ai/chat", headers=PH,
        body={"message": "已发布活动对话"}, timeout=30)
check("已发布活动 1037", r.get("code") == 1037, str(r)[:80])

print(f"\n=== E1 结果: PASS {PASS} / FAIL {FAIL} ===")
sys.exit(1 if FAIL else 0)
