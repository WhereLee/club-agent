"""T-QA search_knowledge：双源知识检索（Java /ai/knowledge：经验条目 + rag 文件块）。

与概念起草的 search_experience 同源接口，区别：问答场景不做联网兜底
（问答求"有据"，无命中如实告知；联网内容不属于社团经验库）。
边界（K20 铁律）：工具永不抛异常，失败返回可读提示。
"""
import logging

from langchain_core.tools import tool

from .java_client import get_club_id, java_get

logger = logging.getLogger(__name__)


@tool
def search_knowledge(query: str) -> str:
    """检索社团知识库：历史经验条目 + 活动资料/总结报告文件（双源），返回命中内容与来源。

    当需要回答关于本社团历史活动、经验做法、资料内容的问题时调用。
    返回：数据水位、历史经验命中（分类/标题/内容）、文件资料命中（来源文件名/章节/页码 + 内容片段）。
    若命中内容不覆盖问题的关键维度，可改写问句后重新调用（最多 2 次）。"""
    try:
        body = java_get(f"/clubs/{get_club_id()}/ai/knowledge", params={"q": query, "topK": 8})
    except Exception as e:
        return f"知识检索失败（后端暂不可用）：{e}"
    data = body.get("data") or {}
    sql_items = data.get("sqlItems") or []
    file_items = data.get("fileItems") or []
    water = data.get("similarActivityCount") or 0

    parts = [f"【数据水位】本社团经验库共 {water} 条沉淀"]
    if sql_items:
        parts.append("【历史经验命中】")
        for it in sql_items:
            parts.append(f"- [{it.get('category') or ''}] {it.get('title') or ''}：{it.get('content') or ''}")
    if file_items:
        parts.append("【文件资料命中】")
        for fi in file_items:
            parts.append(_format_file_item(fi))
    if not sql_items and not file_items:
        parts.append("【未命中】知识库中暂无与该问题相关的记录")
    return "\n".join(parts)


def _format_file_item(fi: dict) -> str:
    """文件块 → 单行文本（来源溯源：文件名 > 章节路径 [第X页]：内容截断）。"""
    src = fi.get("filename") or "未知文件"
    heading = fi.get("headingPath") or ""
    page = fi.get("pageNo")
    loc = f" > {heading}" if heading else ""
    if page:
        loc += f" [第{page}页]"
    content = (fi.get("content") or "").strip().replace("\n", " ")
    if len(content) > 300:
        content = content[:300] + "…"
    return f"- 来源《{src}》{loc}：{content}"


QA_TOOLS = [search_knowledge]
