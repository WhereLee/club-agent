"""T7 经验/思考角度起草：只生成草稿 JSON，不落库（人确认后由前端调 Java 写入）。

与 generate_draft 同边界：AI 产出必经人确认；工具永不抛异常（K20 教训）。
"""
import json
import logging

from langchain_core.tools import tool

logger = logging.getLogger(__name__)

_MAX_INSIGHT = 200
_MAX_TITLE = 100


def _json_out(d: dict) -> str:
    return json.dumps(d, ensure_ascii=False)


@tool
def extract_experience(insight: str, category: str = "筹备知识", title: str = "") -> str:
    """起草一条可沉淀的经验（不落库，发起人确认后由前端写入经验库）。

    当对话中出现对后续类似活动有价值的知识/经验/教训时调用（如筹备细节、风险规避、资源经验）。
    参数：insight 知识内容（50-200 字，说清场景与做法）；category 类别（筹备知识/总结教训/context）；
    title 简短标题（20 字内，可空则自动概括）。
    """
    content = insight.strip()[: _MAX_INSIGHT]
    t = title.strip()[: _MAX_TITLE] or (content[:20] + "…" if len(content) > 20 else content)
    cats = ("筹备知识", "总结教训", "context")
    if category not in cats:
        category = "筹备知识"
    return _json_out({"category": category, "title": t, "content": content})


@tool
def extract_thinking_pattern(patterns: str) -> str:
    """起草发起人的思考角度清单（不落库，确认后由前端写入，归属当前发起人）。

    当发起人在对话中表现出稳定的思考习惯（如先定强度再看时间、关注成员体验、风险优先）时调用。
    参数：patterns 思考角度描述，每条一行（1-5 条，每条 50 字内）。
    """
    lines = [ln.strip() for ln in patterns.strip().splitlines() if ln.strip()][:5]
    for i, ln in enumerate(lines):
        if len(ln) > 50:
            lines[i] = ln[:50]
    if not lines:
        lines = ["（空）"]
    return _json_out({"content": "\n".join(lines)})
