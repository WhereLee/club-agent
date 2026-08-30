"""对话 Agent 图：LangGraph create_agent（ReAct 主循环），按会话类型参数化。

- concept：概念起草助手（D1 起；thread_id = concept_id）
- file：活动正式文件撰写助手（活动前 Agent；thread_id = activity_id）

两种会话共享模型与 PostgresSaver 检查点，工具集与系统提示词按类型隔离
（CONCEPT_TOOLS / FILE_TOOLS），避免概念会话误用文件工具。
"""
import json
import logging

from langchain.agents import create_agent
from langchain_openai import ChatOpenAI
from langgraph.checkpoint.postgres import PostgresSaver

from . import config
from .persistence import get_saver
from .prompts import ACTIVITY_FILE_SYSTEM_PROMPT, SYSTEM_PROMPT
from .tools import CONCEPT_TOOLS, FILE_TOOLS

logger = logging.getLogger(__name__)

# 会话类型 → (工具集, 系统提示词)
AGENT_CONFIGS = {
    "concept": (CONCEPT_TOOLS, SYSTEM_PROMPT),
    "file": (FILE_TOOLS, ACTIVITY_FILE_SYSTEM_PROMPT),
}

_agents: dict[str, object] = {}


def build_llm() -> ChatOpenAI:
    """DeepSeek / MiMo（OpenAI 兼容协议）。"""
    kwargs = {
        "model": config.LLM_MODEL,
        "base_url": config.LLM_BASE_URL,
        "api_key": config.LLM_API_KEY,
        "temperature": 0.6,
        "timeout": config.LLM_TIMEOUT_SECONDS,
    }
    if config.LLM_ENABLE_THINKING:
        kwargs["extra_body"] = {"thinking": {"type": "enabled"}}
    return ChatOpenAI(**kwargs)


def build_agent(session_type: str):
    """组装指定会话类型的对话 Agent（懒加载单例）。"""
    if session_type not in AGENT_CONFIGS:
        raise ValueError(f"未知会话类型：{session_type}")
    global _agents
    if session_type not in _agents:
        tools, system_prompt = AGENT_CONFIGS[session_type]
        llm = build_llm()
        saver: PostgresSaver = get_saver()
        _agents[session_type] = create_agent(
            model=llm,
            tools=tools,
            system_prompt=system_prompt,
            checkpointer=saver,
        )
        logger.info("对话 Agent 就绪：type=%s model=%s tools=%d", session_type, config.LLM_MODEL, len(tools))
    return _agents[session_type]


def run_agent(session_type: str, thread_id: str, message: str) -> tuple[str, list[dict]]:
    """单轮对话：stream 图（带 thread_id 检查点续聊），返回 (assistant 最终回复, 本轮工具调用列表)。

    工具调用列表元素：{"tool_name": str, "tool_args": str(JSON), "tool_result": str}。
    由 Java 侧落业务会话表（role=tool），保证会话表是完整事实源。
    """
    agent = build_agent(session_type)
    tools: list[dict] = []
    reply = ""
    for chunk in agent.stream(
        {"messages": [{"role": "user", "content": message}]},
        config={
            "configurable": {"thread_id": thread_id},
            "recursion_limit": config.CHAT_MAX_STEPS,
        },
        stream_mode="updates",  # 显式按节点更新消费（chunk = {节点名: {"messages": [...]}}）
    ):
        # 按消息类型收集（model 节点出 AI 消息，tools 节点出 ToolMessage；不依赖节点名）
        for step in chunk.values():
            for msg in step.get("messages", []):
                t = getattr(msg, "type", "")
                if t == "tool":
                    # 工具输出：填充最近一个同名的未填充调用（支持并行调用）
                    name = getattr(msg, "name", "")
                    for tc in reversed(tools):
                        if tc["tool_name"] == name and not tc["tool_result"]:
                            tc["tool_result"] = str(getattr(msg, "content", ""))
                            break
                elif t == "ai":
                    calls = getattr(msg, "tool_calls", None)
                    if calls:
                        for call in calls:
                            tools.append({
                                "tool_name": call.get("name", ""),
                                "tool_args": _json_dumps(call.get("args", {})),
                                "tool_result": "",
                            })
                    else:
                        # 无工具调用的 AI 消息 = 最终回复（或中间说明）
                        reply = str(getattr(msg, "content", ""))
    return reply, tools


def chat(concept_id: str, message: str) -> tuple[str, list[dict]]:
    """概念起草会话（兼容入口，thread_id = concept_id）。"""
    return run_agent("concept", concept_id, message)


def file_chat(activity_id: str, message: str) -> tuple[str, list[dict]]:
    """正式文件撰写会话（thread_id = activity_id）。"""
    return run_agent("file", activity_id, message)


def _json_dumps(obj: dict) -> str:
    try:
        return json.dumps(obj, ensure_ascii=False)
    except Exception:
        return str(obj)
