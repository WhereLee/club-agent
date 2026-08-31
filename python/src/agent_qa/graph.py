"""问答 Agent 图：LangGraph create_agent（ReAct 主循环）+ PostgresSaver。

独立于概念起草主 Agent：自己的工具集（search_knowledge）、自己的系统提示词、
自己的会话键（thread_id = qa_session_id）；共享 checkpoint 表（按 thread 隔离）。
"""
import logging

from langchain.agents import create_agent
from langchain_openai import ChatOpenAI
from langgraph.checkpoint.postgres import PostgresSaver

from . import config
from .persistence import get_saver
from .prompts import QA_SYSTEM_PROMPT
from .tools import QA_TOOLS

logger = logging.getLogger(__name__)

_agent = None


def build_llm() -> ChatOpenAI:
    return ChatOpenAI(
        model=config.LLM_MODEL,
        base_url=config.LLM_BASE_URL,
        api_key=config.LLM_API_KEY,
        temperature=0.3,  # 问答求稳，比起草更低
        timeout=config.LLM_TIMEOUT_SECONDS,
    )


def build_agent():
    """组装问答 Agent（懒加载单例）。"""
    global _agent
    if _agent is None:
        saver: PostgresSaver = get_saver()
        _agent = create_agent(
            model=build_llm(),
            tools=QA_TOOLS,
            system_prompt=QA_SYSTEM_PROMPT,
            checkpointer=saver,
        )
        logger.info("问答 Agent 就绪：model=%s tools=%d", config.LLM_MODEL, len(QA_TOOLS))
    return _agent


def run_agent(thread_id: str, message: str) -> tuple[str, list[dict]]:
    """单轮问答：带 thread_id 检查点续聊，返回 (回答, 本轮工具调用记录)。

    工具调用记录元素：{"tool_name", "tool_args"(JSON 串), "tool_result"}，
    由 Java 侧落 qa_message 表（role=tool），保证会话表是完整事实源。
    """
    agent = build_agent()
    tools: list[dict] = []
    reply = ""
    for chunk in agent.stream(
        {"messages": [{"role": "user", "content": message}]},
        config={
            "configurable": {"thread_id": thread_id},
            "recursion_limit": config.CHAT_MAX_STEPS,
        },
        stream_mode="updates",
    ):
        for node_update in chunk.values():
            for msg in node_update.get("messages", []):
                mtype = getattr(msg, "type", "")
                if mtype == "tool":
                    tools.append({
                        "tool_name": getattr(msg, "name", ""),
                        "tool_args": "",  # args 在 AI 消息的 tool_calls 上，此处只留结果溯源
                        "tool_result": str(getattr(msg, "content", ""))[:4000],
                    })
                elif mtype == "ai":
                    content = getattr(msg, "content", "")
                    if isinstance(content, str) and content.strip():
                        reply = content
                    # 补全工具入参（ai 消息的 tool_calls 与 tool 消息一一对应）
                    for tc in getattr(msg, "tool_calls", None) or []:
                        if tools and tools[-1]["tool_name"] == tc.get("name") and not tools[-1]["tool_args"]:
                            import json
                            try:
                                tools[-1]["tool_args"] = json.dumps(tc.get("args") or {}, ensure_ascii=False)
                            except Exception:
                                pass
    return reply, tools
