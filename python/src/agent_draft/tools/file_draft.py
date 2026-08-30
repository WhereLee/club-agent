"""T10 generate_file_draft：正式活动文件章节起草（仅生成不落库，经人确认后由前端写入）。

复用 T5 generate_draft 的模式：工具内部独立 LLM 请求 + Pydantic 结构化校验 +
重试 2 次 + 绝不抛异常（K20 铁律 + checkpoint 坏历史坑：工具异常会让
tool_calls 缺配对 ToolMessage，续聊时 LLM 请求 400）。
"""
import json
import logging

from langchain_core.tools import tool
from pydantic import BaseModel, Field

logger = logging.getLogger(__name__)

_FORMAT_PROMPT = (
    "你是正式活动文件的章节起草器。根据发起人的要求与活动上下文，生成一版正式文件章节。\n"
    "只输出一个 JSON 对象，不要任何多余文字，字段：\n"
    '{"sections": [{"title": "章节标题（如：活动安排/后勤保障/分工说明，2-20字）", '
    '"content": "章节内容（具体、可执行，200字内）"}], '
    '"decision_note": "决策说明（100字内，一句话说明这版章节的关键取舍，供人确认时参考）"}'
)


class FileDraftOutput(BaseModel):
    """generate_file_draft 的结构化输出（sections 与前端文件编辑器对齐）。"""

    sections: list["FileDraftOutput.Section"] = Field(..., min_length=1)
    decision_note: str = Field(..., max_length=100)

    class Section(BaseModel):
        title: str = Field(..., min_length=2, max_length=20)
        content: str = Field(..., max_length=2000)


@tool
def generate_file_draft(requirements: str, activity_context: str = "") -> str:
    """生成或修订一版正式活动文件的章节草稿（仅生成不落库，产物经发起人确认后由前端写入）。
    当发起人说"生成一版/帮我起草/整理成正式文件"时调用。
    参数：requirements 发起人的要求与约束（尽量完整复述对话中的关键信息，如章节范围/重点）；
    activity_context 活动上下文（先调用 get_activity_context 拿到结果后传入，用于综合概念/讨论/问卷）。"""
    # 延迟导入：graph 与 tools 互相引用（graph → tools → file_draft → build_llm）
    from ..graph import build_llm

    llm = build_llm()
    prompt = _FORMAT_PROMPT + f"\n\n发起人要求：{requirements}"
    if activity_context:
        prompt += f"\n\n活动上下文：\n{activity_context}"
    last_err = None
    for attempt in range(2):
        try:
            raw = llm.invoke(prompt).content
            draft = _extract_json(raw)
            validated = FileDraftOutput(**draft)
            return json.dumps(validated.model_dump(), ensure_ascii=False)
        except Exception as e:
            last_err = e
            logger.warning("generate_file_draft 第 %s 次校验失败：%s", attempt + 1, e)
    return f"章节草稿生成失败（模型输出不符合格式）：{last_err}。请重新调用生成，或向用户说明。"


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
