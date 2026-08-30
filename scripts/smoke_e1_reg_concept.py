# -*- coding: utf-8 -*-
"""E1 回归：概念起草会话（graph.py 参数化重构后，验证 concept Agent 未破坏）。

SQL 造起草中概念 → 真实对话 → 断言回复 + 落库（concept_draft_session）。
"""
import json
import subprocess
import sys
import urllib.request

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

BASE = "http://127.0.0.1:8093"
CLUB = "2092285569724362753"

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


def api(method, path, headers=None, body=None, timeout=60):
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
        env={**__import__("os").environ, "PGPASSWORD": "root"},
        capture_output=True, text=True, encoding="utf-8", errors="replace",
    ).stdout.strip()


def login(username, password="Test123456"):
    cap = api("GET", "/auth/captcha")
    key = cap["data"]["captchaKey"]
    code = subprocess.run(["F:/Redis/redis-cli.exe", "GET", f"club:captcha:{key}"], capture_output=True, text=True).stdout.strip()
    return api("POST", "/auth/login", body={"username": username, "password": password, "captchaKey": key, "captchaCode": code})["data"]["token"]


print("=== E1 回归：概念起草会话 ===")
tok = login("stu_7f36dc")
H = {"Authorization": f"Bearer {tok}"}

# 1) SQL 造起草中概念（并发概念限制：先作废进行中的）
sql(f"UPDATE concept_session SET status=6 WHERE club_id={CLUB} AND status IN (1,2,3,4)")
r = api("POST", f"/clubs/{CLUB}/concepts", headers=H, body={
    "reason": "E1 回归：概念会话",
    "plannedTime": "2026-12-01 10:00",
    "plannedLocation": "测试地点",
    "content": "回归验证 graph 参数化后的概念 Agent",
})
CID = str((r.get("data") or {}).get("id") or "")
check("创建起草中概念", bool(CID), str(r)[:120])

# 2) 真实对话（概念 Agent：应回复且可用经验检索工具）
before = int(sql(f"SELECT count(*) FROM concept_draft_session WHERE concept_id={CID}") or 0)
r = api("POST", f"/clubs/{CLUB}/concepts/{CID}/ai/chat", headers=H,
        body={"message": "帮我分析一下这个活动想法的可行性和关键考量点"}, timeout=240)
msgs = r.get("data") or []
check("概念 chat 返回消息", r.get("code") == 200 and len(msgs) >= 2, str(r)[:150])
roles = [m.get("role") for m in msgs]
check("user/assistant 落库", "user" in roles and "assistant" in roles, str(roles))
check("最后一条是 assistant 回复", len(msgs) > 0 and msgs[-1].get("role") == "assistant" and bool(msgs[-1].get("content")), str(msgs[-1].get("content"))[:80])
check("落库增量 ≥2", int(sql(f"SELECT count(*) FROM concept_draft_session WHERE concept_id={CID}") or 0) - before >= 2, f"before={before}")

print(f"\n=== 回归结果: PASS {PASS} / FAIL {FAIL} ===")
sys.exit(1 if FAIL else 0)
