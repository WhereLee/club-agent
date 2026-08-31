"""经验检索评估（双项目集成 · 阶段2 J2：评估集固化版）。

评估集固化在 club-agent/eval/（seeds/*.md + questions.json），脚本只负责
入库与评估，数据默认保留在 rag org 空间（回归可重复执行，幂等：
已存在的种子文件跳过入库，只补入库缺失项）。

流程：核对 org 空间内已有种子 → 补入库缺失项 → 等待解析 → 15 题检索评估
（Recall@8 / MRR，目标 Recall@8 ≥ 0.7）。
--cleanup：评估后物理清理全部评估数据（回归场景不需要时用）。

运行：cd club-agent/python && python ../scripts/verify_club_knowledge_eval.py [--cleanup]
"""
import json
import sys
import time
from pathlib import Path

import httpx
import psycopg

# ===== 固化评估集（J2：数据在 eval/ 目录，脚本不再内嵌语料） =====
EVAL_DIR = Path(__file__).resolve().parents[1] / "eval"
SEED_DIR = EVAL_DIR / "seeds"
QUESTION_FILE = EVAL_DIR / "questions.json"

# rag 连接（清理用；密钥从 rag/.env 读取；目录重整后 rag 项目在工作区根/rag/ 下）
RAG_ROOT = Path(__file__).resolve().parents[2] / "rag"
env = {}
for line in (RAG_ROOT / ".env").read_text(encoding="utf-8").splitlines():
    if "=" in line and not line.strip().startswith("#"):
        k, v = line.split("=", 1)
        env[k.strip()] = v.strip()
PG_DSN = env.get("PG_DSN", "postgresql://postgres:root@localhost:5432/rag_kb")
INTERNAL_KEY = env["INTERNAL_API_KEY"]

RAG = "http://127.0.0.1:8090"
CLUB_ID = 2092285569724362753  # 篮球社_6958（测试社团，org 空间）
HDR = {"X-Internal-Key": INTERNAL_KEY}
TARGET_RECALL = 0.7


def load_questions() -> list[tuple[str, str]]:
    data = json.loads(QUESTION_FILE.read_text(encoding="utf-8"))
    return [(d["query"], d["expected"]) for d in data]


def existing_seed_ids() -> dict[str, int]:
    """org 空间内已存在且有效的种子文件：{filename: file_id}（幂等入库的依据）。"""
    with psycopg.connect(PG_DSN) as conn:
        rows = conn.execute(
            "SELECT id, filename FROM user_file "
            "WHERE org_id=%s AND owner_type='org' AND status=1 AND filename LIKE %s",
            (CLUB_ID, "eval\\_%")).fetchall()
    return {fn: fid for fid, fn in rows}


def ingest_missing(existing: dict[str, int]) -> dict[str, int]:
    """仅入库缺失的种子文件；返回 {filename: rag_file_id}。"""
    out = {}
    for p in sorted(SEED_DIR.glob("*.md")):
        if p.name in existing:
            continue
        with open(p, "rb") as f:
            r = httpx.post(f"{RAG}/api/org/ingest", headers=HDR,
                           files={"file": (p.name, f, "text/markdown")},
                           data={"org_id": CLUB_ID, "biz_type": "eval_seed"}, timeout=30)
        assert r.status_code == 200, f"ingest {p.name} failed: {r.text}"
        out[p.name] = r.json()["file_id"]
    return out


def wait_parsed(file_ids: dict[str, int]) -> None:
    """轮询解析状态直至全部终态（最长 120s）。无新入库直接返回。"""
    if not file_ids:
        return
    deadline = time.time() + 120
    pending = set(file_ids.values())
    while pending and time.time() < deadline:
        time.sleep(3)
        for fid in list(pending):
            r = httpx.get(f"{RAG}/api/org/files/{fid}/status",
                          headers=HDR, params={"org_id": CLUB_ID}, timeout=15).json()
            if r["status"] in ("success", "partial", "failed"):
                pending.discard(fid)
                if r["status"] == "failed":
                    print(f"WARN 种子解析失败: file_id={fid} err={r.get('error')}")
    assert not pending, f"解析超时：{pending}"


def evaluate(questions: list[tuple[str, str]]) -> tuple[float, float, list]:
    hits, detail = 0, []
    mrr_sum = 0.0
    for q, expected in questions:
        r = httpx.post(f"{RAG}/api/org/retrieve", headers=HDR,
                       json={"query": q, "org_id": CLUB_ID, "top_k": 8}, timeout=60).json()
        names = [it["filename"] for it in r["items"]]
        rank = next((i + 1 for i, n in enumerate(names) if n == expected), None)
        if rank:
            hits += 1
            mrr_sum += 1.0 / rank
        detail.append((q, expected, rank, names[:3]))
    recall = hits / len(questions)
    mrr = mrr_sum / len(questions)
    return recall, mrr, detail


def cleanup() -> None:
    """物理清理全部评估数据（--cleanup 时用；默认保留以支持重复回归）。"""
    with psycopg.connect(PG_DSN) as conn:
        rows = conn.execute(
            "SELECT id, blob_id FROM user_file "
            "WHERE org_id=%s AND owner_type='org' AND filename LIKE %s",
            (CLUB_ID, "eval\\_%")).fetchall()
        ids = [r[0] for r in rows]
        blobs = [r[1] for r in rows if r[1]]
        if ids:
            conn.execute("DELETE FROM parse_tasks WHERE file_id = ANY(%s)", (ids,))
            conn.execute("DELETE FROM user_file WHERE id = ANY(%s)", (ids,))
        if blobs:
            conn.execute("DELETE FROM file_blob WHERE id = ANY(%s)", (blobs,))
        conn.commit()
    for fid in [r[0] for r in rows]:
        (RAG_ROOT / "data" / "parsed" / f"{fid}.json").unlink(missing_ok=True)
    print(f"评估数据已物理清理（{len(ids)} 份）")


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    keep = "--cleanup" not in sys.argv
    print("=== 经验检索评估（固化评估集版） ===")
    questions = load_questions()
    existing = existing_seed_ids()
    new_ids = ingest_missing(existing)
    if new_ids:
        print(f"新入库种子：{len(new_ids)} 份（已存在 {len(existing)} 份），等待解析...")
        wait_parsed(new_ids)
    else:
        print(f"种子已全部在库（{len(existing)} 份，幂等跳过入库），直接评估")
    print(f"开始评估 {len(questions)} 题...")
    recall, mrr, detail = evaluate(questions)
    for q, exp, rank, top3 in detail:
        mark = f"hit@{rank}" if rank else "MISS"
        print(f"  [{mark:6}] {q} → {exp}  (top: {[n.replace('eval_', '') for n in top3]})")
    print(f"\nRecall@8 = {recall:.4f}  (目标 >= {TARGET_RECALL})")
    print(f"MRR      = {mrr:.4f}")
    if keep:
        print("评估数据保留在 org 空间（重复运行幂等；清理用 --cleanup）")
    else:
        cleanup()
    if recall < TARGET_RECALL:
        print("FAIL：检索质量未达标")
        sys.exit(1)
    print("PASS：检索质量达标")


if __name__ == "__main__":
    main()
