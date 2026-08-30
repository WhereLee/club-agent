# -*- coding: utf-8 -*-
"""块 H 冒烟：建议提炼（Java AI）+ 采纳 + 留痕预评/打分 + 奖励统计"""
import json
import os
import subprocess
import sys
import urllib.request

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

BASE = "http://127.0.0.1:8093"
CLUB = "2092285569724362753"
ACT_A = "2093889438208274435"   # 讨论已关闭（4 条消息：3 高质量 + 1 低质量）
ACT_B = "2093806568215207938"   # 留痕中（块 G 冒烟后：VH 已提交留痕 + 签到）

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


def api(method, path, headers=None, body=None, timeout=120):
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
        env={**os.environ, "PGPASSWORD": "root"},
        capture_output=True, text=True, encoding="utf-8", errors="replace",
    ).stdout.strip()


def login(username, password="Test123456"):
    cap = api("GET", "/auth/captcha")
    key = cap["data"]["captchaKey"]
    code = subprocess.run(["F:/Redis/redis-cli.exe", "GET", f"club:captcha:{key}"], capture_output=True, text=True).stdout.strip()
    return api("POST", "/auth/login", body={"username": username, "password": password, "captchaKey": key, "captchaCode": code})["data"]["token"]


print("=== 块 H 冒烟：奖励 + Java AI ===")
ptok = login("stu_7f36dc")
vtok = login("stu_4512db")
PH = {"Authorization": f"Bearer {ptok}"}
VH = {"Authorization": f"Bearer {vtok}"}
check("登录", True)

uid_p = sql(f"SELECT user_id FROM activity WHERE id={ACT_A}")
uid_v = sql(f"SELECT id FROM sys_user WHERE username='stu_4512db'")

# ---- 0) 数据准备 ----
sql(f"DELETE FROM activity_suggestion WHERE activity_id IN ({ACT_A},{ACT_B})")
sql(f"DELETE FROM activity_record_score WHERE activity_id IN ({ACT_A},{ACT_B})")

# ---- 1) 讨论未关闭活动不可提炼 ----
r = api("POST", f"/clubs/{CLUB}/activities/{ACT_B}/suggestions/extract", headers=PH)
check("未关闭讨论提炼 1037", r.get("code") == 1037, str(r)[:80])

# ---- 2) AI 提炼（活动 A：讨论已关闭 + 3 条高质量消息）----
r = api("POST", f"/clubs/{CLUB}/activities/{ACT_A}/suggestions/extract", headers=PH)
d = r.get("data") or []
check("提炼接口 200", r.get("code") == 200, str(r)[:120])
n = int(sql(f"SELECT count(*) FROM activity_suggestion WHERE activity_id={ACT_A}") or 0)
check("建议已落库（>=1 条）", n >= 1, f"n={n}")
sids = sql(f"SELECT string_agg(DISTINCT sender_id::text, ',') FROM activity_suggestion WHERE activity_id={ACT_A}")
senders = set(sids.split(",")) if sids else set()
check("建议人映射合法（社长/副社长）", senders.issubset({uid_p, uid_v}), f"senders={senders}")
if d:
    check("返回含摘要", d[0].get("summary") and len(d[0]["summary"]) > 0, str(d[0])[:120])
# 幂等：再次提炼不重复调用
r = api("POST", f"/clubs/{CLUB}/activities/{ACT_A}/suggestions/extract", headers=PH)
n2 = int(sql(f"SELECT count(*) FROM activity_suggestion WHERE activity_id={ACT_A}") or 0)
check("重复提炼幂等", n2 == n, f"n={n} n2={n2}")

