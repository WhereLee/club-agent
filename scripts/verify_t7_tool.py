"""任务7 验证：search_experience 双源检索链路（工具直调，不经 LLM）。

链路：登录 → 上传种子资料 → 等解析 → search_experience.invoke → 断言三要素（水位/经验/文件）→ 清理。
运行：cd club-agent/python && python ../scripts/verify_t7_tool.py
"""
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "python" / "src"))

import httpx
import redis

BASE = "http://127.0.0.1:8093"
CLUB_ID = "2092285569724362753"
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
    body = {"username": PRESIDENT, "password": PASS,
            "captchaKey": cap["captchaKey"], "captchaCode": code}
    resp = httpx.post(f"{BASE}/auth/login", json=body, timeout=15).json()
    assert resp["code"] == 200, resp
    return resp["data"]["token"]


def main():
    token = login()
    check("社长登录", bool(token))
    headers = {"Authorization": f"Bearer {token}"}

    # 上传种子资料（rag org 空间）
    seed = Path(__file__).with_name("_t7_seed.md")
    seed.write_text(
        "# 社团破冰活动组织指南\n\n"
        "## 破冰游戏设计\n"
        "破冰活动按10人一组进行，每组配备一名老成员带动；游戏时长控制在20分钟以内，"
        "避免冷场。物资预算人均15元。\n", encoding="utf-8")
    with open(seed, "rb") as f:
        up = httpx.post(f"{BASE}/clubs/{CLUB_ID}/file-lib/upload", headers=headers,
                        files={"file": ("_t7_seed.md", f, "text/markdown")}, timeout=30).json()
    check("种子资料上传", up["code"] == 200)
    lib_id = up["data"]["id"]

    # 等解析完成（列表懒同步）
    ok = False
    for _ in range(20):
        time.sleep(3)
        rows = httpx.get(f"{BASE}/clubs/{CLUB_ID}/file-lib", headers=headers, timeout=15).json()["data"]
        row = next((r for r in rows if r["id"] == lib_id), None)
        if row and row["ragStatus"] in ("success", "partial"):
            ok = True
            break
        if row and row["ragStatus"] == "failed":
            break
    check("种子资料解析完成", ok)

    # 工具直调（双源链路：Java /ai/knowledge → SQL + rag）
    from agent_draft.tools.java_client import set_request_context
    from agent_draft.tools.experience import search_experience
    set_request_context(auth_header=f"Bearer {token}", club_id=CLUB_ID)
    out = search_experience.invoke({"query": "破冰活动的分组和预算怎么安排"})
    print("---- 工具输出 ----")
    print(out)
    print("------------------")
    check("输出含数据水位", "【数据水位】" in out)
    check("输出含历史经验命中", "【历史经验命中】" in out)
    check("输出含文件资料命中", "【文件资料命中】" in out)
    check("文件命中带来源溯源", "_t7_seed.md" in out)

    # 清理
    d = httpx.delete(f"{BASE}/clubs/{CLUB_ID}/file-lib/{lib_id}", headers=headers, timeout=15).json()
    check("清理种子资料", d["code"] == 200)
    seed.unlink(missing_ok=True)

    print(f"=== 结果：PASS={passed} FAIL={failed} ===")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
