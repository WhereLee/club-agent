"""T1 search_experience：检索系统内历史经验（B1 增强：数据水位 + 质量信号 + 低水位联网兜底）。

边界（K20 铁律）：工具永不抛异常；联网兜底是工具内部的独立 LLM 请求（MiMo 原生 web_search），
原始搜索结果不进主 Agent 上下文——主 Agent 只看到整理后的精炼结论。
"""
import json
import logging
import urllib.request

from langchain_core.tools import tool

from .. import config
from .java_client import get_club_id, java_get

logger = logging.getLogger(__name__)

_LOW_WATER = config.WEB_SEARCH_LOW_WATER


@tool
def search_experience(query: str) -> str:
    """检索系统内的历史活动经验（总结结论/筹备知识/风险教训），返回数据水位与命中结果。

    当发起人的想法涉及以往办过类似活动、需要参考历史经验时调用。
    返回内容含：数据水位（本社团经验总数——水位高说明经验可依赖，水位低说明应结合通用知识）、
    命中条目（分类/标题/内容）；水位低或未命中时附带 [在线检索补充]（工具内部联网整理结果）。
    若命中内容明显不覆盖问题的关键维度，可改写问句后重新调用（最多 2 次）。"""
    # 1) 经验库检索（Java 返回 items + similarActivityCount 数据水位）
    try:
        body = java_get(f"/clubs/{get_club_id()}/ai/experience", params={"q": query})
    except Exception as e:
        return f"经验检索失败（后端暂不可用）：{e}"
    data = body.get("data") or {}
    items = data.get("items") or []
    water = int(data.get("similarActivityCount") or 0)

    parts = [f"【数据水位】本社团现有经验 {water} 条（{'经验较丰富，优先参考' if water >= _LOW_WATER else '经验有限，需结合通用知识'}）。"]
    if items:
        parts.append("【历史经验命中】")
        parts.extend(f"- [{it.get('category')}] {it.get('title')}：{it.get('content')}" for it in items)
    else:
        parts.append("【历史经验命中】无相关条目。")

    # 2) 低水位或未命中 → 工具内部联网兜底（独立请求，主 Agent 只看到整理结论）
    if config.WEB_SEARCH_ENABLED and (water < _LOW_WATER or not items):
        online = _web_search(query)
        parts.append(f"【在线检索补充】{online}" if online else "【在线检索补充】联网暂不可用，可基于通用知识分析。")
    return "\n".join(parts)


def _web_search(query: str) -> str:
    """MiMo 原生 web_search：独立 LLM 请求（tools=[web_search]）整理精炼结论；失败返回空串（绝不抛异常）。"""
    try:
        payload = {
            "model": config.WEB_SEARCH_MODEL,
            "messages": [
                {"role": "system", "content": "你是社团活动筹备助手。基于联网搜索结果，整理与该问题直接相关的要点（2-4 条，每条一行，共 200 字内）；若搜索结果与问题无关，明确说'未找到相关内容'。"},
                {"role": "user", "content": query},
            ],
            "tools": [{"type": "web_search"}],
            "max_completion_tokens": 800,
            "stream": False,
        }
        req = urllib.request.Request(
            f"{config.LLM_BASE_URL}/chat/completions",
            data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
            headers={"Content-Type": "application/json", "Authorization": f"Bearer {config.LLM_API_KEY}"},
            method="POST",
        )
        with urllib.request.urlopen(req, timeout=config.LLM_TIMEOUT_SECONDS) as r:
            resp = json.loads(r.read().decode("utf-8"))
        return (resp["choices"][0]["message"].get("content") or "").strip()
    except Exception as e:
        logger.warning("web_search 兜底失败：%s", e)
        return ""
