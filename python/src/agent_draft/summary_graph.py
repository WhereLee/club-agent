"""活动总结 Agent（活动后阶段 I3）：主图 SummaryGraph + 子图 ReviewSubgraph。

主图：ingest → review(子图) → output
子图：audit → decide(LLM 条件边) → clarify(中断等发起人回答) | retrieve_history | draft → lessons

- 结构化指标由 Java 聚合（SummaryAggregateService，确定性强），本 Agent 只做：
  审计、澄清回问、历史经验参考、文字总结、经验提炼
- 跨语言中断恢复：thread_id = activity_id；Java resume 时注入 answers 恢复子图
- checkpoint：PostgresSaver（与对话 Agent 共用同一连接池配置）
"""
import json
import logging
from typing import Any, TypedDict

from langchain_core.messages import HumanMessage, SystemMessage
from langgraph.graph import END, START, StateGraph
from langgraph.types import Command, interrupt

from . import config
from .graph import build_llm
from .persistence import get_saver
# 历史经验由 Java 聚合预检索（input.experience），本模块不再回调 Java

logger = logging.getLogger(__name__)

MAX_QUESTIONS = 3


class SummaryState(TypedDict, total=False):
    activity_id: str
    input: dict          # Java 聚合输入（指标快照）
    audit: dict          # 审计结果 {signals: [...]}
    decision: dict       # decide 输出 {decision, questions}
    answers: dict        # 发起人回答 {qid: answer}
    history: str         # 历史经验检索结果
    report_text: str     # AI 总结文字
    lessons: list        # 经验条目 [{category, title, content, metrics}]
    status: str          # success / awaiting


_llm = None
_graph = None


def _model():
    global _llm
    if _llm is None:
        _llm = build_llm()
    return _llm


def _llm_json(system: str, user: str, default: Any) -> Any:
    """LLM 结构化 JSON 输出；解析失败返回 default（图永不因 LLM 异常中断）。"""
    try:
        resp = _model().invoke([SystemMessage(content=system), HumanMessage(content=user)])
        text = (resp.content or "").strip()
        # 容错：LLM 可能输出说明文本/代码块/多个对象，只取第一个完整 JSON 值
        decoder = json.JSONDecoder()
        for ch in ("{", "["):
            idx = text.find(ch)
            if idx >= 0:
                try:
                    obj, _ = decoder.raw_decode(text[idx:])
                    return obj
                except Exception:
                    continue
        return default
    except Exception as e:
        logger.warning("总结 LLM JSON 输出失败，用默认值：%s", e)
        return default


# ================= 子图节点 =================

def audit(state: SummaryState) -> dict:
    """数据审计（确定性规则，不调 LLM）：产出异常信号清单，供 decide 判断。"""
    inp = state["input"]
    signup, attendance = inp.get("signup") or {}, inp.get("attendance") or {}
    record, reward = inp.get("record") or {}, inp.get("reward") or {}
    discussion = inp.get("discussion") or {}
    signals = []
    if not inp.get("activity"):
        signals.append("活动基本信息缺失，总结需注明")
    if signup.get("total", 0) == 0:
        signals.append("无人报名，总结需说明组织情况")
    if attendance.get("expected", 0) > 0 and attendance.get("present", 0) < attendance["expected"] * 0.7:
        signals.append("到场率明显偏低，需发起人说明原因")
    if record.get("submitted", 0) == 0:
        signals.append("无留痕提交，无法评价执行质量")
    elif record.get("coverage", 0) < 0.5:
        signals.append("留痕覆盖率偏低，需发起人说明原因")
    if record.get("missing"):
        signals.append("存在未提交留痕的成员，需发起人说明是否正常")
    if discussion.get("message_count", 0) == 0:
        signals.append("讨论区无消息，前期讨论参与度低")
    if reward.get("adopted_suggestions", 0) == 0:
        signals.append("无采纳建议，奖励环节未发挥作用")
    return {"audit": {"signals": signals}}


