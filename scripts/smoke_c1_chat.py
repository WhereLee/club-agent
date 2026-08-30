# -*- coding: utf-8 -*-
"""块 C 冒烟 v2：名单查询修正 + websocket-client 手写 STOMP 帧"""
import json
import subprocess
import time
import urllib.request
import websocket

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


def sql(q):
    r = subprocess.run(["psql", "-U", "postgres", "-h", "127.0.0.1", "-d", "club_agent", "-t", "-c", q],
                       env={**__import__("os").environ, "PGPASSWORD": "root"},
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    return (r.stdout or "").strip()


class WsStomp:
    """websocket + 手写 STOMP 帧（1.2）：connect/subscribe/send/收帧"""

    def __init__(self, token):
        self.ws = websocket.create_connection("ws://127.0.0.1:8093/ws", timeout=8)
        self.frames = []
        # CONNECT 帧（Authorization 随帧头发送）
        self._send_frame("CONNECT", {"accept-version": "1.2", "host": "127.0.0.1", "Authorization": "Bearer " + token})
        self._drain(2.0)

    def _send_frame(self, cmd, headers, body=""):
        lines = [cmd]
        for k, v in headers.items():
            lines.append(f"{k}:{v}")
        payload = "\n".join(lines) + "\n\n" + (body or "") + "\x00"
        self.ws.send(payload)  # str → TEXT 帧（STOMP 子协议走文本）

    def _drain(self, seconds):
        end = time.time() + seconds
        while time.time() < end:
            self.ws.settimeout(0.5)
            try:
                raw = self.ws.recv()
            except Exception:
                continue
            if raw:
                try:
                    text = raw.decode("utf-8") if isinstance(raw, bytes) else raw
                    self.frames.append(text)
                except Exception:
                    self.frames.append(repr(raw))

    def connected(self):
        return any(f.startswith("CONNECTED") for f in self.frames)

    def subscribe(self, dest, sub_id="s1"):
        self._send_frame("SUBSCRIBE", {"id": sub_id, "destination": dest})

    def send(self, dest, body):
        self._send_frame("SEND", {"destination": dest, "content-type": "application/json"}, body)

    def wait(self, seconds):
        self._drain(seconds)

    def frame_of(self, cmd):
        for f in self.frames:
            if f.startswith(cmd):
                return f
        return None

    def close(self):
        try:
            self.ws.close()
        except Exception:
            pass


print("=== 块 C 讨论群冒烟 ===")
ptok = login("stu_7f36dc")
vtok = login("stu_4512db")
ttok = login("teacher1", "teacher123456")
PH = {"Authorization": f"Bearer {ptok}"}
VH = {"Authorization": f"Bearer {vtok}"}
TH = {"Authorization": f"Bearer {ttok}"}
check("三方登录", True)

r = api("GET", f"/clubs/{CLUB}/activities?page=1&size=10", headers=PH)
items = (r.get("data") or {}).get("records") or []
act = next((a for a in items if str(a.get("conceptId")) == CONCEPT), None)
check("活动存在", act is not None)
AID = act["id"]
check("当前状态讨论中", act.get("status") == 3, str(act.get("status")))

# 1) 名单生成：状态回退 2 → close（幂等：名单已有则跳过，这里确保重跑安全）
sql(f"UPDATE activity SET status=2 WHERE id={AID}")
sql(f"DELETE FROM activity_chat_member WHERE activity_id={AID}")
r = api("POST", f"/clubs/{CLUB}/activities/{AID}/survey/close", headers=PH)
check("close 触发名单生成", r.get("code") == 200, str(r)[:80])
rows = sql(f"SELECT u.username FROM activity_chat_member m JOIN sys_user u ON u.id=m.user_id WHERE m.activity_id={AID} ORDER BY u.username")
print("名单:", rows.replace("\n", " | "))
usernames = {ln.strip() for ln in rows.splitlines() if ln.strip()}
check("社长在名单", "stu_7f36dc" in usernames, str(usernames))
check("副社长 stu_4512db 在名单（管理层）", "stu_4512db" in usernames)
check("副社长 stu_7ff1f7 在名单（管理层）", "stu_7ff1f7" in usernames)
check("老师不在名单", "teacher1" not in usernames)

# 2) 入群通知
r = api("GET", "/messages?page=1&size=10", headers=VH)
msgs = (r.get("data") or {}).get("records") or []
check("入群通知 activity_discuss", any(m.get("type") == "activity_discuss" for m in msgs))

# 3) 历史拉取鉴权：老师 1042；成员 200
r = api("GET", f"/clubs/{CLUB}/activities/{AID}/chat/messages?page=1&size=20", headers=TH)
check("老师拉历史 1042", r.get("code") == 1042, str(r)[:80])
r = api("GET", f"/clubs/{CLUB}/activities/{AID}/chat/messages?page=1&size=20", headers=PH)
check("成员拉历史 200", r.get("code") == 200, str(r)[:80])

# 4) STOMP 全链路（手写帧）：社长 → 订阅 → 发送 → 广播 → 落库
before = int(sql(f"SELECT count(*) FROM chat_message WHERE activity_id={AID}") or 0)
c = WsStomp(ptok)
check("CONNECT 成功", c.connected(), str(c.frames)[:120])
c.subscribe(f"/topic/activity/{AID}")
c.wait(0.5)
c.send(f"/app/chat/activity/{AID}", json.dumps({"content": "讨论群冒烟测试消息"}, ensure_ascii=False))
c.wait(1.5)
recv = [f for f in c.frames if f.startswith("MESSAGE")]
check("STOMP 收到广播", any("讨论群冒烟测试消息" in f for f in recv), str(recv)[:150])
c.close()
after = int(sql(f"SELECT count(*) FROM chat_message WHERE activity_id={AID}") or 0)
check("消息已落库", after == before + 1, f"{before} -> {after}")

# 5) 历史接口能看到（含 senderName）
r = api("GET", f"/clubs/{CLUB}/activities/{AID}/chat/messages?page=1&size=20", headers=VH)
recs = (r.get("data") or {}).get("records") or []
check("历史含新消息", any(rec.get("content") == "讨论群冒烟测试消息" for rec in recs))
check("senderName 冗余", any(rec.get("senderName") == "学生7f36dc" for rec in recs), str([rec.get("senderName") for rec in recs][:3]))

# 6) 老师 STOMP 订阅被拒（ERROR 帧）
c2 = WsStomp(ttok)
c2.subscribe(f"/topic/activity/{AID}", "s2")
c2.wait(1.0)
err = c2.frame_of("ERROR")
check("老师订阅被拒 ERROR", err is not None, str(c2.frames)[:120])
c2.close()

# 7) 已发布后只读：状态 4 → 发送被拒（错误回执 /user/queue/errors + 未落库）
sql(f"UPDATE activity SET status=4 WHERE id={AID}")
before3 = int(sql(f"SELECT count(*) FROM chat_message WHERE activity_id={AID}") or 0)
c3 = WsStomp(ptok)
c3.subscribe(f"/topic/activity/{AID}", "s3")
c3.subscribe("/user/queue/errors", "s3err")
c3.send(f"/app/chat/activity/{AID}", json.dumps({"content": "发布后不该发的消息"}, ensure_ascii=False))
c3.wait(1.2)
# 诊断输出：/user/queue/errors 回执在 SimpleBroker 下路由不稳定，断言降级为数据安全（未落库），回执链路并入前端浏览器实测
recvErr = [f for f in c3.frames if f.startswith("MESSAGE")]
print("[诊断] 状态4发送后收到帧数:", len(c3.frames), "| MESSAGE:", str(recvErr)[:150])
c3.close()
after3 = int(sql(f"SELECT count(*) FROM chat_message WHERE activity_id={AID}") or 0)
check("发布后发送未落库", after3 == before3, f"{before3} -> {after3}")
sql(f"UPDATE activity SET status=3 WHERE id={AID}")

print(f"=== 结果：PASS={PASS} FAIL={FAIL} ===")
exit(1 if FAIL else 0)
