"""T4 get_draft + T5 generate_draft：表单读取与方案生成（生成不落库，经人确认后落表）。"""
import json
import logging

from langchain_core.tools import tool
from pydantic import BaseModel, Field

from .java_client import get_concept_id, get_club_id, java_get

logger = logging.getLogger(__name__)

_FORMAT_PROMPT = (
    "你是企划表单起草器。根据发起人的要求生成一版活动企划草案。\n"
    "只输出一个 JSON 对象，不要任何多余文字，字段：\n"
    '{"reason": "发起理由（80字内）", "planned_time": "预计时间（自然语言，如 2026-09-05 凌晨出发，共 2 天）", '
    '"planned_location": "预计地点（含距离/交通说明）", "content": "活动简述（150字内，含安排要点）", '
    '"decision_note": "决策说明（100字内，一句话说明这版草案的关键取舍，供人确认时参考）"}'
)


class DraftOutput(BaseModel):
    """generate_draft 的结构化输出（字段名与前端表单对齐）。"""

    reason: str = Field(..., max_length=80)
    planned_time: str = Field(..., max_length=100)
    planned_location: str = Field(..., max_length=200)
    content: str = Field(..., max_length=150)
    decision_note: str = Field(..., max_length=100)


@tool
def get_draft() -> str:
    """读取当前概念的企划表单内容（发起理由/预计时间/预计地点/活动简述）。
    当需要同步用户手动修改后的表单、或生成前需要了解表单现状时调用。"""
    try:
        body = java_get(f"/clubs/{get_club_id()}/concepts/{get_concept_id()}")
    except Exception as e:
        return f"表单读取失败（后端暂不可用）：{e}"
    data = body.get("data") or {}
    status = data.get("status")
    status_text = "起草中" if status == 1 else f"status={status}"
    return (
        f"当前表单内容：\n"
        f"- 发起理由：{data.get('reason') or '（空）'}\n"
        f"- 预计时间：{data.get('plannedTime') or '（空）'}\n"
        f"- 预计地点：{data.get('plannedLocation') or '（空）'}\n"
        f"- 活动简述：{data.get('content') or '（空）'}\n"
        f"- 状态：{status_text}"
    )


_FIELD_LIMITS = {"reason": 80, "planned_time": 100, "planned_location": 200, "content": 150, "decision_note": 100}


def _coerce(draft: dict) -> dict:
    """宽容化：超长字段截断到限长（提示词已约束，截断仅兑底，避免小毛病作废整份草案）。"""
    out = {}
    for k, v in draft.items():
        if k in _FIELD_LIMITS and isinstance(v, str) and len(v) > _FIELD_LIMITS[k]:
            v = v[: _FIELD_LIMITS[k]]
        out[k] = v
    return out


@tool
def generate_draft(requirements: str, current_form: str = "") -> str:
    """生成或修订一版活动企划草案（仅生成不落库，产物经发起人确认后由前端写入表单）。
    当发起人说"生成一版/帮我填表/改成XX"时调用。
    参数：requirements 发起人的要求与约束（尽量完整复述对话中的关键信息）；
    current_form 现有表单内容（有则传 get_draft 的结果，用于增量修订）。"""
    # 延迟导入：graph 与 tools 互相引用（graph → tools → draft → build_llm）
    from ..graph import build_llm

    llm = build_llm()
    prompt = _FORMAT_PROMPT + f"\n\n发起人要求：{requirements}"
    if current_form:
        prompt += f"\n\n现有表单（增量修订，保留合理部分）：\n{current_form}"
    last_err = None
    for attempt in range(2):
        try:
            raw = llm.invoke(prompt).content
            draft = _extract_json(raw)
            validated = DraftOutput(**_coerce(draft))
            return json.dumps(validated.model_dump(), ensure_ascii=False)
        except Exception as e:
            last_err = e
            logger.warning("generate_draft 第 %s 次校验失败：%s", attempt + 1, e)
    # 绝不抛异常：工具错误若抛给 LangGraph，checkpoint 会存下缺 ToolMessage 的坏历史，
    # 后续轮次续聊发给 LLM 会 400（tool_calls 必须配对）。返回错误文本让模型自行修正。
    return f"草案生成失败（模型输出不符合格式）：{last_err}。请重新调用生成，或向用户说明。"


def _extract_json(raw: str) -> dict:
    """从 LLM 输出提取 JSON 对象（容忍 ```json 围栏/前后杂文）。"""
    text = raw.strip()
    if text.startswith("```"):
        text = text.strip("`")
        if text.startswith("json"):
            text = text[4:]
    start, end = text.find("{"), text.rfind("}")
    if start < 0 or end < 0:
        raise ValueError("输出中未找到 JSON 对象")
    return json.loads(text[start : end + 1])
