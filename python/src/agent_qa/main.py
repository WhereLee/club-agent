"""FastAPI 入口：管理层经验问答服务（agent_qa，独立 Agent 服务）。

接口：
- GET  /health  健康检查
- POST /chat    单轮问答（body: {club_id, session_id, message}）

调用方：Java 后端代理（携带用户 JWT 透传 + 内部密钥；权限判断在 Java 业务层）。
"""
import logging

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from . import config
from .graph import run_agent
from .java_client import set_request_context

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")

app = FastAPI(title="agent-qa", version="0.1.0")


@app.middleware("http")
async def internal_secret_check(request: Request, call_next):
    """内部密钥校验：Java 携带 X-Internal-Secret 才放行；未配置 secret 时跳过（仅限本地开发）。"""
    if request.url.path == "/health":
        return await call_next(request)
    secret = config.QA_INTERNAL_SECRET
    if secret and request.headers.get("X-Internal-Secret") != secret:
        return JSONResponse(status_code=401, content={"detail": "unauthorized"})
    return await call_next(request)


class ChatRequest(BaseModel):
    club_id: str = Field(..., description="社团雪花 ID（工具回调 Java 的路径参数）")
    session_id: str = Field(..., description="问答会话雪花 ID（thread_id，会话隔离键）")
    message: str = Field(..., min_length=1, max_length=2000, description="管理层本轮提问")


class ChatResponse(BaseModel):
    reply: str
    tools: list[dict] = Field(default_factory=list, description="本轮工具调用记录（Java 落库 role=tool）")


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "model": config.LLM_MODEL}


@app.post("/chat", response_model=ChatResponse)
def chat_endpoint(req: ChatRequest, request: Request) -> ChatResponse:
    # 身份透传 + 社团上下文注入（工具回调 Java /ai/knowledge 时使用；权限判断永远在 Java）
    set_request_context(
        auth_header=request.headers.get("Authorization"),
        club_id=req.club_id,
    )
    reply, tools = run_agent(req.session_id, req.message)
    return ChatResponse(reply=reply, tools=tools)


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="127.0.0.1", port=config.SERVICE_PORT)
