# -*- coding: utf-8 -*-
"""阶段 0 冒烟：状态机扩展 + 讨论结束链路（endDiscussion/质量快照/文件解锁/新状态推进）"""
import json
import subprocess
import sys
import urllib.request

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

BASE = "http://127.0.0.1:8093"
CLUB = "2092285569724362753"
ACT_DISCUSS = "2093889438208274435"   # 状态 3 讨论中（未关闭）
ACT_PUBLISHED = "2093806568215207938"  # 状态 4 已发布

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


print("=== 阶段 0 冒烟 ===")
ptok = login("stu_7f36dc")
vtok = login("stu_4512db")
PH = {"Authorization": f"Bearer {ptok}"}
VH = {"Authorization": f"Bearer {vtok}"}
check("登录", True)

# ---- 0) 数据准备：重置活动 A 讨论状态 + 插入测试消息（含低质量短回复）----
# 清历史快照与关闭标记，插入 4 条消息（社长 3 条含 1 条低质量、副社长 1 条高质量）
sql(f"DELETE FROM activity_discussion_summary WHERE activity_id={ACT_DISCUSS}")
sql(f"UPDATE activity SET discussion_closed_at=NULL WHERE id={ACT_DISCUSS}")
uid_p = sql(f"SELECT user_id FROM activity WHERE id={ACT_DISCUSS}")          # 发起人
uid_v = sql(f"SELECT id FROM sys_user WHERE username='stu_4512db'")
sql(f"DELETE FROM chat_message WHERE activity_id={ACT_DISCUSS}")
sql(f"UPDATE activity SET status=4, signup_deadline=NULL, record_deadline=NULL WHERE id={ACT_PUBLISHED}")
# 用 INSERT 模拟讨论消息（Windows 下 psql 命令行传中文会损坏编码 → 写临时 SQL 文件执行）
_sql_lines = []
for i, (content, sender, low) in enumerate([
    ("建议活动安排分两天进行，第一天集合拉练，第二天自由交流", uid_p, False),
    ("好的", uid_p, True),
    ("后勤保障方面建议提前准备补给点和维修工具，路线难度中等", uid_p, False),
    ("分工我建议社长统筹全局，副社长负责路线与安全，我来负责后勤", uid_v, False),
]):
    mid = 9000000000000000000 + i
    wc = len(content.replace(" ", ""))
    _sql_lines.append(
        f"INSERT INTO chat_message (id, activity_id, sender_id, sender_name, content, word_count, is_low_quality) "
        f"VALUES ({mid}, {ACT_DISCUSS}, {sender}, '测试', E'{content.replace(chr(39), chr(39)*2)}', {wc}, {str(low).upper()});"
    )
import os as _os
_tmp_sql = _os.path.join(_os.environ.get("TEMP", "."), "_stage0_msg.sql")
with open(_tmp_sql, "w", encoding="utf-8") as _f:
    _f.write("\n".join(_sql_lines))
subprocess.run(["psql", "-U", "postgres", "-h", "127.0.0.1", "-d", "club_agent", "-f", _tmp_sql],
               env={**_os.environ, "PGPASSWORD": "root"}, capture_output=True, text=True, encoding="utf-8", errors="replace")
check("测试消息已插入", int(sql(f"SELECT count(*) FROM chat_message WHERE activity_id={ACT_DISCUSS}") or 0) >= 4)

# ---- 1) 讨论关闭前：文件/AI 起草被锁 ----
r = api("POST", f"/clubs/{CLUB}/activities/{ACT_DISCUSS}/file/save", headers=PH,
        body={"sections": [{"title": "活动安排", "content": "测试"}]})
check("关闭前保存文件 1037", r.get("code") == 1037, str(r)[:80])
r = api("POST", f"/clubs/{CLUB}/activities/{ACT_DISCUSS}/ai/chat", headers=PH,
        body={"message": "测试"}, timeout=30)
check("关闭前 AI 起草 1037", r.get("code") == 1037, str(r)[:80])

# ---- 2) 结束讨论 ----
r = api("POST", f"/clubs/{CLUB}/activities/{ACT_DISCUSS}/discussion/end", headers=PH)
check("结束讨论 200", r.get("code") == 200, str(r)[:80])
r = api("POST", f"/clubs/{CLUB}/activities/{ACT_DISCUSS}/discussion/end", headers=PH)
check("重复结束讨论 1037（幂等）", r.get("code") == 1037, str(r)[:80])
r = api("POST", f"/clubs/{CLUB}/activities/{ACT_DISCUSS}/discussion/end", headers=VH)
check("非发起人结束讨论 403", r.get("code") == 403, str(r)[:80])

