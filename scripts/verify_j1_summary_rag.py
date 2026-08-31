"""J1 总结报告入 rag 活验证：真实归档链路。

链路：社长登录 → 归档活动（2093806568215207938，总结已就绪）
→ 异步推 rag → 轮询 activity_summary.rag_file_id 回填 → rag 解析终态 + 文件名断言。
运行：cd club-agent/python && python ../scripts/verify_j1_summary_rag.py
"""
import sys
import time
from pathlib import Path

import httpx
import psycopg
import redis

sys.stdout.reconfigure(encoding="utf-8")

BASE = "http://127.0.0.1:8093"
CLUB_ID = "2092285569724362753"
ACTIVITY_ID = "2093806568215207938"
PRESIDENT = "stu_7f36dc"
PASS = "Test123456"

RAG = "http://127.0.0.1:8090"
RAG_ROOT = Path(__file__).resolve().parents[2] / "rag"
env = {}
for line in (RAG_ROOT / ".env").read_text(encoding="utf-8").splitlines():
    if "=" in line and not line.strip().startswith("#"):
        k, v = line.split("=", 1)
        env[k.strip()] = v.strip()
PG_DSN = env.get("PG_DSN", "postgresql://postgres:root@localhost:5432/rag_kb")
RAG_KEY = {"X-Internal-Key": env["INTERNAL_API_KEY"]}

CLUB_DSN = "postgresql://postgres:root@localhost:5432/club_agent"

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

    # 1) 归档（前置：总结已 success）
    r = httpx.post(f"{BASE}/clubs/{CLUB_ID}/activities/{ACTIVITY_ID}/archive", headers=h, timeout=20).json()
    check("归档成功", r["code"] == 200)

    # 2) 轮询 rag_file_id 回填（@Async 推送，最长 60s）
    rag_file_id = None
    deadline = time.time() + 60
    while time.time() < deadline:
        with psycopg.connect(CLUB_DSN) as conn:
            row = conn.execute("SELECT rag_file_id FROM activity_summary WHERE activity_id=%s",
                               (int(ACTIVITY_ID),)).fetchone()
        if row and row[0]:
            rag_file_id = row[0]
            break
        time.sleep(3)
    check("rag_file_id 已回填", rag_file_id is not None)
    print(f"  rag_file_id={rag_file_id}")
    if rag_file_id is None:
        print(f"\n结果：{passed} passed / {failed} failed")
        sys.exit(1)

    # 3) rag 侧文件存在 + 文件名断言（活动总结-*.md）
    with psycopg.connect(PG_DSN) as conn:
        row = conn.execute("SELECT filename, owner_type, org_id, status FROM user_file WHERE id=%s",
                           (rag_file_id,)).fetchone()
    check("rag 侧文件记录存在", row is not None)
    if row:
        print(f"  filename={row[0]} owner_type={row[1]} org_id={row[2]} status={row[3]}")
        check("归属 org 空间（社团）", row[1] == "org" and str(row[2]) == CLUB_ID)
        check("文件名为总结报告", row[0].startswith("活动总结-") and row[0].endswith(".md"))

    # 4) 等解析终态（最长 120s）
    final = None
    deadline = time.time() + 120
    while time.time() < deadline:
        st = httpx.get(f"{RAG}/api/org/files/{rag_file_id}/status",
                       headers=RAG_KEY, params={"org_id": CLUB_ID}, timeout=15).json()
        if st["status"] in ("success", "partial", "failed"):
            final = st["status"]
            break
        time.sleep(3)
    check(f"解析终态（实际：{final}）", final in ("success", "partial"))

    # 5) 检索可用性：总结报告内容可被检索命中
    q = "这次活动的总结报告讲了什么"
    r = httpx.post(f"{RAG}/api/org/retrieve", headers=RAG_KEY,
                   json={"query": q, "org_id": int(CLUB_ID), "top_k": 8}, timeout=60).json()
    hit = any(it["filename"].startswith("活动总结-") for it in r["items"])
    check("总结报告可被检索命中", hit)

    print(f"\n结果：{passed} passed / {failed} failed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