def decide(state: SummaryState) -> dict:
    """LLM 决策：审计信号 + 指标摘要 → clarify / history / ready（附待确认问题）。"""
    inp = state["input"]
    signals = (state.get("audit") or {}).get("signals", [])
    answers = state.get("answers")
    brief = {
        "signup": inp.get("signup"), "attendance": inp.get("attendance"),
        "record": inp.get("record"), "reward": inp.get("reward"),
        "discussion": inp.get("discussion"),
    }
    sys_prompt = (
        "你是社团活动总结的数据审查员。根据数据审计信号与指标摘要，决定总结生成路径，输出 JSON：\n"
        '{"decision": "clarify|history|ready", "questions": [{"id": "q1", "question": "问题文本"}]}\n'
        "规则：\n"
        "1. clarify：仅当存在需要发起人解释才能写准总结的信号（如留痕覆盖率低、成员未提交），一次最多 3 个问题，问题必须具体可答；\n"
        "2. history：数据完整但主题有先例可循（如常规团建/讲座），可先检索历史经验再写；\n"
        "3. ready：无异常信号或异常无需人工解释（如无人报名），直接基于现有数据写总结并注明局限；\n"
        "4. 已收到发起人回答时（answers 非空），不得再选 clarify。"
    )
    user_prompt = json.dumps({"signals": signals, "metrics": brief}, ensure_ascii=False)
    out = _llm_json(sys_prompt, user_prompt, {"decision": "ready", "questions": []})
    decision = out.get("decision") if isinstance(out, dict) else "ready"
    if decision not in ("clarify", "history", "ready"):
        decision = "ready"
    questions = out.get("questions") if isinstance(out, dict) else []
    questions = [q for q in questions if isinstance(q, dict) and q.get("id") and q.get("question")]
    # 防死锁：resume 后（answers 已存在）不再二次中断；clarify 无实际问题则降级
    if decision == "clarify" and answers is not None:
        decision = "history" if not state.get("history") else "ready"
    if decision == "clarify" and not questions:
        decision = "history" if not state.get("history") else "ready"
    return {"decision": {"decision": decision, "questions": questions[:MAX_QUESTIONS]}}


def route_after_decide(state: SummaryState) -> str:
    return state["decision"]["decision"]


def clarify(state: SummaryState) -> dict:
    """回问闭环：首次进入中断等待发起人回答；resume 注入 answers 后继续（不二次中断）。"""
    if state.get("answers"):
        return {}
    qs = state["decision"]["questions"]
    answered = interrupt({"questions": qs})
    return {"answers": answered if isinstance(answered, dict) else {}}


def retrieve_history(state: SummaryState) -> dict:
    """历史经验参考：直接消费 Java 预检索结果（聚合快照携带，避免跨服务回调与权限透传）。"""
    items = ((state["input"].get("experience")) or {}).get("items") or []
    if items:
        lines = [f"- [{it.get('category')}] {it.get('title')}：{it.get('content')}" for it in items]
        return {"history": "【历史经验参考】\n" + "\n".join(lines)}
    return {"history": ""}


def draft(state: SummaryState) -> dict:
    """AI 总结报告（LLM）：基于结构化指标 + 发起人回答 + 历史经验。"""
    inp = state["input"]
    activity = inp.get("activity") or {}
    answers = state.get("answers") or {}
    history = state.get("history") or ""
    sys_prompt = (
        "你是社团活动总结撰写专家。根据结构化指标撰写 500-800 字的中文总结报告，Markdown 格式，分节："
        "## 活动概况 / ## 数据回顾 / ## 亮点 / ## 不足与原因 / ## 改进建议。"
        "要求：只陈述指标支撑的事实；指标缺失或异常处如实注明；改进建议要具体可执行。"
    )
    user_prompt = json.dumps({
        "activity": activity,
        "metrics": {k: inp.get(k) for k in ("signup", "attendance", "record", "reward", "discussion")},
        "发起人补充": answers,
        "历史经验参考": history,
    }, ensure_ascii=False)
    try:
        resp = _model().invoke([SystemMessage(content=sys_prompt), HumanMessage(content=user_prompt)])
        text = str(resp.content or "").strip()
        if not text:
            raise ValueError("空报告")
        return {"report_text": text}
    except Exception as e:
        logger.warning("总结报告生成失败，降级结构化摘要：%s", e)
        return {"report_text": _fallback_report(inp)}


