"""T8 generate_skill：起草可复用的 SKILL.md（只生成不落盘，确认后由前端触发 Java 落盘）。

与 generate_draft 同边界：AI 产出必经人确认；工具永不抛异常（K20 教训）。
body 由模板拼装（frontmatter + 正文），不二次调 LLM——framework 已是模型总结。
"""
import json
import logging
import re

from langchain_core.tools import tool

logger = logging.getLogger(__name__)

_MAX_FRAMEWORK = 600
_MAX_DESC = 80
_MAX_WHEN = 40

_SKILL_TEMPLATE = """---
name: {name}
description: {description}
when_to_use: {when_to_use}
---

# {title}

{framework}
"""


def _kebab(s: str) -> str:
    """宽容化：转 kebab-case（小写、空白/下划线转连字符、去非法字符、压缩连续连字符）。"""
    s = re.sub(r"[^a-zA-Z0-9\-_\s]", "", s.lower())
    s = re.sub(r"[\s_]+", "-", s).strip("-")
    return re.sub(r"-{2,}", "-", s)


def _json_out(d: dict) -> str:
    return json.dumps(d, ensure_ascii=False)


@tool
def generate_skill(framework: str, name: str = "", description: str = "", when_to_use: str = "") -> str:
    """起草一份可复用的 SKILL.md（不落盘，确认后由前端触发落盘）。

    当对话中沉淀出可复用的方法论/思考框架（如活动筹备的完整思考流程：强度分级→天气→补给→住宿）时调用。
    参数：framework 方法论内容，分步骤或分维度描述（300 字内，用编号或要点列出）；
    name 简短英文名（kebab-case，如 activity-prep-thinking）；description 一句话说明这个 skill 教 AI 什么（40 字内）；
    when_to_use 触发场景（20 字内）。
    """
    body_fw = framework.strip()[: _MAX_FRAMEWORK]
    desc = description.strip()[: _MAX_DESC]
    when = when_to_use.strip()[: _MAX_WHEN]
    raw_name = name.strip() or "skill"
    nm = _kebab(raw_name)[:50] or "skill"
    title = raw_name.replace("-", " ").replace("_", " ").title()
    body = _SKILL_TEMPLATE.format(name=nm, description=desc, when_to_use=when, title=title, framework=body_fw)
    return _json_out({"name": nm, "description": desc, "when_to_use": when, "body": body})