# ---- 3) 快照与 trace ----
r = api("GET", f"/clubs/{CLUB}/activities/{ACT_DISCUSS}", headers=PH)
d = r.get("data") or {}
check("discussionClosedAt 已置位", d.get("discussionClosedAt") is not None, str(d.get("discussionClosedAt")))
check("trace end_discussion", any(t.get("action") == "end_discussion" for t in (d.get("traces") or [])), str([t.get("action") for t in (d.get("traces") or [])]))
cnt = int(sql(f"SELECT count(*) FROM activity_discussion_summary WHERE activity_id={ACT_DISCUSS}") or 0)
check("讨论快照已生成（2 成员）", cnt >= 2, f"rows={cnt}")
hf = int(sql(f"SELECT count(*) FROM activity_discussion_summary WHERE activity_id={ACT_DISCUSS} AND is_high_freq") or 0)
check("高频标记生效（社长 3 条）", hf == 1, f"hf={hf}")
qc = int(sql(f"SELECT COALESCE(SUM(quality_count),0) FROM activity_discussion_summary WHERE activity_id={ACT_DISCUSS}") or 0)
check("高质量计数=3（低质量被剔除）", qc == 3, f"qc={qc}")

# ---- 4) context 数据接口：高质量过滤 + stats ----
r = api("GET", f"/clubs/{CLUB}/activities/{ACT_DISCUSS}/ai/context", headers=PH)
d = r.get("data") or {}
discs = d.get("discussions") or []
check("context 仅高质量消息", all(len((x.get("content") or "").replace(" ", "")) >= 10 for x in discs), f"n={len(discs)}")
stats = d.get("discussionStats") or {}
check("stats 总消息数=4", (stats.get("totalMessages") or 0) == 4, str(stats))
check("stats 高质量=3", (stats.get("qualityMessages") or 0) == 3, str(stats))
check("stats 高频 1 人", len(stats.get("highFreqMembers") or []) == 1, str(stats.get("highFreqMembers"))[:100])

# ---- 5) 关闭后文件可写 ----
r = api("POST", f"/clubs/{CLUB}/activities/{ACT_DISCUSS}/file/save", headers=PH,
        body={"sections": [{"title": "活动安排", "content": "阶段0验证草稿"}]})
check("关闭后保存文件 200", r.get("code") == 200, str(r)[:80])

# ---- 6) 状态推进链（活动 B：4 → 5 → 6 → 7 → 8）----
r = api("POST", f"/clubs/{CLUB}/activities/{ACT_PUBLISHED}/signup/start", headers=PH, body={})
check("开始报名无截止 400", r.get("code") == 400, str(r)[:80])
r = api("POST", f"/clubs/{CLUB}/activities/{ACT_PUBLISHED}/signup/start", headers=PH,
        body={"deadline": "2026-09-10T20:00:00"})
check("开始报名 200", r.get("code") == 200, str(r)[:80])
r = api("GET", f"/clubs/{CLUB}/activities/{ACT_PUBLISHED}", headers=PH)
check("状态=5 报名中", (r.get("data") or {}).get("status") == 5, str((r.get("data") or {}).get("status")))
check("signupDeadline 已存", (r.get("data") or {}).get("signupDeadline") is not None)
r = api("POST", f"/clubs/{CLUB}/activities/{ACT_PUBLISHED}/execution/start", headers=PH)
check("开始执行 200", r.get("code") == 200, str(r)[:80])
r = api("GET", f"/clubs/{CLUB}/activities/{ACT_PUBLISHED}", headers=PH)
check("状态=6 执行中", (r.get("data") or {}).get("status") == 6)
r = api("POST", f"/clubs/{CLUB}/activities/{ACT_PUBLISHED}/execution/complete", headers=PH)
check("结束执行 200", r.get("code") == 200, str(r)[:80])
r = api("GET", f"/clubs/{CLUB}/activities/{ACT_PUBLISHED}", headers=PH)
check("状态=7 留痕中", (r.get("data") or {}).get("status") == 7)
r = api("POST", f"/clubs/{CLUB}/activities/{ACT_PUBLISHED}/records/close", headers=PH)
check("关闭留痕 200", r.get("code") == 200, str(r)[:80])
r = api("GET", f"/clubs/{CLUB}/activities/{ACT_PUBLISHED}", headers=PH)
check("状态=8 总结（预留）", (r.get("data") or {}).get("status") == 8)

# ---- 7) 通知 ----
r = api("GET", "/messages?page=1&size=20", headers=VH)
msgs = (r.get("data") or {}).get("records") or []
check("开始报名通知全员", any(m.get("type") == "activity_signup_open" for m in msgs), str([m.get("type") for m in msgs[:6]]))
check("留痕开放通知全员", any(m.get("type") == "activity_record_open" for m in msgs), str([m.get("type") for m in msgs[:6]]))

print(f"\n=== 阶段 0 结果: PASS {PASS} / FAIL {FAIL} ===")
sys.exit(1 if FAIL else 0)