def _fallback_report(inp: dict) -> str:
    """降级：LLM 失败时输出结构化数据摘要，保证总结不为空。"""
    signup, attendance = inp.get("signup") or {}, inp.get("attendance") or {}
    record, discussion = inp.get("record") or {}, inp.get("discussion") or {}
    lines = [
        "## 活动概况", f"（AI 生成暂不可用，以下为结构化数据摘要）",
        "## 数据回顾",
        f"- 报名 {signup.get('total', 0)} 人（参加 {signup.get('participate', 0)}，不参加 {signup.get('not_participate', 0)}）",
        f"- 签到：应到 {attendance.get('expected', 0)}，实到 {attendance.get('present', 0)}",
        f"- 留痕提交 {record.get('submitted', 0)} 份，覆盖率 {record.get('coverage', 0)}",
        f"- 讨论消息 {discussion.get('message_count', 0)} 条",
        "## 亮点 / ## 不足与原因 / ## 改进建议", "（待 AI 恢复后重新生成完整总结）",
    ]
    return "\n".join(lines)


def lessons(state: SummaryState) -> dict:
    """经验提炼（LLM）：从总结与指标提炼 2-4 条可复用经验，落统一经验库。"""
    inp = state["input"]
    report = state.get("report_text") or ""
    sys_prompt = (
        "你是社团活动经验提炼师。根据活动总结与指标，提炼 2-4 条对未来活动可复用的经验，输出 JSON 数组："
        '[{"category": "筹备知识|总结教训|context", "title": "一句话标题", "content": "场景与做法（50-200字）", '
        '"metrics": {"key": "value"}}]。category 只能是三个之一；metrics 放 1-3 个相关指标值。'
    )
    user_prompt = json.dumps({
        "activity": (inp.get("activity") or {}).get("content"),
        "metrics": {k: inp.get(k) for k in ("signup", "attendance", "record", "reward", "discussion")},
        "report": report[:2000],
    }, ensure_ascii=False)
    out = _llm_json(sys_prompt, user_prompt, [])
    # 容错：模型可能包一层 {"lessons": [...]} 或 {"items": [...]}
    items = out if isinstance(out, list) else \
        (out.get("lessons") or out.get("items") or []) if isinstance(out, dict) else []
    cleaned = []
    for it in items:
        if not isinstance(it, dict) or not it.get("title") or not it.get("content"):
            continue
        cat = it.get("category") if it.get("category") in ("筹备知识", "总结教训", "context") else "总结教训"
        cleaned.append({
            "category": cat,
            "title": str(it["title"])[:60],
            "content": str(it["content"])[:200],
            "metrics": it.get("metrics") if isinstance(it.get("metrics"), dict) else {},
        })
    if not cleaned:
        cleaned = _fallback_lessons(inp)
    return {"lessons": cleaned}


