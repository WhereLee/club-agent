"""端到端验收（双项目集成任务10）：全链路真实闭环（含 LLM）。

链路：登录 → 上传活动资料 → 等解析 → 概念起草对话（LLM 自主调 search_experience）
→ 断言工具调用发生 + 命中种子资料 + AI 回复引用历史经验 → 清理资料。
运行：cd club-agent/python && python ../scripts/e2e_integration.py
"""
import sys
import time
from pathlib import Path

import httpx
import redis

sys.stdout.reconfigure(encoding="utf-8")  # 防 GBK 控制台无法输出 emoji/特殊字符（K29）

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
    headers = {"Authorization": f"Bearer {token}"}
    check("社长登录", True)

    # 1) 起草中概念（发起人会话载体）
    lst = httpx.get(f"{BASE}/clubs/{CLUB_ID}/concepts", headers=headers,
                    params={"page": 1, "size": 5, "status": 1}, timeout=15).json()
    records = lst["data"]["records"]
    check("起草中概念存在", bool(records))
    if not records:
        sys.exit(1)
    concept_id = records[0]["id"]

    # 2) 上传种子资料（历史经验载体）
    seed = Path(__file__).with_name("_e2e_seed.md")
    seed.write_text(
        "# 篮球社三人制篮球赛办赛经验\n\n"
        "## 场地与赛程\n"
        "三人制篮球赛租用校体育馆副馆即可，半天赛程安排8支队伍循环赛；"
        "裁判由体育部学生裁判担任，需提前一周预约。\n\n"
        "## 预算要点\n"
        "场地费约300元半天，奖品预算每队不超过50元，饮用水按人均2瓶准备。\n",
        encoding="utf-8")
    with open(seed, "rb") as f:
        up = httpx.post(f"{BASE}/clubs/{CLUB_ID}/file-lib/upload", headers=headers,
                        files={"file": ("_e2e_seed.md", f, "text/markdown")},
                        params={"activityId": ""}, timeout=30).json()
    check("种子资料上传", up["code"] == 200)
    lib_id = up["data"]["id"]

    # 3) 等解析完成（列表懒同步）
    ok = False
    for _ in range(20):
        time.sleep(3)
        rows = httpx.get(f"{BASE}/clubs/{CLUB_ID}/file-lib", headers=headers, timeout=15).json()["data"]
        row = next((r for r in rows if r["id"] == lib_id), None)
        if row and row["ragStatus"] in ("success", "partial"):
            ok = True
            break
    check("种子资料解析完成", ok)

    # 4) 概念起草对话：LLM 应自主调用 search_experience（双源：经验条目 + 活动资料）
    msg = "我想策划一场三人制篮球赛，先帮我查查社团有没有办过类似活动的历史经验和资料可以参考"
    chat = httpx.post(f"{BASE}/clubs/{CLUB_ID}/concepts/{concept_id}/ai/chat",
                      headers=headers, json={"message": msg}, timeout=300).json()
    check("起草对话成功", chat["code"] == 200)
    msgs = chat["data"]

    # 5) 断言：工具调用发生 + 命中种子资料 + 回复引用历史经验
    tool_msgs = [m for m in msgs if m.get("role") == "tool" and m.get("toolName") == "search_experience"]
    check("Agent 调用了 search_experience", bool(tool_msgs))
    tool_text = " ".join(str(m.get("content") or "") for m in tool_msgs)
    check("工具输出含文件资料命中", "【文件资料命中】" in tool_text)
    check("命中种子资料（来源溯源）", "_e2e_seed.md" in tool_text)
    ai_msgs = [m for m in msgs if m.get("role") == "assistant"]
    ai_text = str(ai_msgs[-1].get("content") or "") if ai_msgs else ""
    print("---- AI 回复（节选） ----")
    print(ai_text[:400])
    print("------------------------")
    check("AI 回复引用了历史经验", ("篮球" in ai_text or "经验" in ai_text or "参考" in ai_text))

    # 6) 清理种子资料
    d = httpx.delete(f"{BASE}/clubs/{CLUB_ID}/file-lib/{lib_id}", headers=headers, timeout=15).json()
    check("清理种子资料", d["code"] == 200)
    seed.unlink(missing_ok=True)

    print(f"=== 结果：PASS={passed} FAIL={failed} ===")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
