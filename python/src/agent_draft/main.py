"""FastAPI 入口：概念阶段起草助手服务。

接口：
- GET  /health        健康检查
- POST /chat          单轮对话（body: {concept_id, message}）
- POST /ai/brief      发起人想法简析（D3：Java 提交后异步调用，重放会话纯生成）

调用方：Java 后端代理（携带用户会话；本服务不做权限判断，权限在 Java 业务层）。
"""
import logging
from typing import Any

from fastapi import FastAPI, Request
from pydantic import BaseModel, Field

from . import config
from .brief import generate_brief
from .graph import chat, file_chat
from .summary_graph import resume as summary_resume
from .summary_graph import summarize as summary_generate
from .tools.java_client import set_request_context

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")

app = FastAPI(title="agent-draft", version="0.1.0")


class ChatRequest(BaseModel):
    club_id: str = Field(..., description="社团雪花 ID（工具回调 Java 的路径参数）")
    concept_id: str = Field(..., description="概念雪花 ID（thread_id，会话隔离键）")
    message: str = Field(..., min_length=1, max_length=2000, description="发起人本轮消息")


class ChatResponse(BaseModel):
    reply: str
    tools: list[dict] = Field(default_factory=list, description="本轮工具调用记录（Java 落库 role=tool）")


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "model": config.LLM_MODEL}


@app.post("/chat", response_model=ChatResponse)
def chat_endpoint(req: ChatRequest, request: Request) -> ChatResponse:
    # 身份透传 + 会话上下文注入（工具回调 Java 时使用；权限判断永远在 Java）
    set_request_context(
        auth_header=request.headers.get("Authorization"),
        club_id=req.club_id,
        concept_id=req.concept_id,
    )
    reply, tools = chat(req.concept_id, req.message)
    return ChatResponse(reply=reply, tools=tools)


class BriefMessage(BaseModel):
    role: str
    tool_name: str = ""
    content: str = ""


class BriefRequest(BaseModel):
    messages: list[BriefMessage] = Field(..., description="完整会话（user/assistant/tool）")
    form: dict[str, Any] = Field(..., description="最终企划表单四字段")


class BriefResponse(BaseModel):
    brief: str


@app.post("/ai/brief", response_model=BriefResponse)
def brief_endpoint(req: BriefRequest) -> BriefResponse:
    """D3：想法简析（Java 提交后异步调用；失败抛 500，Java 侧捕获降级不影响提交）。"""
    messages = [m.model_dump() for m in req.messages]
    brief = generate_brief(messages, req.form)
    return BriefResponse(brief=brief)


class ActivityFileChatRequest(BaseModel):
    club_id: str = Field(..., description="社团雪花 ID（工具回调 Java 的路径参数）")
    activity_id: str = Field(..., description="活动雪花 ID（thread_id，会话隔离键）")
    message: str = Field(..., min_length=1, max_length=2000, description="发起人本轮消息")


@app.post("/ai/activity-file/chat", response_model=ChatResponse)
def activity_file_chat_endpoint(req: ActivityFileChatRequest, request: Request) -> ChatResponse:
    """正式文件撰写会话（活动前 Agent）：身份透传 + 活动上下文注入；权限判断在 Java。"""
    set_request_context(
        auth_header=request.headers.get("Authorization"),
        club_id=req.club_id,
        activity_id=req.activity_id,
    )
    reply, tools = file_chat(req.activity_id, req.message)
    return ChatResponse(reply=reply, tools=tools)


class SummarizeRequest(BaseModel):
    activity_id: str = Field(..., description="活动雪花 ID（thread_id，中断恢复键）")
    input: dict = Field(..., description="Java 聚合的结构化指标（SummaryAggregateService）")


class SummarizeResponse(BaseModel):
    status: str
    report: dict | None = None
    lessons: list[dict] = Field(default_factory=list)
    questions: list[dict] = Field(default_factory=list)


@app.post("/agent/summarize", response_model=SummarizeResponse)
def summarize_endpoint(req: SummarizeRequest) -> SummarizeResponse:
    """活动总结生成（活动后阶段）：Java 进总结中自动触发 / 手动重生成。
    子图自主决策：需要发起人补充时返回 status=awaiting + questions（跨语言中断）。"""
    out = summary_generate(req.activity_id, req.input)
    return SummarizeResponse(
        status=out.get("status", "failed"),
        report=out.get("report"),
        lessons=out.get("lessons") or [],
        questions=out.get("questions") or [],
    )


class SummarizeResumeRequest(BaseModel):
    activity_id: str = Field(..., description="活动雪花 ID（thread_id）")
    answers: dict = Field(..., description="发起人对待确认问题的回答 {qid: answer}")


@app.post("/agent/summarize/resume", response_model=SummarizeResponse)
def summarize_resume_endpoint(req: SummarizeResumeRequest) -> SummarizeResponse:
    """回问闭环恢复：注入 answers 到中断点，子图继续生成。"""
    out = summary_resume(req.activity_id, req.answers)
    return SummarizeResponse(
        status=out.get("status", "failed"),
        report=out.get("report"),
        lessons=out.get("lessons") or [],
        questions=out.get("questions") or [],
    )


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="127.0.0.1", port=config.SERVICE_PORT)