def _fallback_lessons(inp: dict) -> list:
    """降级：LLM 失败时基于指标给结构化兜底经验，保证经验库沉淀不为空。"""
    record = inp.get("record") or {}
    discussion = inp.get("discussion") or {}
    out = []
    if record.get("coverage", 1) < 0.5:
        out.append({"category": "总结教训", "title": "留痕覆盖率低需提前强调",
                    "content": f"本次留痕覆盖率仅 {record.get('coverage')}，下次活动应在执行前向成员强调留痕要求并设置截止提醒。",
                    "metrics": {"coverage": record.get("coverage")}})
    if discussion.get("message_count", 0) > 0:
        out.append({"category": "筹备知识", "title": "讨论区参与度数据可复用",
                    "content": f"本次讨论消息 {discussion.get('message_count')} 条，筹备讨论参与情况可作后续活动组织参考。",
                    "metrics": {"message_count": discussion.get("message_count")}})
    if not out:
        out.append({"category": "总结教训", "title": "活动数据已归档沉淀",
                    "content": "本次活动完成总结归档，指标数据进入经验库供后续活动参考。", "metrics": {}})
    return out


def _build_review_subgraph():
    """子图 ReviewSubgraph：audit → decide(条件边) → clarify/retrieve_history/draft → lessons。"""
    sub = StateGraph(SummaryState)
    sub.add_node("audit", audit)
    sub.add_node("decide", decide)
    sub.add_node("clarify", clarify)
    sub.add_node("retrieve_history", retrieve_history)
    sub.add_node("draft", draft)
    sub.add_node("lessons", lessons)
    sub.add_edge(START, "audit")
    sub.add_edge("audit", "decide")
    sub.add_conditional_edges("decide", route_after_decide, {
        "clarify": "clarify",
        "history": "retrieve_history",
        "ready": "draft",
    })
    sub.add_edge("clarify", "decide")          # 回答后回到决策（answers 已存在，不再 clarify）
    sub.add_edge("retrieve_history", "draft")
    sub.add_edge("draft", "lessons")
    sub.add_edge("lessons", END)
    return sub.compile()


def _build_graph():
    """主图 SummaryGraph：ingest → review(子图) → output；checkpoint=PostgresSaver。"""
    global _graph
    if _graph is None:
        builder = StateGraph(SummaryState)
        builder.add_node("ingest", lambda state: {"input": state.get("input") or {}})
        builder.add_node("review", _build_review_subgraph())
        builder.add_node("output", lambda state: {"status": "success"})
        builder.add_edge(START, "ingest")
        builder.add_edge("ingest", "review")
        builder.add_edge("review", "output")
        builder.add_edge("output", END)
        _graph = builder.compile(checkpointer=get_saver())
        logger.info("总结 Agent 就绪：主图 ingest→review→output，子图 audit→decide→clarify/history/draft→lessons")
    return _graph


def _finish(state: dict) -> dict:
    """统一收口：把最终状态转成 Java 期望的响应结构。"""
    return {
        "status": "success",
        "report": {
            "metrics": state.get("input") or {},
            "report_text": state.get("report_text") or "",
        },
        "lessons": state.get("lessons") or [],
    }


def summarize(activity_id: str, input_data: dict) -> dict:
    """首次生成（Java 进总结中自动触发 / 手动重生成）。
    返回 {status: success|awaiting, report, lessons, questions}。"""
    graph = _build_graph()
    cfg = {"configurable": {"thread_id": activity_id}, "recursion_limit": 30}
    graph.invoke({"activity_id": activity_id, "input": input_data}, cfg)
    st = graph.get_state(cfg)
    if st.next:
        qs = []
        if st.tasks and st.tasks[0].interrupts:
            qs = st.tasks[0].interrupts[0].value.get("questions") or []
        return {"status": "awaiting", "questions": qs}
    return _finish(st.values)


def resume(activity_id: str, answers: dict) -> dict:
    """发起人回答后恢复生成（Java resume 接口）：Command(resume=answers) 注入中断点。"""
    graph = _build_graph()
    cfg = {"configurable": {"thread_id": activity_id}}
    graph.invoke(Command(resume=answers), cfg)
    st = graph.get_state(cfg)
    if st.next:
        logger.warning("总结恢复后仍中断（异常路径）activity=%s", activity_id)
    return _finish(st.values)