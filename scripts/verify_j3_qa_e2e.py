"""J3 管理层经验问答活验证（含 LLM 全链路）。

链路：社长登录 → 创建问答会话 → 提问（命中 org 空间评估集种子）
→ 断言：AI 回答有据 + 工具留痕（search_knowledge）+ 首问自动命名 → 会话重放 → 软删清理。
前提：rag 已启动（8090）且评估种子在库（verify_club_knowledge_eval.py 已跑过，数据保留）。
运行：cd club-agent/python && python ../scripts/verify_j3_qa_e2e.py
"""
import sys

import httpx
import redis

sys.stdout.reconfigure(encoding="utf-8")

BASE = "http://127.0.0.1:8093"
CLUB_ID = "2092285569724362753"   # 篮球社_6958
PRESIDENT = "stu_7f36dc"
PASS = "Test123456"

passed, failed = 0, 0


def check(name, cond):
    global passed, failed
    if cond:
        passed += 1
        print(f"PASS {name}")
    else:
        failed += 1
        print(f"FAIL {name}")


def login():
    cap = httpx.get(f"{BASE}/auth/captcha", timeout=10).json()["data"]
    r = redis.Redis(host="127.0.0.1", port=6379, decode_responses=True)
    code = r.get(f"club:captcha:{cap['captchaKey']}")
    resp = httpx.post(f"{BASE}/auth/login", timeout=15,
                      json={"username": PRESIDENT, "password": PASS,
                            "captchaKey": cap["captchaKey"], "captchaCode": code}).json()
    assert resp["code"] == 200, resp
    return resp["data"]["token"]


def main():
    token = login()
    h = {"Authorization": f"Bearer {token}"}
    check("社长登录", True)

    # 1) 创建会话
    r = httpx.post(f"{BASE}/clubs/{CLUB_ID}/ai/qa/sessions", headers=h, json={"title": ""}, timeout=15).json()
    check("创建问答会话", r["code"] == 200 and r["data"]["id"])
    sid = r["data"]["id"]
    print(f"  session_id={sid}")

    # 2) 提问（命中评估集种子 eval_budget_rule.md：报销流程）
    q = "社团的报销流程是什么？"
    print(f"  提问：{q}（等待 LLM + 检索，最长 150s）...")
    r = httpx.post(f"{BASE}/clubs/{CLUB_ID}/ai/qa/sessions/{sid}/chat", headers=h,
                   json={"message": q}, timeout=180).json()
    check("问答接口返回", r["code"] == 200)
    msgs = r["data"]
    roles = [m["role"] for m in msgs]
    reply = next((m["content"] for m in reversed(msgs) if m["role"] == "assistant"), "")
    print(f"  消息角色序列：{roles}")
    print(f"  AI 回答（前 200 字）：{reply[:200].replace(chr(10), ' ')}")
    check("三方留痕（user+tool+assistant）", "user" in roles and "tool" in roles and "assistant" in roles)
    tool_msgs = [m for m in msgs if m["role"] == "tool"]
    check("工具为 search_knowledge", any(m.get("toolName") == "search_knowledge" for m in tool_msgs))
    check("工具命中评估种子（来源溯源）", any("eval_budget_rule" in (m.get("content") or "") for m in tool_msgs))
    check("回答包含报销关键信息", ("填单" in reply or "社长审批" in reply or "财务复核" in reply or "报销" in reply))

    # 3) 首问自动命名
    r = httpx.get(f"{BASE}/clubs/{CLUB_ID}/ai/qa/sessions", headers=h, timeout=15).json()
    target = next((s for s in r["data"] if s["id"] == sid), None)
    check("首问自动命名", target is not None and "报销" in (target.get("title") or ""))

    # 4) 会话重放
    r = httpx.get(f"{BASE}/clubs/{CLUB_ID}/ai/qa/sessions/{sid}/messages", headers=h, timeout=15).json()
    check("会话重放条数一致", r["code"] == 200 and len(r["data"]) == len(msgs))

    # 5) 清理：软删会话
    r = httpx.delete(f"{BASE}/clubs/{CLUB_ID}/ai/qa/sessions/{sid}", headers=h, timeout=15).json()
    check("软删会话", r["code"] == 200)
    r = httpx.get(f"{BASE}/clubs/{CLUB_ID}/ai/qa/sessions", headers=h, timeout=15).json()
    check("删除后列表不可见", all(s["id"] != sid for s in r["data"]))

    print(f"\n结果：{passed} passed / {failed} failed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
