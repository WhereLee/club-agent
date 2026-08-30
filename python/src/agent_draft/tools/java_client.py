"""工具回 Java 的 HTTP 客户端：身份透传 + 会话上下文注入。

请求身份/上下文来源：main.py /chat 端点从请求头取 Authorization、从请求体取
club_id/concept_id，存入 contextvars；工具函数读取后随请求转发，形成
"前端 → Java → Python → Java"闭环（权限判断永远在 Java）。
"""
import logging
import os
from contextvars import ContextVar

import httpx

logger = logging.getLogger(__name__)

JAVA_BASE_URL = os.getenv("JAVA_BASE_URL", "http://127.0.0.1:8093")

# 当前请求上下文（chat 端点注入；无身份时工具调用按未登录处理）
_auth_header: ContextVar[str | None] = ContextVar("auth_header", default=None)
_club_id: ContextVar[str] = ContextVar("club_id", default="")
_concept_id: ContextVar[str] = ContextVar("concept_id", default="")
_activity_id: ContextVar[str] = ContextVar("activity_id", default="")


def set_request_context(auth_header: str | None, club_id: str, concept_id: str = "", activity_id: str = "") -> None:
    _auth_header.set(auth_header)
    _club_id.set(club_id)
    _concept_id.set(concept_id)
    _activity_id.set(activity_id)


def get_club_id() -> str:
    return _club_id.get()


def get_concept_id() -> str:
    return _concept_id.get()


def get_activity_id() -> str:
    return _activity_id.get()


def _headers() -> dict:
    h = {"Content-Type": "application/json"}
    token = _auth_header.get()
    if token:
        h["Authorization"] = token
    return h


def java_get(path: str, params: dict | None = None, timeout: float = 10.0) -> dict:
    """GET Java 接口，返回 R 结构（{code, message, data}）。HTTP/业务错误抛 RuntimeError。"""
    try:
        resp = httpx.get(JAVA_BASE_URL + path, params=params, headers=_headers(), timeout=timeout)
    except Exception as e:
        raise RuntimeError(f"Java 服务不可达：{JAVA_BASE_URL}{path}（{e}）")
    body = resp.json()
    if resp.status_code != 200 or body.get("code") != 200:
        raise RuntimeError(f"Java 返回错误 code={body.get('code')} msg={body.get('message')}（{path}）")
    return body
