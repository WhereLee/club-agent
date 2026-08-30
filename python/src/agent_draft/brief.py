"""D3 想法简析：提交后由 Java 异步调用（重放完整会话 + 表单 → 生成"发起人思路"）。

Java 侧拉数据（业务事实源在 Java），本模块纯生成、无状态、无鉴权。
失败宽容化：重试 2 次仍失败抛异常（Java 侧捕获降级，提交不受影响）。
"""
import logging

from .graph import build_llm
from .tools.draft import _extract_json

logger = logging.getLogger(__name__)

_PROMPT = (
    "你是社团活动企划的\"发起人想法简析器\"。根据发起人与 AI 起草助手的完整对话记录和最终企划表单，"
    "生成一份\"发起人思路\"简析，供投票管理层和指导老师快速理解发起人的想法。\n"
    "只输出一个 JSON 对象，不要任何多余文字，字段：\n"
    '{"brief": "300字内的 Markdown 文本，分四段，每段以小标题开头：'
    '**活动亮点**（1-2 句）/ **风险考量**（1-2 句）/ **参考过的经验**（有则列，无则写\\"未见明显参考\\"）/ '
    '**关键权衡**（1-2 句，说明发起人做过哪些取舍）"}'
)


def generate_brief(messages: list[dict], form: dict) -> str:
    """生成简析：messages = [{role, tool_name, content}]，form = 表单四字段。"""
    # 会话转文本：user/assistant 保留正文，tool 只记工具名（避免噪音）
    turns = []
    for m in messages:
        role = m.get("role", "")
        content = (m.get("content") or "").strip()
        if role == "tool":
            turns.append(f"[工具调用：{m.get('tool_name') or '未知'}]")
        elif content:
            label = "发起人" if role == "user" else "AI"
            turns.append(f"{label}：{content}")
    chat_text = "\n".join(turns) if turns else "（无对话记录）"
    form_text = (
        f"发起理由：{form.get('reason') or '（空）'}\n"
        f"预计时间：{form.get('plannedTime') or '（空）'}\n"
        f"预计地点：{form.get('plannedLocation') or '（空）'}\n"
        f"活动简述：{form.get('content') or '（空）'}"
    )
    prompt = _PROMPT + f"\n\n【最终企划表单】\n{form_text}\n\n【对话记录】\n{chat_text}"

    llm = build_llm()
    last_err = None
    for attempt in range(2):
        try:
            raw = llm.invoke(prompt).content
            brief = _extract_json(raw).get("brief", "")
            if brief.strip():
                return brief.strip()[:500]
            raise ValueError("brief 为空")
        except Exception as e:
            last_err = e
            logger.warning("generate_brief 第 %s 次失败：%s", attempt + 1, e)
    raise RuntimeError(f"想法简析生成失败：{last_err}")
