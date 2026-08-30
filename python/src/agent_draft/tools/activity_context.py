"""T9 get_activity_context：活动前置上下文（概念批复结果 + 讨论群消息 + 问卷统计）。

正式文件撰写 Agent 的核心工具：数据经 Java 业务接口获取（权限在 Java 侧），
本工具只做格式化输出，供 LLM 综合三源撰写章节草稿。
"""
from langchain_core.tools import tool

from .java_client import get_activity_id, get_club_id, java_get


@tool
def get_activity_context() -> str:
    """获取当前活动的前置上下文：概念批复结果（时间/地点/内容/发起人简析）、
    讨论群消息（最近若干条）、问卷统计（感兴趣/不感兴趣人数、自定义题答案汇总）。
    撰写正式文件前必须先调用本工具了解活动全貌。"""
    try:
        body = java_get(f"/clubs/{get_club_id()}/activities/{get_activity_id()}/ai/context")
    except Exception as e:
        return f"活动上下文获取失败（后端暂不可用）：{e}"
    return _format_context(body.get("data") or {})


def _format_context(data: dict) -> str:
    concept = data.get("concept") or {}
    lines = [
        "【概念批复结果】",
        f"- 时间：{concept.get('plannedTime') or '（空）'}",
        f"- 地点：{concept.get('plannedLocation') or '（空）'}",
        f"- 内容：{concept.get('content') or '（空）'}",
    ]
    brief = concept.get("aiBrief")
    if brief:
        lines.append(f"- 发起人简析：{brief}")
    discussions = data.get("discussions") or []
    lines.append(f"【讨论群消息（最近 {len(discussions)} 条·仅高质量）】")
    if discussions:
        for d in discussions:
            lines.append(f"- {d.get('senderName') or '未知'}: {d.get('content')}")
    else:
        lines.append("（暂无高质量讨论消息）")
    stats = data.get("discussionStats") or {}
    if stats:
        lines.append("【讨论质量统计】")
        lines.append(f"- 总消息 {stats.get('totalMessages') or 0} 条 / 高质量 {stats.get('qualityMessages') or 0} 条")
        high = stats.get("highFreqMembers") or []
        if high:
            names = ", ".join(f"{h.get('nickname')}({h.get('msgCount')}条)" for h in high)
            lines.append(f"- 高频讨论者：{names}")
    survey = data.get("survey") or {}
    lines.append("【问卷统计】")
    lines.append(f"- 已提交 {survey.get('totalSubmissions') or 0} 人：感兴趣 {survey.get('interested') or 0} 人 / 不感兴趣 {survey.get('notInterested') or 0} 人")
    for item in survey.get("customStats") or []:
        lines.append(f"- {item.get('label')}：{item.get('summary') or '（空）'}")
    return "\n".join(lines)
