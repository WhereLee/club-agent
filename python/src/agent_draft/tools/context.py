"""T3 get_club_context：社团上下文（简介/管理层/往届概念）。"""
from langchain_core.tools import tool

from .java_client import get_club_id, java_get


@tool
def get_club_context() -> str:
    """获取当前社团上下文：社团名称/简介、当前管理层名单、往届已通过活动列表。
    当需要了解发起人所处的社团背景（有哪些管理层、办过什么活动）时调用。"""
    try:
        body = java_get(f"/clubs/{get_club_id()}/ai/context")
    except Exception as e:
        return f"社团上下文获取失败（后端暂不可用）：{e}"
    return _format_context(body.get("data") or {})


def _format_context(data: dict) -> str:
    managers = "; ".join(f"{m.get('nickname')}({m.get('roleCode')})" for m in data.get("managers") or []) or "无"
    past = data.get("pastConcepts") or []
    past_lines = [f"- {p.get('plannedTime')} {p.get('plannedLocation')}：{p.get('content')}" for p in past]
    return (
        f"社团：{data.get('clubName')}\n"
        f"简介：{data.get('description') or '无'}\n"
        f"管理层：{managers}\n"
        f"往届已通过活动（{len(past_lines)} 条）：\n" + ("\n".join(past_lines) if past_lines else "无")
    )
