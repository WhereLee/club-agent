"""配置：环境变量 > club-agent/.env > rag/.env（LLM 密钥单一来源，不复制）"""
import os
from pathlib import Path

from dotenv import load_dotenv

PROJECT_ROOT = Path(__file__).resolve().parents[3]  # club-agent/
CLUB_ENV = PROJECT_ROOT / ".env"
RAG_ENV = PROJECT_ROOT.parent / "rag" / ".env"  # 目录重整后 rag 项目在工作区根/rag/ 下（LLM 密钥来源）

# 先加载项目自身 .env，再补 rag/.env（不覆盖已存在的变量）
if CLUB_ENV.exists():
    load_dotenv(CLUB_ENV)
if RAG_ENV.exists():
    load_dotenv(RAG_ENV, override=False)


def _require(name: str) -> str:
    val = os.getenv(name)
    if not val:
        raise RuntimeError(f"缺少环境变量 {name}（检查 club-agent/.env 或 rag/.env）")
    return val


# ---------- LLM（DeepSeek / MiMo，OpenAI 兼容协议） ----------
LLM_BASE_URL = _require("LLM_BASE_URL")
LLM_API_KEY = _require("LLM_API_KEY")
LLM_MODEL = _require("LLM_MODEL")
LLM_ENABLE_THINKING = os.getenv("LLM_ENABLE_THINKING", "0") == "1"
LLM_TIMEOUT_SECONDS = int(os.getenv("LLM_TIMEOUT_SECONDS", "60"))

# ---------- 数据库（PostgresSaver 会话检查点；业务事实源在 Java 侧） ----------
_SPRING_URL = _require("SPRING_DATASOURCE_URL")  # jdbc:postgresql://host:port/db
DB_USER = _require("SPRING_DATASOURCE_USERNAME")
DB_PASSWORD = _require("SPRING_DATASOURCE_PASSWORD")

# jdbc:postgresql://127.0.0.1:5432/club_agent -> postgresql://user:pass@127.0.0.1:5432/club_agent
_db_url = _SPRING_URL.replace("jdbc:", "", 1)
DB_URL = f"postgresql://{DB_USER}:{DB_PASSWORD}@{_db_url.split('://', 1)[1]}"

# ---------- S5：内部 secret（Java 调用本服务校验；未配置则跳过，仅限本地开发） ----------
AI_DRAFT_INTERNAL_SECRET = os.getenv("AI_DRAFT_INTERNAL_SECRET", "").strip()
if not AI_DRAFT_INTERNAL_SECRET:
    print("WARN: AI_DRAFT_INTERNAL_SECRET 未配置，内部接口不校验 X-Internal-Secret（仅限本地开发，生产必须配置）")

# ---------- 服务 ----------
SERVICE_PORT = int(os.getenv("AGENT_DRAFT_PORT", "8094"))
CHAT_MAX_STEPS = int(os.getenv("AGENT_CHAT_MAX_STEPS", "15"))  # ReAct 循环上限（防失控）

# ---------- B1：search_experience 工具内部联网兜底（MiMo 原生 web_search，独立请求不污染主 Agent） ----------
WEB_SEARCH_ENABLED = os.getenv("B1_WEB_SEARCH_ENABLED", "1") == "1"
WEB_SEARCH_MODEL = os.getenv("B1_WEB_SEARCH_MODEL", LLM_MODEL)
WEB_SEARCH_LOW_WATER = int(os.getenv("B1_WEB_SEARCH_LOW_WATER", "3"))  # 水位低于此值触发兜底