# ---- 3) 采纳建议 ----
r = api("GET", f"/clubs/{CLUB}/activities/{ACT_A}/suggestions", headers=PH)
d = r.get("data") or []
check("建议列表非空", len(d) >= 1, f"n={len(d)}")
if d:
    sid = d[0]["id"]
    r = api("POST", f"/clubs/{CLUB}/activities/{ACT_A}/suggestions/{sid}/adopt", headers=PH)
    check("采纳建议 200", r.get("code") == 200, str(r)[:80])
    r = api("POST", f"/clubs/{CLUB}/activities/{ACT_A}/suggestions/{sid}/adopt", headers=PH)
    check("重复采纳 1048", r.get("code") == 1048, str(r)[:80])
    r = api("POST", f"/clubs/{CLUB}/activities/{ACT_A}/suggestions/999999999999/adopt", headers=PH)
    check("不存在建议 1051", r.get("code") == 1051, str(r)[:80])
    adopted_cnt = int(sql(f"SELECT count(*) FROM activity_suggestion WHERE activity_id={ACT_A} AND adopted") or 0)
    check("落库 adopted=true", adopted_cnt == 1, f"cnt={adopted_cnt}")

# ---- 4) 留痕 AI 预评（活动 B：VH 已提交留痕 + 已签到）----
r = api("POST", f"/clubs/{CLUB}/activities/{ACT_B}/record-scores/preview?userId={uid_v}", headers=PH)
d = r.get("data") or {}
check("预评接口 200", r.get("code") == 200, str(r)[:120])
check("aiScore 在 0-100", d.get("aiScore") is not None and 0 <= d["aiScore"] <= 100, str(d)[:150])
check("aiReason 非空", d.get("aiReason") and len(d["aiReason"]) > 0, str(d)[:150])
check("checkedIn=true", d.get("checkedIn") is True, str(d)[:120])
# 未提交留痕者预评 → 1052（活动 B 发起人未提交留痕）
r = api("POST", f"/clubs/{CLUB}/activities/{ACT_B}/record-scores/preview?userId={uid_p}", headers=PH)
check("未提交留痕预评 1052", r.get("code") == 1052, str(r)[:80])

# ---- 5) 留痕打分 ----
r = api("POST", f"/clubs/{CLUB}/activities/{ACT_B}/record-scores", headers=PH,
        body={"userId": uid_v, "score": 85})
check("留痕打分 200", r.get("code") == 200, str(r)[:80])
r = api("POST", f"/clubs/{CLUB}/activities/{ACT_B}/record-scores", headers=PH,
        body={"userId": uid_v, "score": 90})
check("重复打分 1049", r.get("code") == 1049, str(r)[:80])
r = api("GET", f"/clubs/{CLUB}/activities/{ACT_B}/record-scores", headers=PH)
d = r.get("data") or []
check("打分列表 1 条", len(d) == 1, f"n={len(d)}")
check("最终分=85", d and d[0].get("score") == 85, str(d)[:150])

# ---- 6) 奖励统计（活动 A：频率 + 建议采纳 + 等级）----
r = api("GET", f"/clubs/{CLUB}/activities/{ACT_A}/rewards", headers=PH)
d = r.get("data") or []
check("奖励 3 人", len(d) == 3, f"n={len(d)}")
m_p = next((x for x in d if x["userId"] == uid_p), None)
check("社长高频 freqScore=5", m_p is not None and m_p.get("freqScore") == 5, str(m_p)[:150])
check("采纳者 suggestionScore=10", any(x.get("suggestionScore", 0) > 0 for x in d), str([(x["nickname"], x["suggestionScore"]) for x in d]))
check("总分=分数和", all(x.get("totalScore") == x.get("freqScore", 0) + x.get("suggestionScore", 0) + x.get("recordScore", 0) for x in d), str(d)[:200])
check("等级非空", all(x.get("levelName") for x in d), str([x.get("levelName") for x in d]))
check("按总分降序", all(d[i].get("totalScore") >= d[i + 1].get("totalScore") for i in range(len(d) - 1)), str([x.get("totalScore") for x in d]))

# ---- 7) 奖励统计（活动 B：留痕分 + 等级）----
r = api("GET", f"/clubs/{CLUB}/activities/{ACT_B}/rewards", headers=PH)
d = r.get("data") or []
m_v = next((x for x in d if x["userId"] == uid_v), None)
check("留痕分=85 计入总分", m_v is not None and m_v.get("recordScore") == 85 and m_v.get("totalScore") == 85, str(m_v)[:150])

print(f"\n=== 块 H 结果: PASS {PASS} / FAIL {FAIL} ===")
sys.exit(1 if FAIL else 0)
